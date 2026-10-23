// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.channels.AssignedNumber
import ca.gureet.plasma.channels.ChannelNumberer
import ca.gureet.plasma.channels.ChannelRef
import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.channels.PlaylistEntry
import ca.gureet.plasma.channels.SourceEntries
import ca.gureet.plasma.core.database.ChannelEntity
import ca.gureet.plasma.core.database.GroupEntity
import ca.gureet.plasma.core.database.NumberUpdate
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.ProgrammeEntity
import ca.gureet.plasma.core.database.SourceEntity
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.network.Redact
import ca.gureet.plasma.epg.GuideKey
import ca.gureet.plasma.epg.GuideMatcher
import ca.gureet.plasma.epg.XmltvChannel
import ca.gureet.plasma.epg.XmltvProgramme
import ca.gureet.plasma.epg.XmltvReader
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What a channel import did. */
data class ChannelImport(
    val channels: Int,
    val groups: Int,
    /** Channels numbered for the first time. */
    val newChannels: Int,
    /** Channels that lost a contested number and were moved (brief 4.30). */
    val newDuplicates: Int,
)

/** What a guide import did. */
data class GuideImport(val programmes: Int, val matchedChannels: Int)

/**
 * The add-source pipeline (brief 6.4): channels and groups first, numbered, so Live TV works at
 * once; then the guide, in the background. Each import fetches everything before it writes, and
 * writes in one swap, so a failed import never empties a list (5.1).
 */
