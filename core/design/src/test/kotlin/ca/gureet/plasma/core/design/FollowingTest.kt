// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A followed value against `animate*AsState`'s, the two given the same targets in the same frames
 * (#127): they're at the same value at every frame, as focus comes, as it leaves, when it turns
 * back part way and when the target changes at two frames running. And nothing is set up for a
 * followed value until its target first changes, however often it's composed again.
 */
class FollowingTest : ScreenshotTest() {
    private var size by mutableStateOf(1f)
    private var shift by mutableStateOf(0.dp)
    private var fill by mutableStateOf(PlasmaColors.Surface2)
    private var atOnce by mutableStateOf(false)
    private var composedAgain by mutableIntStateOf(0)
    private var compositions = 0

    private lateinit var animatedSize: State<Float>
    private lateinit var followedSize: State<Float>
    private lateinit var animatedShift: State<Dp>
    private lateinit var followedShift: State<Dp>
    private lateinit var animatedFill: State<Color>
    private lateinit var followedFill: State<Color>

    /** A size, a shift and a fill, each animated as it was and followed, with the clock stopped. */
    private fun show() {
        screen {
            // Read, so a change composes all of this again with nothing else different.
            composedAgain
            compositions++
            val motion = rememberCoroutineScope()
            val focus: AnimationSpec<Float> = if (atOnce) snap() else PlasmaMotion.focus()
            animatedSize = animateFloatAsState(size, focus, label = "size")
            followedSize = followFloatAsState(size, focus, motion)
            animatedShift = animateDpAsState(shift, PlasmaMotion.focus(), label = "shift")
            followedShift = followDpAsState(shift, PlasmaMotion.focus(), motion)
            animatedFill = animateColorAsState(fill, PlasmaMotion.colour(), label = "fill")
            followedFill = followColorAsState(fill, PlasmaMotion.colour(), motion)
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    /** One frame goes by, and each followed value is where the animated one is, to the last bit. */
    private fun frame(what: String) {
        // What the test has just changed is seen before the frame, and what the frame set off is done after it.
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals("The size, $what", animatedSize.value, followedSize.value, 0f)
        assertEquals("The shift, $what", animatedShift.value.value, followedShift.value.value, 0f)
        assertEquals("The fill, $what", animatedFill.value, followedFill.value)
    }

    private fun frames(count: Int, what: String) = repeat(count) { frame("$what, frame ${it + 1}") }

    private val Any.setUp: Boolean get() = (this as Following<*, *>).setUp

    @Test fun aFollowedValueIsWhereAnAnimatedOneIsAtEveryFrame() {
        show()
        frames(2, "at rest")

        // Focus comes.
        size = 1.08f
        shift = 8.dp
        fill = PlasmaColors.Text
        frames(5, "as focus comes")
        assertTrue("Part way, the size is ${followedSize.value}", followedSize.value > 1f && followedSize.value < 1.08f)
        assertTrue("Part way, the shift is ${followedShift.value}", followedShift.value > 0.dp && followedShift.value < 8.dp)
        assertTrue("Part way, the fill is ${followedFill.value}", followedFill.value != PlasmaColors.Surface2 && followedFill.value != PlasmaColors.Text)

        // It turns back before it has arrived.
        size = 1f
        shift = 0.dp
        fill = PlasmaColors.Surface2
        frames(4, "as focus turns back")
        assertTrue("Part way back, the size is ${followedSize.value}", followedSize.value > 1f && followedSize.value < 1.08f)

        // And changes its mind at two frames running.
        size = 1.08f
        shift = 8.dp
        fill = PlasmaColors.Text
        frame("the first change of two")
        size = 1.04f
        shift = 4.dp
        fill = PlasmaColors.Selected
        frame("the second change of two")
        frames(14, "to the end")
        assertEquals(1.04f, followedSize.value, 0f)
        assertEquals(4.dp, followedShift.value)
        assertEquals(PlasmaColors.Selected, followedFill.value)

        // Focus leaves, all the way.
        size = 1f
        shift = 0.dp
        fill = PlasmaColors.Surface2
        frames(14, "as focus leaves")
        assertEquals(1f, followedSize.value, 0f)
        assertEquals(0.dp, followedShift.value)
        assertEquals(PlasmaColors.Surface2, followedFill.value)
    }

    @Test fun aFollowedValueThatGoesAtOnceGoesInTheFrameAnAnimatedOneDoes() {
        atOnce = true
        show()
        size = 1.08f
        frame("the frame that brings the target")
        frame("the frame after")
        assertEquals(1.08f, followedSize.value, 0f)
        size = 1f
        frames(3, "back again")
        assertEquals(1f, followedSize.value, 0f)
    }

    @Test fun nothingIsSetUpForAFollowedValueUntilItsTargetFirstChanges() {
        show()
        val composedOnce = compositions
        repeat(3) {
            composedAgain++
            frame("composed again")
        }
        assertTrue("It was composed again", compositions > composedOnce)
        assertFalse("The size has nothing set up", followedSize.setUp)
        assertFalse("The shift has nothing set up", followedShift.setUp)
        assertFalse("The fill has nothing set up", followedFill.setUp)
        assertEquals(1f, followedSize.value, 0f)

        // Only the one whose target changes.
        size = 1.08f
        frames(2, "once the size's target has changed")
        assertTrue("The size is set up", followedSize.setUp)
        assertFalse("The shift still has nothing set up", followedShift.setUp)
        assertFalse("The fill still has nothing set up", followedFill.setUp)
    }
}
