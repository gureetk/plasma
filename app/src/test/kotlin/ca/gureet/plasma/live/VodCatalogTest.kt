// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.Movie
import ca.gureet.plasma.core.data.Series
import ca.gureet.plasma.feature.vod.VodCaption
import org.junit.Assert.assertEquals
import org.junit.Test
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
}
