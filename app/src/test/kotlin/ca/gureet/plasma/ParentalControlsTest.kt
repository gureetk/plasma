// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.core.data.ChannelGroup
import ca.gureet.plasma.core.model.AdultWords
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.feature.profiles.UnlockFor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parental controls (brief 5.12): on once there's a PIN, and what the PIN opens for how long (4.25). */
@OptIn(ExperimentalCoroutinesApi::class)
class ParentalControlsTest {
    /** The PIN kept as it's typed, as no store would. */
    private class Pins(private var kept: String?) : PinStore {
        override val pinSet = MutableStateFlow(kept != null)

        override suspend fun setPin(pin: String?) {
            kept = pin
            pinSet.value = pin != null
        }

        override suspend fun isPin(pin: String): Boolean = kept == pin
    }

    private class Clock(var at: Instant) : PlasmaClock {
        override val zone: ZoneId = ZoneId.of("America/Toronto")

        override fun now(): Instant = at
    }

    private val start = Instant.parse("2026-10-21T00:41:00Z")
    private val clock = Clock(start)

    @Test fun withoutAPinNothingIsLocked() = runTest(UnconfinedTestDispatcher()) {
        val parental = ParentalControls(Pins(null), clock, backgroundScope)

        assertFalse(parental.on)
        assertTrue(parental.isUnlocked("home:110"))
        assertFalse(parental.check("1234"))
    }

    @Test fun untilItsKnownWhetherTheresAPinEverythingIsLocked() = runTest(UnconfinedTestDispatcher()) {
        val unknown = object : PinStore {
            override val pinSet = emptyFlow<Boolean>()
            override suspend fun setPin(pin: String?) = Unit
            override suspend fun isPin(pin: String) = false
        }
        val parental = ParentalControls(unknown, clock, backgroundScope)

        assertTrue(parental.on)
        assertFalse(parental.isUnlocked("home:110"))
    }

    @Test fun thePinOpensAChannelForTheProgramAnHourOrUntilTheTvSleeps() = runTest(UnconfinedTestDispatcher()) {
        val parental = ParentalControls(Pins("2580"), clock, backgroundScope)
        assertTrue(parental.on)
        assertTrue(parental.check("2580"))
        assertFalse(parental.check("1234"))

        val programEnd = start.plus(Duration.ofMinutes(19))
        parental.unlock("home:110", programEnd, UnlockFor.Program)
        parental.unlock("home:111", programEnd, UnlockFor.Hour)
        parental.unlock("home:112", programEnd, UnlockFor.UntilSleep)
        assertTrue(parental.isUnlocked("home:110"))
        assertFalse(parental.isUnlocked("home:113"))

        // The program's over: the next one needs the PIN again.
        clock.at = programEnd
        assertFalse(parental.isUnlocked("home:110"))
        assertTrue(parental.isUnlocked("home:111"))

        clock.at = start.plus(Duration.ofHours(1))
        assertFalse(parental.isUnlocked("home:111"))
        assertTrue(parental.isUnlocked("home:112"))

        // The TV sleeps: what was open until then locks again, and so does Settings.
        parental.settingsOpen = true
        parental.slept()
        assertFalse(parental.isUnlocked("home:112"))
        assertFalse(parental.settingsOpen)
    }

    @Test fun withoutAGuideAProgramStaysOpenForAnHour() = runTest(UnconfinedTestDispatcher()) {
        val parental = ParentalControls(Pins("2580"), clock, backgroundScope)

        parental.unlock("home:110", programEnd = null, UnlockFor.Program)
        clock.at = start.plus(Duration.ofMinutes(59))
        assertTrue(parental.isUnlocked("home:110"))
        clock.at = start.plus(Duration.ofMinutes(60))
        assertFalse(parental.isUnlocked("home:110"))
    }

    @Test fun turnedOffEverythingIsOpenAndStaysLockedWhenItsBack() = runTest(UnconfinedTestDispatcher()) {
        val parental = ParentalControls(Pins("2580"), clock, backgroundScope)
        parental.unlock("home:110", null, UnlockFor.UntilSleep)
        parental.settingsOpen = true

        parental.setPin(null)
        assertFalse(parental.on)
        assertTrue(parental.isUnlocked("home:111"))
        assertFalse(parental.settingsOpen)

        // A new PIN: what was opened before isn't any more.
        parental.setPin("1357")
        assertTrue(parental.on)
        assertFalse(parental.isUnlocked("home:110"))
        assertTrue(parental.check("1357"))
    }

    @Test fun groupsLockTheirChannelsAdultOnesByTheirWords() {
        val groups = listOf(
            ChannelGroup("home:1", "home", "Sports", hidden = false),
            ChannelGroup("home:2", "home", "XXX Movies", hidden = false),
            ChannelGroup("home:3", "home", "After Dark", hidden = false, locked = true),
            ChannelGroup("home:4", "home", "Adults Only", hidden = false, locked = false),
        )
        val locks = GroupLocks(groups, AdultWords.parse(AdultWords.DEFAULT))

        // An adult group unlocked in Locked Groups stays unlocked.
        assertEquals(setOf("home:2", "home:3"), locks.lockedIds)
        assertTrue(locks.isAdult(groups[3]))
        assertFalse(locks.isLocked(groups[3]))
        // Lock Adult Groups off: only the groups locked by hand, and none is adult.
        val off = GroupLocks(groups, null)
        assertEquals(setOf("home:3"), off.lockedIds)
        assertFalse(off.isAdult(groups[1]))
    }
}
