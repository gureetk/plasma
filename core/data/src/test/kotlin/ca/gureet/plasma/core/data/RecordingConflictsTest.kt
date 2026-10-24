// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Conflicts against each source's connections (brief 5.6). */
class RecordingConflictsTest {
    private val evening = Instant.parse("2026-10-21T19:00:00Z")

    private fun recording(channelId: String, startHour: Long, hours: Long, status: RecordingStatus = RecordingStatus.Scheduled): Recording {
        val start = evening.plus(Duration.ofHours(startHour))
        return Recording(
            id = "$channelId@${start.epochSecond}",
            channelId = channelId,
            channelName = channelId,
            channelLogo = null,
            title = "$channelId at $startHour",
            subtitle = null,
            description = null,
            category = null,
            image = null,
            season = null,
            episode = null,
            kind = RecordingKind.Other,
            start = start,
            end = start.plus(Duration.ofHours(hours)),
            padBefore = Duration.ZERO,
            padAfter = Duration.ZERO,
            status = status,
            file = null,
            sizeBytes = 0,
            recordedFrom = null,
            recordedTo = null,
            interruptions = 0,
            problem = null,
            keep = false,
            positionMs = 0,
            watched = false,
            watchedAt = null,
        )
    }

    @Test fun moreAtOnceThanTheLimitIsAConflict() {
        val first = recording("home:105", 0, 2)
        val second = recording("home:106", 1, 2)
        val third = recording("home:107", 1, 1)
        // Overlapping the second, so in the same run; another source's has its own connection.
        val later = recording("home:108", 2, 1)
        val elsewhere = recording("garage:5", 1, 1)

        val conflicts = RecordingConflicts.of(listOf(first, second, third, later, elsewhere)) { if (it == "home") 2 else 1 }

        assertEquals(1, conflicts.size)
        assertEquals(2, conflicts.single().limit)
        // The run of overlapping recordings it's in, soonest first.
        assertEquals(listOf(first.id, second.id, third.id, later.id), conflicts.single().recordings.map { it.id })
    }

    @Test fun withinTheLimitOrWithoutOneThereAreNone() {
        val one = recording("home:105", 0, 2, status = RecordingStatus.Recording)
        val two = recording("home:106", 1, 2)
        val done = recording("home:107", 1, 1, status = RecordingStatus.Done)

        assertTrue(RecordingConflicts.of(listOf(one, two, done)) { 2 }.isEmpty())
        assertTrue(RecordingConflicts.of(listOf(one, two, done)) { null }.isEmpty())
        assertEquals(1, RecordingConflicts.of(listOf(one, two, done)) { 1 }.size)
    }
}
