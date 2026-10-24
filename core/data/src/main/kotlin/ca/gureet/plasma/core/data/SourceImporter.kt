// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.channels.ChannelNumberer
import ca.gureet.plasma.channels.ChannelRef
import ca.gureet.plasma.channels.NumberChoice
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
import ca.gureet.plasma.source.m3u.M3uEntry
import ca.gureet.plasma.source.m3u.M3uKeys
import ca.gureet.plasma.source.m3u.M3uKind
import ca.gureet.plasma.source.m3u.M3uReader
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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

/** The server turned the playlist's address down (HTTP 401 or 403). */
class PlaylistRefused(val httpCode: Int) : IOException("The server refused the playlist (HTTP $httpCode)")

/** The source lists no live channels: a refresh that finds none keeps the ones there were. */
class NoLiveChannels : IOException("The source lists no live channels")

/** A source's live channels and groups, fetched and not yet saved. */
private class Lineup(val groups: List<LineupGroup>, val channels: List<LineupChannel>)

private class LineupGroup(val remoteId: String, val name: String)

private class LineupChannel(
    val key: String,
    val number: Int?,
    val name: String,
    val logo: String?,
    val groupRemoteId: String?,
    val tvgId: String?,
    val catchupDays: Int?,
    val isRadio: Boolean,
    val streamUrlEnc: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val catchupType: String? = null,
    val catchupSourceEnc: String? = null,
)

/**
 * The add-source pipeline (brief 6.4): channels and groups first, numbered, so Live TV works at
 * once; then the guide, in the background. Each import fetches everything before it writes, and
 * writes in one swap, so a failed import never empties a list (5.1).
 */
