// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Record and Cancel Recording (brief 5.6), and what the recorder says of each. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RecordingRepositoryTest {
    private val now = Instant.parse("2026-10-21T20:41:00Z")
    private lateinit var db: PlasmaDatabase
    private lateinit var recordings: RecordingRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recordings = RecordingRepository(db, FixedClock(now, ZoneOffset.UTC))
    }

    @After fun tearDown() = db.close()

    private val fightNight = Instant.parse("2026-10-22T00:30:00Z")

    private fun programme(title: String = "Fight Night: Main Card", start: Instant = fightNight, category: String? = "Sports") = ProgrammeToRecord(
        channelId = "home:105",
        channelName = "Arena Sports 1",
        channelLogo = null,
        title = title,
        category = category,
        start = start,
        end = start.plus(Duration.ofMinutes(150)),
    )

    private val minute = Duration.ofMinutes(1)
    private val twenty = Duration.ofMinutes(20)

    @Test fun aRecordingIsKeptUnderItsProgrammesId() = runTest {
        val recording = recordings.schedule(programme(), minute, twenty)
        // Record twice on one programme keeps one recording.
        recordings.schedule(programme(), Duration.ZERO, Duration.ZERO)

        assertEquals("home:105@${fightNight.epochSecond}", recording.id)
        val kept = recordings.recordings.first().single()
        assertEquals(RecordingStatus.Scheduled, kept.status)
        assertEquals(RecordingKind.Sports, kept.kind)
        assertEquals(fightNight.minus(minute), kept.from)
        assertEquals(fightNight.plus(Duration.ofMinutes(150)).plus(twenty), kept.until)
        assertEquals(setOf(recording.id), recordings.ids.first())
    }

    @Test fun cancellingOneComingUpTakesItAway() = runTest {
        val recording = recordings.schedule(programme(), minute, twenty)

        recordings.cancel(recording.id)

        assertTrue(recordings.recordings.first().isEmpty())
        assertTrue(recordings.ids.first().isEmpty())
    }

    @Test fun cancellingOneBeingWrittenStopsItAndKeepsWhatItHas() = runTest {
        val recording = recordings.schedule(programme(), minute, twenty)
        recordings.started(recording.id, "/drive/Recordings/Arena Sports 1 – Fight Night.ts")
        recordings.progress(recording.id, 1_000_000)

        recordings.cancel(recording.id)
        // The recorder stopping after that changes nothing.
        recordings.finished(recording.id, failed = false)

        val kept = recordings.get(recording.id)!!
        assertEquals(RecordingStatus.Done, kept.status)
        assertEquals(RecordingProblems.STOPPED, kept.problem)
        assertEquals(1_000_000L, kept.sizeBytes)
        assertEquals(now, kept.recordedFrom)
        assertTrue(recordings.ids.first().isEmpty())
    }

    @Test fun whatTheRecorderSaysIsKept() = runTest {
        val recording = recordings.schedule(programme(), minute, twenty)
        recordings.started(recording.id, "/drive/a.ts")
        recordings.interrupted(recording.id)
        recordings.progress(recording.id, 4_000)
        recordings.finished(recording.id, failed = false)

        val done = recordings.get(recording.id)!!
        assertEquals(RecordingStatus.Done, done.status)
        assertEquals("/drive/a.ts", done.file)
        assertEquals(1, done.interruptions)
        assertNull(done.problem)

        val other = recordings.schedule(programme("Harbour City v Northport", fightNight.plus(Duration.ofDays(2))), minute, twenty)
        recordings.finished(other.id, failed = true, problem = RecordingProblems.NO_CONNECTION)
        assertEquals(RecordingStatus.Failed, recordings.get(other.id)!!.status)
        // Neither is waiting for the recorder any more.
        assertTrue(recordings.pending().isEmpty())
    }

    @Test fun keptFromLocalCatchUpItsDone() = runTest {
        val yesterday = fightNight.minus(Duration.ofDays(1))
        val failed = recordings.schedule(programme(start = yesterday), minute, twenty)
        recordings.finished(failed.id, failed = true, problem = RecordingProblems.NO_CONNECTION)

        // A recording that couldn't be made is kept from local catch-up instead (5.5).
        val kept = recordings.kept(programme(start = yesterday), "/drive/Recordings/Fight Night.ts", 4_000_000_000)

        assertEquals(failed.id, kept.id)
        val now = recordings.get(kept.id)!!
        assertEquals(RecordingStatus.Done, now.status)
        assertEquals("/drive/Recordings/Fight Night.ts", now.file)
        assertEquals(4_000_000_000, now.sizeBytes)
        assertEquals(Duration.ofMinutes(150), now.recordedFor)
        assertNull(now.problem)
        // Kept again, it stays as it is.
        assertEquals("/drive/Recordings/Fight Night.ts", recordings.kept(programme(start = yesterday), "/elsewhere.ts", 1).file)
    }

    @Test fun deletingSaysWhichFilesWentWithIt() = runTest {
        val recording = recordings.schedule(programme(), minute, twenty)
        recordings.started(recording.id, "/drive/a.ts")

        assertEquals(listOf("/drive/a.ts"), recordings.delete(listOf(recording.id)))
        assertNull(recordings.get(recording.id))
    }

    @Test fun watchedFromNinetyTwoPercent() = runTest {
        val recording = recordings.schedule(programme(), minute, twenty)

        recordings.played(recording.id, positionMs = 60_000, durationMs = 100_000)
        assertEquals(60_000L, recordings.get(recording.id)!!.positionMs)
        assertFalse(recordings.get(recording.id)!!.watched)

        recordings.played(recording.id, positionMs = 93_000, durationMs = 100_000)
        assertTrue(recordings.get(recording.id)!!.watched)
        assertEquals(0L, recordings.get(recording.id)!!.positionMs)

        recordings.markWatched(recording.id, false)
        assertFalse(recordings.get(recording.id)!!.watched)
        recordings.keep(recording.id, true)
        assertTrue(recordings.get(recording.id)!!.keep)
    }

    @Test fun theGuideSaysWhatKindItIs() {
        assertEquals(RecordingKind.Movie, RecordingKind.guess("Film", null, null))
        assertEquals(RecordingKind.Movie, RecordingKind.guess("Movie / Drama", null, null))
        assertEquals(RecordingKind.Sports, RecordingKind.guess("Sports", null, null))
        assertEquals(RecordingKind.News, RecordingKind.guess("News", null, null))
        assertEquals(RecordingKind.Series, RecordingKind.guess("Documentary", 1, 3))
        assertEquals(RecordingKind.Other, RecordingKind.guess(null, null, null))
    }
}
