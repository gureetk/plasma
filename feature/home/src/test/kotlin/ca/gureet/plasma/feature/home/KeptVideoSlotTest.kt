// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.zIndex
import ca.gureet.plasma.core.design.KeptScreen
import ca.gureet.plasma.core.design.KeptScreenShows
import ca.gureet.plasma.core.design.LocalReduceMotion
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.rememberKeptScreen
import ca.gureet.plasma.core.testing.ScreenshotTest
import ca.gureet.plasma.player.LocalVideoLayer
import ca.gureet.plasma.player.VideoLayerState
import ca.gureet.plasma.player.VideoSlot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A video's slot in a screen kept made under another (#127), as Home's is with Live Preview on.
 * The kept screen stays laid out while it's under, so nothing tells the slot its place again as
 * the screen comes back. The slot lets the picture go as its screen leaves, and takes it again at
 * the place it has: with Reduce Motion too, where the screen doesn't even settle in and so nothing
 * of it moves.
 */
class KeptVideoSlotTest : ScreenshotTest() {
    /** The screen in front: [KEPT] or [OTHER]. */
    private var front by mutableIntStateOf(KEPT)
    private lateinit var layer: VideoLayerState

    private fun show(reduceMotion: Boolean) {
        screen {
            layer = LocalVideoLayer.current
            val kept = rememberKeptScreen()
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = front,
                        transitionSpec = {
                            ContentTransform(fadeIn(PlasmaMotion.screen()), fadeOut(PlasmaMotion.screen()), targetContentZIndex = if (targetState == KEPT) -1f else 1f, sizeTransform = null)
                        },
                        label = "host",
                    ) { destination ->
                        if (destination == KEPT) KeptScreenShows(kept) else Box(Modifier.fillMaxSize())
                    }
                    KeptScreen(kept, Modifier.zIndex(-1f)) {
                        Box(Modifier.fillMaxSize()) {
                            VideoSlot(frame = null, modifier = Modifier.offset(LEFT.px, TOP.px).size(WIDE.px, HIGH.px))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun open(destination: Int) {
        compose.runOnIdle { front = destination }
        compose.waitForIdle()
    }

    private fun assertTheSlotTakesThePictureBackWhereItIs(reduceMotion: Boolean) {
        show(reduceMotion)
        val place = Rect(LEFT.toFloat(), TOP.toFloat(), (LEFT + WIDE).toFloat(), (TOP + HIGH).toFloat())
        assertEquals(place, layer.bounds)
        repeat(2) {
            open(OTHER)
            assertEquals("Under another screen the slot has let the picture go", null, layer.bounds)
            open(KEPT)
            assertEquals("In front again, it has the picture where it is", place, layer.bounds)
        }
    }

    @Test fun itTakesThePictureBackWhereItIs() = assertTheSlotTakesThePictureBackWhereItIs(reduceMotion = false)

    @Test fun withReduceMotionTooWhereNothingMovesToTellItItsPlace() = assertTheSlotTakesThePictureBackWhereItIs(reduceMotion = true)

    private companion object {
        const val KEPT = 0
        const val OTHER = 1

        /** The slot's place on the screen. */
        const val LEFT = 200
        const val TOP = 120
        const val WIDE = 640
        const val HIGH = 360
    }
}
