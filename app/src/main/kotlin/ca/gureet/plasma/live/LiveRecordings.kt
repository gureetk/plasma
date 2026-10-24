// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringArrayResource
import ca.gureet.plasma.AppGraph
import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind as Kind
import ca.gureet.plasma.core.data.RecordingProblems
import ca.gureet.plasma.core.data.RecordingRepository
import ca.gureet.plasma.core.data.RecordingStatus
import ca.gureet.plasma.core.data.Reminder
import ca.gureet.plasma.core.design.component.Art
import ca.gureet.plasma.feature.recordings.RecordingItem
import ca.gureet.plasma.feature.recordings.RecordingKind
import ca.gureet.plasma.feature.recordings.RecordingTrouble
import ca.gureet.plasma.feature.recordings.RecordingsActions
import ca.gureet.plasma.feature.recordings.RecordingsScreen
import ca.gureet.plasma.feature.recordings.RecordingsUiState
import ca.gureet.plasma.feature.recordings.ReminderItem
import ca.gureet.plasma.feature.recordings.ScheduledItem
import ca.gureet.plasma.feature.recordings.SeriesRef
import ca.gureet.plasma.feature.recordings.StorageInfo
import ca.gureet.plasma.player.StreamKeys
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import ca.gureet.plasma.feature.settings.R as SettingsR

/** Recordings play in the movie player, from their files; where they got to is kept with them (brief 5.6). */
class RecordingShelf(private val recordings: RecordingRepository) : VodShelf {
    override fun keyOf(id: String): String = StreamKeys.recording(id)

    override suspend fun playable(id: String): Playable? {
        val recording = recordings.get(id)?.takeIf { it.file != null } ?: return null
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

    override suspend fun keep(playable: Playable, at: Duration, total: Duration, paused: Boolean, stopped: Boolean) =
        recordings.played(playable.id, at.toMillis(), total.toMillis())
}

/**
 * Recordings (board 17) on what's recorded and coming up (brief 5.6), with the reminders set. OK on
 * a recording plays it with [onPlay].
 */
@Composable
fun LiveRecordings(graph: AppGraph, onPlay: (RecordingItem) -> Unit, onShowInGuide: (channelName: String, start: ZonedDateTime) -> Unit) {
    val recordings by graph.data.recordings.recordings.collectAsState(emptyList())
    val reminders by graph.data.reminders.reminders.collectAsState(emptyList())
    val world by graph.world.collectAsState()
    val sources by graph.data.sources.sources.collectAsState(emptyList())
    val drives = stringArrayResource(SettingsR.array.record_to_options)
    val zone = graph.clock.zone
    // What's recording now changes what's on the drive.
    val drive = remember(recordings) { graph.recordingDrive() }
    val state = remember(recordings, reminders, world, sources, drive) {
        recordingsState(
            recordings = recordings,
            reminders = reminders,
            channelOf = { id -> world?.byId?.get(id)?.let { it.name to it.logo } },
            zone = zone,
            storage = StorageInfo(drives[if (drive.onUsb) 0 else 1], drive.free, drive.total),
            // The primary source's limit, which recordings count against (5.1).
            connectionLimit = sources.firstOrNull()?.maxConnections,
        )
    }
    RecordingsScreen(
        state = state,
        actions = remember(graph) {
            RecordingsActions(
                onPlay = onPlay,
                onDelete = graph::deleteRecordings,
                onKeep = graph::keepRecording,
                onWatched = graph::markRecordingWatched,
                onShowInGuide = onShowInGuide,
                onCancelScheduled = { id -> graph.record(StreamKeys.channelOf(id), id, on = false) },
                onCancelReminder = { id -> graph.remind(StreamKeys.channelOf(id), id, on = false) },
            )
        },
    )
}

/**
 * Recordings' screen state from what's kept: what's done or being written, newest first in the grid;
 * what's coming up, soonest first; the reminders set. [channelOf] gives a channel's name and logo
 * as the lists have them now.
 */
internal fun recordingsState(
    recordings: List<Recording>,
    reminders: List<Reminder>,
    channelOf: (String) -> Pair<String, String?>?,
    zone: ZoneId,
    storage: StorageInfo?,
    connectionLimit: Int?,
): RecordingsUiState {
    val recorded = recordings.filter { it.status == RecordingStatus.Recording || it.status == RecordingStatus.Done || it.status == RecordingStatus.Failed }
    val scheduled = recordings.filter { it.status == RecordingStatus.Scheduled }.sortedBy { it.start }
    return RecordingsUiState(
        recordings = recorded.map { it.toItem(zone) },
        scheduled = scheduled.map { recording ->
            ScheduledItem(
                id = recording.id,
                title = recording.title,
                channelName = recording.channelName,
                logo = recording.channelLogo,
                start = recording.start.atZone(zone),
                end = recording.end.atZone(zone),
                startsEarly = recording.padBefore,
                endsLate = recording.padAfter,
            )
        },
        reminders = reminders.sortedBy { it.start }.map { reminder ->
            val (name, logo) = channelOf(reminder.channelId) ?: (reminder.channelId to null)
            ReminderItem(reminder.id, reminder.title, name, logo, reminder.start.atZone(zone))
        },
        rules = emptyList(),
        storage = storage,
        connectionsInUse = recordings.count { it.status == RecordingStatus.Recording },
        connectionLimit = connectionLimit,
    )
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
    )
}
