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
}
