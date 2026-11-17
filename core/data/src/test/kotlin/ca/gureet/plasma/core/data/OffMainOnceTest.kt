// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [offMainOnce] (#127): what a screen reads is worked out where it's collected while Plasma
 * starts, and off the collector's thread once it has started.
 */
class OffMainOnceTest {
    /** Runs [block] as a screen would collect: on one thread of its own, which it's handed. */
    private fun collecting(block: suspend (collector: Thread) -> Unit) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            runBlocking(executor.asCoroutineDispatcher()) { withTimeout(TEST_ENDS_MS) { block(Thread.currentThread()) } }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun whilePlasmaStartsAFlowIsWorkedOutWhereItsCollected() = collecting { collector ->
        val workedOutOn = CompletableDeferred<Thread>()
        val rows = flow {
            workedOutOn.complete(Thread.currentThread())
            emit("rows")
        }

        assertEquals("rows", rows.offMainOnce { false }.first())

        assertSame("It was worked out on another thread than its collector's", collector, workedOutOn.await())
    }

    @Test fun oncePlasmaHasStartedItsWorkedOutOffTheCollectorsThread() = collecting { collector ->
        val workedOutOn = CompletableDeferred<Thread>()
        val rows = flow {
            workedOutOn.complete(Thread.currentThread())
            emit("rows")
        }

        assertEquals("rows", rows.offMainOnce { true }.first())

        assertNotSame("It was worked out on its collector's thread", collector, workedOutOn.await())
    }

    @Test fun aCollectionBegunWhilePlasmaStartsStaysWhereItIsAndTheNextOneIsMoved() = collecting { collector ->
        val started = AtomicBoolean(false)
        val workedOutOn = CopyOnWriteArrayList<Thread>()
        val firstIsOut = CompletableDeferred<Unit>()
        val goOn = CompletableDeferred<Unit>()
        val rows = flow {
            workedOutOn += Thread.currentThread()
            emit("first")
            goOn.await()
            workedOutOn += Thread.currentThread()
            emit("second")
        }.offMainOnce(started::get)

        val got = CopyOnWriteArrayList<String>()
        coroutineScope {
            val begunBefore = launch {
                rows.collect {
                    got += it
                    firstIsOut.complete(Unit)
                }
            }
            firstIsOut.await()
            // Plasma has started while that collection is under way.
            started.set(true)
            goOn.complete(Unit)
            begunBefore.join()
        }

        assertEquals(listOf("first", "second"), got.toList())
        assertTrue("A collection begun while Plasma started was moved part-way", workedOutOn.all { it === collector })

        workedOutOn.clear()
        assertEquals("first", rows.first())
        assertNotSame("A collection begun after Plasma started stayed on its collector's thread", collector, workedOutOn.first())
    }

    private companion object {
        const val TEST_ENDS_MS = 30_000L
    }
}
