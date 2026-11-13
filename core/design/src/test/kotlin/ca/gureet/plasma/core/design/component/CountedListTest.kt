// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The column of groups or categories makes only the rows in its window and a few past it (#127),
 * and nothing else about it changes for that: every row is where it would be with all of them
 * made, focus moves through them and comes back to them as it did, and no frame in which the list
 * scrolls shows the empty place of a row that isn't made.
 */
class CountedListTest : ScreenshotTest() {
    private val groups = List(GROUPS) { CountedItem("g$it", "Group $it", it + 1) }
    private var shown by mutableStateOf(groups)

    /** The list, 700 px tall, with something beside it for focus to leave to. */
    @Composable
    private fun Groups(selectedId: String = "g0") {
        val list = remember { FocusRequester() }
        Row {
            CountedList(shown, selectedId, onFocusItem = {}, onOpenItem = {}, modifier = Modifier.testTag(LIST).size(300.px, 700.px).focusRequester(list))
            PlasmaSurface(onClick = {}, modifier = Modifier.size(200.px, 60.px)) { PlasmaText("Beside", plasmaStyle(24f, 500)) }
        }
        LaunchedEffect(Unit) {
            // Once the list has been laid out, a frame on.
            withFrameNanos { }
            list.requestFocus()
        }
    }

    /** A row that's made: which group it shows, and where it is on the screen. */
    private class Made(val index: Int, val top: Float, val height: Int)

    private fun made(): List<Made> = compose.onAllNodes(hasText("Group ", substring = true)).fetchSemanticsNodes().map { node ->
        val name = node.config[SemanticsProperties.Text].first { it.text.startsWith("Group ") }.text
        Made(name.removePrefix("Group ").toInt(), node.positionInRoot.y, node.size.height)
    }.sortedBy { it.index }

    /**
     * Every place in the list's window has its row: the rows that show there follow one another,
     * one row's height and gap apart, from its top edge to its bottom edge or to the last row.
     */
    private fun assertNoEmptyPlace(last: Int = shown.lastIndex) {
        val list = compose.onNodeWithTag(LIST).fetchSemanticsNode()
        val top = list.positionInRoot.y
        val bottom = top + list.size.height
        val inWindow = made().filter { it.top + it.height > top && it.top < bottom }
        assertTrue("Rows show in the list's window", inWindow.isNotEmpty())
        val first = inWindow.first()
        assertTrue("No empty place at the top: row ${first.index} starts ${first.top - top} px down", first.top <= top + GAP_PX)
        inWindow.zipWithNext { above, below ->
            assertEquals("The row after row ${above.index}", above.index + 1, below.index)
            assertEquals("Row ${below.index} is a row's height and gap below row ${above.index}", PITCH_PX, below.top - above.top, 0.5f)
        }
        val end = inWindow.last()
        assertTrue(
            "No empty place at the bottom: row ${end.index} ends ${bottom - end.top - end.height} px above it",
            end.index == last || end.top + end.height >= bottom - GAP_PX,
        )
    }

    private fun focusedRow(): Int {
        val texts = focused().fetchSemanticsNode().config[SemanticsProperties.Text]
        return texts.first { it.text.startsWith("Group ") }.text.removePrefix("Group ").toInt()
    }

    @Test fun onlyTheRowsNearItsWindowAreMade() {
        screen { Groups() }
        focused().assert(hasText("Group 0"))

        // 700 px hold twelve rows, the last cut off; two more are made past it.
        val rows = made()
        assertTrue("${rows.size} of $GROUPS rows are made: ${rows.map { it.index }}", rows.size in 12..16)
        assertEquals("From the first row on", 0, rows.first().index)
        assertNoEmptyPlace()
    }

    @Test fun downTheListAndBackEveryRowIsInItsPlace() {
        screen { Groups() }
        repeat(70) { pressed ->
            press(Key.DirectionDown)
            assertEquals("After ${pressed + 1} presses of Down, focus is on row", pressed + 1, focusedRow())
            assertNoEmptyPlace()
        }
        assertTrue("Far down the list, still only the rows near its window are made: ${made().size}", made().size <= 18)

        repeat(70) { pressed ->
            press(Key.DirectionUp)
            assertEquals("After ${pressed + 1} presses of Up, focus is on row", 69 - pressed, focusedRow())
            assertNoEmptyPlace()
        }
    }

    @Test fun aListThatOpensOnARowFarDownShowsNoEmptyPlaceOnTheWayThere() {
        compose.mainClock.autoAdvance = false
        screen { Groups(selectedId = "g80") }
        // Frame by frame: first as it's laid out at its top, then scrolled to its selected row.
        repeat(8) {
            compose.waitForIdle()
            assertNoEmptyPlace()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.waitForIdle()

        val list = compose.onNodeWithTag(LIST).fetchSemanticsNode()
        val selected = made().first { it.index == 80 }
        assertTrue(
            "The selected row is in the window: ${selected.top - list.positionInRoot.y} px down",
            selected.top >= list.positionInRoot.y && selected.top + selected.height <= list.positionInRoot.y + list.size.height,
        )
        assertEquals("Focus comes to it", 80, focusedRow())
        assertTrue("Once it has opened, only the rows near its window are made: ${made().size}", made().size <= 18)
    }

    @Test fun focusComesBackToTheRowItLeft() {
        screen { Groups() }
        press(Key.DirectionDown, times = 30)
        assertEquals(30, focusedRow())

        press(Key.DirectionRight)
        focused().assert(hasText("Beside"))
        press(Key.DirectionLeft)
        assertEquals("Back in the list, focus is on the row it left", 30, focusedRow())
        assertNoEmptyPlace()
    }

    @Test fun aHeldArrowComesToRowsThatWereNotMadeWhenItSetOff() {
        screen { Groups() }
        // Three seconds held: one row a repeat, then two, then four (HeldKeysTest has the count).
        hold(Key.DirectionDown, 3_000)
        val reached = focusedRow()
        assertTrue("Focus went to row $reached", reached >= 100)
        assertNoEmptyPlace()

        // Let go, a press moves one row again, and the rows a held arrow had made ahead are gone.
        press(Key.DirectionUp)
        assertEquals(reached - 1, focusedRow())
        assertTrue("Only the rows near the window are made again: ${made().size}", made().size <= 18)

        hold(Key.DirectionUp, 8_000)
        assertEquals("Held back up, it stops at the first row", 0, focusedRow())
        assertNoEmptyPlace()
    }

    @Test fun aListThatGetsShorterShowsNoEmptyPlace() {
        screen { Groups() }
        hold(Key.DirectionDown, 8_000)
        assertEquals("Held down, it stops at the last row", GROUPS - 1, focusedRow())

        // The provider's groups, read again, are fewer: the window is past the list's new end.
        compose.mainClock.autoAdvance = false
        shown = groups.take(40)
        repeat(4) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertNoEmptyPlace()
        }
    }

    private companion object {
        const val LIST = "list"
        const val GROUPS = 120

        /** A row and the gap under it, and the gap, in board pixels, which the tests' screen has one to one. */
        const val PITCH_PX = 62f
        const val GAP_PX = 4f
    }
}
