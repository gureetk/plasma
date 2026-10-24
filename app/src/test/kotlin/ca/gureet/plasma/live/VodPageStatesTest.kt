// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.Episode
import ca.gureet.plasma.core.data.FileAudio
import ca.gureet.plasma.core.data.FileVideo
import ca.gureet.plasma.core.data.Movie
import ca.gureet.plasma.core.data.MovieDetails
import ca.gureet.plasma.core.data.MoviePage
import ca.gureet.plasma.core.data.Progress
import ca.gureet.plasma.core.data.Series
import ca.gureet.plasma.core.data.SeriesDetails
import ca.gureet.plasma.core.data.SeriesPage
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.feature.vod.Backdrop
import ca.gureet.plasma.feature.vod.EpisodeItem
import ca.gureet.plasma.feature.vod.EpisodeRef
import ca.gureet.plasma.feature.vod.EpisodesStatus
import ca.gureet.plasma.feature.vod.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** Movie and series pages from a source's catalog and its provider's details (brief 4.14, 4.16 and 5.9). */
class VodPageStatesTest {
    private val words = VodWords(
        container = "Container",
        video = "Video",
        hdr = "HDR",
        audio = "Audio",
        badge4k = "4K",
        badgeHd = "HD",
        badgeHdr = "HDR",
        resolution = { width, height -> "$width × $height" },
        frameRate = { "$it fps" },
        kilobits = { "$it kb/s" },
        years = { first, last -> "$first–$last" },
        layout = { channels -> mapOf(2 to "Stereo", 6 to "5.1", 8 to "7.1")[channels] ?: "$channels channels" },
        audioFormat = { codec -> mapOf("eac3" to "Dolby Digital Plus", "aac" to "AAC")[codec] },
        language = { code -> mapOf("eng" to "English", "fra" to "French")[code] },
    )

    private val neon = Movie("home:7101", "Neon Tide", "https://img.example/neon.jpg", "home:movie:30", null, 7.4f, 2026)

    private fun moviePage(details: MovieDetails? = null, progress: Progress? = null) =
        MoviePage(neon, "mkv", "Thrillers", details, inMyList = false, progress = progress, moreLikeThis = emptyList())

    private val neonDetails = MovieDetails(
        plot = "A harbour town, one long night.",
        cast = listOf("Ana Reyes", "Tom Hale"),
        director = "Mia Stone",
        genres = listOf("Thriller", "Drama"),
        duration = Duration.ofSeconds(7_080),
        poster = null,
        backdrop = "https://img.example/neon-wide.jpg",
        trailer = "abcdefghijk",
        year = 2026,
        rating = 7.4f,
        age = "13+",
        video = FileVideo("hevc", "Main 10", 3840, 2160, 24_000f / 1_001f, "Dolby Vision"),
        audio = FileAudio("eac3", 6, "eng", 640_000),
    )

    @Test fun beforeItsDetailsAMovieShowsWhatTheCatalogHas() {
        val state = moviePageState(moviePage(), words, trailers = true)

        assertEquals("Neon Tide", state.title)
        assertNull(state.titleArt)
        // No backdrop yet: the poster, blurred (5.9).
        assertEquals(Backdrop("https://img.example/neon.jpg", PlasmaColors.Bg, blurred = true), state.backdrop)
        assertEquals(2026, state.year)
        assertNull(state.minutes)
        assertEquals(emptyList<String>(), state.genres)
        assertEquals(emptyList<String>(), state.badges)
        assertEquals("", state.synopsis)
        assertFalse(state.hasTrailer)
        assertNull(state.leftMinutes)
        assertEquals(listOf("Container" to "Matroska (MKV)"), state.fileInfo)
    }

    @Test fun aMoviesDetailsFillItsPage() {
        val state = moviePageState(moviePage(neonDetails, Progress(Duration.ofMinutes(56), Duration.ofSeconds(7_080), watched = false)), words, trailers = true)

        assertEquals(Backdrop("https://img.example/neon-wide.jpg", PlasmaColors.Bg), state.backdrop)
        assertEquals(listOf("Thriller", "Drama"), state.genres)
        assertEquals(118, state.minutes)
        assertEquals(62, state.leftMinutes)
        assertEquals(listOf("13+", "4K", "HDR", "5.1"), state.badges)
        assertEquals("A harbour town, one long night.", state.synopsis)
        assertEquals(listOf(Person("Ana Reyes", ""), Person("Tom Hale", "")), state.cast)
        assertEquals("Mia Stone", state.director)
        assertTrue(state.hasTrailer)
        assertEquals(listOf("English · 5.1 · Dolby Digital Plus"), state.audio)
        assertEquals(
            listOf(
                "Container" to "Matroska (MKV)",
                "Video" to "HEVC Main 10 · 3840 × 2160 · 23.976 fps",
                "HDR" to "Dolby Vision",
                "Audio" to "E-AC-3 5.1 · 640 kb/s",
            ),
            state.fileInfo,
        )
        // Without YouTube on the TV, there's no Trailer button.
        assertFalse(moviePageState(moviePage(neonDetails), words, trailers = false).hasTrailer)
    }

