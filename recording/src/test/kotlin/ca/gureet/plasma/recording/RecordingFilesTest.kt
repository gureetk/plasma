// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind
import ca.gureet.plasma.core.data.RecordingStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Recordings' file names (brief 5.6), and when the alarm goes off. */
class RecordingFilesTest {
    private val folder: File = Files.createTempDirectory("recordings").toFile()

    @After fun tidy() {
        folder.deleteRecursively()
    }

    private fun recording(title: String, start: Instant, status: RecordingStatus = RecordingStatus.Scheduled, early: Long = 1) = Recording(
        id = "home:105@${start.epochSecond}",
        channelId = "home:105",
        channelName = "Arena Sports 1",
        channelLogo = null,
        title = title,
        subtitle = null,
        description = null,
        category = null,
        image = null,
        season = null,
        episode = null,
        kind = RecordingKind.Other,
        start = start,
        end = start.plus(Duration.ofHours(1)),
        padBefore = Duration.ofMinutes(early),
        padAfter = Duration.ofMinutes(5),
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

    private val evening = Instant.parse("2026-10-21T18:45:00Z")
    private val toronto = ZoneId.of("America/Toronto")

    @Test fun namedForTheChannelTheTitleAndTheStart() {
        val file = RecordingFiles.newFile(folder, recording("Harbour City v Redmoor", evening), toronto)
        assertEquals("Arena Sports 1 – Harbour City v Redmoor – 2026-10-21 1445.ts", file.name)

        // Taken already: another name.
        file.writeText("")
        assertEquals("Arena Sports 1 – Harbour City v Redmoor – 2026-10-21 1445 (2).ts", RecordingFiles.newFile(folder, recording("Harbour City v Redmoor", evening), toronto).name)
    }

    @Test fun whatAShareRefusesIsReplaced() {
        assertEquals("What - Who-When-Where", RecordingFiles.clean("What: Who/When|Where"))
        assertEquals("Why Not It", RecordingFiles.clean("Why? \"Not\" <It>*"))
        assertEquals("News at Ten", RecordingFiles.clean("  News\tat  Ten.. "))
    }

    @Test fun theIndexHasAKeyframeAboutEveryTwoSecondsFromTheFirst() {
        val media = File(folder, "Arena Sports 1.ts")
        KeyframeIndex(RecordingFiles.indexOf(media)).use { index ->
            index.add(0, 1_000_000)
            index.add(1_880, 1_001_000)
            index.add(3_760, 1_002_000)
            index.add(5_640, 1_003_500)
            index.add(7_520, 1_004_000)
        }

        assertEquals(listOf("0 1000000", "3760 1002000", "7520 1004000"), RecordingFiles.indexOf(media).readLines())
    }

    @Test fun theAlarmGoesOffBeforeTheNextOneToStart() {
        val later = recording("Late Night Sessions", evening.plus(Duration.ofHours(3)))
        val next = recording("Harbour City v Redmoor", evening, early = 2)
        val done = recording("Business Wrap", evening.minus(Duration.ofHours(1)), status = RecordingStatus.Done)

        assertEquals(
            evening.minus(Duration.ofMinutes(2)).minusMillis(RecordingAlarms.LEAD_MS),
            RecordingAlarms.nextStart(listOf(later, done, next)),
        )
        assertNull(RecordingAlarms.nextStart(listOf(done)))
    }
}
