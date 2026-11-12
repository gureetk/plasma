// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.ShownFocused
import ca.gureet.plasma.core.design.focusFree
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.FixtureImages
import ca.gureet.plasma.core.fixtures.fx
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A plate under a picture that covers it is painted along its edges only (#127), and nothing looks
 * different: the same tile drawn with its plate whole and then with the plate's edges alone, pixel
 * for pixel; and when a picture is taken to cover its place.
 */
class PictureCoverTest : ScreenshotTest() {
    private var covered by mutableStateOf(false)

    /**
     * A poster's tile at [LEFT], [TOP] on the screen's own color, with [content] in it. Focus is
     * kept out of it: it's drawn with focus's look when [focused], and plain otherwise.
     */
    private fun tile(focused: Boolean = false, content: @Composable () -> Unit) = screen {
        Box(Modifier.fillMaxSize().background(PlasmaColors.Bg)) {
            Box(Modifier.offset(LEFT.px, TOP.px).size(W.px, H.px).focusFree()) {
                val surface = @Composable {
                    PlasmaSurface(
                        onClick = {},
                        modifier = Modifier.fillMaxSize(),
                        style = FocusStyle.Tile,
                        shape = PlasmaShapes.Poster,
                        color = PlasmaColors.Surface2,
                        description = "A tile",
                        covered = { covered },
                    ) { content() }
                }
                if (focused) ShownFocused(surface) else surface()
            }
        }
    }

    /** The screen around the tile, far enough out to take in its focused size and its shadow. */
    private fun around(): List<Color> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return buildList {
            for (y in 0 until TOP + H + TOP) {
                for (x in 0 until LEFT + W + LEFT) add(pixels[x, y])
            }
        }
    }

    private fun assertTheEdgesAloneLookAsTheWholePlate() {
        covered = false
        compose.waitForIdle()
        val whole = around()
        covered = true
        compose.waitForIdle()
        val edges = around()
        val across = LEFT + W + LEFT
        for (i in whole.indices) {
            if (whole[i] != edges[i]) throw AssertionError("At ${i % across}, ${i / across} the whole plate gives ${whole[i]} and its edges alone ${edges[i]}")
        }
        assertTrue("The picture is drawn", whole.any { it != PlasmaColors.Bg && it != PlasmaColors.Surface2 })
    }

    @Test fun underAPosterThePlatesEdgesAloneLookAsTheWholePlate() {
        tile { PlasmaImage(POSTER, Modifier.fillMaxSize()) }
        assertTheEdgesAloneLookAsTheWholePlate()
    }

    @Test fun underAPosterWithFocusThePlatesEdgesAloneLookAsTheWholePlate() {
        // Grown by focus, the tile's edges fall between pixels: its outline cuts through them all the way round.
        tile(focused = true) { PlasmaImage(POSTER, Modifier.fillMaxSize()) }
        assertTheEdgesAloneLookAsTheWholePlate()
    }

    @Test fun onlyThePlatesEdgesArePaintedUnderACover() {
        // Nothing in the tile, so what's painted of the plate can be seen.
        tile { }
        covered = true
        compose.waitForIdle()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals("The middle isn't painted", PlasmaColors.Bg, pixels[LEFT + W / 2, TOP + H / 2])
        assertEquals("The top edge is", PlasmaColors.Surface2, pixels[LEFT + W / 2, TOP + 1])
        assertEquals("The left edge is", PlasmaColors.Surface2, pixels[LEFT + 1, TOP + H / 2])
        assertEquals("The bottom edge is", PlasmaColors.Surface2, pixels[LEFT + W / 2, TOP + H - 2])
        assertEquals("The right edge is", PlasmaColors.Surface2, pixels[LEFT + W - 2, TOP + H / 2])
        // As far in as the corners are rounded, 12 px, and two more.
        assertEquals(PlasmaColors.Surface2, pixels[LEFT + W / 2, TOP + 13])
        assertEquals(PlasmaColors.Bg, pixels[LEFT + W / 2, TOP + 15])
        covered = false
        compose.waitForIdle()
        assertEquals("Uncovered, all of it is painted", PlasmaColors.Surface2, compose.onRoot().captureToImage().toPixelMap()[LEFT + W / 2, TOP + H / 2])
    }

    /** Whether [PlasmaImage] takes a picture drawn this way to cover its place. */
    private fun covers(model: String?, contentScale: ContentScale = ContentScale.Crop, zoom: Float = 1f, darkened: Boolean = false): Boolean {
        val cover = PictureCover()
        screen {
            Box(Modifier.size(W.px, H.px)) {
                PlasmaImage(model, Modifier.fillMaxSize(), contentScale = contentScale, zoom = zoom, colorFilter = if (darkened) brightness(0.6f) else null, cover = cover)
            }
        }
        compose.waitForIdle()
        return cover.covers
    }

    @Test fun aPhotographThatFillsItsPlaceCoversIt() {
        assertTrue(covers(POSTER))
    }

    @Test fun aPhotographEnlargedInItsPlaceCoversIt() {
        assertTrue(covers(POSTER, zoom = 2f))
    }

    @Test fun aPictureWithClearPartsDoesNotCover() {
        assertFalse(covers(LOGO))
    }

    @Test fun noPictureDoesNotCover() {
        assertFalse(covers(null))
    }

    @Test fun aPictureFittedWithinItsPlaceDoesNotCover() {
        assertFalse(covers(POSTER, contentScale = ContentScale.Fit))
    }

    @Test fun aPictureDrawnThroughAFilterIsNotTakenToCover() {
        assertFalse(covers(POSTER, darkened = true))
    }

    /** Sample art from a resolver whose pictures fade in over [FADE_MS] from their first frame, as ones from the network do. */
    private object FadingIn : CoveringImageResolver {
        @Composable
        override fun painter(model: String): Painter? = FixtureImages.resolver.painter(model)

        @Composable
        override fun coversAfter(model: String, painter: Painter): Long? = FADE_MS
    }

    @Test fun aPictureThatFadesInCoversOnceItsFadeHasHadItsTime() {
        val cover = PictureCover()
        var model by mutableStateOf(POSTER)
        compose.mainClock.autoAdvance = false
        screen {
            CompositionLocalProvider(LocalImageResolver provides FadingIn) {
                Box(Modifier.size(W.px, H.px)) { PlasmaImage(model, Modifier.fillMaxSize(), cover = cover) }
            }
        }
        // Its fade's time runs from its first frame, however long after it came that is.
        compose.mainClock.advanceTimeBy(2 * FADE_MS)
        compose.waitForIdle()
        assertFalse("Not before it has been drawn", cover.covers)
        compose.onRoot().captureToImage()
        compose.mainClock.advanceTimeBy(FADE_MS / 2)
        compose.waitForIdle()
        assertFalse("Not part way through its fade", cover.covers)
        compose.mainClock.advanceTimeBy(FADE_MS)
        compose.waitForIdle()
        assertTrue("Once the fade has had its time", cover.covers)

        // Another picture in its place starts again.
        model = OTHER
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertFalse("Another picture doesn't cover as it comes", cover.covers)
        compose.onRoot().captureToImage()
        compose.mainClock.advanceTimeBy(2 * FADE_MS)
        compose.waitForIdle()
        assertTrue(cover.covers)
    }

    private companion object {
        /** A poster's size on Home (board 02). */
        const val W = 209
        const val H = 314

        /** Where the tile is, with room around it for its focused size and its shadow. */
        const val LEFT = 100
        const val TOP = 100

        const val FADE_MS = 300L

        /** Photographs: JPEGs, with no pixel that lets anything show through. */
        val POSTER = fx("poster_kite")
        val OTHER = fx("poster_ember")

        /** A logo: a PNG with clear parts. */
        val LOGO = fx("logo_arena1")
    }
}
