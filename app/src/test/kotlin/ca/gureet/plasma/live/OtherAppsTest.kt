// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.ContinueEntry
import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.core.data.Progress
import ca.gureet.plasma.feature.home.ContinueKind
import ca.gureet.plasma.launcher.PlayNextItem
import ca.gureet.plasma.launcher.PlayNextType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Other apps' items in Continue Watching, from Android's Play Next row (brief 5.19). */
class OtherAppsTest {
    private val words = ContinueWords(
        left = { "$it left" },
        episode = { season, number -> "S$season, E$number" },
        upNext = "Up next",
        episodeText = { season, number -> if (number == null) null else listOfNotNull(season?.let { "S$it" }, "E$number").joinToString(", ") },
    )
    private val at = Instant.parse("2026-10-21T20:41:00Z")

    private val episode = PlayNextItem(
        id = 12,
        packageName = "tv.example.reelbox",
        contentId = "ep-204",
        title = "Night Stint",
        episodeTitle = "The Gauntlet",
        season = "2",
        episode = "4",
        poster = "https://img.example/night-stint.jpg",
        wide = true,
        positionMs = Duration.ofMinutes(35).toMillis(),
        durationMs = Duration.ofMinutes(58).toMillis(),
        lastWatched = at.minusSeconds(30),
        intentUri = "intent://open/ep-204#Intent;scheme=reelbox;package=tv.example.reelbox;end",
    )
    private val next = episode.copy(id = 13, contentId = "ep-205", episodeTitle = "Slipstream", episode = "5", positionMs = 0, type = PlayNextType.Next, lastWatched = at.minusSeconds(3_600))
    private val movie = PlayNextItem(id = 20, packageName = "tv.example.tumble", contentId = null, title = "Polar Night", poster = "https://img.example/polar.jpg", wide = false, positionMs = 600_000, durationMs = 6_000_000, lastWatched = at.minusSeconds(7_200))
    private val saved = movie.copy(id = 21, title = "Lighthouse", type = PlayNextType.Watchlist)
    private val fresh = movie.copy(id = 22, title = "Orbit", type = PlayNextType.New)

    @Test fun anotherAppsItemSaysWhatsLeftAndWhoseItIs() {
        val item = episode.continueItem(words, "Reelbox")

        assertEquals("playnext:tv.example.reelbox|ep-204", item.id)
        assertEquals("Night Stint", item.title)
        assertEquals("S2, E4 · The Gauntlet · 23 min left", item.meta)
        assertEquals(ContinueKind.Art, item.kind)
        assertEquals(35f / 58f, item.progress)
        assertEquals("REELBOX", item.sourceTag)

        assertEquals("S2, E5 · Slipstream · Up next", next.continueItem(words, "Reelbox").meta)
        val polar = movie.continueItem(words, "Tumble")
        assertEquals("1 h 30 min left", polar.meta)
        assertEquals(ContinueKind.Poster, polar.kind)
        // Without its own id, the row's id keeps it apart.
        assertEquals("tv.example.tumble|20", playNextKeyOf(polar.id))
        assertEquals("tv.example.tumble", packageOfKey("tv.example.tumble|20"))
        assertNull(playNextKeyOf("vod:movie:home:7101"))
    }

    @Test fun onlyWhatsBeingWatchedAndWhatsNextJoinTheRow() {
        val all = listOf(episode, next, movie, saved, fresh)

        assertEquals(listOf(episode, next, movie), all.forContinue(nextEpisodes = true, since = null))
        assertEquals(listOf(episode, movie), all.forContinue(nextEpisodes = false, since = null))
        // Hide Items Older Than.
        assertEquals(listOf(episode), all.forContinue(nextEpisodes = true, since = at.minusSeconds(60)))
    }

    @Test fun plasmasOwnAndOtherAppsTakeTurnsByWhenTheyWereWatched() {
        val neon = ContinueEntry(
            playable = Playable(Playable.Kind.Movie, "home:7101", "home", "7101", "mkv", "Neon Tide"),
            wide = null,
            poster = null,
            progress = Progress(Duration.ofMinutes(56), Duration.ofMinutes(118), watched = false),
            updated = at.minusSeconds(1_800),
        )
        val row = continueRow(listOf(neon), listOf(episode, next, movie), words) { it.substringAfterLast('.').replaceFirstChar(Char::uppercase) }

        assertEquals(listOf("Night Stint", "Neon Tide", "Night Stint", "Polar Night"), row.map { it.title })
        assertEquals(listOf("REELBOX", null, "REELBOX", "TUMBLE"), row.map { it.sourceTag })

        val many = (1..30).map { movie.copy(id = 100L + it, lastWatched = at.minusSeconds(it.toLong())) }
        assertEquals(20, continueRow(listOf(neon), many, words) { "Tumble" }.size)
    }
}
