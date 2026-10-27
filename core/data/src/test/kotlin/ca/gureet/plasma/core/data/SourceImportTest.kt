// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.network.PlasmaHttp
import ca.gureet.plasma.source.xtream.XtreamException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The add-source pipeline (brief 6.4) against a fake Xtream server. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SourceImportTest {
    private val xtream = FakeXtreamServer()
    private val clock = FixedClock(Instant.parse("2026-10-21T20:41:00Z"), ZoneOffset.UTC)
    private lateinit var db: PlasmaDatabase
    private lateinit var settingsDir: File
    private lateinit var data: PlasmaData

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsDir = Files.createTempDirectory("settings").toFile()
        val settings = SettingsStore(PreferenceDataStoreFactory.create { File(settingsDir, "settings.preferences_pb") })
        data = PlasmaData(db, PlasmaHttp.client("Plasma test"), settings, FakeSecretBox(), clock)
    }

    @After fun tearDown() {
        db.close()
        xtream.close()
        settingsDir.deleteRecursively()
    }

    private suspend fun addAndImport(): Source {
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source
        data.importer.importChannels(source.id)
        return source
    }

    private suspend fun channel(name: String) = data.channels.channels.first().first { it.name == name }

    @Test fun addsAnAccountAndImportsItsChannels() = runTest {
        val added = data.sources.addXtream(xtream.baseUrl, " viewer ", "s3cret")
        val source = added.source

        assertTrue(added.isNew)
        assertEquals("Active", source.status)
        assertTrue(source.isPrimary)
        assertEquals(2, source.maxConnections)
        assertEquals("ts", source.streamFormat)
        assertEquals("viewer", source.username)
        // The password is kept sealed.
        assertEquals("sealed:terc3s", db.sources().get(source.id)?.passwordEnc)

        val result = data.importer.importChannels(source.id)

        assertEquals(ChannelImport(channels = 5, groups = 3, newChannels = 5, newDuplicates = 0), result)
        val channels = data.channels.channels.first()
        // Pulse has no number of its own, so it follows the highest one, 108.
        assertEquals(listOf(101, 105, 107, 108, 109), channels.map { it.number })
        val arena = channels.first { it.name == "Arena Sports 1" }
        assertEquals("${source.id}:9105", arena.id)
        assertEquals("Sports", arena.groupName)
        assertEquals(7, arena.catchupDays)
        assertEquals(NumberReason.Auto, channels.last().numberReason)
        assertEquals(listOf("Sports", "News", "Music"), data.channels.groups.first().map { it.name })
        assertNotNull(data.sources.get(source.id)?.channelsRefreshedAt)
    }

    @Test fun aWrongLoginOrAddressSavesNothing() = runTest {
        expect<XtreamException.WrongLogin> { data.sources.addXtream(xtream.baseUrl, "viewer", "wrong") }
        expect<InvalidServerAddress> { data.sources.addXtream("not an address", "viewer", "s3cret") }
        assertTrue(data.sources.all().isEmpty())
    }

    @Test fun addingTheSameAccountAgainUpdatesIt() = runTest {
        val first = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret")
        val second = data.sources.addXtream(xtream.baseUrl + "player_api.php", "viewer", "s3cret")
        assertEquals(first.source.id, second.source.id)
        assertFalse(second.isNew)
        assertEquals(1, data.sources.all().size)
    }

    @Test fun aRefreshKeepsNumbersFavoritesAndHiddenChannels() = runTest {
        val source = addAndImport()
        data.channels.toggleFavorite(channel("Courtside+").id)
        data.channels.setHidden(channel("Velocity Motor").id, true)

        // The provider renumbers Arena Sports 1, drops Northline News and adds a channel on 105.
        xtream.streams = xtream.streams.filter { it.name != "Northline News" }.map { if (it.id == "9105") it.copy(num = 110) else it } +
            FakeStream("9300", 105, "Harbour TV")
        val result = data.importer.importChannels(source.id)

        assertEquals(1, result.newDuplicates)
        assertEquals(1, result.newChannels)
        val channels = data.channels.channels.first()
        assertEquals(105, channels.first { it.name == "Arena Sports 1" }.number)
        val harbour = channels.first { it.name == "Harbour TV" }
        assertEquals(1105, harbour.number)
        assertEquals(NumberReason.DuplicateFix, harbour.numberReason)
        assertTrue(channels.none { it.name == "Northline News" })
        assertTrue(channels.first { it.name == "Velocity Motor" }.hidden)
        assertEquals(listOf(channel("Courtside+").id), data.channels.favoriteIds.first())
    }

    @Test fun importsTheGuideAndMatchesChannelsByIdThenName() = runTest {
        val source = addAndImport()

        val result = data.importer.importGuide(source.id)

        // Arena Sports 1 and Northline News match by id, Courtside+ by name; nobody claims the last one.
        assertEquals(GuideImport(programmes = 4, matchedChannels = 3), result)
        val arena = channel("Arena Sports 1")
        assertEquals("${source.id}:arena1.example", arena.epgKey)
        assertEquals("${source.id}:courtside.example", channel("Courtside+").epgKey)
        assertNull(channel("Pulse").epgKey)

        val now = clock.now()
        val window = data.guide.window(now.minusSeconds(3_600), now.plusSeconds(4 * 3_600)).first()
        val nowNext = GuideRepository.nowNext(window.getValue(arena.epgKey!!), now)
        assertEquals("Harbour City v Redmoor", nowNext.now?.title)
        assertEquals("Match of the Week Extra", nowNext.next?.title)
        assertNotNull(data.sources.get(source.id)?.guideRefreshedAt)
    }

    @Test fun aGuideRefreshReplacesTheGuideWithoutDoubles() = runTest {
        val source = addAndImport()
        data.importer.importGuide(source.id)

        data.importer.importGuide(source.id)

        val arena = channel("Arena Sports 1")
        val window = data.guide.window(clock.now().minusSeconds(3_600), clock.now().plusSeconds(4 * 3_600)).first()
        assertEquals(listOf("Harbour City v Redmoor", "Match of the Week Extra"), window.getValue(arena.epgKey!!).map { it.title })
        assertEquals(4, db.programmes().countForSource(source.id))
    }

    @Test fun aGuideBeingWrittenStaysOutOfSight() = runTest {
        val source = addAndImport()
        data.importer.importGuide(source.id)
        val committed = db.sources().get(source.id)?.guideGeneration!!

        // What a refresh in progress has written so far.
        db.programmes().insertAll(
            listOf(
                ca.gureet.plasma.core.database.ProgrammeEntity(
                    sourceId = source.id, epgKey = "${source.id}:arena1.example",
                    startAt = clock.now().toEpochMilli() - 60_000, endAt = clock.now().toEpochMilli() + 60_000,
                    title = "Half-written", subtitle = null, description = null, category = null, icon = null,
                    season = null, episode = null, isNew = false, generation = committed + 1,
                ),
            ),
        )

        val window = data.guide.window(clock.now(), clock.now().plusSeconds(60)).first()
        assertTrue(window.values.flatten().none { it.title == "Half-written" })
    }

    @Test fun aFailedGuideImportKeepsTheOldGuide() = runTest {
        val source = addAndImport()
        data.importer.importGuide(source.id)

        xtream.guideCode = 500
        expect<IOException> { data.importer.importGuide(source.id) }

        assertEquals(4, db.programmes().countForSource(source.id))
        val error = data.sources.get(source.id)?.lastError.orEmpty()
        assertTrue(error, "HTTP 500" in error)
        assertFalse(error, "s3cret" in error)
    }

    @Test fun aFailedRefreshKeepsTheChannels() = runTest {
        val source = addAndImport()

        xtream.accountStatus = "Expired"
        expect<XtreamException.NotActive> { data.importer.importChannels(source.id) }

        assertEquals(5, data.channels.channels.first().size)
        assertEquals("The account is Expired", data.sources.get(source.id)?.lastError)
    }

    @Test fun removingASourceTakesEverythingWithIt() = runTest {
        val source = addAndImport()
        data.importer.importGuide(source.id)
        data.channels.toggleFavorite(channel("Arena Sports 1").id)
        data.channels.watched(channel("Arena Sports 1").id, listId = null)

        data.sources.remove(source.id)

        assertTrue(data.channels.channels.first().isEmpty())
        assertEquals(0, db.programmes().countForSource(source.id))
        assertTrue(data.channels.favoriteIds.first().isEmpty())
        assertTrue(data.channels.recentIds.first().isEmpty())
        assertTrue(data.sources.all().isEmpty())
    }

    @Test fun remembersWhatWasWatchedLast() = runTest {
        addAndImport()
        val arena = channel("Arena Sports 1")

        data.channels.watched(arena.id, listId = "favorites")

        assertEquals(LastWatched(arena.id, "favorites"), data.channels.lastWatched.first())
        assertEquals(listOf(arena.id), data.channels.recentIds.first())
    }

    @Test fun refreshesWhatsStaleAtStartUp() = runTest {
        data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret")

        data.refreshStale()

        assertEquals(5, data.channels.channels.first().size)
        assertEquals(3, data.vod.movieCategories.first().sumOf { it.count })
        assertEquals("Harbour City v Redmoor", data.guide.window(clock.now(), clock.now().plusSeconds(60)).first().values.flatten().first { it.title.startsWith("Harbour") }.title)
        val requests = xtream.server.requestCount
        data.refreshStale()
        assertEquals("Nothing was stale the second time", requests, xtream.server.requestCount)
    }

    // ---- Movies and series (brief 5.9 and 6.4) -----------------------------------------------------

    @Test fun importsMoviesAndSeriesByCategory() = runTest {
        val source = addAndImport()

        assertEquals(VodImport(movies = 3, series = 2), data.importer.importVod(source.id))

        val categories = data.vod.movieCategories.first()
        assertEquals(listOf("Thrillers" to 2, "Science Fiction" to 1), categories.map { it.name to it.count })
        assertEquals(listOf("Neon Tide", "Glass Orchard"), data.vod.moviesIn(categories.first().id).first().map { it.name })
        val neon = data.vod.moviesIn(categories.first().id).first().first()
        assertEquals(Movie("${source.id}:7101", "Neon Tide", null, "${source.id}:movie:30", Instant.ofEpochSecond(1_792_500_000), 7.2f, 2026), neon)
        assertEquals(listOf("Drama" to 1, "Crime" to 1), data.vod.seriesCategories.first().map { it.name to it.count })
        // Recently Added goes back 30 days; New Episodes, 14.
        assertEquals(listOf("Neon Tide"), data.vod.recentMovies().first().map { it.name })
        assertEquals(listOf("Atlas Station"), data.vod.newEpisodes().first().map { it.name })
        assertEquals(clock.now(), data.sources.get(source.id)?.vodRefreshedAt)
    }

    @Test fun aVodRefreshKeepsIdsAndDropsWhatsGone() = runTest {
        val source = addAndImport()
        data.importer.importVod(source.id)

        xtream.movies = listOf(FakeTitle("7101", "Neon Tide", "31", added = 1_792_500_000), FakeTitle("7104", "Ember Road", "31", added = null))
        xtream.movieCategories = listOf("31" to "Science Fiction")
        data.importer.importVod(source.id)

        val categories = data.vod.movieCategories.first()
        assertEquals(listOf("Science Fiction" to 2), categories.map { it.name to it.count })
        assertEquals(listOf("${source.id}:7101", "${source.id}:7104"), data.vod.moviesIn(categories.single().id).first().map { it.id })
    }

    @Test fun aFailedOrEmptyVodRefreshKeepsTheCatalog() = runTest {
        val source = addAndImport()
        data.importer.importVod(source.id)

        xtream.movieCode = 500
        expect<IOException> { data.importer.importVod(source.id) }
        assertEquals(3, data.vod.movieCategories.first().sumOf { it.count })
        assertNotNull(data.sources.get(source.id)?.lastError)

        // A catalog that comes back empty keeps what there was, and isn't asked for again till next week.
        xtream.movieCode = 200
        xtream.movies = emptyList()
        xtream.series = emptyList()
        assertEquals(VodImport(movies = 0, series = 0), data.importer.importVod(source.id))
        assertEquals(3, data.vod.movieCategories.first().sumOf { it.count })
        assertEquals(clock.now(), data.sources.get(source.id)?.vodRefreshedAt)
    }

    @Test fun removingASourceTakesItsMoviesAndSeries() = runTest {
        val source = addAndImport()
        data.importer.importVod(source.id)

        data.sources.remove(source.id)

        assertEquals(emptyList<VodCategory>(), data.vod.movieCategories.first())
        assertEquals(emptyList<VodCategory>(), data.vod.seriesCategories.first())
    }

    private inline fun <reified T : Throwable> expect(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return
            throw AssertionError("Expected ${T::class.simpleName}, got $error", error)
        }
        fail("Expected ${T::class.simpleName}")
    }
}
