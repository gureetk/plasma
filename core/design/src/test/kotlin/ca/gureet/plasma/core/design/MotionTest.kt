// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Motion part way through (brief 2.6), the clock stopped: an overlay rises 8 dp as it fades in, a
 * screen settles 16 dp, a row slides to its new place and focus grows a tile by 8 %. With Reduce
 * Motion, each only fades, or grows by 4 % at most, and nothing moves.
 */
class MotionTest : ScreenshotTest() {
    private var shown by mutableStateOf(false)
    private var order by mutableStateOf(listOf("a", "b", "c"))
    private var page by mutableIntStateOf(0)

    /** Shows [content] on black, with Reduce Motion on or off. */
    private fun show(reduceMotion: Boolean, content: @Composable () -> Unit) = screen {
        CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
            Box(Modifier.fillMaxSize().background(PlasmaColors.Black)) { content() }
        }
    }

    /** Stops the clock, makes [change], then lets [ms] go by: part way through what it sets off. */
    private fun partWay(ms: Long, change: () -> Unit) {
        compose.mainClock.autoAdvance = false
        change()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(ms)
    }

    /** Lets everything finish. */
    private fun settle() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun top(tag: String): Dp = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().top

    /** How white the screen is at the top-left 100 px square's middle: 0 black, 1 Plasma's white. */
    private fun whiteness(): Float = compose.onRoot().captureToImage().toPixelMap()[50, 50].red / PlasmaColors.Text.red

    private fun assertFading(whiteness: Float) = assertTrue("Fading, at $whiteness", whiteness > 0.05f && whiteness < 0.95f)

    /** A white square that comes as an overlay. */
    private fun overlay(reduceMotion: Boolean) = show(reduceMotion) {
        Overlay(shown) { Box(Modifier.size(100.px).background(PlasmaColors.Text).testTag("overlay")) }
    }

    @Test fun anOverlayRisesAsItFadesIn() {
        overlay(reduceMotion = false)
        partWay(64) { shown = true }
        val top = top("overlay")
        assertTrue("Rising, at $top", top > 0.dp && top < PlasmaMotion.OVERLAY_RISE)
        assertFading(whiteness())
        settle()
        assertEquals(0f, top("overlay").value, 0.01f)
        assertEquals(1f, whiteness(), 0.02f)
    }

    @Test fun withReduceMotionAnOverlayOnlyFades() {
        overlay(reduceMotion = true)
        partWay(64) { shown = true }
        assertEquals(0f, top("overlay").value, 0.01f)
        assertFading(whiteness())
        settle()
        assertEquals(1f, whiteness(), 0.02f)
    }

    /** A white square that comes as a screen does, with a crossfade. */
    private fun screenComing(reduceMotion: Boolean) = show(reduceMotion) {
        AnimatedVisibility(shown, enter = fadeIn(PlasmaMotion.screen()), exit = fadeOut(PlasmaMotion.screen())) {
            SettlingScreen { Box(Modifier.size(100.px).background(PlasmaColors.Text).testTag("screen")) }
        }
    }

    @Test fun aScreenSettlesUpwardAsItFadesIn() {
        screenComing(reduceMotion = false)
        partWay(64) { shown = true }
        val top = top("screen")
        assertTrue("Settling, at $top", top > 0.dp && top < PlasmaMotion.SCREEN_SETTLE)
        settle()
        assertEquals(0f, top("screen").value, 0.01f)
    }

    @Test fun withReduceMotionAScreenOnlyFadesIn() {
        screenComing(reduceMotion = true)
        partWay(64) { shown = true }
        assertEquals(0f, top("screen").value, 0.01f)
        assertFading(whiteness())
    }

    /** Three 100 px rows, b and c about to change places. */
    private fun rows(reduceMotion: Boolean) = show(reduceMotion) {
        LazyColumn(Modifier.size(200.px, 600.px)) {
            items(order, key = { it }) { id ->
                Box(Modifier.itemMotion(this, reduceMotion).size(200.px, 100.px).testTag(id))
            }
        }
    }

    @Test fun aRowSlidesToItsNewPlace() {
        rows(reduceMotion = false)
        partWay(64) { order = listOf("a", "c", "b") }
        val top = top("b")
        assertTrue("Sliding, at $top", top > 50.dp && top < 100.dp)
        settle()
        assertEquals(100f, top("b").value, 0.01f)
    }

    @Test fun withReduceMotionARowIsInItsNewPlaceAtOnce() {
        rows(reduceMotion = true)
        partWay(64) { order = listOf("a", "c", "b") }
        assertEquals(100f, top("b").value, 0.01f)
    }

    @Test fun contentThatChangesInPlaceFadesInAtOnceWithReduceMotionToo() {
        show(reduceMotion = true) {
            val fade = rememberSwapFade(page)
            Box(Modifier.size(100.px).swapFade(fade).background(if (page == 0) PlasmaColors.Black else PlasmaColors.Text))
        }
        // The new content is there in the frame after the change, fading in from nothing.
        partWay(48) { page = 1 }
        assertFading(whiteness())
        settle()
        assertEquals(1f, whiteness(), 0.02f)
    }

    @Test fun contentThatComesInPartsStaysHiddenUntilEveryPartIsIn() {
        var step = -1
        show(reduceMotion = false) {
            val swap = rememberSteppedSwap(page, steps = 2)
            step = swap.step
            Box(Modifier.size(100.px).swapFade(swap.fade).background(if (page == 0) PlasmaColors.Black else PlasmaColors.Text))
        }
        assertEquals(2, step)
        // The frame after the change has the first part, hidden; a part comes in at each frame.
        compose.mainClock.autoAdvance = false
        page = 1
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        assertTrue("A part at a time, at step $step", step < 2)
        assertEquals(0f, whiteness(), 0.02f)
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(2, step)
        // Then it fades in whole.
        compose.mainClock.advanceTimeBy(48)
        assertFading(whiteness())
        settle()
        assertEquals(1f, whiteness(), 0.02f)
    }

    /** A 200 px tile with focus. */
    private fun focusedTile(reduceMotion: Boolean) {
        val focus = FocusRequester()
        show(reduceMotion) {
            PlasmaSurface(onClick = {}, modifier = Modifier.size(200.px).focusRequester(focus), style = FocusStyle.Tile) {
                Box(Modifier.fillMaxSize().testTag("tile"))
            }
        }
        compose.runOnIdle { focus.requestFocus() }
        compose.waitForIdle()
    }

    private fun tileWidth(): Float = compose.onNodeWithTag("tile", useUnmergedTree = true).getBoundsInRoot().width.value

    @Test fun focusGrowsATileBy8Percent() {
        focusedTile(reduceMotion = false)
        assertEquals(108f, tileWidth(), 0.5f)
    }

    @Test fun withReduceMotionFocusGrowsATileBy4Percent() {
        focusedTile(reduceMotion = true)
        assertEquals(104f, tileWidth(), 0.5f)
    }
}
