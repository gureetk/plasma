// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A list's rows are taken over by the next owner's (#127): the keys that make it so, and a lazy
 * list that keeps its rows with them where its own keys would make every row anew.
 */
class TakenOverKeysTest : ScreenshotTest() {
    private val keys = TakenOverKeys()

    @Test fun aKeyStaysWithItsItemWhileTheOwnerIsTheSame() {
        val first = keys.keysFor("a", listOf("p1", "p2", "p3"))
        assertEquals("Each row has a key of its own", 3, first.toSet().size)
        assertArrayEquals(first, keys.keysFor("a", listOf("p1", "p2", "p3")))
        // The first one ends and another comes at the end: the two that stay keep their keys.
        val later = keys.keysFor("a", listOf("p2", "p3", "p4"))
        assertEquals(first[1], later[0])
        assertEquals(first[2], later[1])
        assertFalse("A new item doesn't get the key of one that went", later[2] in first)
    }

    @Test fun anotherOwnersRowsTakeTheKeysInOrder() {
        val a = keys.keysFor("a", listOf("a1", "a2", "a3"))
        // More rows than there were: those past the last one's get keys of their own.
        val b = keys.keysFor("b", listOf("b1", "b2", "b3", "b4"))
        assertArrayEquals(a, b.copyOf(3))
        assertFalse(b[3] in a)
        // Fewer: the first row is still the first row.
        val c = keys.keysFor("c", listOf("c1"))
        assertEquals(b[0], c[0])
        assertArrayEquals(c, keys.keysFor("c", listOf("c1")))
        // And back to an owner there was before: it's another owner like any other.
        val again = keys.keysFor("a", listOf("a1", "a2", "a3"))
        assertEquals(c[0], again[0])
        assertEquals("Each row has a key of its own", 3, again.toSet().size)
    }

    @Test fun aRowThatTookFocusIsNotPassedOn() {
        val a = keys.keysFor("a", listOf("a1", "a2", "a3"))
        keys.tookFocus(a[1])
        val b = keys.keysFor("b", listOf("b1", "b2", "b3"))
        assertEquals(a[0], b[0])
        assertEquals(a[2], b[2])
        assertFalse("The row in its place is a new one", b[1] in a)
        // Its key doesn't come back, and the row that took its place is passed on like any other.
        val c = keys.keysFor("c", listOf("c1", "c2", "c3", "c4"))
        assertFalse(a[1] in c)
        assertArrayEquals(b, c.copyOf(3))
    }

    @Test fun anIdThatComesTwiceHasTwoKeys() {
        assertEquals(3, keys.keysFor("a", listOf("x", "x", "y")).toSet().size)
        assertEquals(3, keys.keysFor("a", listOf("x", "x", "y")).toSet().size)
        assertEquals(3, keys.keysFor("b", listOf("z", "z", "z")).toSet().size)
    }

    /** Five rows of [owner]'s, in a list that starts again at its top for each owner; [made] has a line for each row made. */
    private fun rows(made: MutableList<String>, takenOver: Boolean, owner: () -> String) = screen {
        val of = owner()
        val items = remember(of) { List(ROWS) { "$of$it" } }
        val rowKeys = remember { TakenOverKeys() }
        val handedOn = remember(of) { rowKeys.keysFor(of, items) }
        val state = remember(of) { LazyListState() }
        LazyColumn(state = state) {
            itemsIndexed(items, key = { i, item -> if (takenOver) handedOn[i] else item }) { _, item ->
                // Remembered once by each row that's made, and not again by one composed again in place.
                remember { made += item }
                BasicText(item)
            }
        }
    }

    @Test fun theNextOwnersRowsAreTheRowsAlreadyThere() {
        var owner by mutableStateOf("a")
        val made = mutableListOf<String>()
        rows(made, takenOver = true) { owner }
        compose.onNodeWithText("a0").assertExists()
        assertEquals(ROWS, made.size)

        owner = "b"
        compose.waitForIdle()
        // Each row shows the next owner's item, and none was made for it.
        for (i in 0 until ROWS) compose.onNodeWithText("b$i").assertExists()
        compose.onAllNodesWithText("a0").assertCountEquals(0)
        assertEquals("No row is made anew: $made", ROWS, made.size)
    }

    @Test fun withKeysOfItsItemsEveryRowIsMadeAnew() {
        var owner by mutableStateOf("a")
        val made = mutableListOf<String>()
        rows(made, takenOver = false) { owner }
        compose.onNodeWithText("a0").assertExists()
        assertEquals(ROWS, made.size)

        owner = "b"
        compose.waitForIdle()
        for (i in 0 until ROWS) compose.onNodeWithText("b$i").assertExists()
        assertEquals("Every row is made again: $made", 2 * ROWS, made.size)
    }

    private companion object {
        const val ROWS = 5
    }
}
