// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import android.graphics.Bitmap
import android.util.Log
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * On a device, what its GPU draws of a covered tile's plate (#127): the whole plate against the
 * frame under the tile's layer, and against the earlier form, a stroked rectangle inside the
 * tile's clip; with nothing in the tile and under a picture, at rest and between pixels. It logs
 * what differs. For this check's branch only.
 */
@RunWith(AndroidJUnit4::class)
class PlateOnGpuCheck {
    @get:Rule val compose = createComposeRule()

    private enum class Way { Surface, StrokedRectInClip }

    private var way by mutableStateOf(Way.Surface)
    private var covered by mutableStateOf(false)
    private var nudge by mutableFloatStateOf(0f)
    private var picture by mutableStateOf(false)

    /** No plate at all under the picture: how much of a plate shows at a tile's edge, for scale. */
    private var noPlate by mutableStateOf(false)
    private var density = 0f

    /** An opaque picture the tile's size, every pixel its own color. */
    private val poster = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until H) {
            for (x in 0 until W) {
                setPixel(x, y, android.graphics.Color.rgb(40 + x * 200 / W, 40 + y * 200 / H, 40 + (x + y) * 200 / (W + H)))
            }
        }
    }.asImageBitmap()

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
                    val inside = @Composable {
                        if (picture) Image(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    }
                    when (way) {
                        Way.Surface -> PlasmaSurface(
                            onClick = {},
                            modifier = Modifier.fillMaxSize(),
                            style = FocusStyle.Tile,
                            shape = PlasmaShapes.Poster,
                            color = PlasmaColors.Surface2,
                            description = "A tile",
                            covered = { covered },
                        ) { inside() }
                        // The earlier form: the plate's edges as one stroked rectangle, inside the rounded clip.
                        Way.StrokedRectInClip -> Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    shape = PlasmaShapes.Poster
                                    clip = true
                                }
                                .drawWithContent {
                                    val fill = PlasmaColors.Surface2
                                    when {
                                        noPlate -> Unit
                                        covered -> drawRect(fill, Offset(EDGE / 2f, EDGE / 2f), Size(size.width - EDGE, size.height - EDGE), style = Stroke(EDGE))
                                        else -> drawRect(fill)
                                    }
                                    drawContent()
                                },
                        ) { inside() }
                    }
                }
            }
        }
    }

    private fun shot(): PixelMap {
        compose.waitForIdle()
        // Twice: the first makes sure a frame with the change has been drawn.
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

    private fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)

    /** How far inside the tile's rounded outline a pixel's middle is, the tile moved by [nudge]; negative outside it. */
    private fun insideBy(x: Int, y: Int, nudge: Float): Float {
        val px = x + 0.5f - (LEFT + nudge + W / 2f)
        val py = y + 0.5f - (TOP + nudge + H / 2f)
        val qx = abs(px) - (W / 2f - CORNER)
        val qy = abs(py) - (H / 2f - CORNER)
        return -(hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - CORNER)
    }

    private fun compare(name: String, a: PixelMap, b: PixelMap, where: (Int, Int) -> Boolean = { _, _ -> true }) {
        var differing = 0
        var most = 0
        val bySteps = sortedMapOf<Int, Int>()
        val some = StringBuilder()
        for (y in 0 until TOP + H + TOP) {
            for (x in 0 until LEFT + W + LEFT) {
                if (!where(x, y) || a[x, y] == b[x, y]) continue
                val by = steps(a[x, y], b[x, y])
                differing++
                most = max(most, by)
                bySteps[by] = (bySteps[by] ?: 0) + 1
                if (differing <= SHOWN) some.append(" ($x,$y) ${hex(a[x, y])}>${hex(b[x, y])}")
            }
        }
        Log.i(TAG, "$name: $differing pixels differ, by $most steps at most; pixels by steps $bySteps;$some")
    }

    @Test fun thePlateAsTheGpuDrawsIt() {
        compose.setContent { Tile() }
        compose.waitForIdle()
        val first = compose.onRoot().captureToImage()
        Log.i(TAG, "density $density, the screen ${first.width} x ${first.height}")
        for (n in NUDGES) {
            nudge = n
            val along: (Int, Int) -> Boolean = { x, y -> insideBy(x, y, n) < ALONG_PX }

            // Nothing in the tile: the plate's own pixels along the outline.
            picture = false
            way = Way.Surface
            covered = false
            val whole = shot()
            covered = true
            val frame = shot()
            assertEquals("Uncovered, the plate is whole", PlasmaColors.Surface2, whole[LEFT + W / 2, TOP + H / 2])
            assertEquals("Covered, only its frame is painted", PlasmaColors.Bg, frame[LEFT + W / 2, TOP + H / 2])
            way = Way.StrokedRectInClip
            covered = false
            val wholePlain = shot()
            covered = true
            val strokedRect = shot()
            assertEquals(PlasmaColors.Bg, strokedRect[LEFT + W / 2, TOP + H / 2])
            compare("nudge $n, nothing in it, all of it: the surface's whole plate vs a plain clipped layer's", whole, wholePlain)
            compare("nudge $n, nothing in it, along the outline: whole plate vs the frame under the layer", whole, frame, along)
            compare("nudge $n, nothing in it, along the outline: whole plate vs a stroked rectangle in the clip", whole, strokedRect, along)
            val samples = listOf(
                "top edge" to (LEFT + W / 2 to TOP),
                "left edge" to (LEFT to TOP + H / 2),
                "bottom edge" to (LEFT + W / 2 to TOP + H - 1),
                "below the bottom edge" to (LEFT + W / 2 to TOP + H),
                "right edge" to (LEFT + W - 1 to TOP + H / 2),
                "past the right edge" to (LEFT + W to TOP + H / 2),
                "corner" to (LEFT + 3 to TOP + 3),
                "corner" to (LEFT + 1 to TOP + 5),
                "corner" to (LEFT + 6 to TOP + 1),
            )
            for ((what, at) in samples) {
                val (x, y) = at
                Log.i(TAG, "nudge $n, nothing in it, $what ($x,$y): whole ${hex(whole[x, y])}, frame ${hex(frame[x, y])}, stroked rectangle ${hex(strokedRect[x, y])}")
            }

            // Under a picture: everything.
            picture = true
            way = Way.Surface
            covered = false
            val wholeUnder = shot()
            covered = true
            val frameUnder = shot()
            way = Way.StrokedRectInClip
            covered = false
            val wholePlainUnder = shot()
            covered = true
            val strokedRectUnder = shot()
            compare("nudge $n, under a picture: the surface's whole plate vs a plain clipped layer's", wholeUnder, wholePlainUnder)
            compare("nudge $n, under a picture: whole plate vs the frame under the layer", wholeUnder, frameUnder)
            compare("nudge $n, under a picture: whole plate vs a stroked rectangle in the clip", wholeUnder, strokedRectUnder)
            noPlate = true
            val pictureAlone = shot()
            noPlate = false
            compare("nudge $n, under a picture: whole plate vs no plate at all, for scale", wholeUnder, pictureAlone)
        }
    }

    private companion object {
        const val TAG = "PlateCheck"

        /** A poster's size on Home (board 02), and where the tile is. */
        const val W = 209
        const val H = 314
        const val LEFT = 100
        const val TOP = 100

        /** How far a poster's corners are rounded, and how wide the earlier form's frame was for it. */
        const val CORNER = 12f
        const val EDGE = 14f

        /** With nothing in the tile, pixels are compared this far in from its outline: where a frame is whole. */
        const val ALONG_PX = 2f

        const val SETTLE_MS = 150L
        const val SHOWN = 8

        /** At rest, and moved by parts of a pixel, as a sliding screen is. */
        val NUDGES = listOf(0f, 0.25f, 0.4f, 0.5f, 0.75f)
    }
}
