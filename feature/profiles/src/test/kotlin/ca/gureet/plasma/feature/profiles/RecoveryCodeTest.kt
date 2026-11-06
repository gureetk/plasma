// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.profiles

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** A PIN's recovery code (the owner's #112): shown once, and typed for Forgot PIN, in board 25's style. */
class RecoveryCodeTest : ScreenshotTest() {
    private val code = "7KQXM4TR9WCH"
    private var done = 0
    private var right = 0
    private val tried = mutableListOf<String>()

    private fun entry(tries: PinTries = PinTries()) = screen {
        RecoveryCodeEntry(
            backLabel = "Enter PIN to Watch",
            checkCode = { typed ->
                tried += typed
                typed == code
            },
            onRight = { right++ },
            tries = tries,
        )
    }

    @Test fun theCodeShowsOnceWithDone() {
        screen { RecoveryCodeScreen(code, onDone = { done++ }) }
        compose.onNodeWithText("Your Recovery Code").assertExists()
        compose.onNodeWithText("7KQX-M4TR-9WCH").assertExists()
        focused().assert(hasText("Done"))
        capture("25-recovery-code")
        press(Key.DirectionCenter)
        check(done == 1) { "Done $done times" }
    }

    @Test fun backAlsoGoesOn() {
        screen { RecoveryCodeScreen(code, onDone = { done++ }) }
        back()
        check(done == 1) { "Done $done times" }
    }

    @Test fun theCodesKeysTypeIt() {
        entry()
        focused().assert(hasText("2"))
        // 7, then K: down a row to the letters, then along.
        press(Key.DirectionRight, times = 5)
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionLeft, times = 4)
        focused().assert(hasText("K"))
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("2 of 12 characters entered").assertExists()
        capture("25-forgot-pin")
        check(tried.isEmpty())
    }

    @Test fun aKeyboardTypesItAndTheRightCodeOpens() {
        entry()
        listOf(Key.Seven, Key.K, Key.Q, Key.X, Key.M, Key.Four, Key.T, Key.R, Key.Nine, Key.W, Key.C, Key.H).forEach { press(it) }
        check(tried == listOf(code)) { "Tried $tried" }
        check(right == 1) { "Right $right times" }
    }

    @Test fun aWrongCodeSaysHowManyTriesAreLeft() {
        val tries = PinTries()
        entry(tries)
        repeat(12) { press(Key.Two) }
        compose.onNodeWithText("That isn’t the code. 4 tries left").assertExists()
        compose.onNodeWithContentDescription("0 of 12 characters entered").assertExists()
        check(right == 0 && tries.wrong == 1) { "Right $right, wrong ${tries.wrong}" }
        // Delete takes the last character away.
        press(Key.Three)
        compose.onNodeWithContentDescription("1 of 12 characters entered").assertExists()
        press(Key.Backspace)
        compose.onNodeWithContentDescription("0 of 12 characters entered").assertExists()
    }

    @Test fun wrongTriesStopItForAMinuteAsThePinsDo() {
        val tries = PinTries()
        repeat(PinTries.TRIES) { tries.failed() }
        entry(tries)
        compose.onNodeWithText("Too many tries. Try again in a minute.").assertExists()
        listOf(Key.Seven, Key.K, Key.Q, Key.X, Key.M, Key.Four, Key.T, Key.R, Key.Nine, Key.W, Key.C, Key.H).forEach { press(it) }
        check(tried.isEmpty() && right == 0) { "Tried $tried" }
    }

    @Test fun thePinScreenOffersForgotPinWithACode() {
        var forgot = 0
        screen {
            ParentalPinScreen(
                request = ProfilesSamples.lockedProgram(),
                backLabel = "Live TV",
                checkPin = { it == ProfilesSamples.PIN },
                onUnlocked = {},
                onForgot = { forgot++ },
            )
        }
        compose.onNodeWithText("You can also type it with the number keys.").assertExists()
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Forgot PIN"))
        press(Key.DirectionCenter)
        check(forgot == 1) { "Forgot $forgot times" }
    }

    @Test fun withoutACodeThereIsNoForgotPin() {
        screen {
            ParentalPinScreen(
                request = ProfilesSamples.lockedProgram(),
                backLabel = "Live TV",
                checkPin = { it == ProfilesSamples.PIN },
                onUnlocked = {},
            )
        }
        compose.onNodeWithText("Forgot PIN").assertDoesNotExist()
    }
}
