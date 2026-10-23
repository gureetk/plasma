// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.profiles

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** The parental PIN against board 25, and how it takes digits. */
class ParentalPinScreenshotTest : ScreenshotTest() {
    private var unlocked: UnlockFor? = null

    private fun show() = screen {
        ParentalPinScreen(
            request = ProfilesSamples.lockedProgram(),
            backLabel = "Live TV",
            checkPin = { it == ProfilesSamples.PIN },
            onUnlocked = { unlocked = it },
        )
    }

    @Test fun parentalPin() {
        show()
        // Two digits in, on its way to 7, as the board shows.
        press(Key.DirectionCenter, times = 2)
        press(Key.DirectionRight, times = 6)
        focused().assert(hasText("7"))
        capture("25-parental-pin")
    }

    @Test fun theRightPinUnlocks() {
        show()
        listOf(Key.One, Key.Two, Key.Three, Key.Four).forEach { press(it) }
        check(unlocked == UnlockFor.Program) { "Got $unlocked" }
    }

    @Test fun aWrongPinSaysHowManyTriesAreLeft() {
        show()
        repeat(4) { press(Key.Nine) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Wrong PIN. 4 tries left").assertExists()
        check(unlocked == null)
    }

    @Test fun unlockForSetsHowLong() {
        show()
        press(Key.DirectionDown)
        focused().assert(hasText("This program"))
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("1 hour"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("1 hour").assertExists()
        listOf(Key.One, Key.Two, Key.Three, Key.Four).forEach { press(it) }
        check(unlocked == UnlockFor.Hour) { "Got $unlocked" }
    }
}
