// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** The screensaver's pictures take turns (brief 4.6). */
class ScreensaverTest {
    @Test fun eachPictureShowsForThreeMinutesInTurn() {
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 0), (0..12).map { pictureAt(it, 4) })
        assertEquals(3, pictureAt(23 * 60 + 59, 4))
    }

    @Test fun oneOrNoPictureStaysPut() {
        assertEquals(0, pictureAt(37, 1))
        assertEquals(0, pictureAt(37, 0))
    }
}
