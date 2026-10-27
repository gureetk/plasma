// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.ui.input.key.Key
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Home and the screensaver against boards 01, 02 and 06 (docs/design/screens). */
class HomeScreenshotTest : ScreenshotTest() {
    @Test fun home() {
        screen { HomeScreen(HomeSamples.state, HomeActions(), initialFocus = HomeFocus.Dock, heroAutoAdvance = false) }
        capture("01-home")
    }

    @Test fun homeRows() {
        screen { HomeScreen(HomeSamples.state, HomeActions(), initialFocus = HomeFocus.Rows, heroAutoAdvance = false) }
        capture("02-home-rows")
    }

    /** Board 04's move mode on Favorite Channels: Wildframe 4K carried one place along. */
    @Test fun homeMovingAFavorite() {
        screen { HomeScreen(HomeSamples.state, HomeActions(), initialFocus = HomeFocus.Favorites, heroAutoAdvance = false) }
        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        capture("01-home-move")
    }

    @Test fun screensaver() {
        screen { ScreensaverScreen(HomeSamples.screensaver, drift = false) }
        capture("06-screensaver")
    }
}
