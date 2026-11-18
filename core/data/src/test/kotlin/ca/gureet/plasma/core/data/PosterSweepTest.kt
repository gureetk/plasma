// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.data.PosterSweep.Title
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Looking posters up while the screensaver is up (#228): the top of each list first, a title at a
 * time with a pause after each, stopping at a failure and when the screensaver goes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PosterSweepTest {
    // Two lists of movies, then one of series: two pages each but the series'.
    private val lists = listOf(
        VodRepository.SweepList(series = false, categoryId = "recent"),
        VodRepository.SweepList(series = false, categoryId = "action"),
        VodRepository.SweepList(series = true, categoryId = "drama"),
    )
    private val pages = mutableMapOf(
        "recent" to listOf(listOf("r1", "r2"), listOf("r3")),
        "action" to listOf(listOf("a1"), listOf("a2", "a3")),
        "drama" to listOf(listOf("d1")),
    )
    private val looked = mutableListOf<String>()
    private val lookedFor = mutableListOf<String>()

    /** A sweep through [pages], where, as in the catalog, a title already looked for is left out of its page. */
    private fun TestScope.sweep(fails: String? = null) = PosterSweep(
        lists = { lists },
        page = { list, round ->
            pages.getValue(list.categoryId!!).getOrNull(round)?.filter { it !in looked && it !in lookedFor }?.map { Title(it, list.series) }
        },
        find = { title ->
            if (title.id == fails) throw IOException("The provider turned it down")
            looked += title.id
            true
        },
        lookedFor = { lookedFor += it.id },
        now = { currentTime },
    )

    @Test fun theTopOfEachListComesFirstATitleAtATime() = runTest {
        val sweep = sweep()
        val running = launch { sweep.sweep() }
        runCurrent()
        // The first, at once; the next after a pause.
        assertEquals(listOf("r1"), looked)
        advanceTimeBy(PosterSweep.PAUSE_MS)
        runCurrent()
        assertEquals(listOf("r1", "r2"), looked)
        advanceTimeBy(PosterSweep.PAUSE_MS * 10)
        // A round of every list before the next of any.
        assertEquals(listOf("r1", "r2", "a1", "d1", "r3", "a2", "a3"), looked)
        assertEquals(7, sweep.tally.read)
        assertNull(sweep.tally.stoppedBy)
        assertNotNull(sweep.tally.finishedAt)
        running.join()
    }

    @Test fun aFailureStopsTheSweepForAWhileAndItsTitleIsntAskedAgain() = runTest {
        val sweep = sweep(fails = "a1")
        sweep.sweep()
        assertEquals(listOf("r1", "r2"), looked)
        assertEquals(listOf("a1"), lookedFor)
        assertEquals("IOException", sweep.tally.stoppedBy)

        // Resting: nothing's asked.
        advanceTimeBy(PosterSweep.REST_MS - 1)
        sweep.sweep()
        assertEquals(listOf("r1", "r2"), looked)

        // Then it carries on, past the title that failed.
        advanceTimeBy(1)
        sweep.sweep()
        assertEquals(listOf("r1", "r2", "d1", "r3", "a2", "a3"), looked)
    }

    @Test fun itStopsWhenTheScreensaverGoesAndCarriesOnLater() = runTest {
        val screensaver = MutableStateFlow(false)
        val sweep = sweep()
        val running = launch { sweep.run(screensaver) }
        screensaver.value = true
        // Nothing until the screensaver has been up a while.
        advanceTimeBy(PosterSweep.START_AFTER_MS - 1)
        assertEquals(emptyList<String>(), looked)
        advanceTimeBy(PosterSweep.PAUSE_MS * 3 + 2)
        assertEquals(listOf("r1", "r2", "a1", "d1"), looked)

        // The screensaver goes: the sweep stops at once.
        screensaver.value = false
        advanceTimeBy(PosterSweep.PAUSE_MS * 10)
        assertEquals(4, looked.size)

        // Up again, it carries on.
        screensaver.value = true
        advanceTimeBy(PosterSweep.START_AFTER_MS + PosterSweep.PAUSE_MS * 10)
        assertEquals(listOf("r1", "r2", "a1", "d1", "r3", "a2", "a3"), looked)
        assertNotNull(sweep.tally.finishedAt)

        // Through every list, it starts again from the top only hours later, for titles new since.
        pages["recent"] = listOf(listOf("r0", "r1", "r2"), listOf("r3"))
        screensaver.value = false
        runCurrent()
        screensaver.value = true
        advanceTimeBy(PosterSweep.START_AFTER_MS + PosterSweep.PAUSE_MS * 10)
        assertEquals(7, looked.size)
        screensaver.value = false
        runCurrent()
        advanceTimeBy(PosterSweep.AGAIN_AFTER_MS)
        screensaver.value = true
        advanceTimeBy(PosterSweep.START_AFTER_MS + 1)
        assertEquals("r0", looked.last())
        running.cancel()
    }

    @Test fun withNoListsNothingIsLookedForNorFinished() = runTest {
        val sweep = PosterSweep(lists = { emptyList() }, page = { _, _ -> null }, find = { true }, lookedFor = {}, now = { currentTime })
        sweep.sweep()
        assertNull(sweep.tally.finishedAt)
    }
}
