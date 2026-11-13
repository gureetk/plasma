// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * The Guide's window moving under a row (#127): a program that's still in the window afterward is
 * drawn by the cell that drew it before, and isn't made over in another's place with its words
 * laid out again.
 */
class GuideWindowTest : ScreenshotTest() {
    /** One channel with a program every half hour from 20:00, where the window starts at 20:41. */
    private fun showHalfHours() {
        val channel = GuideSamples.state.channels.first()
        val start = Sample.at(20, 0).toInstant()
        val halfHours = List(8) {
            GuideProgram(
                id = "half-hour-${it + 1}",
                title = "Half Hour ${it + 1}",
                start = start.plus(Duration.ofMinutes(30L * it)),
                end = start.plus(Duration.ofMinutes(30L * (it + 1))),
            )
        }
        screen {
            GuideScreen(GuideSamples.state.copy(channels = listOf(channel.copy(programs = halfHours)), currentChannelId = channel.id), GuideActions())
        }
    }

    /** The cell that draws the program called [title]: its words' own place in the screen's tree. */
    private fun cellOf(title: String): Int = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().id

    @Test fun aProgramStillInTheWindowKeepsItsCellAsTheWindowMoves() {
        showHalfHours()
        // The second is on now. The fourth ends where the window does, at 22:00.
        press(Key.DirectionRight, times = 2)
        compose.onAllNodesWithText("Half Hour 1", useUnmergedTree = true).assertCountEquals(1)
        val before = cellOf("Half Hour 3")

        // The fifth starts at the window's end, so the window moves on half an hour, and the first
        // program, the row's first cell, is out of it.
        press(Key.DirectionRight)
        compose.onAllNodesWithText("Half Hour 1", useUnmergedTree = true).assertCountEquals(0)
        assertEquals("The third program's cell is the one it had", before, cellOf("Half Hour 3"))

        // And back to the first program, which brings the window back with it: the fifth is out again.
        press(Key.DirectionLeft, times = 4)
        compose.onAllNodesWithText("Half Hour 5", useUnmergedTree = true).assertCountEquals(0)
        assertEquals("The third program's cell is still the one it had", before, cellOf("Half Hour 3"))
    }
}
