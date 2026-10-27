// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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

    @Test fun menuOnAPosterAddsToMyList() {
        var added: Pair<String, Boolean>? = null
        screen { VodBrowseScreen(VodSamples.movies, VodBrowseActions(onMyList = { id, inList -> added = id to inList })) }
        press(Key.Menu)
        focused().assert(hasText("Add to My List"))
        press(Key.DirectionCenter)
        check(added == ("lighthouse" to true)) { "Got $added" }
        // The sheet closes and focus goes back to the poster; the sheet now offers to take it off.
        focusedOn("The Last Lighthouse")
        press(Key.Menu)
        focused().assert(hasText("Remove from My List"))
        press(Key.Back)
        focusedOn("The Last Lighthouse")
    }

    @Test fun aCategoryLoadsAsItShowsAndOkWaitsForIt() {
        val asked = mutableListOf<String>()
        val base = VodSamples.movies
        var state by mutableStateOf(base.copy(titles = base.titles.filterKeys { it == "recent" }))
        screen { VodBrowseScreen(state, VodBrowseActions(onCategory = { asked += it })) }
        press(Key.DirectionLeft, times = 3)
        press(Key.DirectionDown)
        focused().assert(hasText("Continue Watching"))
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        check(asked == listOf("recent", "continue")) { "Asked for $asked" }

        // Still loading: no posters, and no word that there are none.
        compose.onNodeWithText("No movies in this category yet.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Neon Tide").assertDoesNotExist()
        press(Key.DirectionCenter)
        state = base.copy(titles = base.titles.filterKeys { it == "recent" || it == "continue" })
        compose.waitForIdle()
        focusedOn("The Last Lighthouse")
    }

    @Test fun withNoCatalogTheTabSaysSo() {
        screen { VodBrowseScreen(VodSamples.movies.copy(categories = emptyList(), titles = emptyMap()), VodBrowseActions()) }
        focused().assert(hasText("Movies"))
        compose.onNodeWithText("No movies yet. Your sources’ movies show here once they’ve loaded.").assertExists()
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

    @Test fun menuOnAnEpisodeMarksItWatched() {
        var marked: Pair<EpisodeRef, Boolean>? = null
        var played: Pair<EpisodeRef, Boolean>? = null
        screen {
            SeriesDetailScreen(
                checkNotNull(VodSamples.seriesDetail("atlas")),
                SeriesDetailActions(onEpisodeWatched = { ref, watched -> marked = ref to watched }, onPlay = { ref, fromStart -> played = ref to fromStart }),
            )
        }
        press(Key.DirectionDown, times = 2)
        focusedOn("4 · Dead Reckoning")
        press(Key.Menu)
        // It's been started: the sheet offers its start first.
        focused().assert(hasText("From the Beginning"))
        press(Key.DirectionDown)
        focused().assert(hasText("Mark as Watched"))
        press(Key.DirectionCenter)
        check(marked == (EpisodeRef(2, 4) to true)) { "Marked $marked" }
        focusedOn("4 · Dead Reckoning")
        compose.onNode(hasText("Play S2, E5")).assertExists()

        // Watched now, it offers to be unwatched; OK plays it.
        press(Key.Menu)
        focused().assert(hasText("Mark as Unwatched"))
        press(Key.Back)
        press(Key.DirectionCenter)
        check(played == (EpisodeRef(2, 4) to false)) { "Played $played" }
    }

    @Test fun aSeriesWaitingForItsEpisodesSaysSoAndResumeTakesFocusWhenTheyCome() {
        val atlas = checkNotNull(VodSamples.seriesDetail("atlas"))
        var state by mutableStateOf(atlas.copy(seasons = emptyList(), episodes = EpisodesStatus.Loading))
        screen { SeriesDetailScreen(state, SeriesDetailActions()) }
        compose.onNodeWithText("Loading episodes…").assertExists()
        focused().assert(hasText("Trailer"))

        state = atlas
        compose.waitForIdle()
        focused().assert(hasText("Resume S2, E4"))
        compose.onNodeWithText("Loading episodes…").assertDoesNotExist()
    }

    @Test fun episodesThatCouldntLoadSaySo() {
        val atlas = checkNotNull(VodSamples.seriesDetail("atlas"))
        screen { SeriesDetailScreen(atlas.copy(seasons = emptyList(), episodes = EpisodesStatus.Failed, hasTrailer = false), SeriesDetailActions()) }
        compose.onNodeWithText("Can’t load the episodes. Check the connection, then open the series again.").assertExists()
        focusedOn("Add to My List")
    }

    @Test fun specialsAreNamedAndEpisodesWithoutTitlesAreNumbered() {
        val atlas = checkNotNull(VodSamples.seriesDetail("atlas"))
        val specials = SeasonItem(0, listOf(EpisodeItem(1, null, null, minutes = null)))
        screen { SeriesDetailScreen(atlas.copy(seasons = atlas.seasons + specials), SeriesDetailActions()) }
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 2)
        focused().assert(hasText("Specials"))
        press(Key.DirectionDown)
        focusedOn("Episode 1")
        // Specials aren't a season.
        compose.onNodeWithText("3 Seasons").assertExists()
    }
}
