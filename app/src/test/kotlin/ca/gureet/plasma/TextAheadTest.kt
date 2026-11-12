// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Where text is measured ahead of the main thread (#127): the thread it's done on, what goes when
 * more comes than there's room for, and what happens when one can't be measured.
 */
class TextAheadTest {
    private fun CountDownLatch.reached(): Boolean = await(WAIT_S, TimeUnit.SECONDS)

    /** Keeps [ahead]'s thread busy until the latch it gives back is counted down. */
    private fun hold(ahead: TextAhead): CountDownLatch {
        val busy = CountDownLatch(1)
        val let = CountDownLatch(1)
        ahead.execute {
            busy.countDown()
            let.await()
        }
        assertTrue("Its thread starts", busy.reached())
        return let
    }

    /** Waits until everything [ahead] has taken so far is done with: only once nothing is waiting, since this takes a place too. */
    private fun settle(ahead: TextAhead) {
        val through = CountDownLatch(1)
        ahead.execute { through.countDown() }
        assertTrue("What it has taken is done", through.reached())
    }

    @Test fun itRunsWhatItsGivenOnAThreadOfItsOwn() {
        val ahead = TextAhead()
        var on: Thread? = null
        val ran = CountDownLatch(1)
        ahead.execute {
            on = Thread.currentThread()
            ran.countDown()
        }
        assertTrue(ran.reached())
        assertNotSame(Thread.currentThread(), on)
        assertEquals("plasma-text", on?.name)
        assertTrue("It doesn't keep Plasma from ending", on?.isDaemon == true)
    }

    @Test fun whatItsGivenRunsInTheOrderItCame() {
        val ahead = TextAhead()
        val ran = Collections.synchronizedList(mutableListOf<Int>())
        val let = hold(ahead)
        for (i in 1..20) ahead.execute { ran += i }
        let.countDown()
        settle(ahead)
        assertEquals((1..20).toList(), ran.toList())
    }

    @Test fun whenMoreComesThanTheresRoomForTheLongestWaitingGoes() {
        val ahead = TextAhead(waiting = 3)
        val ran = Collections.synchronizedList(mutableListOf<Int>())
        val last = CountDownLatch(1)
        val let = hold(ahead)
        // Room for three: the first goes as the fourth comes, and the second as the fifth does.
        for (i in 1..5) {
            ahead.execute {
                ran += i
                if (i == 5) last.countDown()
            }
        }
        let.countDown()
        assertTrue(last.reached())
        settle(ahead)
        assertEquals(listOf(3, 4, 5), ran.toList())
    }

    @Test fun oneThatCantBeMeasuredEndsTheHeadStarts() {
        val failures = Collections.synchronizedList(mutableListOf<RuntimeException>())
        val told = CountDownLatch(1)
        val ahead = TextAhead(failed = {
            failures += it
            told.countDown()
        })
        val wrong = IllegalStateException("A text that can't be measured")
        val after = CountDownLatch(2)
        val let = hold(ahead)
        ahead.execute { throw wrong }
        // One that was waiting behind it, and one that comes after it has failed.
        ahead.execute { after.countDown() }
        let.countDown()
        assertTrue("The failure is told", told.reached())
        ahead.execute { after.countDown() }
        assertFalse("Nothing runs after it", after.await(QUIET_MS, TimeUnit.MILLISECONDS))
        assertEquals(2L, after.count)
        assertEquals("And it's told once", listOf<RuntimeException>(wrong), failures.toList())
    }

    @Test fun aFailureStaysOnItsOwnThread() {
        val ahead = TextAhead()
        ahead.execute { throw IllegalArgumentException("A text that can't be measured") }
        // Nothing reaches the caller, and giving it more is still safe.
        repeat(300) { ahead.execute { } }
    }

    private companion object {
        /** Far longer than any of it takes: only a failing test waits this long. */
        const val WAIT_S = 10L

        /** How long nothing is seen to run before it's taken that nothing will. */
        const val QUIET_MS = 300L
    }
}
