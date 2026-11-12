// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * On a device, where a GPU draws: under what covers it, a tile's plate painted as a frame under
 * its layer comes out as the whole plate does, pixel for pixel, at rest and between pixels (#127).
 * The unit tests draw in software, which puts the two a few steps apart where a GPU doesn't
 * (`PictureCoverTest`), so this is where "nothing looks different" is checked. Run it on a 1080p
 * Android TV emulator or a TV: `./gradlew :core:design:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class CoveredPlateOnDeviceTest {
    @get:Rule val compose = createComposeRule()

    private var covered by mutableStateOf(false)
    private var nudge by mutableFloatStateOf(0f)
    private var picture by mutableStateOf(false)
    private var density = 0f

    /** An opaque picture the tile's size, every pixel a color of its own. */
    private val poster = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until H) {
            for (x in 0 until W) {
                setPixel(x, y, android.graphics.Color.rgb(40 + x * 200 / W, 40 + y * 200 / H, 40 + (x + y) * 200 / (W + H)))
            }
        }
    }.asImageBitmap()

    /**
     * A poster's tile at [LEFT], [TOP] on the screen's own color, with the picture in it or
     * nothing. Focus is kept out of it. [nudge] moves it by that part of a pixel each way, as a
     * screen sliding in does.
     */
    @Composable
    private fun Tile() {
        density = LocalDensity.current.density
        PlasmaTheme {
            Box(Modifier.fillMaxSize().background(PlasmaColors.Bg)) {
                Box(
                    Modifier
                        .graphicsLayer {
                            translationX = nudge
                            translationY = nudge
                        }
                        .offset(LEFT.px, TOP.px)
                        .size(W.px, H.px)
                        .focusFree(),
                ) {
                    PlasmaSurface(
                        onClick = {},
                        modifier = Modifier.fillMaxSize(),
                        style = FocusStyle.Tile,
                        shape = PlasmaShapes.Poster,
                        color = PlasmaColors.Surface2,
                        description = "A tile",
                        covered = { covered },
                    ) {
                        if (picture) Image(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    }
                }
            }
        }
    }

    private fun show() {
        compose.setContent { Tile() }
        compose.waitForIdle()
        // The tile sits on whole pixels at a 1080p TV's density, 2 px a dp.
        assumeTrue("A 1080p TV's density", density == 2f)
    }

    /** The screen around the tile, once a frame with the last change in it has been drawn. */
    private fun around(): PixelMap {
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        Thread.sleep(SETTLE_MS)
        compose.waitForIdle()
        return compose.onRoot().captureToImage().toPixelMap()
    }

    private fun steps(a: Color, b: Color): Int {
        val p = a.toArgb()
        val q = b.toArgb()
        return maxOf(
            abs((p shr 16 and 255) - (q shr 16 and 255)),
            abs((p shr 8 and 255) - (q shr 8 and 255)),
            abs((p and 255) - (q and 255)),
        )
    }

    /** How far inside the tile's rounded outline a pixel's middle is, the tile moved by [nudge]; negative outside it. */
    private fun insideBy(x: Int, y: Int, nudge: Float): Float {
        val px = x + 0.5f - (LEFT + nudge + W / 2f)
        val py = y + 0.5f - (TOP + nudge + H / 2f)
        val qx = abs(px) - (W / 2f - CORNER)
        val qy = abs(py) - (H / 2f - CORNER)
        return -(hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - CORNER)
    }

    /** Fails if [whole] and [frame] differ at any pixel that [where] takes in. */
    private fun assertTheSame(what: String, whole: PixelMap, frame: PixelMap, where: (Int, Int) -> Boolean = { _, _ -> true }) {
        var differing = 0
        var most = 0
        val some = StringBuilder()
        for (y in 0 until TOP + H + TOP) {
            for (x in 0 until LEFT + W + LEFT) {
                if (!where(x, y) || whole[x, y] == frame[x, y]) continue
                val by = steps(whole[x, y], frame[x, y])
                differing++
                most = max(most, by)
                if (differing <= SHOWN) some.append(" ($x, $y) by $by")
            }
        }
        if (differing > 0) throw AssertionError("$what: $differing pixels differ, by $most steps of 255 at most:$some")
    }

    /** The whole plate against its frame alone, the tile moved by [by] of a pixel: with nothing in it, then under a picture. */
    private fun assertTheFrameIsTheWholePlate(by: Float) {
        nudge = by

        // Nothing in the tile, so the plate itself is seen: the pixels along its outline, where
        // the frame is, must be the whole plate's.
        picture = false
        covered = false
        val whole = around()
        covered = true
        val frame = around()
        assertEquals("Uncovered, the plate is whole", PlasmaColors.Surface2, whole[LEFT + W / 2, TOP + H / 2])
        assertEquals("Covered, only its frame is painted", PlasmaColors.Bg, frame[LEFT + W / 2, TOP + H / 2])
        assertTheSame("Moved by $by px, the plate along its outline", whole, frame) { x, y -> insideBy(x, y, by) < ALONG_PX }

        // Under a picture: everything.
        picture = true
        covered = false
        val wholeUnder = around()
        covered = true
        val frameUnder = around()
        assertTheSame("Moved by $by px, under a picture", wholeUnder, frameUnder)
    }

    @Test fun atRestThePlatesFrameAloneIsTheWholePlate() {
        show()
        assertTheFrameIsTheWholePlate(by = 0f)
    }

    @Test fun betweenPixelsThePlatesFrameAloneIsTheWholePlate() {
        show()
        for (part in PARTS_OF_A_PIXEL) assertTheFrameIsTheWholePlate(by = part)
    }

    private companion object {
        /** A poster's size on Home (board 02), and where the tile is. */
        const val W = 209
        const val H = 314
        const val LEFT = 100
        const val TOP = 100

        /** How far a poster's corners are rounded ([PlasmaShapes.Poster]). */
        const val CORNER = 12f

        /** With nothing in the tile, pixels are compared this far in from its outline: where a frame is as whole as a plate. */
        const val ALONG_PX = 2f

        /** Long enough for a frame to be drawn and shown between a change and its picture. */
        const val SETTLE_MS = 150L

        /** How many of the pixels that differ a failure names. */
        const val SHOWN = 12

        /** Where a tile sits for the frames its screen slides in over. */
        val PARTS_OF_A_PIXEL = listOf(0.25f, 0.4f, 0.5f, 0.75f)
    }
}
