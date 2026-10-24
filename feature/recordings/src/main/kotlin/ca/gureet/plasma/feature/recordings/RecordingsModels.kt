// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.recordings

import androidx.compose.runtime.Immutable
import ca.gureet.plasma.core.design.component.Art
import ca.gureet.plasma.core.model.Titles
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** What a recording is, for the filters down the left (board 17). */
enum class RecordingKind { Series, Sports, News, Movie, Other }

/** What went wrong with a recording, said on its tile in amber. */
enum class RecordingTrouble {
    /** Nothing could be recorded. */
    Failed,

    /** It stopped short of its end, or has gaps. */
    Incomplete,
}

/** The episodes of one series, which Recordings shows as a folder. */
@Immutable
data class SeriesRef(val id: String, val title: String)

@Immutable
data class RecordingItem(
    val id: String,
    val title: String,
    val channelName: String,
    val logo: String?,
    val art: Art,
    val kind: RecordingKind,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    /** Still being recorded: it plays from the start with the live edge available (chase play). */
    val inProgress: Boolean = false,
    /** Never played: the white dot (brief 2.9). */
    val unwatched: Boolean = false,
    val watched: Boolean = false,
    /** How far playback got, while it's part watched. */
    val resume: Float? = null,
    /** Protected from automatic deletion (the green key). */
    val kept: Boolean = false,
    /** When a retention rule will delete it. */
    val deletesOn: LocalDate? = null,
    /** The rule's "keep last N", shown with the warning. */
    val keepLast: Int? = null,
    val series: SeriesRef? = null,
    /** Recorded by a series rule. */
    val byRule: Boolean = false,
    val trouble: RecordingTrouble? = null,
) {
    val duration: Duration get() = Duration.between(start, end)

    /** How much is on the drive: all of it, or up to [now] while it's still recording. */
    fun recordedAt(now: Instant): Duration =
        if (inProgress) Duration.between(start.toInstant(), minOf(now, end.toInstant())).coerceAtLeast(Duration.ZERO) else duration

    /** Where it stands while it records: how much of its slot is done. */
    fun progressAt(now: Instant): Float =
        (recordedAt(now).toMillis().toFloat() / duration.toMillis().coerceAtLeast(1)).coerceIn(0f, 1f)
}

/** A tile in the Recorded grid: one recording, or a series folder holding several. */
@Immutable
sealed interface RecordedTile {
    val id: String
    val title: String

    /** The recordings it stands for. */
    val recordings: List<RecordingItem>

    @Immutable
    data class Single(val recording: RecordingItem) : RecordedTile {
        override val id: String get() = recording.id
        override val title: String get() = recording.title
        override val recordings: List<RecordingItem> get() = listOf(recording)
    }

    @Immutable
    data class Folder(val series: SeriesRef, val episodes: List<RecordingItem>) : RecordedTile {
        override val id: String get() = "series:${series.id}"
        override val title: String get() = series.title
        override val recordings: List<RecordingItem> get() = episodes

        /** The newest episode, whose picture and channel the folder shows. */
        val newest: RecordingItem get() = episodes.maxBy { it.start }
        val unwatchedCount: Int get() = episodes.count { it.unwatched }
        val byRule: Boolean get() = episodes.any { it.byRule }
    }
}

/** The filters down the left. The first five filter the Recorded grid; the last two are lists of their own. */
enum class RecordingFilter(val id: String) {
    All("all"), Series("series"), Sports("sports"), News("news"), Movies("movies"), Scheduled("scheduled"), SeriesRules("rules");

    /** Whether this filter's grid shows [recording]; the two lists show none. */
    fun matches(recording: RecordingItem): Boolean = when (this) {
        All -> true
        Series -> recording.kind == RecordingKind.Series
        Sports -> recording.kind == RecordingKind.Sports
        News -> recording.kind == RecordingKind.News
        Movies -> recording.kind == RecordingKind.Movie
        Scheduled, SeriesRules -> false
    }

    val showsRecordings: Boolean get() = this != Scheduled && this != SeriesRules

    companion object {
        fun of(id: String): RecordingFilter = entries.firstOrNull { it.id == id } ?: All
    }
}

enum class RecordingSort { Newest, Oldest, AZ }

/** A recording that's coming up: a one-off or the next episode a series rule will record. */
@Immutable
data class ScheduledItem(
    val id: String,
    val title: String,
    val channelName: String,
    val logo: String?,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val byRule: Boolean = false,
    /** Padding (brief 5.6): it starts this much early… */
    val startsEarly: Duration = Duration.ofMinutes(1),
    /** …and ends this much late. */
    val endsLate: Duration = Duration.ofMinutes(5),
)

