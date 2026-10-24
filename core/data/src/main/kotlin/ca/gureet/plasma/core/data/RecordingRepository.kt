// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.RecordingEntity
import ca.gureet.plasma.core.database.RecordingRuleEntity
import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Where a recording stands (brief 5.6). */
enum class RecordingStatus(val key: String) {
    Scheduled("scheduled"),
    Recording("recording"),
    Done("done"),
    Failed("failed"),
    Cancelled("cancelled"),
    ;

    companion object {
        fun of(key: String): RecordingStatus = entries.firstOrNull { it.key == key } ?: Failed
    }
}

/** What a recording is, for Recordings' filters and, later, the network folder's layout (brief 5.6). */
enum class RecordingKind(val key: String) {
    Series("series"),
    Sports("sports"),
    News("news"),
    Movie("movie"),
    Other("other"),
    ;

    companion object {
        fun of(key: String): RecordingKind = entries.firstOrNull { it.key == key } ?: Other

        /**
         * What the guide makes of a programme: its category, when it reads as a film, sport or
         * news, and otherwise a series when it has an episode number.
         */
        fun guess(category: String?, season: Int?, episode: Int?): RecordingKind {
            val said = category.orEmpty().lowercase()
            return when {
                "movie" in said || "film" in said -> Movie
                "sport" in said -> Sports
                "news" in said -> News
                season != null || episode != null || "series" in said -> Series
                else -> Other
            }
        }
    }
}

/** A programme to record, as the guide has it. */
data class ProgrammeToRecord(
    val channelId: String,
    val channelName: String,
    val channelLogo: String?,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val image: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val start: Instant,
    val end: Instant,
)

/** A recording (brief 5.6): one coming up, being written, or done. */
data class Recording(
    /** "channelId@start in epoch seconds", the id the screens give the programme. */
    val id: String,
    val channelId: String,
    val channelName: String,
    val channelLogo: String?,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val category: String?,
    val image: String?,
    val season: Int?,
    val episode: Int?,
    val kind: RecordingKind,
    val start: Instant,
    val end: Instant,
    val padBefore: Duration,
    val padAfter: Duration,
    val status: RecordingStatus,
    val file: String?,
    val sizeBytes: Long,
    val recordedFrom: Instant?,
    val recordedTo: Instant?,
    val interruptions: Int,
    val problem: String?,
    val keep: Boolean,
    val positionMs: Long,
    val watched: Boolean,
    val watchedAt: Instant?,
    /** The Record Series rule that set it, if one did. */
    val ruleId: String? = null,
) {
    /** When writing starts: the programme's start, less the padding. */
    val from: Instant get() = start.minus(padBefore)

    /** When it stops: the programme's end, with the padding. */
    val until: Instant get() = end.plus(padAfter)

    /** What's been written, in time: all of it once it's done. */
    val recordedFor: Duration
        get() {
            val first = recordedFrom ?: return Duration.ZERO
            val last = recordedTo ?: return Duration.ZERO
            return Duration.between(first, last).coerceAtLeast(Duration.ZERO)
        }
}

/** A Record Series rule (brief 5.6): every airing of [title], on one channel or any ([channelId] null). */
data class RecordingRule(
    val id: String,
    val title: String,
    val channelId: String?,
    val channelName: String?,
    val channelLogo: String?,
    /** How many of its recordings it keeps, the newest; null for all of them. */
    val keepLast: Int?,
    /** When Keep Last was chosen, or the rule made: older ones go once one is recorded after. */
    val keepLastSince: Instant = Instant.EPOCH,
)

/** What a recording problem was, kept as the recorder said it, for its tile. */
object RecordingProblems {
    /** No stream could be opened for the channel. */
    const val NO_STREAM = "noStream"

    /** Every connection the account allows was in use. */
    const val NO_CONNECTION = "noConnection"

    /** The stream couldn't be written: the drive was full, or went. */
    const val NO_ROOM = "noRoom"

    /** Stopped before its end, by hand. */
    const val STOPPED = "stopped"

    /** Plasma wasn't running when it should have started. */
    const val MISSED = "missed"
}

/** The recordings the recorder follows, and what it says of them (brief 5.6). */
interface RecordingStore {
    /** Coming up or being written, soonest first. */
    suspend fun pending(): List<Recording>

    suspend fun get(id: String): Recording?

    suspend fun started(id: String, file: String)

    suspend fun progress(id: String, sizeBytes: Long)

    suspend fun interrupted(id: String)

    /** It's over, with what was written, and why when it [failed] or has a [problem]. */
    suspend fun finished(id: String, failed: Boolean, problem: String? = null)
}

