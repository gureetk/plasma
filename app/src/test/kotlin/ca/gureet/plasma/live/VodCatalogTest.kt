// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.VodVisibility
import ca.gureet.plasma.core.data.Movie
import ca.gureet.plasma.core.data.Progress
import ca.gureet.plasma.core.data.Series
import ca.gureet.plasma.core.data.VodCategory
import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.feature.vod.VodCaption
import ca.gureet.plasma.nav.MovieDetailRoute
import ca.gureet.plasma.nav.SeriesDetailRoute
import ca.gureet.plasma.nav.titleRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Movies and Series on a source's own catalog (brief 4.13, 4.15 and 5.9). */
class VodCatalogTest {
    private val names = mapOf("home:movie:30" to "Thrillers", "home:series:50" to "Drama")

    @Test fun aMoviesCaptionHasItsYearAndCategory() {
        val movie = Movie("home:7101", "Neon Tide", "https://img.example/neon.jpg", "home:movie:30", Instant.ofEpochMilli(5_000), 7.4f, 2026).title(names)

        assertEquals(VodCaption.Movie(year = 2026, genre = "Thrillers", minutes = null), movie.caption)
        assertEquals(2026, movie.year)
        assertEquals(7.4f, movie.rating)
        assertEquals(5_000L, movie.added)
    }

    @Test fun whatAMovieDoesntSayIsLeftOut() {
        val movie = Movie("home:7103", "Low Orbit", null, null, null, null, null).title(names)

        assertEquals(VodCaption.Movie(year = null, genre = null, minutes = null), movie.caption)
        assertEquals(0L, movie.added)
    }

    @Test fun aSeriesSaysItsOwnGenresOrElseItsCategory() {
        val atlas = Series("home:8101", "Atlas Station", null, null, "home:series:50", "Sci-Fi, Drama", 2024, 8.1f, Instant.ofEpochMilli(9_000)).title(names)
        val kingsway = Series("home:8102", "Kingsway", null, null, "home:series:50", null, null, null, null).title(names)

        assertEquals(VodCaption.Series(years = "2024", seasons = null, genre = "Sci-Fi, Drama"), atlas.caption)
        assertEquals(9_000L, atlas.added)
        assertEquals(VodCaption.Series(years = null, seasons = null, genre = "Drama"), kingsway.caption)
    }

    @Test fun theTabsOwnListsComeFirstWhenTheyHaveAnything() {
        val neon = Movie("home:7101", "Neon Tide", null, "home:movie:30", null, null, 2026).title(names)
        val categories = listOf(VodCategory("home:movie:30", "Thrillers", 2))

        val smartNames = SmartNames("Recently Added", "Continue Watching", "My List")

        val listed = catalogOf(categories, smartNames) { SmartLists(emptyList(), emptyList(), listOf(neon)) }
        assertEquals(listOf(CountedItem(MY_LIST, "My List", 1, smart = true), CountedItem("home:movie:30", "Thrillers", 2)), listed.categories)
        assertEquals(listOf(neon), listed.myList)

        // Recently Added, Continue Watching, My List, as on board 13.
        val all = catalogOf(categories, smartNames) { SmartLists(listOf(neon), listOf(neon), listOf(neon)) }
        assertEquals(listOf(RECENT, CONTINUING, MY_LIST, "home:movie:30"), all.categories.map { it.id })
        assertEquals(listOf(neon), all.continuing)
        assertEquals(listOf("Thrillers"), catalogOf(categories, smartNames) { SmartLists(emptyList(), emptyList(), emptyList()) }.categories.map { it.name })
    }

    @Test fun aLockedCategoryIsMarkedAndItsTitlesStayOut() {
        val seen = VodVisibility(hidden = setOf("home:movie:66"))
        val categories = listOf(VodCategory("home:movie:30", "Thrillers", 2), VodCategory("home:movie:66", "Adults", 9))

        val catalog = catalogOf(categories, SmartNames("Recently Added", "Continue Watching", "My List"), seen::isLocked) { SmartLists(emptyList(), emptyList(), emptyList()) }

        // Listed with its lock, but its titles show nowhere until it's opened with the PIN (5.12).
        assertEquals(listOf(false, true), catalog.categories.map { it.locked })
        assertTrue(seen.lists("home:movie:66"))
        assertFalse(seen.shows("home:movie:66"))
        assertTrue(seen.shows("home:movie:30"))
        // A kids profile lists its own categories only (5.11), and a title in none of them doesn't show.
        val kids = VodVisibility(shown = setOf("home:movie:40"))
        assertFalse(kids.lists("home:movie:30"))
        assertTrue(kids.shows("home:movie:40"))
        assertFalse(kids.shows(null))
        assertTrue(VodVisibility().shows(null))
    }

    @Test fun aPosterShowsWhatTheProfileDidWithIt() {
        val neon = Movie("home:7101", "Neon Tide", null, null, null, null, null).title(names)

        val started = neon.marked(TitleMarks(listed = setOf("home:7101"), progress = mapOf("home:7101" to Progress(Duration.ofMinutes(30), Duration.ofMinutes(120), watched = false))))
        assertTrue(started.inMyList)
        assertEquals(0.25f, started.progress)
        assertFalse(started.watched)

        val watched = neon.marked(TitleMarks(watched = setOf("home:7101")))
        assertTrue(watched.watched)
        assertNull(watched.progress)
        assertFalse(watched.inMyList)
    }

    @Test fun aTitleFoundBySearchOpensItsOwnPage() {
        val movie = Movie("home:7101", "Neon Tide", null, null, null, null, null).searchResult()
        val series = Series("home:7101", "Atlas Station", null, null, null, null, null, null, null).searchResult()

        assertEquals(MovieDetailRoute("home:7101"), titleRoute(movie))
        assertEquals(SeriesDetailRoute("home:7101"), titleRoute(series))
        // A movie and a series may share a number on a source; their results don't share an id.
        assertNotEquals(movie.id, series.id)
    }
}