/** Record Series: a rule that matches a title, on one channel or any. */
@Immutable
data class SeriesRuleItem(
    val id: String,
    val title: String,
    /** Null when the rule records from any channel. */
    val channelName: String?,
    val logo: String?,
    /** Keep only the newest N, or null to keep them all. */
    val keepLast: Int?,
    val startsEarly: Duration = Duration.ofMinutes(1),
    val endsLate: Duration = Duration.ofMinutes(5),
)

/** Reminders are listed under Scheduled, as a section of their own (brief 4.35). */
@Immutable
data class ReminderItem(
    val id: String,
    val title: String,
    val channelName: String,
    val logo: String?,
    val start: ZonedDateTime,
)

/** Where recordings go, and how much room is left (the Storage card). */
@Immutable
data class StorageInfo(val name: String, val freeBytes: Long, val totalBytes: Long) {
    val usedFraction: Float get() = if (totalBytes <= 0) 0f else (1f - freeBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    /** About 2.2 GB an hour in HD (brief 5.6). */
    val hoursInHd: Int get() = (freeBytes / HD_BYTES_PER_HOUR).toInt()

    companion object {
        const val HD_BYTES_PER_HOUR = 2_200_000_000L
    }
}

@Immutable
data class RecordingsUiState(
    val recordings: List<RecordingItem>,
    val scheduled: List<ScheduledItem>,
    val reminders: List<ReminderItem>,
    val rules: List<SeriesRuleItem>,
    val storage: StorageInfo?,
    /** Connections the recordings in progress use, of the source's limit (brief 5.1); null without one. */
    val connectionsInUse: Int,
    val connectionLimit: Int?,
    val conflicts: Int = 0,
)

class RecordingsActions(
    /** OK on a recording: from the start, or with the live edge available while it records. */
    val onPlay: (RecordingItem) -> Unit = {},
    val onDelete: (Set<String>) -> Unit = {},
    val onKeep: (String, Boolean) -> Unit = { _, _ -> },
    val onWatched: (String, Boolean) -> Unit = { _, _ -> },
    /** A scheduled recording or a reminder, in the Guide. */
    val onShowInGuide: (channelName: String, start: ZonedDateTime) -> Unit = { _, _ -> },
    val onCancelScheduled: (String) -> Unit = {},
    val onCancelReminder: (String) -> Unit = {},
    val onDeleteRule: (String) -> Unit = {},
)

/**
 * The Recorded grid for [filter]: the recordings it matches, with a series' episodes gathered into a
 * folder when there are two or more, in [sort] order. Inside an open folder ([folder]), its
 * episodes one by one.
 */
internal fun recordedTiles(
    recordings: List<RecordingItem>,
    filter: RecordingFilter,
    sort: RecordingSort,
    folder: String? = null,
): List<RecordedTile> {
    val matching = recordings.filter { filter.matches(it) }
    val tiles: List<RecordedTile> = if (folder != null) {
        matching.filter { it.series?.id == folder }.map { RecordedTile.Single(it) }
    } else {
        val bySeries = matching.filter { it.series != null }.groupBy { it.series!! }
        val folders = bySeries.filterValues { it.size > 1 }
        val placed = mutableSetOf<String>()
        matching.mapNotNull { recording ->
            val series = recording.series
            when {
                series == null || series !in folders -> RecordedTile.Single(recording)
                placed.add(series.id) -> RecordedTile.Folder(series, folders.getValue(series))
                else -> null
            }
        }
    }
    return when (sort) {
        RecordingSort.Newest -> tiles.sortedByDescending { tile -> tile.recordings.maxOf { it.start } }
        RecordingSort.Oldest -> tiles.sortedBy { tile -> tile.recordings.minOf { it.start } }
        RecordingSort.AZ -> tiles.sortedBy { Titles.sortKey(it.title) }
    }
}

/** All the recorded time, rounded to whole hours for the header ("31 hours"). */
internal fun totalRecorded(recordings: List<RecordingItem>, now: Instant): Duration =
    recordings.fold(Duration.ZERO) { sum, recording -> sum + recording.recordedAt(now) }

/** Days until [date], counting from [today]: 2 for "Deletes in 2 days". */
internal fun daysUntil(date: LocalDate, today: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(today, date)

/** The amber warning shows two days before deletion (brief 5.6), unless the recording is kept. */
internal fun RecordingItem.deletesSoon(today: LocalDate): Boolean =
    !kept && deletesOn != null && daysUntil(deletesOn, today) <= 2
