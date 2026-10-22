// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodLogicTest {
    private fun title(id: String, name: String, year: Int, rating: Float, added: Long) =
        VodTitle(id, name, null, VodCaption.Movie(year, "Drama", 100), year = year, rating = rating, added = added)

    private val titles = listOf(
        title("a", "The Last Lighthouse", 2025, 8.2f, 3),
        title("b", "Kite Season", 2024, 7.2f, 1),
        title("c", "Low Orbit", 2025, 7.9f, 2),
        title("d", "An Ember Road", 2026, 6.8f, 4),
    )

    @Test fun sortsNewestFirst() {
        assertEquals(listOf("d", "a", "c", "b"), sortTitles(titles, VodSort.Newest).map { it.id })
    }

    @Test fun sortsByNameWithoutLeadingArticles() {
        // Ember, Kite, Last, Low
        assertEquals(listOf("d", "b", "a", "c"), sortTitles(titles, VodSort.AZ).map { it.id })
    }

    @Test fun sortsByYearThenNewest() {
        assertEquals(listOf("d", "a", "c", "b"), sortTitles(titles, VodSort.Year).map { it.id })
    }

    @Test fun sortsByRating() {
        assertEquals(listOf("a", "c", "b", "d"), sortTitles(titles, VodSort.Rating).map { it.id })
    }

    private fun episodes(vararg states: String) = states.mapIndexed { i, state ->
        EpisodeItem(i + 1, "E${i + 1}", null, 50, watched = state == "w", leftMinutes = if (state == "p") 18 else null)
    }

    @Test fun upNextIsTheEpisodeInProgress() {
        val seasons = listOf(SeasonItem(1, episodes("w", "w")), SeasonItem(2, episodes("w", "p", "-")))
        assertEquals(EpisodeRef(2, 2), upNext(seasons))
    }

    @Test fun upNextFollowsTheLastWatchedEpisode() {
        // E2 was skipped; the next one is after the last watched, not the first unwatched.
        val seasons = listOf(SeasonItem(1, episodes("w", "-", "w", "-")), SeasonItem(2, episodes("-", "-")))
        assertEquals(EpisodeRef(1, 4), upNext(seasons))
        val finishedSeason = listOf(SeasonItem(1, episodes("w", "w")), SeasonItem(2, episodes("-", "-")))
        assertEquals(EpisodeRef(2, 1), upNext(finishedSeason))
    }

    @Test fun upNextStartsOverWhenEverythingIsWatched() {
        assertEquals(EpisodeRef(1, 1), upNext(listOf(SeasonItem(1, episodes("w", "w")))))
        assertEquals(EpisodeRef(1, 1), upNext(listOf(SeasonItem(1, episodes("-", "-")))))
        assertNull(upNext(emptyList()))
    }
}
