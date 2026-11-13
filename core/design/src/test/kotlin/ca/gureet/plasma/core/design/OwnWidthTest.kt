// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A text measured at its own width where it fits (#127), beside the same text given its whole
 * room: the two come out the same size in any room, and room that changes while it still fits the
 * words lays out only the second again.
 *
 * The texts here tell the test of each layout, which a [PlasmaText] can't; Compose decides whether
 * to lay a text out again the same way for both kinds.
 */
class OwnWidthTest : ScreenshotTest() {
    /** How wide the texts' room is, in board pixels. */
    private var room by mutableIntStateOf(WIDE)
    private val layouts = HashMap<String, Int>()

    /** [WORDS] on one line in the room, cut short where it doesn't fit, as a Guide cell's title is. */
    @Composable
    private fun Title(tag: String, modifier: Modifier) {
        Box(Modifier.width(room.px).height(40.px)) {
            BasicText(
                WORDS,
                modifier.testTag(tag),
                plasmaStyle(22f, 600).copy(color = PlasmaColors.Text),
                onTextLayout = { layouts[tag] = (layouts[tag] ?: 0) + 1 },
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                maxLines = 1,
            )
        }
    }

    private fun show() = screen {
        Column {
            Title(OWN, Modifier.atOwnWidthWhereItFits())
            Title(WHOLE, Modifier)
        }
    }

    /** Gives the texts a room [px] wide, and waits for them. */
    private fun roomOf(px: Int) {
        room = px
        compose.waitForIdle()
    }

    private fun sizeOf(tag: String): IntSize = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().size

    private fun layoutsOf(tag: String): Int = layouts[tag] ?: 0

    @Test fun roomThatStillFitsATextDoesNotLayItOutAgain() {
        show()
        val size = sizeOf(WHOLE)
        assertTrue("The words fit the narrowest of these rooms: ${size.width} px", size.width in 1 until FITS)
        assertEquals("At its own width it's the size it is in its whole room", size, sizeOf(OWN))
        val own = layoutsOf(OWN)
        val whole = layoutsOf(WHOLE)
        assertTrue("Each was laid out to begin with: $own and $whole", own > 0 && whole > 0)

        for (px in intArrayOf(500, FITS, 560, WIDE)) {
            roomOf(px)
            assertEquals("In a room of $px px, at its own width", size, sizeOf(OWN))
            assertEquals("In a room of $px px, given the whole room", size, sizeOf(WHOLE))
        }
        assertEquals("At its own width, the text isn't laid out again", own, layoutsOf(OWN))
        assertTrue("Given its whole room, it's laid out for each room: ${layoutsOf(WHOLE) - whole} times", layoutsOf(WHOLE) - whole >= 4)
    }

    @Test fun aTextThatDoesNotFitHasItsRoomAsBefore() {
        show()
        val fits = sizeOf(WHOLE)
        val own = layoutsOf(OWN)

        // Too narrow for the words: both are cut short, at the room's width.
        for (px in intArrayOf(NARROW, NARROW - 30)) {
            roomOf(px)
            assertTrue("Cut short in a room of $px px: ${sizeOf(WHOLE).width} px", sizeOf(WHOLE).width in 1..px)
            assertEquals("In a room of $px px", sizeOf(WHOLE), sizeOf(OWN))
        }
        assertTrue("A text that's cut is laid out for its room: ${layoutsOf(OWN) - own} times", layoutsOf(OWN) - own >= 2)

        // And wide again: whole, as it was.
        roomOf(WIDE)
        assertEquals(fits, sizeOf(WHOLE))
        assertEquals(fits, sizeOf(OWN))
    }

    private companion object {
        const val WORDS = "The Late Edition"
        const val OWN = "own"
        const val WHOLE = "whole"

        /** Rooms in board pixels: the words are some 180 px wide at 22 px. */
        const val WIDE = 600
        const val FITS = 420
        const val NARROW = 120
    }
}
