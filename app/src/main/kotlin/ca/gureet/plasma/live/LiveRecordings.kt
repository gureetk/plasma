// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringArrayResource
import ca.gureet.plasma.AppGraph
import ca.gureet.plasma.core.data.CopyState
import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind as Kind
import ca.gureet.plasma.core.data.RecordingConflicts
import ca.gureet.plasma.core.data.RecordingProblems
import ca.gureet.plasma.core.data.RecordingRepository
import ca.gureet.plasma.core.data.RecordingRule
import ca.gureet.plasma.core.data.RecordingStatus
import ca.gureet.plasma.core.data.Reminder
import ca.gureet.plasma.core.design.component.Art
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.feature.recordings.Conflict
import ca.gureet.plasma.feature.recordings.RecordingCopy
import ca.gureet.plasma.feature.recordings.RecordingItem
import ca.gureet.plasma.feature.recordings.RecordingKind
import ca.gureet.plasma.feature.recordings.RecordingTrouble
import ca.gureet.plasma.feature.recordings.RecordingsActions
import ca.gureet.plasma.feature.recordings.RecordingsScreen
import ca.gureet.plasma.feature.recordings.RecordingsUiState
import ca.gureet.plasma.feature.recordings.ReminderItem
import ca.gureet.plasma.feature.recordings.ScheduledItem
import ca.gureet.plasma.feature.recordings.SeriesRuleItem
import ca.gureet.plasma.feature.recordings.SeriesRef
import ca.gureet.plasma.feature.recordings.StorageInfo
import ca.gureet.plasma.player.StreamKeys
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import ca.gureet.plasma.feature.settings.R as SettingsR

/** Recordings play in the movie player, from their files; where they got to is kept with them (brief 5.6). */
class RecordingShelf(private val recordings: RecordingRepository) : VodShelf {
    override fun keyOf(id: String): String = StreamKeys.recording(id)

    override suspend fun playable(id: String): Playable? {
        // On the drive, on its way to the network folder (Record To), or only there now (After Copying, 5.6).
        val recording = recordings.get(id)?.takeIf { it.file != null || it.spool != null || it.onShareOnly } ?: return null
        return Playable(
            kind = Playable.Kind.Movie,
            id = recording.id,
            sourceId = "",
            remoteId = recording.id,
            extension = "ts",
            title = recording.title,
            episodeTitle = recording.subtitle,
            art = recording.image,
            duration = recording.recordedFor.takeIf { !it.isZero } ?: Duration.between(recording.start, recording.end),
        )
    }

    override suspend fun next(episode: Playable): Playable? = null

    override suspend fun resumeFrom(playable: Playable): Duration = Duration.ofMillis(recordings.get(playable.id)?.positionMs ?: 0)

    override suspend fun keep(playable: Playable, at: Duration, total: Duration, paused: Boolean, stopped: Boolean) {
        val recording = recordings.get(playable.id)
        // Still being written: it's as long as it will be, not what's there yet, so watching at the
        // live edge doesn't count it watched (chase play, 5.6).
        val length = if (recording?.status == RecordingStatus.Recording) {
            maxOf(total.toMillis(), Duration.between(recording.from, recording.until).toMillis())
        } else {
            total.toMillis()
        }
        recordings.played(playable.id, at.toMillis(), length)
    }
}

/**
 * Recordings (board 17) on what's recorded and coming up (brief 5.6), with the reminders set. OK on
 * a recording plays it with [onPlay].
 */
