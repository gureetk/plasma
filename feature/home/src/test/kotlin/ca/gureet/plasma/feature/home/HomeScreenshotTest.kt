// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Home against boards 01 and 02 (docs/design/screens). */
class HomeScreenshotTest : ScreenshotTest() {
    @Test fun home() {
        screen { HomeScreen(HomeSamples.state, HomeActions(), initialFocus = HomeFocus.Dock, heroAutoAdvance = false) }
        capture("01-home")
    }

    @Test fun homeRows() {
        screen { HomeScreen(HomeSamples.state, HomeActions(), initialFocus = HomeFocus.Rows, heroAutoAdvance = false) }
        capture("02-home-rows")
    }
}
