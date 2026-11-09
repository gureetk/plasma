// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind
import ca.gureet.plasma.core.data.RecordingStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

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

    @Test fun aMomentIsFoundAtTheKeyframeBeforeIt() {
        val media = File(folder, "Arena Sports 1.ts")
        assertNull(RecordingFiles.keyframeAt(media, 0))
        KeyframeIndex(RecordingFiles.indexOf(media)).use { index ->
            index.add(0, 1_000_000)
            index.add(1_880, 1_002_000)
            index.add(3_760, 1_004_000)
        }

        assertEquals(1_880L to 1_002_000L, RecordingFiles.keyframeAt(media, 1_003_500))
        assertEquals(3_760L to 1_004_000L, RecordingFiles.keyframeAt(media, 9_999_999))
        // Before the first: the first.
        assertEquals(0L to 1_000_000L, RecordingFiles.keyframeAt(media, 0))
        // A line still being written is passed over.
        RecordingFiles.indexOf(media).appendText("5640 10")
        assertEquals(3_760L to 1_004_000L, RecordingFiles.keyframeAt(media, 9_999_999))
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

    /** A recording made in Plasma's own folder, with what the guide said and its index. */
    private fun made(own: File, recording: Recording, stream: String = "stream"): File {
        own.mkdirs()
        val file = RecordingFiles.newFile(own, recording, toronto)
        file.writeText(stream)
        RecordingFiles.writeSidecar(file, recording)
        RecordingFiles.indexOf(file).writeText("0 1000\n")
        return file
    }

    @Test fun aRecordingMovesWithWhatTheGuideSaidAndItsIndex() = runBlocking {
        val file = made(File(folder, "own/Recordings"), recording("Harbour City v Redmoor", evening))
        val shared = File(folder, "Download/Plasma/Recordings")
        var told: File? = null

        val moved = RecordingFiles.moveTo(file, shared) { told = it }

        assertEquals(File(shared, file.name), moved)
        assertEquals(moved, told)
        assertEquals("stream", moved!!.readText())
        assertTrue(RecordingFiles.sidecarOf(moved).isFile)
        assertEquals("0 1000\n", RecordingFiles.indexOf(moved).readText())
        assertFalse(file.exists())
        assertFalse(RecordingFiles.sidecarOf(file).exists())
        assertFalse(RecordingFiles.indexOf(file).exists())
    }

    @Test fun aNameTakenByAnotherRecordingGetsAnother() = runBlocking {
        val file = made(File(folder, "own/Recordings"), recording("Harbour City v Redmoor", evening))
        val shared = File(folder, "Download/Plasma/Recordings").apply { mkdirs() }
        File(shared, file.name).writeText("another recording")

        val moved = RecordingFiles.moveTo(file, shared) { }

        assertEquals("Arena Sports 1 – Harbour City v Redmoor – 2026-10-21 1445 (2).ts", moved!!.name)
        assertEquals("another recording", File(shared, file.name).readText())
    }

    @Test fun aMoveCutShortCarriesOnWithWhatItCopied() = runBlocking {
        val file = made(File(folder, "own/Recordings"), recording("Harbour City v Redmoor", evening))
        val shared = File(folder, "Download/Plasma/Recordings").apply { mkdirs() }
        // The file itself was copied before Plasma stopped, and Recordings didn't hear of it.
        File(shared, file.name).writeText("stream")

        val moved = RecordingFiles.moveTo(file, shared) { }

        assertEquals(File(shared, file.name), moved)
        assertTrue(RecordingFiles.sidecarOf(moved!!).isFile)
        assertFalse(file.exists())
    }

    @Test fun aFoundRecordingIsWhatTheGuideSaid() {
        val said = recording("Kingdoms of Ice", evening).copy(
            subtitle = "Ep. 4",
            category = "Nature",
            season = 1,
            episode = 4,
            kind = RecordingKind.Series,
            image = "https://images.example.com/kingdoms.jpg",
            rating = "TV-PG",
        )
        val file = made(File(folder, "Recordings"), said)
        RecordingFiles.indexOf(file).writeText("0 ${evening.minusSeconds(60).toEpochMilli()}\n100 ${evening.plusSeconds(3_900).toEpochMilli()}\n")

        val found = RecordingFiles.found(file, toronto)!!

        assertEquals(file.path, found.file)
        assertEquals(6L, found.sizeBytes)
        assertEquals("home:105", found.channelId)
        assertEquals("Arena Sports 1", found.channelName)
        assertEquals("Kingdoms of Ice", found.title)
        assertEquals("Ep. 4", found.subtitle)
        assertEquals("Nature", found.category)
        assertEquals(1, found.season)
        assertEquals(4, found.episode)
        assertEquals(RecordingKind.Series, found.kind)
        assertEquals("https://images.example.com/kingdoms.jpg", found.image)
        assertEquals("TV-PG", found.rating)
        assertEquals(evening, found.start)
        assertEquals(evening.plus(Duration.ofHours(1)), found.end)
        // When it was written, from its index.
        assertEquals(evening.minusSeconds(60), found.recordedFrom)
        assertEquals(evening.plusSeconds(3_900), found.recordedTo)
    }

    @Test fun withoutWhatTheGuideSaidItsNameSays() {
        val file = File(folder, "Arena Sports 1 – Fight Night - Main Card – 2026-10-21 1445 (2).ts")
        file.writeText("stream")
        file.setLastModified(evening.plus(Duration.ofHours(2)).toEpochMilli())

        val found = RecordingFiles.found(file, toronto)!!

        assertNull(found.channelId)
        assertEquals("Arena Sports 1", found.channelName)
        assertEquals("Fight Night - Main Card", found.title)
        assertEquals(evening, found.start)
        // Written till the file last changed.
        assertEquals(evening.plus(Duration.ofHours(2)), found.recordedTo)
        assertNull(found.kind)
        // A name Plasma didn't give it.
        assertNull(RecordingFiles.found(File(folder, "Holiday.ts").apply { writeText("x") }, toronto))
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
