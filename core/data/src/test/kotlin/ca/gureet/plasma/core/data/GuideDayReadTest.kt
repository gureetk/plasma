// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.ProgrammeEntity
import ca.gureet.plasma.core.database.SourceEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * A guide window read a few guide channels at a time (#127), from a real database: it's what one
 * query gives, in its order, and it answers again when the guide changes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GuideDayReadTest {
    private val start = Instant.parse("2026-10-21T00:00:00Z").toEpochMilli()
    private val home = SourceEntity(id = "home", type = "xtream", name = "Home", server = "http://line.example.org/", username = "viewer", passwordEnc = "x", priority = 0, addedAt = 1)
        .copy(guideGeneration = 1)
    private lateinit var db: PlasmaDatabase
    private lateinit var guide: GuideRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        guide = GuideRepository(db)
    }

    @After fun tearDown() {
        db.close()
    }

    private fun programme(channel: Int, halfHour: Int, generation: Long = 1, title: String = "P$halfHour") = ProgrammeEntity(
        sourceId = "home",
        epgKey = "guide.%03d".format(channel),
        startAt = start + halfHour * HALF_HOUR_MS,
        endAt = start + (halfHour + 1) * HALF_HOUR_MS,
        title = title,
        subtitle = null,
        description = null,
        category = null,
        icon = null,
        season = null,
        episode = null,
        isNew = false,
        generation = generation,
    )

    @Test fun aWindowReadInPiecesIsWhatOneQueryGives() = runBlocking {
        db.sources().upsert(home)
        // More guide channels than three pieces take, written last channel first, with a guide
        // still being written beside the complete one.
        val rows = (CHANNELS - 1 downTo 0).flatMap { channel -> (0 until 6).map { programme(channel, it) } } +
            programme(3, 2, generation = 2, title = "Not yet")
        db.programmes().insertAll(rows)
        // From the middle of each channel's second programme to the end of its fourth.
        val from = start + HALF_HOUR_MS + HALF_HOUR_MS / 2
        val to = start + 4 * HALF_HOUR_MS
        val whole = db.programmes().observeWindow(from, to).first()
        assertEquals(CHANNELS * 3, whole.size)
        assertTrue(CHANNELS > FIRST_PIECE_KEYS * 3)

        val window = withTimeout(TEST_ENDS_MS) { guide.window(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to)).first() }

        assertEquals(whole.map { it.epgKey }.distinct(), window.keys.toList())
        assertEquals(whole.groupBy({ it.epgKey }, { it.toProgramme() }), window)
        assertEquals(listOf("P1", "P2", "P3"), window.getValue("guide.003").map { it.title })
    }

    @Test fun aChangeToTheGuideBringsItsNextAnswer() = runBlocking {
        db.sources().upsert(home)
        db.programmes().insertAll(listOf(programme(0, 0), programme(1, 0)))
        val answers = Channel<Map<String, List<Programme>>>(Channel.UNLIMITED)
        val reading = launch(Dispatchers.Default) {
            guide.window(Instant.ofEpochMilli(start), Instant.ofEpochMilli(start + DAY_MS)).collect { answers.send(it) }
        }
        try {
            val first = withTimeout(TEST_ENDS_MS) { answers.receive() }
            assertEquals(listOf("guide.000", "guide.001"), first.keys.toList())

            // A guide still being written changes the table and not the window: no answer comes of
            // it. A programme of the complete guide brings the next.
            db.programmes().insertAll(listOf(programme(2, 0, generation = 2)))
            db.programmes().insertAll(listOf(programme(1, 1, title = "Late News")))

            val next = withTimeout(TEST_ENDS_MS) { answers.receive() }
            assertEquals(listOf("guide.000", "guide.001"), next.keys.toList())
            assertEquals(listOf("P0", "Late News"), next.getValue("guide.001").map { it.title })
        } finally {
            reading.cancel()
        }
    }

    private companion object {
        const val CHANNELS = 130
        const val HALF_HOUR_MS = 30 * 60 * 1000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val TEST_ENDS_MS = 30_000L
    }
}
