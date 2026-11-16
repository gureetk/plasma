// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.component.FadeBetween
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.fx
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The screensaver's pictures and the photos in Home's banner look as they did before their fades
 * and their blurred copies were kept (#127). On the left, what drew them then: `Crossfade` around
 * a [PlasmaImage], with a blur around the copy behind a narrow photo. On the right, what draws
 * them now: [FadeBetween] around [Picture]. The two are compared point by point, at rest and
 * through the fade from one picture to the next.
 *
 * A picture alone is the same to a step or two, which is rounding. A narrow photo's blurred copy
 * is blurred from a copy half the size each way, and is within a few steps of the whole one
 * blurred, as `BackdropsTest` (core/design) holds for every such blur: fewest away from its
 * edges, and a few more at them.
 */
class PictureTest : ScreenshotTest() {
    private var target by mutableIntStateOf(0)

    /** [old] at the top left and [new] beside it, each [W] × [H] px, on the screen's own color. */
    private fun sideBySide(old: @Composable () -> Unit, new: @Composable () -> Unit) = screen {
        Box(Modifier.fillMaxSize().background(PlasmaColors.Bg)) {
            Box(Modifier.size(W.px, H.px)) { old() }
            Box(Modifier.offset(x = APART.px).size(W.px, H.px)) { new() }
        }
    }

    /** A picture as the screensaver drew it, still: alone, or whole over a blurred, darkened copy of itself. */
    @Composable
    private fun AsItWas(model: String, whole: Boolean) {
        if (whole) {
            Box(Modifier.fillMaxSize()) {
                PlasmaImage(
                    model,
                    Modifier.fillMaxSize().blur(BLUR.px),
                    placeholder = PlasmaColors.Black,
                    colorFilter = ColorFilter.tint(PlasmaColors.black(0.5f), BlendMode.SrcAtop),
                )
                PlasmaImage(model, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, placeholder = PlasmaColors.black(0f))
            }
        } else {
            PlasmaImage(model, Modifier.fillMaxSize(), alignment = Alignment.Center, placeholder = PlasmaColors.Black)
        }
    }

    private fun pictures(models: List<String>, whole: Boolean) = sideBySide(
        old = {
            Crossfade(target, animationSpec = tween(FADE_MS), label = "old") { shown -> AsItWas(models[shown], whole) }
        },
        new = {
            FadeBetween(target, animationSpec = tween(FADE_MS), label = "new") { shown, alpha ->
                Picture(models[shown], Alignment.Center, pan = false, whole = whole, panMs = PAN_MS, alpha = alpha)
            }
        },
    )

    /**
     * How far apart the two sides are at their furthest, in steps of a color channel's 255, and
     * how far the left side is from the screen's own color, which says something is drawn at all.
     */
    private fun compare(inset: Int = 0): Pair<Int, Int> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var apart = 0
        var drawn = 0
        for (y in inset until H - inset step 7) {
            for (x in inset until W - inset step 7) {
                apart = maxOf(apart, steps(pixels[x, y], pixels[x + APART, y]))
                drawn = maxOf(drawn, steps(pixels[x, y], PlasmaColors.Bg))
            }
        }
        return apart to drawn
    }

    private fun steps(a: Color, b: Color): Int =
        (maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) * 255f).roundToInt()

    private fun assertTheSame(within: Int) {
        val (apart, drawn) = compare()
        assertTrue("Something is drawn ($drawn steps from the background)", drawn > 8)
        assertTrue("The two sides are $apart steps apart at most", apart <= within)
    }

    /** Within [INSIDE] steps away from the edges, as far in as the blur reaches, and within [AT_THE_EDGES] anywhere. */
    private fun assertTheBlurredCopyLooksTheSame() {
        val (apart, drawn) = compare(inset = BLUR_REACH)
        assertTrue("Something is drawn ($drawn steps from the background)", drawn > 8)
        assertTrue("Away from the edges the two sides are $apart steps apart at most", apart <= INSIDE)
        val (anywhere, _) = compare()
        assertTrue("At the edges the two sides are $anywhere steps apart at most", anywhere <= AT_THE_EDGES)
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

    /** The screensaver's own pictures, and a wide photo in Home's banner. */
    @Test fun aPictureFadesIntoTheNextAsItDid() {
        pictures(Wide, whole = false)
        assertTheSame(within = 1)
        starting { target = 1 }
        // The whole of the fade, and past it.
        repeat(7) {
            assertTheSame(within = 2)
            later(32)
        }
        settle()
        assertTheSame(within = 1)
    }

    /** One of the viewer's photos that's taller than wide (#170, #171). */
    @Test fun aNarrowPhotoOverItsBlurredCopyFadesIntoTheNextAsItDid() {
        pictures(Narrow, whole = true)
        assertTheBlurredCopyLooksTheSame()
        starting { target = 1 }
        repeat(7) {
            assertTheBlurredCopyLooksTheSame()
            later(32)
        }
        settle()
        assertTheBlurredCopyLooksTheSame()
    }

    private companion object {
        const val W = 600
        const val H = 400

        /** From one side's left edge to the other's. */
        const val APART = 700

        /** One picture's fade into the next: shorter than the screensaver's, and the same on both sides. */
        const val FADE_MS = 200

        /** A pan's length, for pictures that are kept still here. */
        const val PAN_MS = 20_000

        /** The blur behind a photo shown whole, as the screensaver has it. */
        const val BLUR = 60

        /** How far in from a picture's edges its blur carries them: twice the blur's sigma. */
        const val BLUR_REACH = 70

        /** How far, in steps of 255, a copy blurred at half the size may be from the whole one blurred, away from its edges. */
        const val INSIDE = 4

        /** And at its edges and corners, where the outermost pixels count for most. */
        const val AT_THE_EDGES = 6

        val Wide = listOf(fx("aurora2"), fx("city2"))
        val Narrow = listOf(fx("poster_lighthouse"), fx("poster_kite"))
    }
}
