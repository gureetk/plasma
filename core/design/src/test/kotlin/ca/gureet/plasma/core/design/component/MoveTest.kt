// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Move mode's list (board 04): it holds still while an item is carried, and scrolls only to keep
 * the items either side of it in view. A list follows its first item by itself, so without this
 * the list would run along with an item carried from the top, or lose one carried up past it.
 */
class MoveTest : ScreenshotTest() {
    private val start = List(30) { "item$it" }
    private var items by mutableStateOf(start)
    private lateinit var list: LazyListState

    /** Five whole rows of 100 px. */
    private fun show() = screen {
        list = rememberLazyListState()
        LazyColumn(Modifier.size(200.px, 500.px), state = list) {
            items(items, key = { it }) { Box(Modifier.size(200.px, 100.px)) }
        }
    }

    private fun carry(id: String, delta: Int, times: Int = 1) = repeat(times) {
        compose.runOnIdle { list.carry(items, items.indexOf(id), delta)?.let { items = it } }
        compose.waitForIdle()
    }

    private fun firstShown() = compose.runOnIdle { list.firstVisibleItemIndex }

    @Test fun theListHoldsStillUntilTheItemNearsAnEdge() {
        show()
        // Down from the top: still while the item and the one after it fit in the five rows.
        carry("item0", +1, times = 3)
        assertEquals(3, items.indexOf("item0"))
        assertEquals(0, firstShown())
        // Then a row at a time, keeping one below it.
        carry("item0", +1, times = 2)
        assertEquals(5, items.indexOf("item0"))
        assertEquals(2, firstShown())
        // Back up, it's still until it nears the top.
        carry("item0", -1, times = 2)
        assertEquals(2, firstShown())
        carry("item0", -1)
        assertEquals(2, items.indexOf("item0"))
        assertEquals(1, firstShown())
    }

    @Test fun theFirstItemShownCanMoveWithoutTheListFollowingIt() {
        show()
        compose.runOnIdle { list.requestScrollToItem(5) }
        compose.waitForIdle()
        carry("item5", +1)
        assertEquals(6, items.indexOf("item5"))
        assertEquals(5, firstShown())
    }

    @Test fun itStopsAtTheEnds() {
        show()
        compose.runOnIdle {
            assertNull(list.carry(items, 0, -1))
            assertNull(list.carry(items, items.lastIndex, +1))
        }
    }

    @Test fun anItemPutBackIsKeptInView() {
        show()
        carry("item0", +1, times = 10)
        assertEquals(7, firstShown())
        compose.runOnIdle {
            list.keepInView(start.indexOf("item0"), start.size)
            items = start
        }
        compose.waitForIdle()
        assertEquals(0, firstShown())
    }
}
