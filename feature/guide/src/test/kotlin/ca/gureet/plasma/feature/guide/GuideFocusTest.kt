// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** D-pad focus on the Guide (brief 4.8): the grid, and the days above it. */
class GuideFocusTest : ScreenshotTest() {
    private val opened = mutableListOf<Pair<String, ProgramAction>>()

    private fun show() = screen { GuideScreen(GuideSamples.state, GuideActions(onProgram = { channel, _, action -> opened += channel to action })) }

    @Test fun okOpensWhatTheGridHasFocusedOn() {
        show()
        // The grid opens on what's on now on the last channel watched: OK plays it.
        press(Key.DirectionCenter)
        check(opened.last() == ("ch105" to ProgramAction.Play)) { "Opened $opened" }
        // Right moves on to what's next, where OK opens Remind and Record. Its reminder is set.
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        focused().assert(hasText("Cancel Reminder"))
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        focused().assert(hasText("Remind Me"))
        press(Key.DirectionDown)
        focused().assert(hasText("Record"))
        press(Key.Back)

        // Down keeps to the time, on the next channel.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Today, 22:00–23:30 · Velocity Motor").assertExists()
        check(opened.size == 1) { "Opened $opened" }
    }

    @Test fun upFromTheTopRowGoesToTheDaysAndDownComesBack() {
        show()
        press(Key.DirectionUp, times = 3)
        focused().assert(hasText("Today"))
        // Moving onto a day shows it.
        press(Key.DirectionRight)
        focused().assert(hasText("Thu 22"))
        compose.onNodeWithText("Thu 22 Oct").assertExists()
        // Back on today, Down returns to the grid on what's on now, which OK plays.
        press(Key.DirectionLeft)
        compose.onNodeWithText("Wed 21 Oct").assertExists()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(opened.singleOrNull()?.second == ProgramAction.Play) { "Opened $opened" }
        // OK on a day goes back to the grid too.
        press(Key.DirectionUp)
        focused().assert(hasText("Today"))
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        check(opened.size == 2) { "Opened $opened" }
    }

    @Test fun menuOffersRecordAndRemind() {
        show()
        // Holding OK on what's on now offers Record alone; Menu on what's next adds Remind.
        longPress(Key.DirectionCenter)
        focused().assert(hasText("Record"))
        compose.onNodeWithText("Remind Me").assertDoesNotExist()
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.Menu)
        focused().assert(hasText("Cancel Reminder"))
        press(Key.DirectionDown)
        focused().assert(hasText("Record"))
        press(Key.Back)
        check(opened.isEmpty()) { "Opened $opened" }
    }
}
