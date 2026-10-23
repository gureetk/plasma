// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
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
        // Right moves on to what's next, which opens Remind and Record.
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        check(opened.last() == ("ch105" to ProgramAction.Sheet)) { "Opened $opened" }
        // Down keeps to the time, on the next channel.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(opened.last().first == "ch107") { "Opened $opened" }
    }

    @Test fun upFromTheTopRowGoesToTheDaysAndDownComesBack() {
        show()
        press(Key.DirectionUp, times = 3)
        focused().assert(hasText("Today"))
        press(Key.DirectionRight)
        focused().assert(hasText("Thu 22"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(opened.isNotEmpty()) { "Down didn't return to the grid" }
    }
}
