// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.ProgrammeEntity
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A guide window's rows, sorted by guide channel for the screens (brief 5.3): what a collector
 * gets, and that going through the rows is never done on the thread that collects, which for a
 * screen is the main thread (#127).
 */
class GuideRepositoryTest {
    private val start = Instant.parse("2026-10-21T20:00:00Z").toEpochMilli()

    private fun row(epgKey: String, title: String, halfHour: Int) = ProgrammeEntity(
        sourceId = "source",
        epgKey = epgKey,
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
        generation = 1,
    )

    /** An evening on two guide channels, as the query answers it: by guide channel, then by time. */
    private val evening = listOf(
        row("arena", "Courtside Tonight", 0),
        row("arena", "City Lights v Northport", 1),
        row("harbour", "Harbour News", 0),
    )

    /**
     * Runs [block] as a screen would collect: on one thread of its own, which it's handed. A flow
     * that waits for its collector where it shouldn't ends the test, not the run.
     */
    private fun collecting(block: suspend (collector: Thread) -> Unit) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            runBlocking(executor.asCoroutineDispatcher()) { withTimeout(TEST_ENDS_MS) { block(Thread.currentThread()) } }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun anAnswerComesByGuideChannelWithItsRowsInTheOrderTheyCame() = collecting { _ ->
        val window = flowOf(evening).byGuideChannel().first()

        assertEquals(listOf("arena", "harbour"), window.keys.toList())
        assertEquals(listOf("Courtside Tonight", "City Lights v Northport"), window.getValue("arena").map { it.title })
        assertEquals(listOf("Harbour News"), window.getValue("harbour").map { it.title })
        assertEquals(Instant.ofEpochMilli(start + HALF_HOUR_MS), window.getValue("arena")[1].start)
    }

    @Test fun rowsAreTurnedIntoProgrammesOffTheThreadThatCollects() = collecting { collector ->
        val turnedOn = CopyOnWriteArrayList<Thread>()

        val window = flowOf(evening).byGuideChannel(programme = { turnedOn += Thread.currentThread(); it.toProgramme() }).first()

        assertEquals(3, window.values.sumOf { it.size })
        assertEquals(3, turnedOn.size)
        assertFalse("A row was turned into a programme on the collector's thread", turnedOn.any { it === collector })
    }

    @Test fun anAnswerIsComparedWithTheLastOffTheThreadThatCollects() = collecting { collector ->
        val comparedOn = CopyOnWriteArrayList<Thread>()
        val later = evening + row("harbour", "Harbour Late", 1)
        // The same rows twice, as when a write elsewhere in the table re-runs the query, then new ones.
        val answers = flowOf<List<ProgrammeEntity>>(Watched(evening, comparedOn), Watched(evening.toList(), comparedOn), Watched(later, comparedOn))

        val windows = answers.byGuideChannel().toList()

        // The repeat is told from the first by going through its rows, and so are the new ones.
        assertEquals(2, comparedOn.size)
        assertFalse("An answer was compared with the last on the collector's thread", comparedOn.any { it === collector })
        // The repeat isn't passed on. (The first answer may have given way to the last before it was taken.)
        assertTrue(windows.size in 1..2)
        assertEquals(listOf("Harbour News", "Harbour Late"), windows.last().getValue("harbour").map { it.title })
    }

    @Test fun aNewerAnswerTakesThePlaceOfOneNotYetTaken() = collecting { _ ->
        val firstTaken = CompletableDeferred<Unit>()
        val allSent = CompletableDeferred<Unit>()
        val answers = flow {
            emit(listOf(row("arena", "First", 0)))
            firstTaken.await()
            emit(listOf(row("arena", "Second", 0)))
            emit(listOf(row("arena", "Third", 0)))
            allSent.complete(Unit)
        }
        val taken = mutableListOf<String>()

        answers.byGuideChannel().collect { window ->
            taken += window.getValue("arena").single().title
            // The collector is busy with the first answer while two more come.
            if (taken.size == 1) {
                firstTaken.complete(Unit)
                allSent.await()
            }
        }

        assertEquals(listOf("First", "Third"), taken)
    }

    // ---- A window's pieces ---------------------------------------------------------------------

    @Test fun aPieceGrowsTowardsWhatAWindowHoldsAndNoFasterThanTwofold() {
        // 25 guide channels of short rows: 650 of them come to a fifth of a megabyte.
        val light = List(650) { row("arena", "Courtside Tonight", it) }

        assertEquals(50, keysToAPiece(25, light))
        // 100 guide channels of the same: 2,600 rows, three quarters of a megabyte, so a few more fit.
        assertEquals(134, keysToAPiece(100, light + light + light + light))
    }

    @Test fun aPieceOfLongDescriptionsGetsSmaller() {
        // 25 guide channels with a programme every half hour, each described in a thousand characters.
        val long = List(1_600) { row("arena", "T", it).copy(description = "x".repeat(1_000)) }

        assertEquals(7, keysToAPiece(25, long))
        // And never under the fewest, however heavy.
        assertEquals(FEWEST_PIECE_KEYS, keysToAPiece(5, long + long + long))
    }

    @Test fun aPieceWithNothingOnDoublesAndNeverPassesTheMost() {
        assertEquals(50, keysToAPiece(25, emptyList()))
        assertEquals(MOST_PIECE_KEYS, keysToAPiece(400, emptyList()))
        assertEquals(MOST_PIECE_KEYS, keysToAPiece(MOST_PIECE_KEYS, listOf(row("arena", "Courtside Tonight", 0))))
    }

    /** A query's answer that notes the thread it's compared with another on. */
    private class Watched(private val rows: List<ProgrammeEntity>, private val comparedOn: MutableList<Thread>) : AbstractList<ProgrammeEntity>() {
        override val size get() = rows.size
        override fun get(index: Int) = rows[index]
        override fun equals(other: Any?): Boolean {
            comparedOn += Thread.currentThread()
            return super.equals(other)
        }
        override fun hashCode() = super.hashCode()
    }

    private companion object {
        const val HALF_HOUR_MS = 30 * 60 * 1000L
        const val TEST_ENDS_MS = 10_000L
    }
}
