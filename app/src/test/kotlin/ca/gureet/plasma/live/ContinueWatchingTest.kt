// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.ContinueEntry
import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.core.data.Progress
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.feature.home.ContinueItem
import ca.gureet.plasma.feature.home.ContinueKind
import ca.gureet.plasma.feature.vod.VodCaption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Continue Watching on Home and on Movies and Series (brief 4.2, 4.13, 4.15 and 5.9). */
class ContinueWatchingTest {
    private val words = ContinueWords(left = { "$it left" }, episode = { season, number -> "S$season, E$number" }, upNext = "Up next")
    private val at = Instant.parse("2026-10-21T20:41:00Z")

    private val neon = ContinueEntry(
        playable = Playable(Playable.Kind.Movie, "home:7101", "home", "7101", "mkv", "Neon Tide", year = 2026, genres = listOf("Thriller"), duration = Duration.ofMinutes(118)),
        wide = "https://img.example/neon-wide.jpg",
        poster = "https://img.example/neon.jpg",
        progress = Progress(Duration.ofMinutes(56), Duration.ofMinutes(118), watched = false),
        updated = at,
    )
    private val reckoning = ContinueEntry(
        playable = Playable(Playable.Kind.Episode, "home:9104", "home", "9104", "mkv", "Atlas Station", seriesId = "home:8101", season = 2, number = 4, episodeTitle = "Dead Reckoning"),
        wide = null,
        poster = "https://img.example/atlas.jpg",
        progress = Progress(Duration.ofMinutes(40), Duration.ofMinutes(58), watched = false),
        updated = at.minusSeconds(60),
    )
    private val quietSide = reckoning.copy(
        playable = reckoning.playable.copy(id = "home:9105", number = 5, episodeTitle = "The Quiet Side"),
        wide = "https://img.example/s2e5.jpg",
        progress = null,
    )

    @Test fun homesRowSaysWhatsLeftOrWhatsNext() {
        val items = continueItems(listOf(neon, reckoning, quietSide), words)

        assertEquals(
            ContinueItem("vod:movie:home:7101", "Neon Tide", "1 h 02 min left", "https://img.example/neon-wide.jpg", ContinueKind.Art, 56f / 118f),
            items[0],
        )
        // Only a poster: Home shows it sharp over a blurred enlargement.
        assertEquals(ContinueKind.Poster, items[1].kind)
        assertEquals("S2, E4 · 18 min left", items[1].meta)
        assertEquals("S2, E5 · Up next", items[2].meta)
        assertEquals(0f, items[2].progress)
        assertEquals(Playable.Kind.Episode to "home:9105", continuePlayable(items[2].id))
        assertNull(continuePlayable("series:atlas:s2e4"))
    }

    @Test fun theTabsListTheirOwn() {
        val movies = continueMovies(listOf(neon, reckoning))
        assertEquals(listOf("home:7101"), movies.map { it.id })
        assertEquals(VodCaption.Movie(year = 2026, genre = "Thriller", minutes = 118), movies.single().caption)
        assertEquals(56f / 118f, movies.single().progress!!, 0.0001f)

        val series = continueSeries(listOf(neon, reckoning, quietSide))
        assertEquals(listOf("home:8101", "home:8101"), series.map { it.id })
        assertEquals(VodCaption.ResumeEpisode(season = 2, episode = 4, leftMinutes = 18), series[0].caption)
        assertEquals(VodCaption.NextEpisode(season = 2, episode = 5), series[1].caption)
    }

    @Test fun hideItemsOlderThanSaysHowFarBack() {
        assertEquals(at.minus(Duration.ofDays(30)), continueSince(SettingsChoices(), at))
        assertEquals(at.minus(Duration.ofDays(7)), continueSince(SettingsChoices(mapOf(SettingKeys.HIDE_OLDER to 0)), at))
        assertNull(continueSince(SettingsChoices(mapOf(SettingKeys.HIDE_OLDER to 4)), at))
    }
}
