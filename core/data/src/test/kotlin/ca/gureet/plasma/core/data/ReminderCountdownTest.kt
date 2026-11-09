// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.data.ReminderCountdown.Count
import ca.gureet.plasma.core.model.PlasmaClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Switch Automatically's count (#115), on Plasma's virtual-time clock. */
class ReminderCountdownTest {
    private val start = Instant.parse("2026-10-21T22:00:00Z")
    private val extra = Reminder(ReminderRepository.idOf("home:105", start), "home:105", "Match of the Week Extra", start, start.plusSeconds(3_600))

    /** A clock [before] the start of [extra] (after it when negative), moving with virtual time. */
    private fun TestScope.clock(before: Duration) = object : PlasmaClock {
        private val origin = start.minus(before)
        override val zone: ZoneId = ZoneOffset.UTC
        override fun now(): Instant = origin.plusMillis(testScheduler.currentTime)
    }

    private fun TestScope.after(time: Duration) {
        advanceTimeBy(time.toMillis())
        runCurrent()
    }

    @Test fun countsTheLastTenSecondsUpToTheStart() = runTest {
        val due = MutableStateFlow<Reminder?>(null)
        val countdown = ReminderCountdown(due, flowOf(true), clock(Duration.ofMinutes(1)), backgroundScope)
        runCurrent()
        assertNull(countdown.count.value)

        // A minute before the start, the reminder shows; it doesn't count yet.
        due.value = extra
        runCurrent()
        assertEquals(Count(extra.id, null), countdown.count.value)
        after(Duration.ofSeconds(50))
        assertEquals(Count(extra.id, 10), countdown.count.value)
        after(Duration.ofSeconds(1))
        assertEquals(Count(extra.id, 9), countdown.count.value)
        after(Duration.ofSeconds(9))
        assertEquals(Count(extra.id, 0), countdown.count.value)
    }

    @Test fun shownLateItStillCountsTenSeconds() = runTest {
        // Half a minute after the start, behind another reminder.
        val countdown = ReminderCountdown(MutableStateFlow(extra), flowOf(true), clock(Duration.ofSeconds(-30)), backgroundScope)
        runCurrent()
        assertEquals(Count(extra.id, 10), countdown.count.value)
        after(Duration.ofSeconds(9))
        assertEquals(Count(extra.id, 1), countdown.count.value)
        after(Duration.ofSeconds(1))
        assertEquals(Count(extra.id, 0), countdown.count.value)
    }

    @Test fun itCountsOnlyWhileItsOnAndPlasmaIsInFront() = runTest {
        val running = MutableStateFlow(false)
        val countdown = ReminderCountdown(MutableStateFlow(extra), running, clock(Duration.ofSeconds(5)), backgroundScope)
        runCurrent()
        assertNull(countdown.count.value)

        // Five seconds before the start: ten seconds from now.
        running.value = true
        runCurrent()
        assertEquals(Count(extra.id, 10), countdown.count.value)
        after(Duration.ofSeconds(4))
        assertEquals(Count(extra.id, 6), countdown.count.value)

        // Away and back: it starts again.
        running.value = false
        runCurrent()
        assertNull(countdown.count.value)
        running.value = true
        runCurrent()
        assertEquals(Count(extra.id, 10), countdown.count.value)
    }

    @Test fun dismissedItStopsCounting() = runTest {
        val due = MutableStateFlow<Reminder?>(extra)
        val countdown = ReminderCountdown(due, flowOf(true), clock(Duration.ofSeconds(8)), backgroundScope)
        runCurrent()
        assertEquals(Count(extra.id, 10), countdown.count.value)

        due.value = null
        runCurrent()
        assertNull(countdown.count.value)
        after(Duration.ofSeconds(20))
        assertNull(countdown.count.value)
    }
}
