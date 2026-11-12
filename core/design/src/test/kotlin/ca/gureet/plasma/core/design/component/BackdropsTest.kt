// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.FixtureImages
import ca.gureet.plasma.core.fixtures.fx
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pictures behind a screen look as they did before they were kept as layers (#127): on the left
 * what drew them then (`Crossfade`, a blur around a [PlasmaImage]), on the right what draws them
 * now ([FadeBetween], [BlurredImage]), compared point by point.
 */
class BackdropsTest : ScreenshotTest() {
    private var target by mutableIntStateOf(0)
    private var arrived by mutableFloatStateOf(1f)

    /** [old] at the top left and [new] beside it, each [W] × [H] px, on the screen's own color. */
    private fun sideBySide(old: @Composable () -> Unit, new: @Composable () -> Unit) = screen {
        Box(Modifier.fillMaxSize().background(PlasmaColors.Bg)) {
            Box(Modifier.size(W.px, H.px)) { old() }
            Box(Modifier.offset(x = APART.px).size(W.px, H.px)) { new() }
        }
    }

    /**
     * How far apart the two sides are at their furthest, in steps of a color channel's 255, and
     * how far the left side is from the screen's own color, which says something is drawn at all.
     */
    private fun compare(): Pair<Int, Int> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var apart = 0
        var drawn = 0
        for (y in 0 until H step 9) {
            for (x in 0 until W step 9) {
                apart = maxOf(apart, steps(pixels[x, y], pixels[x + APART, y]))
                drawn = maxOf(drawn, steps(pixels[x, y], PlasmaColors.Bg))
            }
        }
        return apart to drawn
    }

    private fun steps(a: Color, b: Color): Int =
        (maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) * 255f).roundToInt()

    private fun assertTheSame(within: Int = 1) {
        val (apart, drawn) = compare()
        assertTrue("Something is drawn ($drawn steps from the background)", drawn > 8)
        assertTrue("The two sides are $apart steps apart at most", apart <= within)
    }

    /** Stops the clock and makes [change]: [later] then lets time go by, part way through what it set off. */
    private fun starting(change: () -> Unit) {
        compose.mainClock.autoAdvance = false
        change()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun later(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    private fun settle() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    /** Squares of three colors, one fading into the next. */
    private fun squares() = sideBySide(
        old = {
            Crossfade(target, animationSpec = PlasmaMotion.screen(), label = "old") { shown ->
                Box(Modifier.size(W.px, H.px).background(Squares[shown]).testTag("old"))
            }
        },
        new = {
            FadeBetween(target, animationSpec = PlasmaMotion.screen(), label = "new") { shown, alpha ->
                Box(
                    Modifier
                        .size(W.px, H.px)
                        .graphicsLayer {
                            this.alpha = alpha.value
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .background(Squares[shown])
                        .testTag("new"),
                )
            }
        },
    )

    @Test fun oneFadesIntoTheNextAsCrossfadeDoes() {
        squares()
        assertTheSame()
        starting { target = 1 }
        // The whole of the 200 ms, and past it.
        repeat(7) {
            assertTheSame()
            later(32)
        }
        settle()
        assertTheSame()
    }

    @Test fun aFadeCutShortGoesOnAsCrossfadeDoes() {
        squares()
        starting { target = 1 }
        later(64)
        // To a third before the second is in: all three show.
        target = 2
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(3, compose.onAllNodesWithTag("new").fetchSemanticsNodes().size)
        repeat(4) {
            assertTheSame()
            later(32)
        }
        // And back to the second, part way.
        target = 1
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        repeat(8) {
            assertTheSame()
            later(32)
        }
        settle()
        assertTheSame()
    }

    @Test fun onlyTheTargetIsLeftOnceItIsIn() {
        squares()
        starting { target = 1 }
        later(64)
        assertEquals(2, compose.onAllNodesWithTag("new").fetchSemanticsNodes().size)
        assertEquals(2, compose.onAllNodesWithTag("old").fetchSemanticsNodes().size)
        settle()
        assertEquals(1, compose.onAllNodesWithTag("new").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithTag("old").fetchSemanticsNodes().size)
    }

    @Test fun aKeptBlurLooksAsTheBlurDid() {
        sideBySide(
            old = { PlasmaImage(Pictures[0], Modifier.fillMaxSize().blur(BLUR.px), colorFilter = brightness(0.6f)) },
            new = { BlurredImage(Pictures[0], BLUR.px, Modifier.fillMaxSize(), colorFilter = brightness(0.6f)) },
        )
        assertTheSame()
    }

    /** Home's rows: 45 % of the picture, under the blur, with nothing where there's no picture. */
    @Test fun aKeptBlurOfADimmedPictureLooksAsItDid() {
        sideBySide(
            old = { PlasmaImage(Pictures[0], Modifier.fillMaxSize().blur(BLUR.px).alpha(0.45f), placeholder = Color.Transparent) },
            new = { BlurredImage(Pictures[0], BLUR.px, Modifier.fillMaxSize(), placeholder = Color.Transparent, opacity = 0.45f) },
        )
        assertTheSame()
    }

    @Test fun withoutAPictureTheBlurredPlaceholderLooksAsItDid() {
        sideBySide(
            old = { PlasmaImage(null, Modifier.fillMaxSize().blur(BLUR.px), placeholder = PlasmaColors.Surface3) },
            new = { BlurredImage(null, BLUR.px, Modifier.fillMaxSize(), placeholder = PlasmaColors.Surface3) },
        )
        assertTheSame()
    }

    @Test fun withoutAPictureAClearPlaceholderDrawsNothing() {
        sideBySide(
            old = { PlasmaImage(null, Modifier.fillMaxSize().blur(BLUR.px).alpha(0.45f), placeholder = Color.Transparent) },
            new = { BlurredImage(null, BLUR.px, Modifier.fillMaxSize(), placeholder = Color.Transparent, opacity = 0.45f) },
        )
        val (apart, drawn) = compare()
        assertEquals(0, apart)
        assertEquals(0, drawn)
    }

    @Test fun oneBlurredPictureFadesIntoTheNextAsItDid() {
        sideBySide(
            old = {
                Crossfade(target, animationSpec = PlasmaMotion.screen(), label = "old") { shown ->
                    PlasmaImage(Pictures[shown], Modifier.fillMaxSize().blur(BLUR.px).alpha(0.45f), placeholder = Color.Transparent)
                }
            },
            new = {
                FadeBetween(target, animationSpec = PlasmaMotion.screen(), label = "new") { shown, alpha ->
                    BlurredImage(Pictures[shown], BLUR.px, Modifier.fillMaxSize(), placeholder = Color.Transparent, opacity = 0.45f, alpha = alpha)
                }
            },
        )
        starting { target = 1 }
        repeat(7) {
            assertTheSame(within = 2)
            later(32)
        }
        settle()
        assertTheSame()
    }

    /**
     * A picture coming in: faded under the blur, as a painter that fades itself draws it, and
     * faded on the kept layer, where the resolver leaves the fade to [BlurredImage].
     */
    @Test fun aPictureComingInFadesOnItsKeptLayerAsItDidUnderTheBlur() {
        val shown = object : State<Float> {
            override val value: Float get() = arrived
        }
        val fadingItself = ImageResolver { model -> FixtureImages.resolver.painter(model)?.let { painter -> remember(painter) { FadesItself(painter, shown) } } }
        val leavingItsFade = object : ArrivingImageResolver {
            @Composable
            override fun painter(model: String): Painter? = FixtureImages.resolver.painter(model)

            @Composable
            override fun arriving(model: String): ArrivingPainter? = painter(model)?.let { painter -> remember(painter) { ArrivingPainter(painter, shown) } }
        }
        sideBySide(
            old = {
                CompositionLocalProvider(LocalImageResolver provides fadingItself) {
                    PlasmaImage(Pictures[0], Modifier.fillMaxSize().blur(BLUR.px), colorFilter = brightness(0.6f))
                }
            },
            new = {
                CompositionLocalProvider(LocalImageResolver provides leavingItsFade) {
                    BlurredImage(Pictures[0], BLUR.px, Modifier.fillMaxSize(), colorFilter = brightness(0.6f))
                }
            },
        )
        for (step in listOf(0.25f, 0.5f, 0.75f, 1f)) {
            arrived = step
            compose.waitForIdle()
            assertTheSame(within = 2)
        }
        // Not there yet: nothing of it shows on either side.
        arrived = 0f
        compose.waitForIdle()
        val (apart, drawn) = compare()
        assertEquals(0, apart)
        assertEquals(0, drawn)
        // And in from nothing, as a picture arrives.
        arrived = 0.5f
        compose.waitForIdle()
        assertTheSame(within = 2)
    }

    /** A painter that draws its own fade, as one from the network does while its picture arrives. */
    private class FadesItself(private val picture: Painter, private val shown: State<Float>) : Painter() {
        override val intrinsicSize: Size get() = picture.intrinsicSize

        private var colorFilter: ColorFilter? = null

        override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
            this.colorFilter = colorFilter
            return true
        }

        override fun DrawScope.onDraw() {
            val alpha = shown.value
            if (alpha > 0f) with(picture) { draw(size, alpha, colorFilter) }
        }
    }

    private companion object {
        const val W = 600
        const val H = 400

        /** From one side's left edge to the other's. */
        const val APART = 700

        /** Home's blur, CSS `blur(60px)`. */
        const val BLUR = 104

        val Squares = listOf(PlasmaColors.Text, PlasmaColors.Live, PlasmaColors.Ok)
        val Pictures = listOf(fx("aurora2"), fx("city2"), fx("lighthouse3"))
    }
}
