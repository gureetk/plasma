// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the picture goes on the screen for each Picture Size (brief 4.10 and 5.4.4). */
class PictureSizeTest {
    private val screen = Rect(0f, 0f, 1920f, 1080f)
    private val standard = 4f / 3f

    @Test fun fitShowsAllOfAStandardPictureWithBarsAtTheSides() {
        assertEquals(Rect(240f, 0f, 1680f, 1080f), place(screen, standard, PictureSize.Fit))
        // A wide picture fills a wide screen.
        assertEquals(screen, place(screen, 16f / 9f, PictureSize.Fit))
    }

    @Test fun fillStretchesItToTheScreen() {
        assertEquals(screen, place(screen, standard, PictureSize.Fill))
    }

    @Test fun zoomFillsTheScreenAndCutsTheTopAndBottom() {
        assertEquals(Rect(0f, -180f, 1920f, 1260f), place(screen, standard, PictureSize.Zoom))
        // A film wider than the screen loses its sides instead.
        val cinema = place(screen, 2.4f, PictureSize.Zoom)
        assertEquals(1080f, cinema.height)
        assertEquals(1080f * 2.4f, cinema.width, 0.5f)
        assertEquals(960f, cinema.center.x, 0.5f)
    }

    @Test fun wideAndStandardSetTheShapeWhateverTheStreamSays() {
        assertEquals(screen, place(screen, standard, PictureSize.Wide))
        assertEquals(Rect(240f, 0f, 1680f, 1080f), place(screen, 16f / 9f, PictureSize.Standard))
    }

    @Test fun anUnknownShapeFillsTheSlot() {
        assertEquals(screen, place(screen, null, PictureSize.Fit))
        assertEquals(screen, place(screen, null, PictureSize.Zoom))
    }
}
