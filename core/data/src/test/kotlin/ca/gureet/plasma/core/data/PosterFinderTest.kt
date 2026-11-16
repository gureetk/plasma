// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Looking for movies' own posters as they show (the owner's #81, #228): the latest first, four at
 * a time, each once, and what's found kept together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PosterFinderTest {
    @Test fun theLatestWantedComeFirstFourAtATimeAndEachOnce() = runTest {
        val looked = mutableListOf<String>()
        val answer = CompletableDeferred<Unit>()
        val finder = PosterFinder<String>(
            lookUp = { id ->
                looked += id
                answer.await()
                id
            },
            keep = {},
        )
        listOf("a", "b", "c", "d", "e", "f", "a").forEach(finder::want)
        val running = launch { finder.run() }
        runCurrent()
        assertEquals(listOf("f", "e", "d", "c"), looked)

        answer.complete(Unit)
        runCurrent()
        assertEquals(listOf("f", "e", "d", "c", "b", "a"), looked)

        // A title is looked for once while Plasma runs.
        finder.want("b")
        runCurrent()
        assertEquals(6, looked.size)
        running.cancel()
    }

    @Test fun moviesAndSeriesAreFourAtATimeTogether() = runTest {
        val looked = mutableListOf<String>()
        val answer = CompletableDeferred<Unit>()
        val slots = kotlinx.coroutines.sync.Semaphore(PosterFinder.WORKERS)
        fun finder() = PosterFinder<String>(
            lookUp = { id ->
                looked += id
                answer.await()
                id
            },
            keep = {},
            slots = slots,
        )
        val movies = finder()
        val series = finder()
        listOf("m1", "m2", "m3").forEach(movies::want)
        listOf("s1", "s2", "s3").forEach(series::want)
        val running = listOf(launch { movies.run() }, launch { series.run() })
        runCurrent()
        assertEquals(4, looked.size)
        answer.complete(Unit)
        runCurrent()
        assertEquals(6, looked.size)
        // Waiting for more, neither holds a slot: one looked up at once still gets one.
        assertEquals(true, movies.findNow("m9"))
        assertEquals(4, movies.tally.read)
        assertEquals(3, series.tally.read)
        running.forEach { it.cancel() }
    }

    @Test fun lookupsThatFailAreCountedByKind() = runTest {
        val finder = PosterFinder<String>(lookUp = { id -> if (id == "a") throw java.io.IOException("The provider is out of reach") else id }, keep = {})
        assertEquals(true, finder.findNow("b"))
        val failed = runCatching { finder.findNow("a") }.exceptionOrNull()
        assertEquals("The provider is out of reach", failed?.message)
        assertEquals(1, finder.tally.read)
        assertEquals(mapOf("IOException" to 1), finder.tally.failed)
    }

    @Test fun aFailureIsQuietAndTheRestGoOn() = runTest {
        val kept = mutableListOf<String>()
        val finder = PosterFinder<String>(
            lookUp = { id ->
                if (id == "a") error("The provider is out of reach")
                id
            },
            keep = { kept += it },
        )
        finder.want("a")
        finder.want("b")
        val running = launch { finder.run() }
        runCurrent()
        finder.want("c")
        advanceTimeBy(PosterFinder.KEEP_EVERY_MS + 1)
        runCurrent()
        assertEquals(listOf("b", "c"), kept)
        running.cancel()
    }

    @Test fun whatsFoundIsKeptTogetherAtMostTwiceASecond() = runTest {
        val kept = mutableListOf<List<String>>()
        val finder = PosterFinder<String>(
            lookUp = { id ->
                delay(LOOKUP_MS)
                id
            },
            keep = { kept += it.sorted() },
        )
        finder.want("a")
        val running = launch { finder.run() }
        // The first found is kept at once.
        advanceTimeBy(LOOKUP_MS + 1)
        runCurrent()
        assertEquals(listOf(listOf("a")), kept)

        // Three more found soon after wait for the next keeping, and are kept together.
        listOf("b", "c", "d").forEach(finder::want)
        advanceTimeBy(LOOKUP_MS + 1)
        runCurrent()
        assertEquals(1, kept.size)
        advanceTimeBy(PosterFinder.KEEP_EVERY_MS)
        runCurrent()
        assertEquals(listOf(listOf("a"), listOf("b", "c", "d")), kept)
        running.cancel()
    }

    private companion object {
        const val LOOKUP_MS = 100L
    }
}
