// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.feature.player.UpNext
import ca.gureet.plasma.feature.player.VodEpisode
import ca.gureet.plasma.player.LiveStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Playing movies and episodes (brief 4.9 and 5.9). */
class VodPlaybackTest {
    private val quietSide = Playable(
        kind = Playable.Kind.Episode,
        id = "home:9105",
        sourceId = "home",
        remoteId = "9105",
        extension = "mkv",
        title = "Atlas Station",
        seriesId = "home:8101",
        season = 2,
        number = 5,
        episodeTitle = "The Quiet Side",
        art = "https://img.example/s2e5.jpg",
    )

    @Test fun upNextShowsForAnEpisodesLast30Seconds() {
        val length = 3_480_000L

        assertNull(upNextOf(quietSide, positionMs = 3_000_000, lengthMs = length, ended = false, cancelled = false, countdown = null))
        assertEquals(
            UpNext(VodEpisode(2, 5, "The Quiet Side"), "https://img.example/s2e5.jpg"),
            upNextOf(quietSide, positionMs = length - 20_000, lengthMs = length, ended = false, cancelled = false, countdown = null),
        )
        // Once it has ended, the card counts down.
        assertEquals(8, upNextOf(quietSide, positionMs = 0, lengthMs = 0, ended = true, cancelled = false, countdown = 8)?.countdown)
        // Put away with Back, or with nothing after it, there's no card.
        assertNull(upNextOf(quietSide, positionMs = length - 20_000, lengthMs = length, ended = false, cancelled = true, countdown = null))
        assertNull(upNextOf(null, positionMs = length - 20_000, lengthMs = length, ended = true, cancelled = false, countdown = null))
    }

    @Test fun whereAMovieIsCountsOnceThePlayerHasItOpen() {
        val key = vodKey(Playable.Kind.Movie, "home:7101")

        assertEquals("vod:movie:home:7101", key)
        assertEquals("vod:episode:home:9105", vodKey(Playable.Kind.Episode, "home:9105"))
        assertTrue(isPlaying(LiveStatus.Playing(key), key))
        assertTrue(isPlaying(LiveStatus.Ended(key), key))
        // Opening, the player's place is still what played before.
        assertFalse(isPlaying(LiveStatus.Opening(key), key))
        assertFalse(isPlaying(LiveStatus.Playing("ch105"), key))
    }
}
