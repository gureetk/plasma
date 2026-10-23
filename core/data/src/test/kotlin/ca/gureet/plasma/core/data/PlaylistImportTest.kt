// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.network.PlasmaHttp
import ca.gureet.plasma.source.m3u.NotAPlaylist
import okhttp3.HttpUrl.Companion.toHttpUrl
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

/** Adding an M3U playlist by its address (brief 4.21, 7.3), served by the fake Xtream server's `get.php`. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlaylistImportTest {
    private val xtream = FakeXtreamServer()
    private val clock = FixedClock(Instant.parse("2026-10-21T20:41:00Z"), ZoneOffset.UTC)
    private lateinit var db: PlasmaDatabase
    private lateinit var settingsDir: File
    private lateinit var data: PlasmaData

    private val playlistUrl get() = "${xtream.baseUrl}get.php?username=viewer&password=s3cret&type=m3u_plus&output=ts"

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
        val source = data.sources.addM3u(playlistUrl, "Home Playlist").source
        data.importer.importChannels(source.id)
        return source
    }

    private suspend fun channel(name: String) = data.channels.channels.first().first { it.name == name }

    @Test fun addsAPlaylistAndImportsItsLiveChannels() = runTest {
        val added = data.sources.addM3u(playlistUrl, " Home Playlist ")
        val source = added.source

        assertTrue(added.isNew)
        assertEquals(SourceType.M3u, source.type)
        assertEquals("Home Playlist", source.name)
        assertTrue(source.isPrimary)
        // Settings shows the server; the address with its login is kept sealed.
        val url = playlistUrl.toHttpUrl()
        assertEquals("http://${url.host}:${url.port}", source.server)
        assertTrue(db.sources().get(source.id)?.playlistUrlEnc.orEmpty().startsWith("sealed:"))

        val result = data.importer.importChannels(source.id)

        // The movie stays out of Live TV.
        assertEquals(ChannelImport(channels = 5, groups = 3, newChannels = 5, newDuplicates = 0), result)
        val channels = data.channels.channels.first()
        assertEquals(listOf(101, 105, 107, 108, 109), channels.map { it.number })
        val arena = channel("Arena Sports 1")
        assertEquals("${source.id}:id:9105", arena.id)
        assertEquals("Sports", arena.groupName)
        assertEquals(listOf("News", "Sports", "Music"), data.channels.groups.first().map { it.name })
    }

    @Test fun channelAddressesAreKeptEncrypted() = runTest {
        addAndImport()
        val arena = channel("Arena Sports 1")

        val stored = db.channels().get(arena.id)?.streamUrlEnc.orEmpty()
        assertFalse(stored, "s3cret" in stored)
        assertEquals("${xtream.baseUrl}live/viewer/s3cret/9105.ts", data.channels.streamOf(arena.id)?.url)
    }

    @Test fun theGuideComesFromThePlaylistsHeader() = runTest {
        val source = addAndImport()

        val result = data.importer.importGuide(source.id)

        assertEquals(GuideImport(programmes = 4, matchedChannels = 3), result)
        assertEquals("${source.id}:arena1.example", channel("Arena Sports 1").epgKey)
        assertNull(channel("Pulse").epgKey)
        assertNotNull(data.sources.get(source.id)?.guideRefreshedAt)
    }

    @Test fun aGuideTypedInWinsOverThePlaylists() = runTest {
        val source = data.sources.addM3u(playlistUrl, "Home Playlist", guideUrls = "${xtream.baseUrl}elsewhere.xml").source
        data.importer.importChannels(source.id)

        // The fake server has nothing there, which shows which guide was asked for.
        expect<IOException> { data.importer.importGuide(source.id) }
        assertTrue("HTTP 404" in data.sources.get(source.id)?.lastError.orEmpty())
    }

    @Test fun aWebPageIsNotAPlaylist() = runTest {
        xtream.playlistBody = "<!DOCTYPE html>\n<html><body>Sign in</body></html>"
        val source = data.sources.addM3u(playlistUrl).source

        expect<NotAPlaylist> { data.importer.importChannels(source.id) }
        assertTrue(data.channels.channels.first().isEmpty())
    }

    @Test fun aRefusedAddressSaysSo() = runTest {
        val source = data.sources.addM3u(playlistUrl.replace("s3cret", "wrong")).source

        expect<PlaylistRefused> { data.importer.importChannels(source.id) }
        val error = data.sources.get(source.id)?.lastError.orEmpty()
        assertFalse(error, "wrong" in error)
    }

    @Test fun aRefreshThatFindsNoChannelsKeepsTheOldOnes() = runTest {
        val source = addAndImport()

        xtream.playlistBody = "#EXTM3U\n"
        expect<NoLiveChannels> { data.importer.importChannels(source.id) }

        assertEquals(5, data.channels.channels.first().size)
    }

    @Test fun addingTheSamePlaylistAgainUpdatesIt() = runTest {
        val first = data.sources.addM3u(playlistUrl, "Home Playlist")
        val second = data.sources.addM3u(playlistUrl, "Living Room")

        assertFalse(second.isNew)
        assertEquals(first.source.id, second.source.id)
        assertEquals("Living Room", second.source.name)
        assertEquals(1, data.sources.all().size)
    }

    @Test fun anAddressMustBeAWebAddress() = runTest {
        expect<InvalidServerAddress> { data.sources.addM3u("playlist.m3u") }
        expect<InvalidServerAddress> { data.sources.addM3u(playlistUrl, guideUrls = "guide.xml") }
        assertTrue(data.sources.all().isEmpty())
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
