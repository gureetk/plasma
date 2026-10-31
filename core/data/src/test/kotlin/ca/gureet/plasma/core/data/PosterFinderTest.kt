// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Looking for movies' own posters as they show (the owner's #81): the latest first, two at a time, each once. */
@OptIn(ExperimentalCoroutinesApi::class)
class PosterFinderTest {
    @Test fun theLatestWantedComeFirstTwoAtATimeAndEachOnce() = runTest {
        val found = mutableListOf<String>()
        val answer = CompletableDeferred<Unit>()
        val finder = PosterFinder { id ->
            found += id
            answer.await()
        }
        finder.want("a")
        finder.want("b")
        finder.want("c")
        finder.want("a")
        val running = launch { finder.run() }
        runCurrent()
        assertEquals(listOf("c", "b"), found)

        answer.complete(Unit)
        runCurrent()
        assertEquals(listOf("c", "b", "a"), found)

        // A title is looked for once while Plasma runs.
        finder.want("b")
        runCurrent()
        assertEquals(listOf("c", "b", "a"), found)
        running.cancel()
    }

    @Test fun aFailureIsQuietAndTheRestGoOn() = runTest {
        val found = mutableListOf<String>()
        val finder = PosterFinder { id ->
            if (id == "a") error("The provider is out of reach")
            found += id
        }
        finder.want("a")
        finder.want("b")
        val running = launch { finder.run() }
        runCurrent()
        finder.want("c")
        runCurrent()
        assertEquals(listOf("b", "c"), found)
        running.cancel()
    }
}
