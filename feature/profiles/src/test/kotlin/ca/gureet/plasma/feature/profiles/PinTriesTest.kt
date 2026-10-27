// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wrong tries at the parental PIN (brief 5.12): five stop entry for a minute. */
class PinTriesTest {
    private var now = 1_000_000L
    private val tries = PinTries { now }

    @Test fun fiveWrongTriesStopEntryForAMinute() {
        repeat(4) { tries.failed() }
        assertFalse(tries.blocked)
        tries.failed()
        assertTrue(tries.blocked)

        now += PinTries.BLOCKED_MS - 1
        tries.reopenIfDue()
        assertTrue(tries.blocked)

        now += 1
        tries.reopenIfDue()
        assertFalse(tries.blocked)
        assertEquals(0, tries.wrong)
    }

    @Test fun theRightPinStartsTheCountAgain() {
        repeat(3) { tries.failed() }
        tries.succeeded()

        assertEquals(0, tries.wrong)
        assertFalse(tries.blocked)
    }
}