    @Test fun aWatchedMovieOffersPlayAndAStartedOneWhatsLeft() {
        assertNull(moviePageState(moviePage(neonDetails, Progress(Duration.ZERO, Duration.ZERO, watched = true)), words, true).leftMinutes)
        // Half a minute left is still a minute, and the player's length stands in for the provider's.
        val nearlyDone = moviePageState(moviePage(progress = Progress(Duration.ofSeconds(6_990), Duration.ofSeconds(7_020), watched = false)), words, true)
        assertEquals(1, nearlyDone.leftMinutes)
        assertEquals(117, nearlyDone.minutes)
    }

    @Test fun anHdPictureWithStereoSoundHasOnlyItsBadge() {
        val hd = neonDetails.copy(age = null, video = FileVideo("h264", null, 1920, 800, 25f, null), audio = FileAudio("aac", 2, null, null))
        val state = moviePageState(moviePage(hd), words, true)

        assertEquals(listOf("HD"), state.badges)
        assertEquals(listOf("Stereo · AAC"), state.audio)
        assertEquals(listOf("Container" to "Matroska (MKV)", "Video" to "H.264 · 1920 × 800 · 25 fps", "Audio" to "AAC 2.0"), state.fileInfo)
    }

    private val atlas = Series("home:8101", "Atlas Station", "https://img.example/atlas.jpg", null, "home:series:50", "Sci-Fi, Drama", 2024, 8.1f, null)

    private fun episode(id: String, season: Int, number: Int, title: String?, minutes: Long?, year: Int? = null) =
        Episode("home:$id", season, number, title, null, null, minutes?.let(Duration::ofMinutes), year, null, "mkv", null, null)

    private fun seriesPage(episodes: List<Episode>, details: SeriesDetails? = null, progress: Map<String, Progress> = emptyMap()) =
        SeriesPage(atlas, "A crew on the edge.", emptyList(), null, trailer = null, category = "Drama", details = details, episodes = episodes, progress = progress, inMyList = true)

    @Test fun aSeriesHasItsSeasonsTheSpecialsLastAndWhereEachEpisodeWasLeft() {
        val page = seriesPage(
            episodes = listOf(
                episode("9101", 1, 1, "Launch", 52, year = 2024),
                episode("9102", 1, 2, "Drift", 49, year = 2024),
                episode("9104", 2, 1, null, null, year = 2026),
                episode("9100", 0, 1, "Behind the Scenes", 20),
            ),
            details = SeriesDetails("A crew on the edge.", emptyList(), null, emptyList(), null, null, 2024, 8.1f, "16+"),
            progress = mapOf(
                "home:9101" to Progress(Duration.ZERO, Duration.ZERO, watched = true),
                "home:9102" to Progress(Duration.ofMinutes(31), Duration.ofMinutes(49), watched = false),
            ),
        )

        val screen = seriesPageState(page, words, trailers = true, failed = false)
        val state = screen.state

        assertEquals(listOf(1, 2, 0), state.seasons.map { it.number })
        assertEquals(EpisodeItem(1, "Launch", null, 52, watched = true), state.seasons[0].episodes[0])
        assertEquals(EpisodeItem(2, "Drift", null, 49, leftMinutes = 18), state.seasons[0].episodes[1])
        assertEquals(EpisodeItem(1, null, null, null), state.seasons[1].episodes[0])
        assertEquals("home:9104", screen.episodeIds[EpisodeRef(2, 1)])
        assertEquals("home:9100", screen.episodeIds[EpisodeRef(0, 1)])
        assertEquals("2024–2026", state.years)
        // The details have no genres: the catalog's stand in.
        assertEquals(listOf("Sci-Fi", "Drama"), state.genres)
        assertEquals(listOf("16+"), state.badges)
        assertEquals(Backdrop("https://img.example/atlas.jpg", PlasmaColors.Bg, blurred = true), state.backdrop)
        assertTrue(state.inMyList)
        assertFalse(state.hasTrailer)
        assertEquals(EpisodesStatus.Ready, state.episodes)
    }

    @Test fun episodesWithoutTheirOwnNumbersAreCountedInOrder() {
        val page = seriesPage(listOf(episode("9201", 1, 0, "One", 40), episode("9202", 1, 0, "Two", 40), episode("9203", 1, 3, "Three", 40)))

        val screen = seriesPageState(page, words, trailers = true, failed = false)

        assertEquals(listOf(1, 2, 3), screen.state.seasons.single().episodes.map { it.number })
        assertEquals("home:9202", screen.episodeIds[EpisodeRef(1, 2)])
    }

    @Test fun aSeriesSaysWhetherItsEpisodesAreStillComing() {
        assertEquals(EpisodesStatus.Loading, seriesPageState(seriesPage(emptyList()), words, true, failed = false).state.episodes)
        assertEquals(EpisodesStatus.Failed, seriesPageState(seriesPage(emptyList()), words, true, failed = true).state.episodes)
        // Read, and there are none.
        val read = SeriesDetails(null, emptyList(), null, emptyList(), null, null, null, null, null)
        assertEquals(EpisodesStatus.Ready, seriesPageState(seriesPage(emptyList(), read), words, true, failed = false).state.episodes)
        // A year alone isn't a range.
        assertEquals("2024", seriesPageState(seriesPage(emptyList(), read), words, true, failed = false).state.years)
    }
}
