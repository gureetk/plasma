// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.core.design.component.CountedList
import ca.gureet.plasma.core.design.component.PlasmaColumn
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An arrow held down (the owner's ask, 1 November): it stops at a list's ends rather than leaving
 * it, never brings focus up into the top bar, and goes further the longer it's held. A press
 * leaves a list, and goes up to the top bar, as before.
 */
class HeldKeysTest : ScreenshotTest() {
    /** A row of a list: its words, focusable. */
    @Composable
    private fun Item(text: String, modifier: Modifier = Modifier) {
        PlasmaSurface(onClick = {}, modifier = modifier.size(400.px, 60.px)) {
            PlasmaText(text, plasmaStyle(24f, 500))
        }
    }

    @Test fun aHeldUpStopsAtTheTopOfAListAndAPressLeavesIt() {
        screen {
            val start = remember { FocusRequester() }
            Column {
                Item("Above")
                PlasmaColumn(modifier = Modifier.size(400.px, 600.px)) {
                    items((0 until 30).toList()) { i -> Item("Row $i", if (i == 3) Modifier.focusRequester(start) else Modifier) }
                }
            }
            LaunchedEffect(Unit) {
                // Once the list has been laid out, a frame on.
                withFrameNanos { }
                start.requestFocus()
            }
        }
        focused().assert(hasText("Row 3"))

        hold(Key.DirectionUp, 1_000)
        focused().assert(hasText("Row 0"))

        press(Key.DirectionUp)
        focused().assert(hasText("Above"))
    }

    @Test fun aHeldArrowNeverBringsFocusUpIntoTheTopBar() {
        screen {
            val start = remember { FocusRequester() }
            Box(Modifier.fillMaxSize()) {
                TopBar(current = TopTab.Home, profileInitial = "M", profileName = "Maya", onTab = {}, onSettings = {}, onProfile = {})
                Column(Modifier.offset(96.px, 200.px)) {
                    (0 until 3).forEach { i -> Item("Item $i", if (i == 2) Modifier.focusRequester(start) else Modifier) }
                }
            }
            LaunchedEffect(Unit) {
                // Once the list has been laid out, a frame on.
                withFrameNanos { }
                start.requestFocus()
            }
        }
        focused().assert(hasText("Item 2"))

        hold(Key.DirectionUp, 1_000)
        focused().assert(hasText("Item 0"))

        press(Key.DirectionUp)
        focused().assert(!hasText("Item 0"))
    }

    @Test fun aHeldArrowGoesFurtherTheLongerItsHeld() {
        val items = List(300) { CountedItem("g$it", "Group $it", it) }
        screen {
            val list = remember { FocusRequester() }
            CountedList(items, selectedId = "g0", onFocusItem = {}, onOpenItem = {}, modifier = Modifier.size(300.px, 700.px).focusRequester(list))
            LaunchedEffect(Unit) {
                withFrameNanos { }
                list.requestFocus()
            }
        }
        focused().assert(hasText("Group 0"))

        // Three seconds: the press, then 51 repeats: one row each for the first second, two each
        // until 2.5 seconds, then four each. One row a repeat would have gone 52 rows.
        hold(Key.DirectionDown, 3_000)
        val row = focusedRow()
        assertTrue("Focus went to row $row", row in 100..120)

        // Held back up, it stops at the first row.
        hold(Key.DirectionUp, 6_000)
        focused().assert(hasText("Group 0"))
    }

    /** The number in "Group N" on the row with focus. */
    private fun focusedRow(): Int {
        val texts = focused().fetchSemanticsNode().config[SemanticsProperties.Text]
        return texts.first { it.text.startsWith("Group ") }.text.removePrefix("Group ").toInt()
    }
}
