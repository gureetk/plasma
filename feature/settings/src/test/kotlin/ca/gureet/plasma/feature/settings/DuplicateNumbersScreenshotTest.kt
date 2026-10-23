// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.gureet.plasma.core.model.DuplicateRule
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Duplicate Numbers against board 30, and its D-pad paths. */
class DuplicateNumbersScreenshotTest : ScreenshotTest() {
    private fun show(initial: String? = null, actions: DuplicateNumbersActions = DuplicateNumbersActions()) = screen {
        DuplicateNumbersScreen(DuplicateNumbersSamples.state, initialConflict = initial, actions = actions)
    }

    @Test fun duplicateNumbers() {
        show(initial = CLASSICS)
        capture("30-duplicate-numbers")
    }

    @Test fun okMovesToTheButtonsAndBackReturns() {
        show(initial = CLASSICS)
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
        val swapped = mutableListOf<String>()
        show(initial = CLASSICS, actions = DuplicateNumbersActions(onSwap = { swapped += it.id }))
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Swap Numbers with Courtside+"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Keeps 108").assertExists()
        compose.onNodeWithText("You gave 108 to Courtside Classics. Type 1108 to watch Courtside+.").assertExists()
        focused().assert(hasText("108"))
        // The app keeps it.
        assertEquals(listOf(CLASSICS), swapped)
    }

    @Test fun choosingAnotherNumberChecksItIsFree() {
        val chosen = mutableListOf<Pair<String, Int>>()
        show(initial = CLASSICS, actions = DuplicateNumbersActions(onChoose = { conflict, number -> chosen += conflict.id to number }))
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        // The remote's number keys type: 105 is taken, 1110 is free.
        press(Key.One)
        press(Key.Zero)
        press(Key.Five)
        compose.onNodeWithText("105 is taken by Arena Sports 1").assertExists()
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
        assertEquals(listOf(CLASSICS to 1110), chosen)
    }

    @Test fun menuSwitchesTheRule() {
        var rule: DuplicateRule? = null
        show(actions = DuplicateNumbersActions(onRule = { rule = it }))
        compose.onNodeWithText("Now 1104").assertExists()
        press(Key.Menu)
        press(Key.DirectionDown)
        focused().assert(hasText("Next Free Number"))
        press(Key.DirectionCenter)
        // 105 to 110 are taken, so Arena Sports Extra 2 gets 111, and the rest follow.
        compose.onNodeWithText("Now 111").assertExists()
        compose.onNodeWithText("Now 112").assertExists()
        compose.onNodeWithText("Fix rule: next free number").assertExists()
        assertEquals(DuplicateRule.NextFree, rule)
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

    @Test fun aClashOverOneNumberAmongThreeSaysSo() {
        val three = DuplicateNumbersSamples.state.let { state ->
            val ringside = state.conflicts.last()
            state.copy(conflicts = state.conflicts + ringside.copy(id = "extras:ringside-3", mover = ringside.mover.copy(name = "Ringside 3"), newNumber = 2212, uses = 3))
        }
        screen { DuplicateNumbersScreen(three, initialConflict = "extras:ringside-3") }

        focused().assert(hasText("used 3 times"))
        compose.onNodeWithText("Now 2212").assertExists()
    }

    @Test fun theAppsNewStateTakesOver() {
        var state by mutableStateOf(DuplicateNumbersSamples.state)
        screen { DuplicateNumbersScreen(state, initialConflict = CLASSICS) }

        // Kept elsewhere: the app's next state has Courtside Classics on 500.
        state = state.copy(conflicts = state.conflicts.map { if (it.id == CLASSICS) it.copy(newNumber = 500, chosen = true) else it })
        compose.waitForIdle()

        compose.onNodeWithText("Now 500").assertExists()
        focused().assert(hasText("108"))
    }

    @Test fun withNoClashesFocusStaysOnTheTabs() {
        val one = DuplicateNumbersSamples.state.merged.first()
        screen { DuplicateNumbersScreen(DuplicateNumbersSamples.state.copy(conflicts = emptyList(), merged = listOf(one), mergedCount = 1)) }

        focused().assert(hasText("Renumbered"))
        compose.onNodeWithText("No two channels share a number. The same channel from two sources is merged instead.").assertExists()
        press(Key.Menu)
        press(Key.Back)
        focused().assert(hasText("Renumbered"))

        // Unmerging the only merged channel leaves focus on its tab.
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        focused().assert(hasText("Unmerge"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Merged"))
    }

    private companion object {
        const val CLASSICS = "extras:courtside-classics"
    }
}
