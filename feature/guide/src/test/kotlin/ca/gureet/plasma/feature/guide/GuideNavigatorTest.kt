// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The guide's time-anchored focus (brief 4.8), on board 08's rows. */
class GuideNavigatorTest {
    private fun t(hour: Int, minute: Int) = (hour * 60L + minute) * 60_000L
    private fun slot(id: String, from: Long, to: Long) = GuideSlot(id, from, to)

    // 101 Northline, 105 Arena Sports 1, 107 Velocity, 120 Atlas One
    private val rows = listOf(
        listOf(slot("bulletin", t(20, 0), t(20, 30)), slot("wrap", t(20, 30), t(21, 0)), slot("world", t(21, 0), t(22, 0))),
        listOf(slot("harbour", t(19, 45), t(22, 0)), slot("extra", t(22, 0), t(23, 0)), slot("tonight", t(23, 0), t(24, 30))),
        listOf(slot("stint", t(20, 0), t(21, 30)), slot("pit", t(21, 30), t(22, 0)), slot("stint2", t(22, 0), t(23, 30))),
        listOf(slot("quiz", t(20, 0), t(21, 0)), slot("bakers", t(21, 0), t(22, 0))),
    )
    private val guide = GuideNavigator(rows)
    private val window = t(20, 0)

    @Test fun entersOnWhatsOnNow() {
        val cursor = guide.enter(1, now = t(20, 41), windowStart = window)
        assertEquals("harbour", cursor.programId)
        assertEquals(t(20, 41), cursor.anchor)
    }

    @Test fun downKeepsTheAnchorTime() {
        val start = guide.enter(1, now = t(20, 41), windowStart = window)
        val down = guide.vertical(start, +1)!!
        assertEquals("stint", down.programId)
        val down2 = guide.vertical(down, +1)!!
        assertEquals("quiz", down2.programId)
        // Back up the same column: the anchor hasn't drifted to 20:00.
        val up = guide.vertical(guide.vertical(down2, -1)!!, -1)!!
        assertEquals("harbour", up.programId)
        assertEquals(t(20, 41), up.anchor)
    }

    @Test fun rightSetsTheAnchorToTheNewProgramme() {
        val wrap = guide.enter(0, now = t(20, 41), windowStart = window)
        assertEquals("wrap", wrap.programId)
        val world = guide.right(wrap)
        assertEquals("world", world.programId)
        assertEquals(t(21, 0), world.anchor)
        // Down from 21:00 lands on the programme overlapping 21:00, not the one on now.
        assertEquals("harbour", guide.vertical(world, +1)!!.programId)
        assertEquals("stint", guide.vertical(guide.vertical(world, +1)!!, +1)!!.programId)
    }

    @Test fun aLongProgrammeScrollsTheWindowBeforeFocusMoves() {
        val harbour = guide.enter(1, now = t(20, 41), windowStart = window)
        // Harbour City ends at 22:00, the window's end: Right moves straight on, scrolling to show it.
        val extra = guide.right(harbour)
        assertEquals("extra", extra.programId)
        assertEquals(t(20, 30), extra.windowStart)
        // Match of the Week Extra runs to 23:00, past the window: Right scrolls 30 minutes first.
        val scrolled = guide.right(extra)
        assertEquals("extra", scrolled.programId)
        assertEquals(t(21, 0), scrolled.windowStart)
        val tonight = guide.right(scrolled)
        assertEquals("tonight", tonight.programId)
        assertEquals(t(21, 30), tonight.windowStart)
        assertEquals(t(23, 0), tonight.anchor)
    }

    @Test fun anchorIsClampedToTheWindow() {
        val harbour = guide.enter(1, now = t(20, 41), windowStart = window)
        val left = guide.left(harbour)
        // Harbour City started at 19:45, before the window: Left scrolls back first.
        assertEquals("harbour", left.programId)
        assertEquals(t(19, 30), left.windowStart)
        assertEquals(t(19, 45), left.anchor)
    }

    @Test fun upFromTheTopRowLeavesTheGrid() {
        val top = guide.enter(0, now = t(20, 41), windowStart = window)
        assertNull(guide.vertical(top, -1))
    }
}