@Composable
fun LiveRecordings(graph: AppGraph, onPlay: (RecordingItem) -> Unit, onShowInGuide: (channelName: String, start: ZonedDateTime) -> Unit) {
    val recordings by graph.data.recordings.recordings.collectAsState(emptyList())
    val rules by graph.data.recordings.rules.collectAsState(emptyList())
    val watchedSince by remember(graph) { graph.data.settings.chosenAt(SettingKeys.DELETE_WATCHED) }.collectAsState(null)
    val localIds by graph.data.settings.localCatchUp.collectAsState(emptySet())
    val reminders by graph.data.reminders.reminders.collectAsState(emptyList())
    val world by graph.world.collectAsState()
    val sources by graph.data.sources.sources.collectAsState(emptyList())
    val drives = stringArrayResource(SettingsR.array.record_to_options)
    val zone = graph.clock.zone
    // What's recording now changes what's on the drive.
    val drive = remember(recordings) { graph.recordingDrive() }
    val chosen = graph.choices
    val state = remember(recordings, rules, reminders, world, sources, drive, chosen.deleteWatchedDays, watchedSince, chosen.startEarlyMinutes, chosen.endLateMinutes, localIds) {
        val limits = sources.associate { it.id to it.maxConnections }
        recordingsState(
            recordings = recordings,
            rules = rules,
            reminders = reminders,
            channelOf = { id -> world?.byId?.get(id)?.let { it.name to it.logo } },
            zone = zone,
            storage = StorageInfo(drives[if (drive.onUsb) 0 else 1], drive.free, drive.total),
            // The primary source's limit, which recordings count against (5.1).
            connectionLimit = sources.firstOrNull()?.maxConnections,
            limitOf = { limits[it] },
            deleteWatchedDays = chosen.deleteWatchedDays,
            deleteWatchedSince = watchedSince,
            localChannels = localIds,
            padding = Duration.ofMinutes(chosen.startEarlyMinutes.toLong()) to Duration.ofMinutes(chosen.endLateMinutes.toLong()),
        )
    }
    RecordingsScreen(
        state = state,
        actions = remember(graph) {
            RecordingsActions(
                onPlay = onPlay,
                onDelete = graph::deleteRecordings,
                onDeleteEverywhere = graph::deleteRecordingsEverywhere,
                onKeep = graph::keepRecording,
                onWatched = graph::markRecordingWatched,
                onShowInGuide = onShowInGuide,
                onCancelScheduled = { id -> graph.record(StreamKeys.channelOf(id), id, on = false) },
                onCancelReminder = { id -> graph.remind(StreamKeys.channelOf(id), id, on = false) },
                onDeleteRule = graph::deleteRule,
                onKeepLast = graph::setKeepLast,
            )
        },
    )
}

/**
 * Recordings' screen state from what's kept: what's done or being written, newest first in the grid;
 * what's coming up, soonest first, with any conflicts against each source's connections
 * ([limitOf]); the Record Series rules; the reminders set. [channelOf] gives a channel's name and
 * logo as the lists have them now. A recording retention will let go of soon says when (brief 5.6):
 * the day a rule's next recording pushes it past Keep Last, or [deleteWatchedDays] after it was
 * watched, counting from when that was chosen ([deleteWatchedSince]) at the earliest.
 */
internal fun recordingsState(
    recordings: List<Recording>,
    reminders: List<Reminder>,
    channelOf: (String) -> Pair<String, String?>?,
    zone: ZoneId,
    storage: StorageInfo?,
    connectionLimit: Int?,
    rules: List<RecordingRule> = emptyList(),
    limitOf: (sourceId: String) -> Int? = { null },
    deleteWatchedDays: Int? = null,
    deleteWatchedSince: Instant? = null,
    localChannels: Set<String> = emptySet(),
    padding: Pair<Duration, Duration> = Duration.ofMinutes(1) to Duration.ofMinutes(5),
): RecordingsUiState {
    val recorded = recordings.filter { it.status == RecordingStatus.Recording || it.status == RecordingStatus.Done || it.status == RecordingStatus.Failed }
    val scheduled = recordings.filter { it.status == RecordingStatus.Scheduled }.sortedBy { it.start }
    val byRule = rules.associateBy { it.id }
    // Local catch-up holds a connection of its source all the time (5.5): a recording of one of its
    // channels shares it, and the rest have what's left.
    val conflicts = RecordingConflicts.of(recordings.filter { it.channelId !in localChannels }) { sourceId ->
        limitOf(sourceId)?.let { limit -> limit - localChannels.count { it.substringBefore(':') == sourceId } }
    }
    val conflictOf = HashMap<String, Conflict>()
    conflicts.forEachIndexed { group, conflict -> conflict.recordings.forEach { conflictOf[it.id] = Conflict(group, conflict.limit) } }
    val deletes = deletionDates(recordings, byRule, deleteWatchedDays, deleteWatchedSince, zone)
    return RecordingsUiState(
        recordings = recorded.map { recording ->
            recording.toItem(zone).copy(
                byRule = recording.ruleId != null,
                keepLast = recording.ruleId?.let(byRule::get)?.keepLast,
                deletesOn = deletes[recording.id],
            )
        },
        scheduled = scheduled.map { recording ->
            ScheduledItem(
                id = recording.id,
                title = recording.title,
                channelName = recording.channelName,
                logo = recording.channelLogo,
                start = recording.start.atZone(zone),
                end = recording.end.atZone(zone),
                byRule = recording.ruleId != null,
                startsEarly = recording.padBefore,
                endsLate = recording.padAfter,
                conflict = conflictOf[recording.id],
            )
        },
        reminders = reminders.sortedBy { it.start }.map { reminder ->
            val (name, logo) = channelOf(reminder.channelId) ?: (reminder.channelId to null)
            ReminderItem(reminder.id, reminder.title, name, logo, reminder.start.atZone(zone))
        },
        rules = rules.map { rule ->
            SeriesRuleItem(
                id = rule.id,
                title = rule.title,
                channelName = rule.channelName,
                logo = rule.channelLogo,
                keepLast = rule.keepLast,
                startsEarly = padding.first,
                endsLate = padding.second,
            )
        },
        storage = storage,
        connectionsInUse = recordings.count { it.status == RecordingStatus.Recording },
        connectionLimit = connectionLimit,
        conflicts = conflicts.size,
    )
}

