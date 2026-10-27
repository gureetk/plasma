// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The picture moves to the small window and back without the stream stopping (brief 5.20). */
class SurfacesTest {
    private val surfaces = Surfaces<String> { a, b -> a == b }

    @Test fun thePictureShowsOnTheNewestAndGoesBack() {
        surfaces.add("screen")
        surfaces.add("small window")
        assertEquals("small window", surfaces.top)

        // The small window closes: back to the screen.
        assertEquals("screen", surfaces.remove("small window")?.value)
        assertEquals("screen", surfaces.top)

        // The screen goes too: nothing's left to show on.
        val last = surfaces.remove("screen")
        assertEquals(null, last?.value)
        assertNull(surfaces.top)
    }

    @Test fun aSurfaceUnderneathGoingMovesNothing() {
        surfaces.add("screen")
        surfaces.add("small window")

        // The screen's surface is rebuilt while the small window shows.
        assertNull(surfaces.remove("screen"))
        assertEquals("small window", surfaces.top)
        assertNull("Never here", surfaces.remove("elsewhere"))

        // Arriving again, a surface goes on top.
        surfaces.add("screen")
        surfaces.add("small window")
        assertEquals("small window", surfaces.top)
        surfaces.add("screen")
        assertEquals("screen", surfaces.top)
    }
}
