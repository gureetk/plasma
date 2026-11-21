// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.ReadsForBenchmarks
import ca.gureet.plasma.core.model.ReadsForBenchmarks.Way
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * The benchmarks' twin in which a read changes over ([Way.ChangingOverAtTheWorld], #127): what
 * begins before the first world is in is worked out where it's collected until the world is in,
 * and takes each step after that off the collector's thread, without being begun again; what
 * begins once the world is in is off that thread from its first step.
 */
class ReadsChangeOverTest {
    @After fun tearDown() {
        ReadsForBenchmarks.way = Way.WhereCollectedUntilTheWorld
        ReadsForBenchmarks.worldIn = false
    }

    /** Runs [block] as a screen would collect: on one thread of its own, which it's handed. */
    private fun collecting(block: suspend CoroutineScope.(collector: Thread) -> Unit) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            runBlocking(executor.asCoroutineDispatcher()) { withTimeout(TEST_ENDS_MS) { block(Thread.currentThread()) } }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun aReadBegunBeforeTheWorldGoesOnOffItsCollectorsThreadOnceTheWorldIsIn() = collecting { collector ->
        ReadsForBenchmarks.way = Way.ChangingOverAtTheWorld
        val steps = CopyOnWriteArrayList<Thread>()
        var begun = 0
        val waitingForTheWorld = CompletableDeferred<Unit>()
        val worldIsIn = CompletableDeferred<Unit>()
        val read = flow {
            begun++
            steps += Thread.currentThread()
            // A step that waits and comes back, as a query does: still before the world.
            yield()
            steps += Thread.currentThread()
            emit("before")
            waitingForTheWorld.complete(Unit)
            worldIsIn.await()
            steps += Thread.currentThread()
            yield()
            steps += Thread.currentThread()
            emit("after")
        }.readOffMain()

        val values = async { read.toList() }
        waitingForTheWorld.await()
        ReadsForBenchmarks.worldIn = true
        worldIsIn.complete(Unit)

        assertEquals(listOf("before", "after"), values.await())
        assertEquals("The read was begun again", 1, begun)
        assertEquals(4, steps.size)
        assertSame("Before the world, the first step wasn't on the collector's thread", collector, steps[0])
        assertSame("Before the world, a step that came back wasn't on the collector's thread", collector, steps[1])
        assertNotSame("With the world in, the next step was still on the collector's thread", collector, steps[2])
        assertNotSame("With the world in, a step that came back was on the collector's thread", collector, steps[3])
    }

    @Test fun aReadBegunOnceTheWorldIsInIsOffItsCollectorsThreadFromItsFirstStep() = collecting { collector ->
        ReadsForBenchmarks.way = Way.ChangingOverAtTheWorld
        ReadsForBenchmarks.worldIn = true
        val steps = CopyOnWriteArrayList<Thread>()
        val read = flow {
            steps += Thread.currentThread()
            yield()
            steps += Thread.currentThread()
            emit(1)
        }.readOffMain()

        assertEquals(1, read.first())
        assertEquals(2, steps.size)
        assertNotSame(collector, steps[0])
        assertNotSame(collector, steps[1])
    }

    @Test fun asBuiltAReadBegunBeforeTheWorldStaysOnItsCollectorsThread() = collecting { collector ->
        val steps = CopyOnWriteArrayList<Thread>()
        val waitingForTheWorld = CompletableDeferred<Unit>()
        val worldIsIn = CompletableDeferred<Unit>()
        val read = flow {
            steps += Thread.currentThread()
            waitingForTheWorld.complete(Unit)
            worldIsIn.await()
            steps += Thread.currentThread()
            emit(1)
        }.readOffMain()

        val value = async { read.first() }
        waitingForTheWorld.await()
        ReadsForBenchmarks.worldIn = true
        worldIsIn.complete(Unit)

        assertEquals(1, value.await())
        assertEquals(2, steps.size)
        assertSame(collector, steps[0])
        assertSame(collector, steps[1])
    }

    private companion object {
        const val TEST_ENDS_MS = 30_000L
    }
}