class SourceImporter(
    private val db: PlasmaDatabase,
    private val sources: SourceRepository,
    private val settings: SettingsStore,
    private val http: OkHttpClient,
    private val clock: PlasmaClock,
) {
    // Numbering reads every source, so channel imports take turns; so do guide imports.
    private val channelLock = Mutex()
    private val guideLock = Mutex()

    suspend fun importChannels(sourceId: String): ChannelImport = channelLock.withLock {
        val source = db.sources().get(sourceId) ?: throw NoSuchElementException("No source $sourceId")
        recordingFailure(source) {
            val client = sources.xtreamClient(source)
            val account = client.signIn()
            val categories = client.liveCategories()
            val streams = client.liveStreams().distinctBy { it.streamId }
            sources.recordAccount(sourceId, account)

            val oldGroups = db.groups().forSource(sourceId).associateBy { it.remoteId }
            val existing = db.channels().all()
            val generation = (existing.filter { it.sourceId == sourceId }.maxOfOrNull { it.generation } ?: 0) + 1
            val groups = categories.distinctBy { it.id }.mapIndexed { index, category ->
                GroupEntity(
                    id = "$sourceId:${category.id}",
                    sourceId = sourceId,
                    remoteId = category.id,
                    name = category.name,
                    position = index,
                    hidden = oldGroups[category.id]?.hidden ?: false,
                    generation = generation,
                )
            }
            val groupIds = groups.mapTo(HashSet()) { it.remoteId }

            // Every enabled source is numbered together, the primary first (brief 5.2).
            val enabled = db.sources().all().filter { it.enabled || it.id == sourceId }
            val entries = enabled.map { s ->
                if (s.id == sourceId) {
                    SourceEntries(sourceId, streams.map { PlaylistEntry(it.streamId, it.number) })
                } else {
                    SourceEntries(s.id, existing.filter { it.sourceId == s.id }.sortedBy { it.position }.map { PlaylistEntry(it.key, it.playlistNumber) })
                }
            }
            val previous = existing.associate { channel ->
                ChannelRef(channel.sourceId, channel.key) to AssignedNumber(channel.number, reasonOf(channel.numberReason), channel.wantedNumber)
            }
            val overrides = db.numberOverrides().all().associate { ChannelRef(it.sourceId, it.channelKey) to it.number }
            val numberingSettings = settings.numberingNow()
            val numbering = ChannelNumberer.assign(entries, previous, overrides, numberingSettings.source, numberingSettings.duplicateRule)

            val old = existing.filter { it.sourceId == sourceId }.associateBy { it.key }
            val channels = streams.mapIndexed { index, stream ->
                val number = numbering.numbers.getValue(ChannelRef(sourceId, stream.streamId))
                val before = old[stream.streamId]
                ChannelEntity(
                    id = "$sourceId:${stream.streamId}",
                    sourceId = sourceId,
                    key = stream.streamId,
                    groupId = stream.categoryId?.takeIf { it in groupIds }?.let { "$sourceId:$it" },
                    name = stream.name,
                    position = index,
                    playlistNumber = stream.number,
                    number = number.number,
                    numberReason = number.reason.name,
                    wantedNumber = number.wanted,
                    logo = stream.logo,
                    tvgId = stream.epgChannelId,
                    // Kept until the next guide import matches it again.
                    epgKey = before?.epgKey,
                    catchupDays = stream.archiveDays,
                    isRadio = stream.isRadio,
                    hidden = before?.hidden ?: false,
                    locked = before?.locked ?: false,
                    generation = generation,
                )
            }
            val renumbered = existing.filter { it.sourceId != sourceId }.mapNotNull { channel ->
                val number = numbering.numbers[ChannelRef(channel.sourceId, channel.key)] ?: return@mapNotNull null
                if (number.number == channel.number && number.reason.name == channel.numberReason) return@mapNotNull null
                NumberUpdate(channel.id, number.number, number.reason.name, number.wanted)
            }

            db.channels().replaceSource(sourceId, generation, groups, channels, renumbered)
            db.sources().get(sourceId)?.let {
                db.sources().upsert(it.copy(channelsRefreshedAt = clock.now().toEpochMilli(), lastError = null))
            }
            ChannelImport(
                channels = channels.size,
                groups = groups.size,
                newChannels = numbering.newlyNumbered.count { it.sourceId == sourceId },
                newDuplicates = numbering.newDuplicates.size,
            )
        }
    }

    /**
     * Imports the source's full XMLTV guide (brief 5.3), keeping the past for as long as its
     * longest catch-up archive, and matching guide channels to channels by id, then by name.
     */
    suspend fun importGuide(sourceId: String, onProgress: (programmes: Int) -> Unit = {}): GuideImport = guideLock.withLock {
        val source = db.sources().get(sourceId) ?: throw NoSuchElementException("No source $sourceId")
        recordingFailure(source) {
            val client = sources.xtreamClient(source)
            val channels = db.channels().forSource(sourceId)
            val keys = channels.map { GuideKey(it.id, it.tvgId, it.name) }
            val now = clock.now().toEpochMilli()
            val pastDays = (channels.maxOfOrNull { it.catchupDays ?: 0 } ?: 0).coerceAtLeast(1)
            val generation = (db.programmes().maxGeneration(sourceId) ?: 0) + 1
            val request = Request.Builder().url(client.xmltvUrl()).apply { source.userAgent?.let { header("User-Agent", it) } }.build()

            val guideChannels = mutableListOf<XmltvChannel>()
            var matches: Map<String, List<String>>? = null
            var count = 0
            try {
                withContext(Dispatchers.IO) {
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("The guide answered with HTTP ${response.code}")
                        val batch = ArrayList<ProgrammeEntity>(BATCH)
                        XmltvReader(notBefore = now - pastDays * DAY, offsetMillis = source.epgOffsetMinutes * MINUTE).read(
                            response.body.byteStream(),
                            onChannel = { guideChannels += it },
                            onProgramme = { programme ->
                                // XMLTV lists every channel before the first programme, so match once here.
                                val matched = matches ?: GuideMatcher.match(keys, guideChannels).also { matches = it }
                                val guideId = programme.channelId.lowercase()
                                if (guideId in matched) {
                                    batch += programme.toEntity(sourceId, "$sourceId:$guideId", generation)
                                    count++
                                    if (batch.size == BATCH) {
                                        ensureActive()
                                        db.programmes().insertAllBlocking(batch.toList())
                                        batch.clear()
                                        onProgress(count)
                                    }
                                }
                            },
                        )
                        if (batch.isNotEmpty()) db.programmes().insertAllBlocking(batch)
                    }
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) { db.programmes().deleteGeneration(sourceId, generation) }
                throw error
            }

            db.programmes().deleteOtherGenerations(sourceId, generation)
            val matched = matches ?: GuideMatcher.match(keys, guideChannels)
            val epgKeys = matched.flatMap { (guideId, channelIds) -> channelIds.map { it to "$sourceId:$guideId" } }.toMap()
            db.channels().setEpgKeys(sourceId, epgKeys)
            db.sources().get(sourceId)?.let { db.sources().upsert(it.copy(guideRefreshedAt = now, lastError = null)) }
            onProgress(count)
            GuideImport(programmes = count, matchedChannels = epgKeys.size)
        }
    }

    /** Runs [block], noting a failure on the source, redacted, before passing it on. */
    private suspend fun <T> recordingFailure(source: SourceEntity, block: suspend () -> T): T = try {
        block()
    } catch (error: Exception) {
        if (error is IOException || error is IllegalArgumentException) {
            withContext(NonCancellable) {
                db.sources().get(source.id)?.let { db.sources().upsert(it.copy(lastError = Redact.text(error.message ?: error.javaClass.simpleName))) }
            }
        }
        throw error
    }

    private fun reasonOf(name: String): NumberReason = runCatching { NumberReason.valueOf(name) }.getOrDefault(NumberReason.Playlist)

    private fun XmltvProgramme.toEntity(sourceId: String, epgKey: String, generation: Long) = ProgrammeEntity(
        sourceId = sourceId,
        epgKey = epgKey,
        startAt = start,
        endAt = end,
        title = title,
        subtitle = subtitle,
        description = description,
        category = category,
        icon = icon,
        season = season,
        episode = episode,
        isNew = isNew,
        generation = generation,
    )

    private companion object {
        const val BATCH = 1_000
        const val MINUTE = 60_000L
        const val DAY = 86_400_000L
    }
}
