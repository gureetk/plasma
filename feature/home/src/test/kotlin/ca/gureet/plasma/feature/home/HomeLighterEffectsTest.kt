// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import ca.gureet.plasma.core.design.LocalLighterEffects
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Settings › Appearance › Lighter Effects on Home (#127): the picture behind the rows follows the
 * focused item once focus has rested, and at once without it.
 */
class HomeLighterEffectsTest : ScreenshotTest() {
    private fun show(lighterEffects: Boolean) = screen {
        CompositionLocalProvider(LocalLighterEffects provides lighterEffects) {
            HomeScreen(HomeSamples.state, HomeActions(), initialFocus = HomeFocus.Rows, heroAutoAdvance = false)
        }
    }

    /** The picture behind the rows where nothing is drawn over it: between the top bar and the first row. */
    private fun behindTheRows(): List<Color> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return Across.map { x -> pixels[x, BETWEEN_BAR_AND_ROW] }
    }

    /** How far apart two looks at it are at their furthest, in steps of a color channel's 255. */
    private fun apart(a: List<Color>, b: List<Color>): Int = a.zip(b).maxOf { (one, other) ->
        (maxOf(abs(one.red - other.red), abs(one.green - other.green), abs(one.blue - other.blue)) * 255f).roundToInt()
    }

    /** Right, from Continue Watching's first item to its second, whose art is another picture. */
    private fun moveAlong() {
        compose.mainClock.autoAdvance = false
        press(Key.DirectionRight)
    }

    @Test fun withLighterEffectsThePictureBehindTheRowsWaitsForFocusToRest() {
        show(lighterEffects = true)
        val before = behindTheRows()
        moveAlong()
        compose.mainClock.advanceTimeBy(PlasmaMotion.BACKDROP_REST_MS - 100)
        assertEquals("The picture is as it was while focus hasn't rested", 0, apart(before, behindTheRows()))
        // Rested, and faded in.
        compose.mainClock.advanceTimeBy(100 + 400)
        assertTrue("The picture has followed focus", apart(before, behindTheRows()) > CLEARLY)
    }

    @Test fun withoutLighterEffectsItFollowsFocusAtOnce() {
        show(lighterEffects = false)
        val before = behindTheRows()
        moveAlong()
        compose.mainClock.advanceTimeBy(PlasmaMotion.BACKDROP_REST_MS - 100)
        assertTrue("The picture has followed focus", apart(before, behindTheRows()) > CLEARLY)
    }

    @Test fun aValueFollowsOnceItHasRested() {
        var value by mutableStateOf("first")
        var seen = ""
        screen { seen = rememberRested(REST) { value }.value }
        assertEquals("first", seen)
        compose.mainClock.autoAdvance = false

        // Two changes, the second before the first has rested: neither shows yet.
        value = "passed"
        compose.mainClock.advanceTimeBy(REST - 100)
        assertEquals("first", seen)
        value = "second"
        compose.mainClock.advanceTimeBy(REST - 100)
        assertEquals("first", seen)
        // The second has rested: it shows, and what was passed on the way never did.
        compose.mainClock.advanceTimeBy(200)
        assertEquals("second", seen)

        // Away and back before it rests: nothing changes.
        value = "passed"
        compose.mainClock.advanceTimeBy(REST - 100)
        value = "second"
        compose.mainClock.advanceTimeBy(REST * 2)
        assertEquals("second", seen)
    }

    private companion object {
        /** A row of points across the screen, clear of the logo and the tabs above and the row's title below. */
        const val BETWEEN_BAR_AND_ROW = 125
        val Across = listOf(300, 700, 1100, 1500)

        /** More than rounding: the two pictures are different ones. */
        const val CLEARLY = 3

        const val REST = 300L
    }
}
