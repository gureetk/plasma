// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.network.PlasmaHttp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * Movies' posters when the provider gives a placeholder (the owner's #81): its catalog's picture
 * kept as given, the movie's own found in its details, and Replace Placeholder Posters.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PostersTest {
    private class MovingClock(var instant: Instant) : PlasmaClock {
        override val zone: ZoneId = ZoneOffset.UTC

        override fun now(): Instant = instant
    }

    private val xtream = FakeXtreamServer()
    private val clock = MovingClock(Instant.parse("2026-10-21T20:41:00Z"))
    private lateinit var db: PlasmaDatabase
    private lateinit var settingsDir: File
    private lateinit var settings: SettingsStore
    private lateinit var data: PlasmaData

    private val placeholder = "https://media.home.lan/placeholder.png"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsDir = Files.createTempDirectory("settings").toFile()
        settings = SettingsStore(PreferenceDataStoreFactory.create { File(settingsDir, "settings.preferences_pb") })
        data = PlasmaData(db, PlasmaHttp.client("Plasma test"), settings, FakeSecretBox(), clock)
    }

    @After fun tearDown() {
        db.close()
        xtream.close()
        settingsDir.deleteRecursively()
    }

    /** Twelve movies with the server's placeholder, their details with [details] each, and Neon Tide with its own. */
    private suspend fun catalog(details: (Int) -> List<String> = { listOf("https://media.home.lan/posters/movie-$it.jpg") }): Source {
        xtream.movies = (1..12).map { FakeTitle("71${it.toString().padStart(2, '0')}", "Movie $it", "30", added = null, poster = placeholder, detailsPosters = details(it)) } +
            FakeTitle("7199", "Neon Tide", "30", added = null, poster = "https://media.home.lan/posters/neon-tide.jpg")
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source
        data.importer.importChannels(source.id)
        data.importer.importVod(source.id)
        return source
    }

    private suspend fun shown(): Map<String, String?> =
        data.vod.moviesIn(data.vod.movieCategories.first().first().id).first().associate { it.name to it.poster }

    @Test fun aMovieShowingThePlaceholderHasItsOwnPosterFoundInItsDetails() = runTest {
        val source = catalog()
        // Its name shows until its own poster is found.
        assertEquals(null, shown()["Movie 1"])
        assertEquals("https://media.home.lan/posters/neon-tide.jpg", shown()["Neon Tide"])

        assertTrue(data.vod.findPoster("${source.id}:7101"))

        assertEquals("https://media.home.lan/posters/movie-1.jpg", shown()["Movie 1"])
        assertEquals(null, shown()["Movie 2"])
        // Its details are kept for its page too, and it isn't asked again.
        assertEquals("Movie 1: a night in the harbour.", data.vod.moviePage("${source.id}:7101").first()?.details?.plot)
        val asked = xtream.server.requestCount
        assertFalse(data.vod.findPoster("${source.id}:7101"))
        // A movie with a poster of its own, and one that isn't a movie, aren't asked.
        assertFalse(data.vod.findPoster("${source.id}:7199"))
        assertFalse(data.vod.findPoster("${source.id}:8101"))
        assertEquals(asked, xtream.server.requestCount)
    }

    @Test fun aPosterThatDoesntLoadIsReplacedAsAPlaceholderIs() = runTest {
        // Neon Tide's poster is on a server the TV can't find; its details name that one first.
        val gone = "https://gone.home.lan/neon-tide.jpg"
        xtream.movies = listOf(FakeTitle("7199", "Neon Tide", "30", added = null, poster = gone, detailsPosters = listOf(gone, "https://media.home.lan/posters/neon-tide.jpg")))
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source
        data.importer.importChannels(source.id)
        data.importer.importVod(source.id)
        assertEquals(gone, shown()["Neon Tide"])
        // While it isn't known not to load, it isn't looked for.
        assertFalse(data.vod.findPoster("${source.id}:7199"))

        // Pictures come from another server, and Neon Tide's isn't found.
        data.unloadablePictures.came("https://media.home.lan/posters/another.jpg")
        data.unloadablePictures.failed(gone, UnloadablePictures.Why.NoServer)

        // Its title shows in type until its own poster is found in its details, past the one that doesn't load.
        assertEquals(null, shown()["Neon Tide"])
        assertTrue(data.vod.findPoster("${source.id}:7199"))
        assertEquals("https://media.home.lan/posters/neon-tide.jpg", shown()["Neon Tide"])
        // Its page doesn't show the one that doesn't load either.
        assertEquals(null, data.vod.moviePage("${source.id}:7199").first()?.details?.poster)

        // Found, it's kept: the old one isn't asked for again, even once its server answers.
        data.unloadablePictures.checked("gone.home.lan", dead = null)
        assertEquals("https://media.home.lan/posters/neon-tide.jpg", shown()["Neon Tide"])
    }

    @Test fun aMovieWhoseDetailsHaveNoneIsAskedAgainAWeekLater() = runTest {
        val source = catalog { emptyList() }
        assertTrue(data.vod.findPoster("${source.id}:7101"))
        assertFalse(data.vod.findPoster("${source.id}:7101"))
        assertEquals(null, shown()["Movie 1"])

        clock.instant = clock.instant.plus(Duration.ofDays(8))
        assertTrue(data.vod.findPoster("${source.id}:7101"))
    }

    @Test fun theDetailsPlaceholderIsntAPosterEither() = runTest {
        // The details name the same placeholder first, and the movie's own after it.
        val source = catalog { listOf(placeholder, "https://media.home.lan/posters/movie-$it.jpg") }
        data.vod.findPoster("${source.id}:7101")
        assertEquals("https://media.home.lan/posters/movie-1.jpg", shown()["Movie 1"])
        // Its page doesn't show the placeholder as its poster.
        assertEquals(null, data.vod.moviePage("${source.id}:7101").first()?.details?.poster)
    }

    @Test fun aPictureTheDetailsOfManyMoviesGiveIsAPlaceholderToo() = runTest {
        val source = catalog { listOf("https://media.home.lan/no-poster.jpg") }
        (1..12).forEach { data.vod.findPoster("${source.id}:71${it.toString().padStart(2, '0')}") }

        assertEquals(emptyList<String>(), shown().filterKeys { it != "Neon Tide" }.values.filterNotNull())
    }

    @Test fun offPostersShowAsTheProviderGivesThem() = runTest {
        val source = catalog()
        data.vod.findPoster("${source.id}:7101")
        settings.choose(SettingKeys.PLACEHOLDER_POSTERS, 1)

        assertEquals(placeholder, shown()["Movie 1"])
        assertEquals(placeholder, shown()["Movie 2"])
        // Nothing is looked for while it's off.
        assertFalse(data.vod.findPoster("${source.id}:7102"))
    }

    @Test fun aRefreshKeepsWhatWasFound() = runTest {
        val source = catalog()
        data.vod.findPoster("${source.id}:7101")
        data.importer.importVod(source.id)
        assertEquals("https://media.home.lan/posters/movie-1.jpg", shown()["Movie 1"])
    }

    /** Twelve series with the server's placeholder, their details naming [details] each, and Kingsway with its own (#228). */
    private suspend fun seriesCatalog(details: (Int) -> List<String> = { listOf("https://media.home.lan/posters/series-$it.jpg") }): Source {
        xtream.movies = emptyList()
        xtream.series = (1..12).map { FakeTitle("81${it.toString().padStart(2, '0')}", "Series $it", "50", added = null, poster = placeholder, detailsPosters = details(it)) } +
            FakeTitle("8199", "Kingsway", "50", added = null, poster = "https://media.home.lan/posters/kingsway.jpg")
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source
        data.importer.importChannels(source.id)
        data.importer.importVod(source.id)
        return source
    }

    private suspend fun shownSeries(): Map<String, String?> =
        data.vod.seriesIn(data.vod.seriesCategories.first().first().id).first().associate { it.name to it.poster }

    private suspend fun findSeriesPoster(id: String): Boolean {
        val found = data.vod.lookUpSeriesPoster(id) ?: return false
        data.vod.keepSeriesPosters(listOf(found))
        return true
    }

    @Test fun aSeriesShowingThePlaceholderHasItsOwnPosterFoundInItsDetails() = runTest {
        val source = seriesCatalog()
        assertEquals(null, shownSeries()["Series 1"])
        assertEquals("https://media.home.lan/posters/kingsway.jpg", shownSeries()["Kingsway"])

        assertTrue(findSeriesPoster("${source.id}:8101"))
        assertEquals("https://media.home.lan/posters/series-1.jpg", shownSeries()["Series 1"])
        assertEquals(null, shownSeries()["Series 2"])

        // Not asked again, nor a series with its own poster; and a refresh keeps what was found.
        val asked = xtream.server.requestCount
        assertFalse(findSeriesPoster("${source.id}:8101"))
        assertFalse(findSeriesPoster("${source.id}:8199"))
        assertEquals(asked, xtream.server.requestCount)
        data.importer.importVod(source.id)
        assertEquals("https://media.home.lan/posters/series-1.jpg", shownSeries()["Series 1"])
    }

    @Test fun aSeriesWhoseDetailsOnlyNameThePlaceholderAgainKeepsItsName() = runTest {
        val source = seriesCatalog { listOf(placeholder) }
        assertTrue(findSeriesPoster("${source.id}:8101"))
        assertEquals(null, shownSeries()["Series 1"])
        // Looked for, it isn't asked again for a week.
        assertFalse(findSeriesPoster("${source.id}:8101"))
    }

    @Test fun aSeriesPosterThatDoesntLoadIsReplaced() = runTest {
        val gone = "https://gone.home.lan/kingsway.jpg"
        xtream.movies = emptyList()
        xtream.series = listOf(FakeTitle("8199", "Kingsway", "50", added = null, poster = gone, detailsPosters = listOf(gone, "https://media.home.lan/posters/kingsway.jpg")))
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source
        data.importer.importChannels(source.id)
        data.importer.importVod(source.id)
        assertFalse(findSeriesPoster("${source.id}:8199"))

        data.unloadablePictures.checked("gone.home.lan", UnloadablePictures.Why.NoServer)
        assertEquals(null, shownSeries()["Kingsway"])
        assertTrue(findSeriesPoster("${source.id}:8199"))
        assertEquals("https://media.home.lan/posters/kingsway.jpg", shownSeries()["Kingsway"])

        // Found, it's kept, whatever becomes of the old one.
        data.unloadablePictures.checked("gone.home.lan", dead = null)
        assertEquals("https://media.home.lan/posters/kingsway.jpg", shownSeries()["Kingsway"])
    }

    @Test fun theSweepTakesTheTopOfEachListAndLeavesOutWhatsBeenLookedFor() = runTest {
        val source = catalog()
        val lists = data.vod.sweepLists()
        // Recently Added and the movie category, then New Episodes and the series categories.
        assertEquals(listOf(false, false, true, true, true), lists.map { it.series })
        val thrillers = lists[1]
        val first = data.vod.sweepPage(thrillers, 0)!!
        assertEquals((1..12).map { "${source.id}:71${it.toString().padStart(2, '0')}" }.take(VodRepository.SWEEP_PAGE), first.map { it.id })

        data.posterSweep.sweep()
        // Every placeholder movie's details read, a title at a time, and the two series' without a
        // picture; none left to look for.
        assertEquals(14, data.posterSweep.tally.read)
        db.pictures().foundPosters().let { found ->
            assertEquals(12, found.moviesFound)
            assertEquals(2, found.seriesNone)
        }
        assertEquals(emptyList<PosterSweep.Title>(), data.vod.sweepPage(thrillers, 0))
        assertEquals(null, data.vod.sweepPage(thrillers, 1))
        assertEquals("https://media.home.lan/posters/movie-12.jpg", shown()["Movie 12"])

        settings.choose(SettingKeys.PLACEHOLDER_POSTERS, 1)
        assertEquals(emptyList<VodRepository.SweepList>(), data.vod.sweepLists())
    }

    @Test fun thePicturesAreTakenTogetherByServer() = runTest {
        val source = catalog()
        data.vod.findPoster("${source.id}:7101")
        val servers = db.pictures().servers(10)
        // The placeholder twelve times, and Neon Tide's and Movie 1's own: all on one server.
        assertEquals(listOf("media.home.lan" to 14), servers.map { it.server to it.pictures })
        assertEquals(1, db.pictures().foundPosters().moviesFound)
    }
}
