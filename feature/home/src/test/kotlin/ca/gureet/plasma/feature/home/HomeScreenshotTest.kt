// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.ui.input.key.Key
import ca.gureet.plasma.core.model.HomeRow
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

    /**
     * Down to Apps: the rows go up, so Apps takes the second place, under New in Series. No board
     * draws it.
     */
    @Test fun homeRowsAtApps() {
        screen { HomeScreen(HomeSamples.state, HomeActions(), initialFocus = HomeFocus.Rows, heroAutoAdvance = false) }
        press(Key.DirectionDown, times = 3)
        compose.mainClock.advanceTimeBy(400)
        capture("02-home-rows-apps")
    }

    /**
     * Every row on, Upcoming Events and Recently Recorded first under Favorite Channels (the
     * owner's #80): board 02's place, focus on Upcoming Events. No board draws it.
     */
    @Test fun homeRowsUpcoming() {
        screen { HomeScreen(HomeSamples.allRows, HomeActions(), initialFocus = HomeFocus.Rows, heroAutoAdvance = false) }
        capture("02-home-rows-upcoming")
    }

    /** The same, focus on Recently Recorded. No board draws it. */
    @Test fun homeRowsRecorded() {
        screen { HomeScreen(HomeSamples.allRows, HomeActions(), initialFocus = HomeFocus.Rows, heroAutoAdvance = false) }
        press(Key.DirectionDown)
        compose.mainClock.advanceTimeBy(400)
        capture("02-home-rows-recorded")
    }

    /** Upcoming Events put first: it sits under the dock, where Favorite Channels does on board 01. No board draws it. */
    @Test fun homeUpcomingFirst() {
        val state = HomeSamples.allRows.copy(rows = listOf(HomeRow.UpcomingEvents, HomeRow.Favorites, HomeRow.ContinueWatching, HomeRow.NewMovies, HomeRow.Apps))
        screen { HomeScreen(state, HomeActions(), initialFocus = HomeFocus.Dock, heroAutoAdvance = false) }
        capture("01-home-upcoming-first")
    }

    /** Board 04's move mode on Favorite Channels: Wildframe 4K carried one place along. */
    @Test fun homeMovingAFavorite() {
        screen { HomeScreen(HomeSamples.state, HomeActions(), initialFocus = HomeFocus.FirstRow, heroAutoAdvance = false) }
        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        capture("01-home-move")
    }

    /** A source's channels have no pictures: their logos, large, with what's on (the owner's #77). No board draws it. */
    @Test fun homeFavoritesWithoutPictures() {
        screen { HomeScreen(HomeSamples.withoutPictures, HomeActions(), initialFocus = HomeFocus.FirstRow, heroAutoAdvance = false) }
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
