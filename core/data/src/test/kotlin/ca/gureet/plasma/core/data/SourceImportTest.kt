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
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret")
        data.importer.importChannels(source.id)
        return source
    }

    private suspend fun channel(name: String) = data.channels.channels.first().first { it.name == name }

    @Test fun addsAnAccountAndImportsItsChannels() = runTest {
        val source = data.sources.addXtream(xtream.baseUrl, " viewer ", "s3cret")

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
        assertEquals(first.id, second.id)
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
        assertEquals("Harbour City v Redmoor", data.guide.window(clock.now(), clock.now().plusSeconds(60)).first().values.flatten().first { it.title.startsWith("Harbour") }.title)
        val requests = xtream.server.requestCount
        data.refreshStale()
        assertEquals("Nothing was stale the second time", requests, xtream.server.requestCount)
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
