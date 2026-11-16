// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A screen's state kept made while the screen isn't open (#127): [MadeAhead], which makes it, and
 * [Opened], which is what a screen shows of it.
 */
class MadeAheadTest {
    /** What a state is made from. Two are told apart by which object each is, as two worlds are. */
    private class World(val name: String)

    private val a = World("a")
    private val b = World("b")
    private val c = World("c")

    @Test fun eachWorldsStateIsMadeOnTheThreadGivenAndTheLastOneStandsUntilTheNextIsReady() {
        val threads = Collections.synchronizedList(mutableListOf<Thread>())
        val worker = Executors.newSingleThreadExecutor { Thread(it, "worker").also(threads::add) }
        val makingB = CompletableDeferred<Unit>()
        val letBEnd = CountDownLatch(1)
        val madeOn = Collections.synchronizedList(mutableListOf<Thread>())
        val ahead = MadeAhead<World, String>(worker.asCoroutineDispatcher()) { world ->
            madeOn += Thread.currentThread()
            if (world === b) {
                makingB.complete(Unit)
                assertTrue("The test lets b's making end", letBEnd.await(WAIT_S, TimeUnit.SECONDS))
            }
            "rows of ${world.name}"
        }
        try {
            runBlocking {
                val worlds = MutableSharedFlow<World>(extraBufferCapacity = 8)
                val shown = Collections.synchronizedList(mutableListOf<String>())
                val watching = launch { ahead.made.collect { made -> made?.let { shown += it.value } } }
                val keeping = launch { ahead.keep(worlds) }
                // Nothing here blocks this thread, which the two above run on too.
                suspend fun watchedUntil(rows: String) { while (shown.lastOrNull() != rows) yield() }
                withTimeout(WAIT_S * 1000) {
                    assertNull("Nothing is made before a world comes", ahead.made.value)
                    worlds.subscriptionCount.first { it > 0 }

                    worlds.emit(a)
                    val first = ahead.made.first { it != null }!!
                    assertSame(a, first.from)
                    assertEquals("rows of a", first.value)
                    watchedUntil("rows of a")

                    // While b's is being made, a's still stands.
                    worlds.emit(b)
                    makingB.await()
                    assertSame(a, ahead.made.value?.from)

                    // c comes while b's is being made: b's is never shown, and c's is made next.
                    worlds.emit(c)
                    letBEnd.countDown()
                    val last = ahead.made.first { it?.from === c }!!
                    assertEquals("rows of c", last.value)
                    watchedUntil("rows of c")

                    // What's kept is let go when keeping ends.
                    keeping.cancelAndJoin()
                    assertNull(ahead.made.value)
                    watching.cancelAndJoin()
                }
                assertEquals(listOf("rows of a", "rows of c"), shown.toList())
            }
            assertEquals("a's, b's and c's were each made, b's though it was never shown", 3, madeOn.size)
            assertEquals("Every state was made on the thread given", threads.toSet(), madeOn.toSet())
            assertTrue("And not on the thread that asked", Thread.currentThread() !in madeOn)
        } finally {
            letBEnd.countDown()
            worker.shutdownNow()
        }
    }

    @Test fun aScreenOpensOnWhatsKeptForTheWorldItShows() {
        var madeAtOnce = 0
        val kept = MadeAhead.Made(a, "kept rows of a")

        val opened = Opened(kept, isCurrent = { it === a }) { madeAtOnce++; "rows of a, made at once" }

        assertEquals("kept rows of a", opened.shown)
        assertEquals("Nothing is made as the screen opens", 0, madeAtOnce)
        // The same one, as the screen starts to follow what's made: nothing changes.
        opened.take(kept)
        assertEquals("kept rows of a", opened.shown)
    }

    @Test fun whatsKeptForAnotherWorldIsntShownAndTheStateIsMadeAtOnce() {
        var madeAtOnce = 0
        val keptForAWorldBefore = MadeAhead.Made(a, "kept rows of a")

        val opened = Opened(keptForAWorldBefore, isCurrent = { it === b }) { madeAtOnce++; "rows of b, made at once" }

        assertEquals("rows of b, made at once", opened.shown)
        assertEquals(1, madeAtOnce)
        // An older world's rows don't take the place of the ones made for this world.
        opened.take(keptForAWorldBefore)
        assertEquals("rows of b, made at once", opened.shown)
        // What's made for this world, with what came after the screen opened, does.
        opened.take(MadeAhead.Made(b, "kept rows of b"))
        assertEquals("kept rows of b", opened.shown)
        assertEquals("It's made at once only as the screen opens", 1, madeAtOnce)
    }

    @Test fun withNothingKeptTheStateIsMadeAtOnce() {
        var madeAtOnce = 0

        val opened = Opened<World, String>(null, isCurrent = { true }) { madeAtOnce++; "rows of a, made at once" }

        assertEquals("rows of a, made at once", opened.shown)
        assertEquals(1, madeAtOnce)
        opened.take(null)
        assertEquals("rows of a, made at once", opened.shown)
    }

    @Test fun anOpenScreenShowsWhateverIsMadeNextAndKeepsItWhenNothingIsKept() {
        val opened = Opened(MadeAhead.Made(a, "kept rows of a"), isCurrent = { it === a }) { "made at once" }

        opened.take(MadeAhead.Made(b, "kept rows of b"))
        assertEquals("A new minute's world: its rows take the last one's place", "kept rows of b", opened.shown)

        // Behind another app nothing is kept: the screen goes on showing what it had.
        opened.take(null)
        assertEquals("kept rows of b", opened.shown)

        opened.take(MadeAhead.Made(c, "kept rows of c"))
        assertEquals("kept rows of c", opened.shown)
    }

    private companion object {
        /** Far longer than anything here takes: a wait that runs out means the test has failed. */
        const val WAIT_S = 20L
    }
}
