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

    /** Down to Apps: the rows go up, so Apps sits where New in Movies was. No board draws it. */
    @Test fun homeRowsAtApps() {
        screen { HomeScreen(HomeSamples.state, HomeActions(), initialFocus = HomeFocus.Rows, heroAutoAdvance = false) }
        press(Key.DirectionDown, times = 2)
        compose.mainClock.advanceTimeBy(400)
        capture("02-home-rows-apps")
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

    /** A source's channels have no pictures: their logos, large, with what's on (the owner's #77). No board draws it. */
    @Test fun homeFavoritesWithoutPictures() {
        screen { HomeScreen(HomeSamples.withoutPictures, HomeActions(), initialFocus = HomeFocus.Favorites, heroAutoAdvance = false) }
        capture("01-home-favorites-logos")
    }

    /** Movies without a poster: their names on the plate, as on Movies (the owner's #77). No board draws it. */
    @Test fun homeRowsWithoutPosters() {
        screen { HomeScreen(HomeSamples.withoutPictures, HomeActions(), initialFocus = HomeFocus.Rows, heroAutoAdvance = false) }
        press(Key.DirectionDown)
        capture("02-home-rows-names")
    }

    /** Show TV Guide off: the dock's other five tiles share its width (the owner's #78). No board draws it. */
    @Test fun homeWithoutTheGuide() {
        screen { HomeScreen(HomeSamples.state.copy(showGuide = false), HomeActions(), initialFocus = HomeFocus.Dock, heroAutoAdvance = false) }
        capture("01-home-no-guide")
    }

    @Test fun screensaver() {
        screen { ScreensaverScreen(HomeSamples.screensaver, drift = false) }
        capture("06-screensaver")
    }
}