/**
 * When retention lets each recording go, for the ones it will (brief 5.6): a rule's oldest beyond
 * Keep Last when its next recording finishes, and a watched one [deleteWatchedDays] after it was
 * watched, or after the setting was chosen ([since]) when that was later.
 */
private fun deletionDates(
    recordings: List<Recording>,
    rules: Map<String, RecordingRule>,
    deleteWatchedDays: Int?,
    since: Instant?,
    zone: ZoneId,
): Map<String, LocalDate> {
    val dates = HashMap<String, LocalDate>()
    if (deleteWatchedDays != null) {
        for (recording in recordings) {
            val watchedAt = recording.watchedAt ?: continue
            if (recording.watched && !recording.keep && recording.status == RecordingStatus.Done) {
                val from = if (since != null && since.isAfter(watchedAt)) since else watchedAt
                dates[recording.id] = from.plus(Duration.ofDays(deleteWatchedDays.toLong())).atZone(zone).toLocalDate()
            }
        }
    }
    for ((ruleId, ofRule) in recordings.filter { it.ruleId != null }.groupBy { it.ruleId!! }) {
        val keep = rules[ruleId]?.keepLast ?: continue
        val next = ofRule.filter { it.status == RecordingStatus.Scheduled }.minByOrNull { it.start } ?: continue
        val done = ofRule.filter { it.status == RecordingStatus.Done && !it.keep }.sortedByDescending { it.start }
        // The next one finished, these are past Keep Last.
        for (old in done.drop((keep - 1).coerceAtLeast(0))) {
            val day = next.until.atZone(zone).toLocalDate()
            dates[old.id] = dates[old.id]?.let { minOf(it, day) } ?: day
        }
    }
    return dates
}

/** A recording as its tile shows it. */
private fun Recording.toItem(zone: ZoneId): RecordingItem {
    val length = recordedFor.toMillis()
    val trouble = when {
        status == RecordingStatus.Failed || (status == RecordingStatus.Done && sizeBytes == 0L) -> RecordingTrouble.Failed
        status == RecordingStatus.Done && (interruptions > 0 || problem == RecordingProblems.NO_ROOM) -> RecordingTrouble.Incomplete
        else -> null
    }
    return RecordingItem(
        id = id,
        // An episode by its own title too, inside its series' folder.
        title = if (kind == Kind.Series && subtitle != null) "$title: $subtitle" else title,
        channelName = channelName,
        logo = channelLogo,
        art = Art(image),
        kind = when (kind) {
            Kind.Series -> RecordingKind.Series
            Kind.Sports -> RecordingKind.Sports
            Kind.News -> RecordingKind.News
            Kind.Movie -> RecordingKind.Movie
            Kind.Other -> RecordingKind.Other
        },
        start = start.atZone(zone),
        end = end.atZone(zone),
        inProgress = status == RecordingStatus.Recording,
        unwatched = status == RecordingStatus.Done && !watched && positionMs == 0L && trouble != RecordingTrouble.Failed,
        watched = watched,
        resume = if (!watched && positionMs > 0 && length > 0) (positionMs.toFloat() / length).coerceIn(0f, 1f) else null,
        kept = keep,
        // A series' episodes share a folder, by its title.
        series = if (kind == Kind.Series) SeriesRef(title.lowercase(), title) else null,
        trouble = trouble,
        copy = when (copy) {
            CopyState.None -> null
            CopyState.Waiting -> RecordingCopy.Waiting
            CopyState.Copying -> RecordingCopy.Copying
            CopyState.Copied -> networkPath?.let(NetworkFolder::hostOf)?.let(RecordingCopy::Copied)
            CopyState.Failed -> RecordingCopy.Failed
        },
    )
}
