// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** D-pad focus on Movies, Series and the detail pages (brief 3.1, 4.13–4.16). */
class VodFocusTest : ScreenshotTest() {
    private fun focusedOn(description: String) = compose.onNodeWithContentDescription(description).assertIsFocused()

    @Test fun moviesGridTopBarAndCategories() {
        screen { VodBrowseScreen(VodSamples.movies, VodBrowseActions()) }
        focusedOn("The Last Lighthouse")

        press(Key.DirectionLeft)
        focusedOn("Neon Tide")

        // Up from the first row goes to the current tab, and Down comes back to the same poster.
        press(Key.DirectionUp)
        focused().assert(hasText("Movies"))
        press(Key.DirectionDown)
        focusedOn("Neon Tide")

        // Up from the last column goes to Sort, above it.
        press(Key.DirectionRight, times = 5)
        press(Key.DirectionUp)
        focused().assert(hasText("Newest first"))

        // Left from the grid's first column lands on the current category, not the nearest row.
        press(Key.DirectionDown)
        focusedOn("Saltwater Hymn")
        press(Key.DirectionLeft, times = 6)
        focused().assert(hasText("Recently Added"))
    }

    @Test fun categoryShowsWhenFocusRestsAndOkOpensIt() {
        screen { VodBrowseScreen(VodSamples.movies, VodBrowseActions()) }
        press(Key.DirectionLeft, times = 3)
        focused().assert(hasText("Recently Added"))

        press(Key.DirectionDown)
        focused().assert(hasText("Continue Watching"))
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Kite Season").assertExists()
        compose.onNodeWithContentDescription("Neon Tide").assertDoesNotExist()

        press(Key.DirectionCenter)
        focusedOn("The Last Lighthouse")
    }

    @Test fun yellowSortsAndKeepsFocusOnTheSameTitle() {
        screen { VodBrowseScreen(VodSamples.movies, VodBrowseActions()) }
        focusedOn("The Last Lighthouse")
        press(Key.ProgramYellow)
        compose.onNode(hasText("A–Z")).assertExists()
        focusedOn("The Last Lighthouse")
    }

    @Test fun movieDetailButtonsTabsAndRow() {
        screen { MovieDetailScreen(checkNotNull(VodSamples.movieDetail("lighthouse")), MovieDetailActions()) }
        focused().assert(hasText("Resume"))

        press(Key.DirectionRight)
        focused().assert(hasText("From the Beginning"))
        press(Key.DirectionDown)
        focused().assert(hasText("More Like This"))
        press(Key.DirectionDown)
        focusedOn("Saltwater Hymn")

        // Back up through the tabs to the button that had focus.
        press(Key.DirectionUp, times = 2)
        focused().assert(hasText("From the Beginning"))
        press(Key.DirectionUp)
        focused().assert(hasText("Movies"))
    }

    @Test fun seriesDetailSeasonsAndEpisodes() {
        screen { SeriesDetailScreen(checkNotNull(VodSamples.seriesDetail("atlas")), SeriesDetailActions()) }
        focused().assert(hasText("Resume S2, E4"))

        press(Key.DirectionDown)
        focused().assert(hasText("Season 2"))
        // The episode row opens on the current episode.
        press(Key.DirectionDown)
        focusedOn("4 · Dead Reckoning")
        press(Key.DirectionRight)
        focusedOn("5 · The Quiet Side")

        // Green marks the season watched: Resume moves on to season 3.
        press(Key.ProgramGreen)
        compose.onNode(hasText("Play S3, E1")).assertExists()
        compose.onNode(hasText("Mark Season as Unwatched")).assertExists()
    }
}
