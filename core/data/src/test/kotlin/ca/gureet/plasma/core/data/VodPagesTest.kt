// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.network.PlasmaHttp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Movie and series pages (brief 4.14, 4.16 and 5.9), My List and what's watched, against a fake Xtream server. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VodPagesTest {
    /** A clock the test moves on. */
    private class MovingClock(var instant: Instant) : PlasmaClock {
        override val zone: ZoneId = ZoneOffset.UTC

        override fun now(): Instant = instant
    }

    private val xtream = FakeXtreamServer()
    private val clock = MovingClock(Instant.parse("2026-10-21T20:41:00Z"))
    private lateinit var db: PlasmaDatabase
    private lateinit var settingsDir: File
    private lateinit var data: PlasmaData
    private lateinit var source: String

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsDir = Files.createTempDirectory("settings").toFile()
        val settings = SettingsStore(PreferenceDataStoreFactory.create { File(settingsDir, "settings.preferences_pb") })
        data = PlasmaData(db, PlasmaHttp.client("Plasma test"), settings, FakeSecretBox(), clock)
        source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source.id
        data.importer.importVod(source)
    }

    @After fun tearDown() {
        db.close()
        xtream.close()
        settingsDir.deleteRecursively()
    }

    private fun requests(action: String) = xtream.requests.count { it.url.queryParameter("action") == action }

    @Test fun aMoviesPageHasTheCatalogsWhatThenTheProvidersDetails() = runTest {
        val neon = "$source:7101"
        val before = data.vod.moviePage(neon).first()!!

        assertEquals("Neon Tide", before.movie.name)
        assertEquals("Thrillers", before.category)
        assertEquals("mkv", before.extension)
        assertNull(before.details)
        assertEquals(listOf("Glass Orchard"), before.moreLikeThis.map { it.name })

        assertTrue(data.vod.loadMovie(neon))

        val after = data.vod.moviePage(neon).first()!!
        val details = after.details!!
        assertEquals("Neon Tide: a night in the harbour.", details.plot)
        assertEquals(listOf("Ana Reyes", "Tom Hale"), details.cast)
        assertEquals(listOf("Thriller", "Drama"), details.genres)
        assertEquals(Duration.ofMinutes(118), details.duration)
        assertEquals("https://img.example/7101-wide.jpg", details.backdrop)
        assertEquals("abcdefghijk", details.trailer)
        assertEquals("13+", details.age)
        assertEquals(FileVideo("hevc", "Main 10", 3840, 2160, null, "HDR10"), details.video)
        assertEquals(FileAudio("eac3", 6, "eng", null), details.audio)
        // The details know the file better than the catalog did.
        assertEquals("mp4", after.extension)
    }

    @Test fun aMoviesDetailsAreReadAgainAfterAWeek() = runTest {
        val neon = "$source:7101"
        data.vod.loadMovie(neon)
        clock.instant = clock.instant.plus(Duration.ofDays(6))
        data.vod.loadMovie(neon)
        assertEquals(1, requests("get_vod_info"))

        clock.instant = clock.instant.plus(Duration.ofDays(2))
        data.vod.loadMovie(neon)
        assertEquals(2, requests("get_vod_info"))
    }

    @Test fun detailsThatCantBeReadLeaveThePageAndTheSourceAsTheyWere() = runTest {
        xtream.detailsCode = 500

        assertFalse(data.vod.loadMovie("$source:7101"))
        assertFalse(data.vod.loadSeries("$source:8101"))

        assertNull(data.vod.moviePage("$source:7101").first()!!.details)
        assertEquals(emptyList<Episode>(), data.vod.seriesPage("$source:8101").first()!!.episodes)
        // One title's page failing isn't the source failing.
        assertNull(data.sources.get(source)!!.lastError)
        assertNull(data.vod.moviePage("$source:9999").first())
    }

    @Test fun aSeriesPageHasItsEpisodesSeasonBySeasonWithTheSpecialsLast() = runTest {
        val atlas = "$source:8101"
        assertTrue(data.vod.loadSeries(atlas))

        val page = data.vod.seriesPage(atlas).first()!!
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1, 0 to 1), page.episodes.map { it.season to it.number })
        assertEquals(listOf("Launch", "Drift", null, "Behind the Scenes"), page.episodes.map { it.title })
        assertEquals(listOf(Duration.ofMinutes(52), Duration.ofMinutes(49), null, Duration.ofMinutes(20)), page.episodes.map { it.duration })
        assertEquals("$source:9101", page.episodes.first().id)
        assertEquals("A crew on the edge.", page.plot)
        assertEquals(listOf("Sci-Fi", "Drama"), page.details!!.genres)
        assertEquals("16+", page.details!!.age)
        assertEquals("Drama", page.category)
    }

    @Test fun aSeriesIsReadAgainAfterADayOrWhenTheCatalogSeesItChange() = runTest {
        val atlas = "$source:8101"
        data.vod.loadSeries(atlas)
        data.vod.loadSeries(atlas)
        assertEquals(1, requests("get_series_info"))

        // The catalog's next refresh says an episode came since.
        clock.instant = clock.instant.plus(Duration.ofHours(1))
        xtream.series = xtream.series.map { if (it.id == "8101") it.copy(added = clock.instant.epochSecond) else it }
        data.importer.importVod(source)
        xtream.episodes = xtream.episodes + FakeEpisode("9105", 2, 2, "Dead Reckoning", 58)
        data.vod.loadSeries(atlas)
        assertEquals(2, requests("get_series_info"))
        assertEquals(5, data.vod.seriesPage(atlas).first()!!.episodes.size)

        clock.instant = clock.instant.plus(Duration.ofHours(25))
        data.vod.loadSeries(atlas)
        assertEquals(3, requests("get_series_info"))
    }

    @Test fun myListHasTheLatestAddedFirst() = runTest {
        data.vod.setMovieInMyList("$source:7101", inList = true)
        clock.instant = clock.instant.plusSeconds(1)
        data.vod.setMovieInMyList("$source:7102", inList = true)
        data.vod.setSeriesInMyList("$source:8102", inList = true)

        assertEquals(listOf("Glass Orchard", "Neon Tide"), data.vod.myListMovies.first().map { it.name })
        assertEquals(setOf("$source:8102"), data.vod.myListSeriesIds.first())
        assertTrue(data.vod.moviePage("$source:7101").first()!!.inMyList)

        data.vod.setMovieInMyList("$source:7101", inList = false)
        assertEquals(setOf("$source:7102"), data.vod.myListMovieIds.first())
        assertFalse(data.vod.moviePage("$source:7101").first()!!.inMyList)
        // A title that isn't in the catalog can't be added.
        data.vod.setMovieInMyList("$source:9999", inList = true)
        assertEquals(setOf("$source:7102"), data.vod.myListMovieIds.first())
    }

    @Test fun aMovieMarkedWatchedIsWatchedAndUnwatchedForgetsWhereItWasLeft() = runTest {
        val neon = "$source:7101"
        data.vod.setMovieWatched(neon, watched = true)

        assertEquals(true, data.vod.movieProgress.first()[neon]?.watched)
        val progress = data.vod.moviePage(neon).first()!!.progress!!
        assertTrue(progress.watched)
        assertNull(progress.left)

        data.vod.setMovieWatched(neon, watched = false)
        assertNull(data.vod.movieProgress.first()[neon])
    }

    @Test fun aSeriesIsWatchedSeasonBySeasonOrWhole() = runTest {
        val atlas = "$source:8101"
        // Its episodes haven't been read: marking it reads them first.
        assertTrue(data.vod.setSeriesWatched(atlas, watched = true))
        assertEquals(setOf(atlas), data.vod.watchedSeriesIds.first())
        assertEquals(4, data.vod.seriesPage(atlas).first()!!.progress.count { it.value.watched })

        data.vod.setSeasonWatched(atlas, season = 2, watched = false)
        assertEquals(emptySet<String>(), data.vod.watchedSeriesIds.first())
        val page = data.vod.seriesPage(atlas).first()!!
        assertEquals(listOf(true, true, false, true), page.episodes.map { page.progress[it.id]?.watched == true })

        data.vod.setEpisodesWatched(atlas, listOf("$source:9104"), watched = true)
        assertEquals(setOf(atlas), data.vod.watchedSeriesIds.first())

        // Without its episodes, and with the provider out of reach, a series can't be marked.
        xtream.detailsCode = 500
        assertFalse(data.vod.setSeriesWatched("$source:8102", watched = true))
    }

    @Test fun aStartedTitleSaysWhatsLeft() {
        val started = Progress(Duration.ofMinutes(56), Duration.ofMinutes(118), watched = false)

        assertEquals(Duration.ofMinutes(62), started.left)
        assertEquals(56f / 118f, started.fraction!!, 0.0001f)
        assertNull(Progress(Duration.ZERO, Duration.ofMinutes(118), watched = false).left)
        assertNull(Progress(Duration.ofMinutes(5), Duration.ZERO, watched = false).fraction)
        assertNull(Progress(Duration.ofMinutes(56), Duration.ofMinutes(118), watched = true).left)
    }

    @Test fun namesAndGenresAreReadAsProvidersWriteThem() {
        assertEquals(listOf("Ana Reyes", "Tom Hale"), VodRepository.names(" Ana Reyes, Tom Hale ,, Ana Reyes"))
        assertEquals(listOf("Action", "Adventure", "Sci-Fi & Fantasy"), VodRepository.genres("Action / Adventure | Sci-Fi & Fantasy, Drama"))
        assertEquals(emptyList<String>(), VodRepository.genres(null))
    }
}
