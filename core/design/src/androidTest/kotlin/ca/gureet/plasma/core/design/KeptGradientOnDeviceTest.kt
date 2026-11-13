// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.design.component.paintedOnce
import ca.gureet.plasma.core.design.component.paintedOnceAt
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max

/**
 * On a device, where a GPU draws: a wide gradient painted once into a kept layer comes out as the
 * gradient painted straight does, to within [ALLOWED] steps of 255 at any pixel, over the screen's
 * own color, over white and over a picture (#127). And no patch of the screen, [PATCH] px each
 * way, comes out a whole step lighter or darker on average. The gradients are Home's: the two
 * over its hero, the one over
 * its rows' backdrop and the band behind its top bar (boards 01 and 02). Each comparison logs what
 * it found under `KeptGradient`. Run it on a 1080p Android TV emulator or a TV:
 * `./gradlew :core:design:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class KeptGradientOnDeviceTest {
    @get:Rule val compose = createComposeRule()

    private enum class Under { Screen, White, Picture }

    private enum class Scene { Hero, Rows, Band }

    private var kept by mutableStateOf(false)
    private var under by mutableStateOf(Under.Screen)
    private var scene by mutableStateOf(Scene.Hero)
    private var bandAlpha by mutableFloatStateOf(1f)
    private var density = 0f

    /** An opaque picture, every pixel a color of its own, bright at one end and dark at the other. */
    private val picture = Bitmap.createBitmap(PICTURE_W, PICTURE_H, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until PICTURE_H) {
            for (x in 0 until PICTURE_W) {
                setPixel(x, y, android.graphics.Color.rgb(x * 255 / PICTURE_W, y * 255 / PICTURE_H, (x + y) * 255 / (PICTURE_W + PICTURE_H)))
            }
        }
    }.asImageBitmap()

    private val heroAcross = Brush.horizontalGradient(
        0f to PlasmaColors.bg(0.95f),
        0.24f to PlasmaColors.bg(0.82f),
        0.46f to PlasmaColors.bg(0.38f),
        0.64f to Color.Transparent,
    )
    private val heroDown = Brush.verticalGradient(
        0f to Color.Transparent,
        410f / 1080f to Color.Transparent,
        600f / 1080f to PlasmaColors.bg(0.9f),
        700f / 1080f to PlasmaColors.Bg,
    )
    private val band = Brush.verticalGradient(
        0f to PlasmaColors.bg(0.94f),
        120f / 270f to PlasmaColors.bg(0.6f),
        1f to Color.Transparent,
    )

    private fun rows(endY: Float) = Brush.verticalGradient(
        0f to PlasmaColors.bg(0.5f),
        300f / 1080f to PlasmaColors.bg(0.85f),
        ROWS_SOLID_PX / 1080f to PlasmaColors.Bg,
        startY = 0f,
        endY = endY,
    )

    /** One of Home's gradients over [under], painted straight or kept. */
    @Composable
    private fun Screen() {
        density = LocalDensity.current.density
        val screenHeight = with(LocalDensity.current) { 1080.px.toPx() }
        PlasmaTheme {
            Box(Modifier.fillMaxSize().background(PlasmaColors.Bg)) {
                when (under) {
                    Under.Screen -> Unit
                    Under.White -> Box(Modifier.fillMaxSize().background(Color.White))
                    Under.Picture -> Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
                when (scene) {
                    Scene.Hero -> if (kept) {
                        Box(Modifier.size(1920.px, 1080.px).paintedOnce().background(heroAcross).background(heroDown))
                    } else {
                        Box(Modifier.size(1920.px, 1080.px).background(heroAcross))
                        Box(Modifier.size(1920.px, 1080.px).background(heroDown))
                    }
                    Scene.Rows -> if (kept) {
                        Box(Modifier.fillMaxWidth().height(ROWS_SOLID_PX.px).paintedOnce().background(rows(screenHeight)))
                        Box(Modifier.fillMaxSize().padding(top = ROWS_SOLID_PX.px).background(PlasmaColors.Bg))
                    } else {
                        Box(Modifier.fillMaxSize().background(rows(Float.POSITIVE_INFINITY)))
                    }
                    Scene.Band -> if (kept) {
                        Box(Modifier.fillMaxWidth().height(270.px).paintedOnceAt { bandAlpha }.background(band))
                    } else {
                        Box(Modifier.fillMaxWidth().height(270.px).alpha(bandAlpha).background(band))
                    }
                }
            }
        }
    }

    private fun show() {
        compose.setContent { Screen() }
        compose.waitForIdle()
        // The gradients' stops fall on the pixels they do on a 1080p TV, 2 px a dp.
        assumeTrue("A 1080p TV's density", density == 2f)
    }

    /** The screen, once a frame with the last change in it has been drawn. */
    private fun drawn(): PixelMap {
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        Thread.sleep(SETTLE_MS)
        compose.waitForIdle()
        return compose.onRoot().captureToImage().toPixelMap()
    }

    /** How far each of [b]'s red, green and blue is above [a]'s, in steps of 255. */
    private fun above(a: Color, b: Color): IntArray {
        val p = a.toArgb()
        val q = b.toArgb()
        return intArrayOf((q shr 16 and 255) - (p shr 16 and 255), (q shr 8 and 255) - (p shr 8 and 255), (q and 255) - (p and 255))
    }

    /** Paints [scene] over [under] straight and then kept, and fails if any pixel is more than [ALLOWED] steps apart. */
    private fun assertKeptIsAsStraight(scene: Scene, under: Under, alpha: Float = 1f) {
        this.scene = scene
        this.under = under
        bandAlpha = alpha
        kept = false
        val straight = drawn()
        kept = true
        val layer = drawn()
        assumeTrue("A 1080p screen", straight.width == 1920 && straight.height == 1080)

        val apart = IntArray(256)
        var most = 0
        val some = StringBuilder()
        // Each patch's sum of how far the kept one is above the straight one, a color at a time.
        val across = (straight.width + PATCH - 1) / PATCH
        val patches = Array(3) { IntArray(across * ((straight.height + PATCH - 1) / PATCH)) }
        for (y in 0 until straight.height) {
            for (x in 0 until straight.width) {
                val over = above(straight[x, y], layer[x, y])
                val by = maxOf(abs(over[0]), abs(over[1]), abs(over[2]))
                apart[by]++
                if (by > ALLOWED && some.length < SHOWN) some.append(" ($x, $y) by $by")
                most = max(most, by)
                val patch = y / PATCH * across + x / PATCH
                for (c in 0..2) patches[c][patch] += over[c]
            }
        }
        // The edge's patches are whole ones at 1080p, which the patch's size divides.
        val patchMost = patches.maxOf { color -> color.maxOf { abs(it) } } / (PATCH * PATCH).toFloat()
        val what = "$scene over $under" + if (alpha < 1f) " at $alpha" else ""
        val found = (0..most).joinToString { "${apart[it]} by $it" } + "; a patch of $PATCH px by $patchMost at most"
        Log.i(TAG, "$what: $found")
        if (most > ALLOWED) throw AssertionError("$what: kept, it's up to $most steps of 255 from straight ($found):$some")
        if (patchMost >= WHOLE_PATCH) throw AssertionError("$what: kept, a patch of the screen is $patchMost of a step from straight ($found)")
    }

    @Test fun theHerosGradientsKeptAreAsPaintedStraight() {
        show()
        for (under in Under.entries) assertKeptIsAsStraight(Scene.Hero, under)
    }

    @Test fun theRowsGradientKeptIsAsPaintedStraight() {
        show()
        for (under in Under.entries) assertKeptIsAsStraight(Scene.Rows, under)
    }

    @Test fun theBandKeptIsAsPaintedStraightAlsoWhileItFades() {
        show()
        for (under in Under.entries) {
            for (alpha in BAND_ALPHAS) assertKeptIsAsStraight(Scene.Band, under, alpha)
        }
    }

    private companion object {
        const val TAG = "KeptGradient"

        /** How far apart a kept gradient's pixel may be from the straight one's: its color is rounded once more on the way. */
        const val ALLOWED = 2

        /** A patch of the screen, [PATCH] px each way, that differs by this on average is a step apart all over. */
        const val WHOLE_PATCH = 1f
        const val PATCH = 8

        /** Where the gradient over the rows' backdrop turns solid (board 02). */
        const val ROWS_SOLID_PX = 520

        const val PICTURE_W = 480
        const val PICTURE_H = 270

        /** Long enough for a frame to be drawn and shown between a change and its picture. */
        const val SETTLE_MS = 150L

        /** About how many characters of pixels a failure names. */
        const val SHOWN = 240

        /** The band whole, and partway through its fade with Home's top. */
        val BAND_ALPHAS = listOf(1f, 0.75f, 0.5f, 0.25f)
    }
}