/** Recordings, kept in the database (brief 5.6). */
class RecordingRepository(
    private val db: PlasmaDatabase,
    private val clock: PlasmaClock,
) : RecordingStore {
    /** Every recording that isn't cancelled: coming up, being written and done, by start. */
    val recordings: Flow<List<Recording>> = db.recordings().observe().map { list -> list.map { it.toRecording() } }.distinctUntilChanged()

    /** The Record Series rules, by title. */
    val rules: Flow<List<RecordingRule>> = db.recordingRules().observe().map { list -> list.map { it.toRule() } }.distinctUntilChanged()

    /** The programmes being recorded or set to be, for the red dots on the Guide, Live TV and Search. */
    val ids: Flow<Set<String>> = recordings
        .map { list -> list.filter { it.status == RecordingStatus.Scheduled || it.status == RecordingStatus.Recording }.mapTo(HashSet()) { it.id } }
        .distinctUntilChanged()

    /**
     * Records [programme], starting [padBefore] early and ending [padAfter] late, for [ruleId] when
     * a Record Series rule sets it, as [kind] when it's known better than the guide says. Asked
     * again for one already set, it keeps that one.
     */
    suspend fun schedule(
        programme: ProgrammeToRecord,
        padBefore: Duration,
        padAfter: Duration,
        ruleId: String? = null,
        kind: RecordingKind? = null,
    ): Recording {
        val id = idOf(programme.channelId, programme.start)
        val before = db.recordings().get(id)
        before?.takeIf { RecordingStatus.of(it.status) != RecordingStatus.Cancelled }?.let { return it.toRecording() }
        val entity = RecordingEntity(
            id = id,
            channelId = programme.channelId,
            channelName = programme.channelName,
            channelLogo = programme.channelLogo,
            title = programme.title,
            subtitle = programme.subtitle,
            description = programme.description,
            category = programme.category,
            image = programme.image,
            season = programme.season,
            episode = programme.episode,
            kind = (kind ?: RecordingKind.guess(programme.category, programme.season, programme.episode)).key,
            startAt = programme.start.toEpochMilli(),
            endAt = programme.end.toEpochMilli(),
            padBeforeMs = padBefore.toMillis(),
            padAfterMs = padAfter.toMillis(),
            status = RecordingStatus.Scheduled.key,
            filePath = null,
            sizeBytes = 0,
            recordedFrom = null,
            recordedTo = null,
            interruptions = 0,
            problem = null,
            keep = false,
            positionMs = 0,
            watched = false,
            watchedAt = 0,
            createdAt = clock.now().toEpochMilli(),
            // A rule's airing that was cancelled, set again by hand, is still the rule's.
            ruleId = ruleId ?: before?.ruleId,
        )
        db.recordings().upsert(entity)
        return entity.toRecording()
    }

    /** A Record Series rule for [title] on [channel] (id, name and logo), or any channel when it's null. */
    suspend fun addRule(title: String, channel: Triple<String, String, String?>?, keepLast: Int? = null): RecordingRule {
        val rule = RecordingRuleEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            channelId = channel?.first,
            channelName = channel?.second,
            channelLogo = channel?.third,
            keepLast = keepLast,
            keepLastSince = clock.now().toEpochMilli(),
            createdAt = clock.now().toEpochMilli(),
        )
        db.recordingRules().upsert(rule)
        return rule.toRule()
    }

    /** The rule for [title] on [channelId] (or any channel), if there's one. */
    suspend fun ruleFor(title: String, channelId: String?): RecordingRule? =
        db.recordingRules().all().firstOrNull { it.title.equals(title, ignoreCase = true) && (it.channelId == null || it.channelId == channelId) }?.toRule()

    /** Deletes a rule, and what it set that's still to come; what it recorded stays. */
    suspend fun deleteRule(id: String) {
        db.recordings().removeScheduledOfRule(id)
        db.recordingRules().delete(id)
    }

    suspend fun rulesNow(): List<RecordingRule> = db.recordingRules().all().map { it.toRule() }

    /**
     * A rule keeps its newest [keepLast] recordings, or all of them when it's null: older ones go
     * once the next one is recorded, not straight away.
     */
    suspend fun setKeepLast(ruleId: String, keepLast: Int?) {
        val rule = db.recordingRules().get(ruleId) ?: return
        db.recordingRules().upsert(rule.copy(keepLast = keepLast, keepLastSince = clock.now().toEpochMilli()))
    }

    /** Every recording [ruleId] set, coming up or done, oldest first. */
    suspend fun ofRule(ruleId: String): List<Recording> = db.recordings().ofRule(ruleId).map { it.toRecording() }

    /** The airings [ruleId] set that were cancelled since, by id: it doesn't set them again. */
    suspend fun cancelledOfRule(ruleId: String): Set<String> = db.recordings().cancelledOfRule(ruleId).toSet()

    /** Forgets cancelled airings that are over. */
    suspend fun forgetPastCancelled() = db.recordings().forgetCancelledBefore(clock.now().toEpochMilli())

    /** Done, watched and not kept, the oldest watched first: what goes when space runs low. */
    suspend fun watchedOldestFirst(): List<Recording> = db.recordings().watchedOldestFirst().map { it.toRecording() }

    /**
     * What retention lets go of now (brief 5.6): a rule's oldest recordings beyond its Keep Last,
     * once one has been recorded since Keep Last was chosen; and, with [deleteWatchedAfter],
     * those watched longer ago than that, counting from when it was chosen ([watchedSince]) at
     * the earliest. So nothing goes without a warning first. Kept ones never go.
     */
    suspend fun expired(deleteWatchedAfter: Duration?, watchedSince: Instant? = null): List<Recording> {
        val now = clock.now()
        val gone = LinkedHashMap<String, Recording>()
        for (rule in rulesNow()) {
            val keep = rule.keepLast ?: continue
            val done = ofRule(rule.id).filter { it.status == RecordingStatus.Done }
            val lastRecorded = done.maxOfOrNull { it.recordedTo ?: it.until } ?: continue
            if (lastRecorded.isBefore(rule.keepLastSince)) continue
            done.filter { !it.keep }.sortedByDescending { it.start }.drop(keep).forEach { gone[it.id] = it }
        }
        if (deleteWatchedAfter != null && (watchedSince == null || !watchedSince.plus(deleteWatchedAfter).isAfter(now))) {
            db.recordings().watchedBefore(now.minus(deleteWatchedAfter).toEpochMilli()).forEach { gone[it.id] = it.toRecording() }
        }
        return gone.values.toList()
    }

    /**
     * Cancel Recording on a programme: one coming up goes; one being written stops, keeping what
     * it has. A rule's airing stays, cancelled, so that the rule doesn't set it again.
     */
    suspend fun cancel(id: String) {
        val recording = db.recordings().get(id) ?: return
        when (RecordingStatus.of(recording.status)) {
            RecordingStatus.Scheduled ->
                if (recording.ruleId != null) {
                    db.recordings().finished(id, RecordingStatus.Cancelled.key, null)
                } else {
                    db.recordings().delete(listOf(id))
                }
            RecordingStatus.Recording -> db.recordings().finished(id, RecordingStatus.Done.key, RecordingProblems.STOPPED)
            else -> Unit
        }
    }

    /** Deletes [ids], and says which files they had, for the caller to delete. */
    suspend fun delete(ids: Collection<String>): List<String> {
        val files = ids.mapNotNull { db.recordings().get(it)?.filePath }
        db.recordings().delete(ids.toList())
        return files
    }

    suspend fun keep(id: String, keep: Boolean) = db.recordings().setKeep(id, keep)

    /** Where playback got to; watched from 92 %, as movies are (brief 5.9). */
    suspend fun played(id: String, positionMs: Long, durationMs: Long) {
        val watched = durationMs > 0 && positionMs >= durationMs * WATCHED_PERCENT / 100
        db.recordings().setPosition(id, if (watched) 0 else positionMs, watched, clock.now().toEpochMilli())
    }

    /** Mark as Watched, or Unwatched, from its options. */
    suspend fun markWatched(id: String, watched: Boolean) = db.recordings().setPosition(id, 0, watched, clock.now().toEpochMilli())

    override suspend fun pending(): List<Recording> = db.recordings().pending().map { it.toRecording() }

    override suspend fun get(id: String): Recording? = db.recordings().get(id)?.toRecording()

    override suspend fun started(id: String, file: String) = db.recordings().started(id, file, clock.now().toEpochMilli())

    override suspend fun progress(id: String, sizeBytes: Long) = db.recordings().progress(id, sizeBytes, clock.now().toEpochMilli())

    override suspend fun interrupted(id: String) = db.recordings().interrupted(id)

    override suspend fun finished(id: String, failed: Boolean, problem: String?) {
        // Stopped by hand already, or deleted: that stands.
        val now = db.recordings().get(id) ?: return
        if (RecordingStatus.of(now.status) != RecordingStatus.Recording && RecordingStatus.of(now.status) != RecordingStatus.Scheduled) return
        db.recordings().finished(id, if (failed) RecordingStatus.Failed.key else RecordingStatus.Done.key, problem)
    }

    companion object {
        /** The same id a reminder gives the programme. */
        fun idOf(channelId: String, start: Instant): String = ReminderRepository.idOf(channelId, start)

        private const val WATCHED_PERCENT = 92
    }
}

internal fun RecordingEntity.toRecording() = Recording(
    id = id,
    channelId = channelId,
    channelName = channelName,
    channelLogo = channelLogo,
    title = title,
    subtitle = subtitle,
    description = description,
    category = category,
    image = image,
    season = season,
    episode = episode,
    kind = RecordingKind.of(kind),
    start = Instant.ofEpochMilli(startAt),
    end = Instant.ofEpochMilli(endAt),
    padBefore = Duration.ofMillis(padBeforeMs),
    padAfter = Duration.ofMillis(padAfterMs),
    status = RecordingStatus.of(status),
    file = filePath,
    sizeBytes = sizeBytes,
    recordedFrom = recordedFrom?.let(Instant::ofEpochMilli),
    recordedTo = recordedTo?.let(Instant::ofEpochMilli),
    interruptions = interruptions,
    problem = problem,
    keep = keep,
    positionMs = positionMs,
    watched = watched,
    watchedAt = watchedAt.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
    ruleId = ruleId,
)

internal fun RecordingRuleEntity.toRule() = RecordingRule(id, title, channelId, channelName, channelLogo, keepLast, Instant.ofEpochMilli(keepLastSince))