class SourceImporter(
    private val db: PlasmaDatabase,
    private val sources: SourceRepository,
    private val settings: SettingsStore,
    private val vault: Vault,
    private val http: OkHttpClient,
    private val clock: PlasmaClock,
    // Numbering reads every source, so channel imports take turns, with the choices on Duplicate
    // Numbers too ([ChannelNumbering]); guide imports take turns among themselves.
    private val channelLock: Mutex = Mutex(),
) {
    private val guideLock = Mutex()

    private val moved = MutableSharedFlow<List<String>>(extraBufferCapacity = 8)

    /**
     * The ids of the channels each channel import moved off a number another channel kept, when
     * it moved any (brief 4.30), for the notice (4.35).
     */
    val newDuplicates: SharedFlow<List<String>> = moved

    // Numbering and building tens of thousands of channels, and encrypting their addresses, stay
    // off the main thread whoever calls.
    suspend fun importChannels(sourceId: String): ChannelImport = withContext(Dispatchers.Default) { importChannelsLocked(sourceId) }

    private suspend fun importChannelsLocked(sourceId: String): ChannelImport = channelLock.withLock {
        val source = db.sources().get(sourceId) ?: throw NoSuchElementException("No source $sourceId")
        recordingFailure(source) {
            val lineup = when (SourceType.of(source.type)) {
                SourceType.Xtream -> xtreamLineup(source)
                SourceType.M3u -> m3uLineup(source)
                SourceType.Stalker -> stalkerLineup(source)
            }
            if (lineup.channels.isEmpty()) throw NoLiveChannels()
            val streams = lineup.channels

            val oldGroups = db.groups().forSource(sourceId).associateBy { it.remoteId }
            val existing = db.channels().all()
            val generation = (existing.filter { it.sourceId == sourceId }.maxOfOrNull { it.generation } ?: 0) + 1
            val groups = lineup.groups.mapIndexed { index, group ->
                GroupEntity(
                    id = "$sourceId:${group.remoteId}",
                    sourceId = sourceId,
                    remoteId = group.remoteId,
                    name = group.name,
                    position = index,
                    hidden = oldGroups[group.remoteId]?.hidden ?: false,
                    generation = generation,
                )
            }
            val groupIds = groups.mapTo(HashSet()) { it.remoteId }

            // Every enabled source is numbered together, the primary first (brief 5.2).
            val enabled = db.sources().all().filter { it.enabled || it.id == sourceId }
            val entries = enabled.map { s ->
                if (s.id == sourceId) {
                    SourceEntries(sourceId, streams.map { PlaylistEntry(it.key, it.number) })
                } else {
                    SourceEntries(s.id, existing.filter { it.sourceId == s.id }.sortedBy { it.position }.map { PlaylistEntry(it.key, it.playlistNumber) })
                }
            }
            val previous = existing.associate { channel -> ChannelRef(channel.sourceId, channel.key) to channel.assigned() }
            val overrides = db.numberOverrides().all().associate { ChannelRef(it.sourceId, it.channelKey) to NumberChoice(it.number, it.wantedNumber) }
            val numberingSettings = settings.numberingNow()
            val numbering = ChannelNumberer.assign(entries, previous, overrides, numberingSettings.source, numberingSettings.duplicateRule)

            val old = existing.filter { it.sourceId == sourceId }.associateBy { it.key }
            val channels = streams.mapIndexed { index, stream ->
                val number = numbering.numbers.getValue(ChannelRef(sourceId, stream.key))
                val before = old[stream.key]
                ChannelEntity(
                    id = "$sourceId:${stream.key}",
                    sourceId = sourceId,
                    key = stream.key,
                    groupId = stream.groupRemoteId?.takeIf { it in groupIds }?.let { "$sourceId:$it" },
                    name = stream.name,
                    position = index,
                    playlistNumber = stream.number,
                    number = number.number,
                    numberReason = number.reason.name,
                    wantedNumber = number.wanted,
                    logo = stream.logo,
                    tvgId = stream.tvgId,
                    // Kept until the next guide import matches it again.
                    epgKey = before?.epgKey,
                    catchupDays = stream.catchupDays,
                    isRadio = stream.isRadio,
                    hidden = before?.hidden ?: false,
                    locked = before?.locked ?: false,
                    generation = generation,
                    streamUrlEnc = stream.streamUrlEnc,
                    userAgent = stream.userAgent,
                    referrer = stream.referrer,
                    catchupType = stream.catchupType,
                    catchupSourceEnc = stream.catchupSourceEnc,
                )
            }
            val renumbered = existing.filter { it.sourceId != sourceId }.mapNotNull { channel ->
                val number = numbering.numbers[ChannelRef(channel.sourceId, channel.key)] ?: return@mapNotNull null
                if (number.number == channel.number && number.reason.name == channel.numberReason) return@mapNotNull null
                NumberUpdate(channel.id, number.number, number.reason.name, number.wanted)
            }

            db.channels().replaceSource(sourceId, generation, groups, channels, renumbered)
            db.sources().markChannelsRefreshed(sourceId, clock.now().toEpochMilli())
            if (numbering.newDuplicates.isNotEmpty()) moved.tryEmit(numbering.newDuplicates.map { "${it.sourceId}:${it.key}" })
            ChannelImport(
                channels = channels.size,
                groups = groups.size,
                newChannels = numbering.newlyNumbered.count { it.sourceId == sourceId },
                newDuplicates = numbering.newDuplicates.size,
            )
        }
    }

    /** An Xtream account's live categories and streams, signing in first (brief 7.2). */
    private suspend fun xtreamLineup(source: SourceEntity): Lineup {
        val client = sources.xtreamClient(source)
        val account = client.signIn()
        val categories = client.liveCategories()
        val streams = client.liveStreams().distinctBy { it.streamId }
        sources.recordAccount(source.id, account)
        return Lineup(
            groups = categories.distinctBy { it.id }.map { LineupGroup(it.id, it.name) },
            channels = streams.map {
                LineupChannel(
                    key = it.streamId,
                    number = it.number,
                    name = it.name,
                    logo = it.logo,
                    groupRemoteId = it.categoryId,
                    tvgId = it.epgChannelId,
                    catchupDays = it.archiveDays,
                    isRadio = it.isRadio,
                )
            },
        )
    }

    /**
     * A portal's genres and live channels, signing in as its box (brief 7.1). Each channel's `cmd`
     * is kept, encrypted, to ask the portal for an address when it plays.
     */
    private suspend fun stalkerLineup(source: SourceEntity): Lineup {
        val client = sources.stalkerClient(source)
        sources.recordAccount(source.id, client.account())
        val genres = client.genres()
        val channels = client.channels()
        val fields = vault.fields()
        return Lineup(
            groups = genres.distinctBy { it.id }.map { LineupGroup(it.id, it.title) },
            channels = channels.map {
                LineupChannel(
                    key = it.id,
                    number = it.number,
                    name = it.name,
                    logo = it.logo,
                    groupRemoteId = it.genreId,
                    tvgId = it.xmltvId,
                    catchupDays = it.archiveDays,
                    isRadio = false,
                    streamUrlEnc = fields.seal(it.cmd),
                )
            },
        )
    }

    /**
     * A playlist's live channels, read as it downloads (brief 7.3). Movies and series stay out of
     * Live TV. Channel addresses are encrypted as they're read, since they often carry a login.
     */
    private suspend fun m3uLineup(source: SourceEntity): Lineup {
        val fields = vault.fields()
        val request = Request.Builder().url(sources.playlistUrl(source)).apply { source.userAgent?.let { header("User-Agent", it) } }.build()
        val entries = ArrayList<M3uEntry>()
        val guides = LinkedHashSet<String>()
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) throw PlaylistRefused(response.code)
                if (!response.isSuccessful) throw IOException("The playlist answered with HTTP ${response.code}")
                M3uReader.read(
                    response.body.byteStream(),
                    onHeader = { guides += it.guideUrls },
                    onEntry = { if (it.kind == M3uKind.Live) entries += it },
                )
            }
        }
        sources.recordPlaylistGuides(source.id, guides.toList())
        val keys = M3uKeys.of(entries)
        return Lineup(
            groups = entries.mapNotNull { it.group }.distinct().map { LineupGroup(it, it) },
            channels = entries.mapIndexed { index, entry ->
                LineupChannel(
                    key = keys[index],
                    number = entry.number,
                    name = entry.name,
                    logo = entry.logo,
                    groupRemoteId = entry.group,
                    tvgId = entry.tvgId,
                    catchupDays = entry.catchupDays,
                    isRadio = entry.isRadio,
                    streamUrlEnc = fields.seal(entry.url),
                    userAgent = entry.userAgent,
                    referrer = entry.referrer,
                    catchupType = entry.catchup,
                    catchupSourceEnc = entry.catchupSource?.let(fields::seal),
                )
            },
        )
    }

    /**
     * Imports the source's full XMLTV guide (brief 5.3), keeping the past for as long as its
     * longest catch-up archive, and matching guide channels to channels by id, then by name. A
     * playlist can have several guides: a channel takes its programmes from the first that has it.
     */
    suspend fun importGuide(sourceId: String, onProgress: (programmes: Int) -> Unit = {}): GuideImport =
        withContext(Dispatchers.Default) { importGuideLocked(sourceId, onProgress) }

    private suspend fun importGuideLocked(sourceId: String, onProgress: (programmes: Int) -> Unit): GuideImport = guideLock.withLock {
        val source = db.sources().get(sourceId) ?: throw NoSuchElementException("No source $sourceId")
        recordingFailure(source) {
            val urls = when (SourceType.of(source.type)) {
                // The server's own guide, then any extra one from Advanced (brief 4.21).
                SourceType.Xtream -> listOf(sources.xtreamClient(source).xmltvUrl()) + sources.guideUrls(source)
                SourceType.M3u -> sources.guideUrls(source)
                SourceType.Stalker -> return@recordingFailure importPortalGuide(source, onProgress)
            }
            val channels = db.channels().forSource(sourceId)
            val keys = channels.map { GuideKey(it.id, it.tvgId, it.name) }
            val now = clock.now().toEpochMilli()
            val pastDays = (channels.maxOfOrNull { it.catchupDays ?: 0 } ?: 0).coerceAtLeast(1)
            val generation = (db.programmes().maxGeneration(sourceId) ?: 0) + 1

            // Channel id to guide key, filled guide by guide.
            val epgKeys = LinkedHashMap<String, String>()
            // Guide ids whose programmes an earlier guide wrote: a later one doesn't write them again.
            val written = HashSet<String>()
            var count = 0
            try {
                for (url in urls) {
                    val unmatched = keys.filter { it.channelId !in epgKeys }
                    if (unmatched.isEmpty()) break
                    val matched = readGuide(source, url, unmatched, generation, notBefore = now - pastDays * DAY, written) { added ->
                        count += added
                        onProgress(count)
                    }
                    for ((guideId, channelIds) in matched) channelIds.forEach { epgKeys.putIfAbsent(it, "$sourceId:$guideId") }
                    written += matched.keys
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) { db.programmes().deleteGeneration(sourceId, generation) }
                throw error
            }

            db.channels().setEpgKeys(sourceId, epgKeys)
            db.programmes().commit(sourceId, generation, now)
            onProgress(count)
            GuideImport(programmes = count, matchedChannels = epgKeys.size)
        }
    }

    /**
     * Reads one guide, writing the programmes of [keys]' channels as [generation], except for guide
     * ids an earlier guide has [written]. Returns which of its channels matched which of [keys], by
     * guide id.
     */
    private suspend fun readGuide(
        source: SourceEntity,
        url: String,
        keys: List<GuideKey>,
        generation: Long,
        notBefore: Long,
        written: Set<String>,
        onWritten: (Int) -> Unit,
    ): Map<String, List<String>> {
        val request = Request.Builder().url(url).apply { source.userAgent?.let { header("User-Agent", it) } }.build()
        val guideChannels = mutableListOf<XmltvChannel>()
        var matches: Map<String, List<String>>? = null
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("The guide answered with HTTP ${response.code}")
                val batch = ArrayList<ProgrammeEntity>(BATCH)
                XmltvReader(notBefore = notBefore, offsetMillis = source.epgOffsetMinutes * MINUTE).read(
                    response.body.byteStream(),
                    onChannel = { guideChannels += it },
                    onProgramme = { programme ->
                        // XMLTV lists every channel before the first programme, so match once here.
                        val matched = matches ?: GuideMatcher.match(keys, guideChannels).also { matches = it }
                        val guideId = programme.channelId.lowercase()
                        if (guideId in matched && guideId !in written) {
                            batch += programme.toEntity(source.id, "${source.id}:$guideId", generation)
                            if (batch.size == BATCH) {
                                ensureActive()
                                db.programmes().insertAllBlocking(batch.toList())
                                onWritten(batch.size)
                                batch.clear()
                            }
                        }
                    },
                )
                if (batch.isNotEmpty()) {
                    db.programmes().insertAllBlocking(batch)
                    onWritten(batch.size)
                }
            }
        }
        return matches ?: GuideMatcher.match(keys, guideChannels)
    }

    /**
     * A portal's own guide for the days ahead (brief 7.1), a channel at a time as it downloads.
     * Programmes are matched to channels by the portal's channel ids.
     */
    private suspend fun importPortalGuide(source: SourceEntity, onProgress: (programmes: Int) -> Unit): GuideImport {
        val client = sources.stalkerClient(source)
        val channels = db.channels().forSource(source.id).associateBy { it.key }
        val generation = (db.programmes().maxGeneration(source.id) ?: 0) + 1
        val offset = source.epgOffsetMinutes * MINUTE
        val epgKeys = LinkedHashMap<String, String>()
        val batch = ArrayList<ProgrammeEntity>(BATCH)
        var count = 0
        try {
            client.guide(PORTAL_GUIDE_HOURS) { portalId, programmes ->
                val channel = channels[portalId] ?: return@guide
                if (programmes.isEmpty()) return@guide
                val epgKey = "${source.id}:portal:$portalId"
                epgKeys[channel.id] = epgKey
                for (programme in programmes) {
                    batch += ProgrammeEntity(
                        sourceId = source.id,
                        epgKey = epgKey,
                        startAt = programme.start.toEpochMilli() + offset,
                        endAt = programme.end.toEpochMilli() + offset,
                        title = programme.title,
                        subtitle = null,
                        description = programme.description,
                        category = programme.category,
                        icon = null,
                        season = null,
                        episode = null,
                        isNew = false,
                        generation = generation,
                    )
                    if (batch.size == BATCH) {
                        db.programmes().insertAllBlocking(batch.toList())
                        count += batch.size
                        batch.clear()
                        onProgress(count)
                    }
                }
            }
            if (batch.isNotEmpty()) {
                db.programmes().insertAll(batch)
                count += batch.size
            }
        } catch (error: Throwable) {
            withContext(NonCancellable) { db.programmes().deleteGeneration(source.id, generation) }
            throw error
        }
        // A portal with nothing in its guide this time keeps the guide it had.
        if (count == 0) return GuideImport(programmes = 0, matchedChannels = 0)
        db.channels().setEpgKeys(source.id, epgKeys)
        db.programmes().commit(source.id, generation, clock.now().toEpochMilli())
        onProgress(count)
        return GuideImport(programmes = count, matchedChannels = epgKeys.size)
    }

    /** Runs [block], noting a failure on the source, redacted, before passing it on. */
    private suspend fun <T> recordingFailure(source: SourceEntity, block: suspend () -> T): T = try {
        block()
    } catch (error: Exception) {
        if (error is IOException || error is IllegalArgumentException) {
            withContext(NonCancellable) {
                db.sources().markError(source.id, Redact.text(error.message ?: error.javaClass.simpleName))
            }
        }
        throw error
    }

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
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val BATCH = 1_000

        /** How far ahead a portal's guide is read: three days. */
        const val PORTAL_GUIDE_HOURS = 72
        const val MINUTE = 60_000L
        const val DAY = 86_400_000L
    }
}
