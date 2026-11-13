// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

/**
 * A Guide row's layout of its cells (#127): each cell is where its own offset and size put it
 * before, to the pixel; the cells are drawn in the order of their programs' times, whatever order
 * they were composed in; and a cell whose program's times change is measured and placed again.
 */
class GuideCellsTest : ScreenshotTest() {
    private val windowStart: Instant = Sample.at(20, 0).toInstant()
    private val windowEnd: Instant = windowStart.plusMillis(GuideNavigator.WINDOW)
    private val window = windowStart.toEpochMilli()

    /** So long after the window's start. */
    private fun at(minutes: Int, seconds: Int = 0): Instant = windowStart.plusSeconds(minutes * 60L + seconds)

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    /** Programs' times: a row's would follow one another, but each is placed by itself. */
    private val spans: List<Pair<Instant, Instant>> = buildList {
        // Half hours, as much of a guide is.
        for (step in 0 until 4) add(at(30 * step) to at(30 * step + 30))
        // Cut off at the window's start, at its end, and at both.
        add(at(-50) to at(20))
        add(at(100) to at(150))
        add(at(-30) to at(200))
        // Out of step with the pixels, at 11.77 px a minute.
        add(at(7, 13) to at(52, 41))
        add(at(61, 1) to at(63, 59))
        add(at(33, 20) to at(34, 50))
        // Shorter than the room between two cells, and no time at all.
        add(at(90) to at(90, 15))
        add(at(110) to at(110))
        // And whatever a day brings, of what the window shows any of.
        val random = Random(8)
        repeat(60) {
            val start = at(0, random.nextInt(-3_600, 7_200))
            val end = start.plusSeconds(random.nextLong(1, 5_400))
            if (end.isAfter(windowStart) && start.isBefore(windowEnd)) add(start to end)
        }
    }

    @Test fun aCellIsWhereItsOwnOffsetAndSizePutIt() {
        screen {
            Column {
                // As each cell placed itself before: these are the sums the row had for a cell.
                Box(Modifier.size(WINDOW_PX.px, ROW_PX.px)) {
                    spans.forEachIndexed { index, (start, end) ->
                        val from = maxOf(start, windowStart)
                        val to = minOf(end, windowEnd)
                        val left = (from.toEpochMilli() - windowStart.toEpochMilli()) * PX_PER_MS
                        val width = (to.toEpochMilli() - from.toEpochMilli()) * PX_PER_MS
                        Box(Modifier.offset(x = (left + 2f).px).size((width - 4f).px, ROW_PX.px).testTag("own $index"))
                    }
                }
                // As the row places them.
                GuideCells(window, Modifier.size(WINDOW_PX.px, ROW_PX.px)) {
                    spans.forEachIndexed { index, (start, end) ->
                        Box(Modifier.guideSlot(start.toEpochMilli(), end.toEpochMilli()).testTag("row $index"))
                    }
                }
            }
        }
        spans.forEachIndexed { index, (start, end) ->
            val own = node("own $index")
            val row = node("row $index")
            val program = "the program from ${start.epochSecond - windowStart.epochSecond} s to ${end.epochSecond - windowStart.epochSecond} s"
            assertEquals("Where the cell of $program starts", own.positionInRoot.x, row.positionInRoot.x, 0f)
            assertEquals("The size of the cell of $program", own.size, row.size)
        }
        // And they aren't all nowhere: the second half hour's cell.
        assertEquals(355f, node("row 1").positionInRoot.x, 0f)
        assertEquals(349, node("row 1").size.width)
        assertEquals(ROW_PX, node("row 1").size.height)
    }

    @Test fun cellsAreDrawnInTheOrderOfTheirTimesWhateverOrderTheyWereComposedIn() {
        screen {
            GuideCells(window, Modifier.size(WINDOW_PX.px, ROW_PX.px)) {
                // The later program's cell is composed first, as when it has taken over the cell
                // of a program that left. It draws 40 px before its start, over the end of the
                // cell before it, as a focused cell's shadow does.
                Box(
                    Modifier.guideSlot(at(30).toEpochMilli(), at(60).toEpochMilli()).drawBehind {
                        drawRect(PlasmaColors.Text, topLeft = Offset(-40f, 0f), size = Size(size.width + 40f, size.height))
                    },
                )
                Box(Modifier.guideSlot(at(0).toEpochMilli(), at(30).toEpochMilli()).background(PlasmaColors.Black))
            }
        }
        // The first cell ends at 351 px and the second starts at 355: 335 is in what the second
        // draws over the first.
        val drawn = compose.onRoot().captureToImage().toPixelMap()[335, ROW_PX / 2]
        assertEquals("The later cell is drawn over the earlier one", PlasmaColors.Text.red, drawn.red, 0.02f)
    }

    @Test fun aCellWhoseProgramsTimesChangeIsMeasuredAndPlacedAgain() {
        var turn by mutableStateOf(at(30))
        screen {
            GuideCells(window, Modifier.size(WINDOW_PX.px, ROW_PX.px)) {
                Box(Modifier.guideSlot(at(0).toEpochMilli(), turn.toEpochMilli()).testTag("first"))
                Box(Modifier.guideSlot(turn.toEpochMilli(), at(90).toEpochMilli()).testTag("second"))
            }
        }
        assertEquals(349, node("first").size.width)
        assertEquals(355f, node("second").positionInRoot.x, 0f)
        assertEquals(702, node("second").size.width)

        // The guide now has the first program running half an hour longer, in the same window.
        turn = at(60)
        compose.waitForIdle()
        assertEquals(702, node("first").size.width)
        assertEquals(708f, node("second").positionInRoot.x, 0f)
        assertEquals(349, node("second").size.width)
    }
}
