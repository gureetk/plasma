// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.RecordingEntity
import java.time.Instant

/**
 * A recording's file that Recordings doesn't list, found in Plasma's folders by Find Recordings
 * (#161): after Plasma was reinstalled, or its storage cleared, which takes Recordings' list with
 * it. What it was, as the info file beside it says, or else its name ("Channel – Title –
 * 2026-10-21 1945.ts") and when it was last written to.
 */
data class FoundRecording(
    val file: String,
    val sizeBytes: Long,
    /** The channel, as the info file says; null when only the name's there to go by. */
    val channelId: String?,
    val channelName: String,
    val title: String,
    /** The programme's start and end, as the guide had them. */
    val start: Instant,
    val end: Instant,
    /** When the file's first and last bytes came. */
    val recordedFrom: Instant,
    val recordedTo: Instant,
    val channelLogo: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val image: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val kind: RecordingKind? = null,
    val rating: String? = null,
) {
    /** As a recording that's done, under [id], for [ruleId] when the rule's still there, found at [now]. */
    internal fun toEntity(id: String, ruleId: String?, now: Long) = RecordingEntity(
        id = id,
        channelId = channelId ?: channelIdOf(channelName),
        channelName = channelName,
        channelLogo = channelLogo,
        title = title,
        subtitle = subtitle,
        description = description,
        category = category,
        image = image,
        season = season,
        episode = episode,
        kind = (kind ?: RecordingKind.guess(category, season, episode)).key,
        startAt = start.toEpochMilli(),
        endAt = end.toEpochMilli(),
        padBeforeMs = 0,
        padAfterMs = 0,
        status = RecordingStatus.Done.key,
        filePath = file,
        sizeBytes = sizeBytes,
        recordedFrom = recordedFrom.toEpochMilli(),
        recordedTo = recordedTo.toEpochMilli(),
        interruptions = 0,
        problem = null,
        keep = false,
        positionMs = 0,
        watched = false,
        watchedAt = 0,
        createdAt = now,
        ruleId = ruleId,
        rating = rating,
    )

    companion object {
        /**
         * A channel known only by its name, from a file's name: "found:Arena Sports 1". No source
         * has a channel by that id, so the recording only plays.
         */
        fun channelIdOf(name: String): String = "found:$name"
    }
}
