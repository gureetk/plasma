// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Duplicate Numbers against board 30, and its D-pad paths. */
class DuplicateNumbersScreenshotTest : ScreenshotTest() {
    private fun show(initial: Int? = null) = screen { DuplicateNumbersScreen(DuplicateNumbersSamples.state, initialConflict = initial) }

    @Test fun duplicateNumbers() {
        show(initial = 108)
        capture("30-duplicate-numbers")
    }

    @Test fun okMovesToTheButtonsAndBackReturns() {
        show(initial = 108)
        press(Key.DirectionCenter)
        focused().assert(hasText("Keep 1108"))
        press(Key.Back)
        // Back from the buttons lands on the conflict they belong to.
        focused().assert(hasText("108"))
        press(Key.DirectionDown)
        focused().assert(hasText("109"))
        // The panel follows focus.
        compose.onNodeWithText("Keep 1109").assertExists()
    }

    @Test fun swappingGivesTheNumberToTheOtherChannel() {
        show(initial = 108)
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Swap Numbers with Courtside+"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Keeps 108").assertExists()
        compose.onNodeWithText("You gave 108 to Courtside Classics. Type 1108 to watch Courtside+.").assertExists()
        focused().assert(hasText("108"))
    }

    @Test fun choosingAnotherNumberChecksItIsFree() {
        show(initial = 108)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        // The remote's number keys type: 105 is taken, 1110 is free.
        press(Key.One)
        press(Key.Zero)
        press(Key.Five)
        compose.onNodeWithText("105 is Arena Sports 1").assertExists()
        press(Key.Back)
        // Back cancels: the conflict is as it was.
        compose.onNodeWithText("Now 1108").assertExists()

        focused().assert(hasText("108"))
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Choose Another Number"))
        press(Key.DirectionCenter)
        focused().assert(hasText("1"))
        listOf(Key.One, Key.One, Key.One, Key.Zero).forEach { press(it) }
        compose.onNodeWithText("1110 is free").assertExists()
        press(Key.DirectionDown)
        focused().assert(hasText("Use 1110"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Now 1110").assertExists()
    }

    @Test fun menuSwitchesTheRule() {
        show()
        compose.onNodeWithText("Now 1104").assertExists()
        press(Key.Menu)
        press(Key.DirectionDown)
        focused().assert(hasText("Next Free Number"))
        press(Key.DirectionCenter)
        // 105 to 110 are taken, so Arena Sports Extra 2 gets 111, and the rest follow.
        compose.onNodeWithText("Now 111").assertExists()
        compose.onNodeWithText("Now 112").assertExists()
        compose.onNodeWithText("Fix rule: next free number").assertExists()
    }

    @Test fun mergedTabListsMergedChannels() {
        show()
        press(Key.DirectionUp)
        focused().assert(hasText("Renumbered"))
        press(Key.DirectionRight)
        focused().assert(hasText("Merged"))
        press(Key.DirectionDown)
        focused().assert(hasText("Northline News"))
        compose.onNodeWithText("Merged from 2 sources").assertExists()
        press(Key.DirectionCenter)
        focused().assert(hasText("Unmerge"))
        press(Key.DirectionCenter)
        // Unmerged, it leaves the list and the count drops.
        compose.onNodeWithText("37").assertExists()
        focused().assert(hasText("Meridian 24"))
    }
}
