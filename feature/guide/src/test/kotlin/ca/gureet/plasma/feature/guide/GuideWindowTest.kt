// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
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
 * laid out again, nor composed again where it only moves; the program that comes in is drawn by
 * the cell of the one that left. The time header's labels go the same way.
 */
class GuideWindowTest : ScreenshotTest() {
    /**
     * The first [channels] channels, each with a program every half hour from 20:00, at 20:41: the
     * window starts at 20:00. The first row's are Half Hour 1 to 8.
     */
    private fun showHalfHours(channels: Int = 1) {
        val start = Sample.at(20, 0).toInstant()
        val rows = GuideSamples.state.channels.take(channels).mapIndexed { row, channel ->
            channel.copy(
                programs = List(8) {
                    GuideProgram(
                        id = if (row == 0) "half-hour-${it + 1}" else "row-${row + 1}-half-hour-${it + 1}",
                        title = if (row == 0) "Half Hour ${it + 1}" else "Row ${row + 1} Half Hour ${it + 1}",
                        start = start.plus(Duration.ofMinutes(30L * it)),
                        end = start.plus(Duration.ofMinutes(30L * (it + 1))),
                    )
                },
            )
        }
        screen { GuideScreen(GuideSamples.state.copy(channels = rows, currentChannelId = rows.first().id), GuideActions()) }
    }

    /**
     * The cell that draws the program called [title]: its words' own place in the screen's tree.
     * Not for the program the cursor is on, whose title the panel above the grid shows too.
     */
    private fun cellOf(title: String): Int = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().id

    /** The time header's label for [time], as "21:00": its words' own place in the screen's tree. */
    private fun labelOf(time: String): Int = compose.onNodeWithText(time, useUnmergedTree = true).fetchSemanticsNode().id

    /**
     * How many times a cell of the grid was composed while [block] ran. Compose tells a tracer of
     * each composable function it runs, by name, and of none it skips.
     */
    @OptIn(InternalComposeTracingApi::class)
    private fun cellsComposedDuring(block: () -> Unit): Int {
        var cells = 0
        Composer.setTracer(
            object : CompositionTracer {
                override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
                    if (info.startsWith("$GUIDE_CELL (")) cells++
                }

                override fun traceEventEnd() = Unit

                override fun isTraceInProgress(): Boolean = true
            },
        )
        try {
            block()
        } finally {
            Composer.setTracer(null)
        }
        return cells
    }

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

    @Test fun aProgramThatComesInTakesTheCellOfTheOneThatLeft() {
        showHalfHours()
        press(Key.DirectionRight, times = 2)
        val first = cellOf("Half Hour 1")

        // The window moves on half an hour: the first program is out, and the fifth comes in.
        // Then Left, to the fourth, which leaves the window where it is.
        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        compose.onAllNodesWithText("Half Hour 1", useUnmergedTree = true).assertCountEquals(0)
        assertEquals("The fifth program's cell is the one the first had", first, cellOf("Half Hour 5"))

        // And back to the first program: the fifth is out, and the first comes in again. Then
        // Right, to the second.
        press(Key.DirectionLeft, times = 3)
        press(Key.DirectionRight)
        compose.onAllNodesWithText("Half Hour 5", useUnmergedTree = true).assertCountEquals(0)
        assertEquals("The first program's cell is the one it had", first, cellOf("Half Hour 1"))
    }

    @Test fun onlyTheCellsThatChangeAreComposedAsTheWindowMoves() {
        showHalfHours()
        press(Key.DirectionRight, times = 2)

        // Focus leaves the fourth program's cell for the fifth's, which comes in as the first goes
        // out. The second and third only move, half an hour to the left.
        assertEquals("Cells composed as the window moved", 2, cellsComposedDuring { press(Key.DirectionRight) })
        compose.onAllNodesWithText("Half Hour 1", useUnmergedTree = true).assertCountEquals(0)

        // The same on the way back: focus leaves the second's cell for the first's, which comes
        // in as the fifth goes out.
        press(Key.DirectionLeft, times = 3)
        assertEquals("Cells composed as the window moved back", 2, cellsComposedDuring { press(Key.DirectionLeft) })
        compose.onAllNodesWithText("Half Hour 5", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun aRowTheCursorIsNotInComposesOnlyTheCellOfTheProgramThatComesIn() {
        showHalfHours(channels = 2)
        press(Key.DirectionRight, times = 2)

        // The cursor's row composes two cells, as above; the other row, the one cell that the
        // program coming into the window takes over.
        assertEquals("Cells composed as the window moved", 3, cellsComposedDuring { press(Key.DirectionRight) })
        compose.onAllNodesWithText("Row 2 Half Hour 5", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("Row 2 Half Hour 1", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun aHeaderLabelKeepsItsPlaceWhileItsHalfHourIsInTheWindow() {
        showHalfHours()
        // The window is 20:00 to 22:00, with a label every half hour.
        press(Key.DirectionRight, times = 2)
        val eight = labelOf("20:00")
        val nine = labelOf("21:00")

        // It moves on to 20:30: three labels only move, and 22:00 comes in where 20:00 was drawn.
        press(Key.DirectionRight)
        compose.onAllNodesWithText("20:00", useUnmergedTree = true).assertCountEquals(0)
        assertEquals("21:00's label is the one it had", nine, labelOf("21:00"))
        assertEquals("22:00's label is the one 20:00 had", eight, labelOf("22:00"))

        // And back.
        press(Key.DirectionLeft, times = 4)
        compose.onAllNodesWithText("22:00", useUnmergedTree = true).assertCountEquals(0)
        assertEquals("21:00's label is still the one it had", nine, labelOf("21:00"))
        assertEquals("20:00's label is the one it had", eight, labelOf("20:00"))
    }

    private companion object {
        /** A cell of the grid, as Compose names it to a tracer: followed by its file and line. */
        const val GUIDE_CELL = "ca.gureet.plasma.feature.guide.GuideCell"
    }
}
