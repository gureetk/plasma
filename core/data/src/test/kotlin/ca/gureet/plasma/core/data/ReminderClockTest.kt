// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.PlasmaClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Reminders come due a minute before the start (brief 5.7), on Plasma's virtual-time clock. */
class ReminderClockTest {
    private val start = Instant.parse("2026-10-21T22:00:00Z")
    private val extra = Reminder(ReminderRepository.idOf("home:105", start), "home:105", "Match of the Week Extra", start, start.plusSeconds(3_600))
    private val tonight = extra.copy(id = ReminderRepository.idOf("home:101", start.plusSeconds(1_800)), channelId = "home:101", title = "Late News", start = start.plusSeconds(1_800))

    private class FakeStore(vararg initial: Reminder) : ReminderStore {
        val kept = MutableStateFlow(initial.sortedBy { it.start })
        var pruned = 0
        override val reminders: Flow<List<Reminder>> = kept
        override suspend fun cancel(id: String) {
            kept.value = kept.value.filterNot { it.id == id }
        }
        override suspend fun pruneStarted() {
            pruned++
        }
    }

    /** A clock [before] the start of [extra], moving with the test's virtual time. */
    private fun TestScope.clock(before: Duration) = object : PlasmaClock {
        private val origin = start.minus(before)
        override val zone: ZoneId = ZoneOffset.UTC
        override fun now(): Instant = origin.plusMillis(testScheduler.currentTime)
    }

    private fun TestScope.after(time: Duration) {
        advanceTimeBy(time.toMillis())
        runCurrent()
    }

    @Test fun comesDueAMinuteBeforeTheStartAndStaysUntilDone() = runTest {
        val store = FakeStore(extra)
        val reminders = ReminderClock(store, clock(Duration.ofMinutes(10)), backgroundScope) { true }
        reminders.start()
        runCurrent()
        assertEquals(1, store.pruned)

        after(Duration.ofMinutes(9).minusSeconds(1))
        assertNull(reminders.due.value)
        after(Duration.ofSeconds(1))
        assertEquals(extra, reminders.due.value)
        // Shown, it's done with.
        assertTrue(store.kept.value.isEmpty())

        // It stays up past the start until it's acted on.
        after(Duration.ofMinutes(5))
        assertEquals(extra, reminders.due.value)
        reminders.done(extra.id)
        runCurrent()
        assertNull(reminders.due.value)
    }

    @Test fun remindersDueTogetherTakeTurns() = runTest {
        val other = extra.copy(id = ReminderRepository.idOf("home:101", start), channelId = "home:101", title = "Evening News")
        val store = FakeStore(extra, other)
        val reminders = ReminderClock(store, clock(Duration.ofMinutes(2)), backgroundScope) { true }
        reminders.start()

        after(Duration.ofMinutes(1))
        assertEquals(extra, reminders.due.value)
        assertTrue(store.kept.value.isEmpty())
        // Dismissing one shows the other.
        reminders.done(extra.id)
        runCurrent()
        assertEquals(other, reminders.due.value)
        reminders.done(other.id)
        runCurrent()
        assertNull(reminders.due.value)
    }

    @Test fun theToastGoesWhenTheProgrammeEnds() = runTest {
        val reminders = ReminderClock(FakeStore(extra), clock(Duration.ofMinutes(2)), backgroundScope) { true }
        reminders.start()

        after(Duration.ofMinutes(1))
        assertEquals(extra, reminders.due.value)
        after(Duration.ofMinutes(60))
        assertEquals(extra, reminders.due.value)
        after(Duration.ofMinutes(1))
        assertNull(reminders.due.value)
    }

    @Test fun aReminderWhilePlasmaIsAwayIsSkipped() = runTest {
        var onScreen = false
        val store = FakeStore(extra, tonight)
        val reminders = ReminderClock(store, clock(Duration.ofMinutes(2)), backgroundScope) { onScreen }
        reminders.start()

        after(Duration.ofMinutes(1))
        assertNull(reminders.due.value)
        assertEquals(listOf(tonight), store.kept.value)

        onScreen = true
        after(Duration.ofMinutes(30))
        assertEquals(tonight, reminders.due.value)
    }

    @Test fun aSoonerReminderSetWhileWaitingComesFirst() = runTest {
        val store = FakeStore(tonight)
        val reminders = ReminderClock(store, clock(Duration.ofMinutes(10)), backgroundScope) { true }
        reminders.start()
        after(Duration.ofMinutes(1))

        // Set less than a minute before its start, it comes due at once.
        store.kept.value = listOf(extra.copy(start = start.minusSeconds(510)), tonight)
        runCurrent()
        assertEquals("Match of the Week Extra", reminders.due.value?.title)
        assertEquals(listOf(tonight), store.kept.value)
    }
}
