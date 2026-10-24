// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind
import ca.gureet.plasma.core.data.RecordingProblems
import ca.gureet.plasma.core.data.RecordingStatus
import ca.gureet.plasma.core.data.Reminder
import ca.gureet.plasma.feature.recordings.RecordingKind as TileKind
import ca.gureet.plasma.feature.recordings.RecordingTrouble
import ca.gureet.plasma.feature.recordings.StorageInfo
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recordings (board 17) on what's recorded, being recorded and coming up (brief 5.6). */
class RecordingsStateTest {
    private val zone = ZoneOffset.UTC
    private val evening = Instant.parse("2026-10-21T19:45:00Z")

    private fun recording(
        title: String,
        start: Instant,
        status: RecordingStatus,
        kind: RecordingKind = RecordingKind.Other,
        subtitle: String? = null,
        size: Long = 1_000,
        interruptions: Int = 0,
        problem: String? = null,
        positionMs: Long = 0,
    ) = Recording(
        id = "home:105@${start.epochSecond}",
        channelId = "home:105",
        channelName = "Arena Sports 1",
        channelLogo = "https://media.home.lan/logos/arena1.png",
        title = title,
        subtitle = subtitle,
        description = null,
        category = null,
        image = null,
        season = null,
        episode = null,
        kind = kind,
        start = start,
        end = start.plus(Duration.ofMinutes(60)),
        padBefore = Duration.ofMinutes(1),
        padAfter = Duration.ofMinutes(5),
        status = status,
        file = "/drive/Recordings/$title.ts".takeIf { status != RecordingStatus.Scheduled },
        sizeBytes = size,
        recordedFrom = start.minus(Duration.ofMinutes(1)).takeIf { status != RecordingStatus.Scheduled },
        recordedTo = start.plus(Duration.ofMinutes(65)).takeIf { status != RecordingStatus.Scheduled },
        interruptions = interruptions,
        problem = problem,
        keep = false,
        positionMs = positionMs,
        watched = false,
        watchedAt = null,
    )

    private fun state(recordings: List<Recording>, reminders: List<Reminder> = emptyList(), limit: Int? = null) = recordingsState(
        recordings = recordings,
        reminders = reminders,
        channelOf = { id -> if (id == "home:108") "Courtside+" to null else null },
        zone = zone,
        storage = StorageInfo("USB drive", 212_000_000_000, 500_000_000_000),
        connectionLimit = limit,
    )

    @Test fun recordedAndComingUpAreApart() {
        val state = state(
            listOf(
                recording("Fight Night: Main Card", evening.plus(Duration.ofHours(5)), RecordingStatus.Scheduled, RecordingKind.Sports),
                recording("Harbour City v Redmoor", evening, RecordingStatus.Recording, RecordingKind.Sports),
                recording("Business Wrap", evening.minus(Duration.ofDays(1)), RecordingStatus.Done, RecordingKind.News),
            ),
            limit = 2,
        )

        assertEquals(listOf("Fight Night: Main Card"), state.scheduled.map { it.title })
        assertEquals(Duration.ofMinutes(5), state.scheduled.single().endsLate)
        assertEquals(listOf("Harbour City v Redmoor", "Business Wrap"), state.recordings.map { it.title })
        val now = state.recordings.first()
        assertTrue(now.inProgress)
        assertEquals(TileKind.Sports, now.kind)
        assertTrue(state.recordings.last().unwatched)
        // The one being written uses a connection of the two.
        assertEquals(1, state.connectionsInUse)
        assertEquals(2, state.connectionLimit)
    }

    @Test fun aSeriesGathersItsEpisodesByTitle() {
        val state = state(
            listOf(
                recording("Kingdoms of Ice", evening.minus(Duration.ofDays(3)), RecordingStatus.Done, RecordingKind.Series, subtitle = "The Long Night"),
                recording("Kingdoms of Ice", evening.minus(Duration.ofDays(1)), RecordingStatus.Done, RecordingKind.Series, subtitle = "Thaw"),
            ),
        )

        assertEquals(listOf("Kingdoms of Ice: The Long Night", "Kingdoms of Ice: Thaw"), state.recordings.map { it.title })
        assertEquals(setOf("Kingdoms of Ice"), state.recordings.mapNotNull { it.series?.title }.toSet())
    }

    @Test fun whatWentWrongShows() {
        val state = state(
            listOf(
                recording("Late Night Sessions", evening.minus(Duration.ofDays(2)), RecordingStatus.Failed, size = 0, problem = RecordingProblems.NO_CONNECTION),
                recording("Deep Field", evening.minus(Duration.ofDays(1)), RecordingStatus.Done, interruptions = 2),
                recording("Tuesday Night Football", evening.minus(Duration.ofHours(30)), RecordingStatus.Done, positionMs = 33 * 60_000L),
            ),
        )

        val (failed, gaps, started) = state.recordings
        assertEquals(RecordingTrouble.Failed, failed.trouble)
        assertFalse(failed.unwatched)
        assertEquals(RecordingTrouble.Incomplete, gaps.trouble)
        assertNull(started.trouble)
        // 33 of its 66 minutes.
        assertEquals(0.5f, started.resume!!, 0.01f)
        assertFalse(started.unwatched)
    }

    @Test fun remindersAreListedWithTheirChannels() {
        val extra = Instant.parse("2026-10-21T22:00:00Z")
        val state = state(emptyList(), listOf(Reminder("home:108@${extra.epochSecond}", "home:108", "Match of the Week Extra", extra, extra.plusSeconds(3_600))))

        val reminder = state.reminders.single()
        assertEquals("Courtside+", reminder.channelName)
        assertEquals(extra.atZone(zone), reminder.start)
        assertNull(state.connectionLimit)
    }
}
