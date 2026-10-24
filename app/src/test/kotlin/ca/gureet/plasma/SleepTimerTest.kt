// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.feature.player.SleepChoice
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Player Options › Sleep Timer (brief 4.10), on the test's virtual time. */
class SleepTimerTest {
    private val start = Instant.parse("2026-10-24T20:41:00Z")
    private var slept = 0
    private var woke = 0

    private fun TestScope.timer(): SleepTimer {
        val clock = object : PlasmaClock {
            override val zone: ZoneId = ZoneOffset.UTC
            override fun now(): Instant = start.plusMillis(testScheduler.currentTime)
        }
        return SleepTimer(clock, backgroundScope, onSleep = { slept++ }, onWake = { woke++ })
    }

    private fun TestScope.after(time: Duration) {
        advanceTimeBy(time.toMillis())
        runCurrent()
    }

    @Test fun aMinuteBeforeItWarnsThenTheChannelStops() = runTest {
        val sleep = timer()
        sleep.set(SleepChoice.Minutes30, Duration.ofMinutes(30))
        runCurrent()
        val at = start.plus(Duration.ofMinutes(30))
        assertEquals(SleepState.Set(SleepChoice.Minutes30, at), sleep.state.value)

        after(Duration.ofMinutes(29).minusSeconds(1))
        assertEquals(SleepState.Set(SleepChoice.Minutes30, at), sleep.state.value)
        after(Duration.ofSeconds(1))
        assertEquals(SleepState.Set(SleepChoice.Minutes30, at, warning = true), sleep.state.value)
        after(Duration.ofSeconds(59))
        assertEquals(0, slept)
        after(Duration.ofSeconds(1))
        assertEquals(SleepState.Asleep, sleep.state.value)
        assertEquals(1, slept)

        // A button plays the channel again.
        sleep.wake()
        assertEquals(SleepState.Off, sleep.state.value)
        assertEquals(1, woke)
    }

    @Test fun keepWatchingTurnsItOff() = runTest {
        val sleep = timer()
        sleep.set(SleepChoice.Minutes60, Duration.ofMinutes(60))
        after(Duration.ofMinutes(59).plusSeconds(30))
        sleep.keepWatching()
        after(Duration.ofMinutes(5))
        assertEquals(SleepState.Off, sleep.state.value)
        assertEquals(0, slept)
    }

    @Test fun stopNowStopsTheChannelAtOnce() = runTest {
        val sleep = timer()
        sleep.set(SleepChoice.EndOfProgram, Duration.ofMinutes(5))
        after(Duration.ofMinutes(4).plusSeconds(30))
        sleep.sleepNow()
        assertEquals(SleepState.Asleep, sleep.state.value)
        after(Duration.ofMinutes(1))
        assertEquals(1, slept)
    }

    @Test fun anotherChoiceReplacesTheTimerAndOffEndsIt() = runTest {
        val sleep = timer()
        sleep.set(SleepChoice.Minutes30, Duration.ofMinutes(30))
        after(Duration.ofMinutes(10))
        sleep.set(SleepChoice.Minutes90, Duration.ofMinutes(90))
        after(Duration.ofMinutes(25))
        assertEquals(SleepState.Set(SleepChoice.Minutes90, start.plus(Duration.ofMinutes(100))), sleep.state.value)
        sleep.set(SleepChoice.Off, null)
        after(Duration.ofHours(2))
        assertEquals(SleepState.Off, sleep.state.value)
        assertEquals(0, slept)
    }

    @Test fun lessThanAMinuteLeftWarnsAtOnce() = runTest {
        val sleep = timer()
        sleep.set(SleepChoice.EndOfProgram, Duration.ofSeconds(30))
        runCurrent()
        assertEquals(SleepState.Set(SleepChoice.EndOfProgram, start.plusSeconds(30), warning = true), sleep.state.value)
    }

    @Test fun comingBackToTheFrontEndsTheDarkWithoutPlaying() = runTest {
        val sleep = timer()
        sleep.set(SleepChoice.Minutes30, Duration.ofMinutes(30))
        sleep.sleepNow()
        // Plasma back in front: what plays is When the TV Turns On's to say, not the timer's.
        sleep.awake()
        assertEquals(SleepState.Off, sleep.state.value)
        assertEquals(0, woke)
        sleep.wake()
        assertEquals(0, woke)
    }
}
