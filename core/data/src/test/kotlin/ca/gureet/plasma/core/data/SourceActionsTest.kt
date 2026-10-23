// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.network.PlasmaHttp
import ca.gureet.plasma.source.xtream.XtreamException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** A source's actions on Portals & Playlists (brief 4.20) against a fake Xtream server. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SourceActionsTest {
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

    private val playlist: String get() = "${xtream.baseUrl}get.php?username=viewer&password=s3cret&type=m3u_plus"

    @Test fun editKeepsTheSourceAndItsPasswordUnlessANewOneIsTyped() = runTest {
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret", "Backup Line").source
        data.importer.importChannels(source.id)
        data.channels.toggleFavorite("${source.id}:9105")

        val edited = data.sources.updateXtream(source.id, xtream.baseUrl, "viewer", password = "", name = "Living Room")

        assertEquals(source.id, edited.id)
        assertEquals("Living Room", edited.name)
        assertEquals("sealed:terc3s", db.sources().get(source.id)?.passwordEnc)
        assertEquals(listOf("${source.id}:9105"), data.channels.favoriteIds.first())
        // The form shows everything but the password.
        assertEquals(EditableSource(SourceType.Xtream, "Living Room", xtream.baseUrl, "viewer", guideUrls = ""), data.sources.editable(source.id))
    }

    @Test fun anEditThatDoesntSignInChangesNothing() = runTest {
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret", "Backup Line").source

        expect<XtreamException.WrongLogin> { data.sources.updateXtream(source.id, xtream.baseUrl, "viewer", "wrong", "Other") }

        assertEquals("sealed:terc3s", db.sources().get(source.id)?.passwordEnc)
        assertEquals("Backup Line", data.sources.get(source.id)?.name)
    }

    @Test fun aPlaylistEditThatDoesntLoadIsUndone() = runTest {
        val source = data.sources.addM3u(playlist, "Extras", "http://lists.example.com/guide.xml").source
        data.importer.importChannels(source.id)
        assertEquals(
            EditableSource(SourceType.M3u, "Extras", playlist, username = "", guideUrls = "http://lists.example.com/guide.xml"),
            data.sources.editable(source.id),
        )

        val wrong = playlist.replace("s3cret", "wrong")
        expect<PlaylistRefused> {
            data.sources.keepingOnFailure(source.id) {
                data.sources.updateM3u(source.id, wrong, "Extras 2", guideUrls = null)
                data.importer.importChannels(source.id)
            }
        }

        assertEquals(playlist, data.sources.editable(source.id)?.address)
        assertEquals("Extras", data.sources.get(source.id)?.name)
        assertEquals(5, data.channels.channels.first().size)
    }

    @Test fun setAsPrimaryPutsTheSourceFirstAndKeepsTheOthersInOrder() = runTest {
        val first = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source
        val second = data.sources.addM3u(playlist, "Extras").source
        val third = data.sources.addM3u("http://lists.example.com/three.m3u", "Three").source

        data.sources.setPrimary(third.id)

        assertEquals(listOf(third.id, first.id, second.id), data.sources.all().map { it.id })
        assertTrue(data.sources.get(third.id)!!.isPrimary)
        assertFalse(data.sources.get(first.id)!!.isPrimary)
    }

    @Test fun aSourceTurnedOffLeavesTheChannelListsAndIsntRefreshed() = runTest {
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source
        data.importer.importChannels(source.id)

        data.sources.setEnabled(source.id, false)

        assertTrue(data.channels.channels.first().isEmpty())
        assertEquals(mapOf(source.id to 5), data.channels.countsBySource.first())
        assertFalse(data.sources.get(source.id)!!.enabled)

        data.sources.setEnabled(source.id, true)
        assertEquals(5, data.channels.channels.first().size)
    }

    @Test fun testConnectionSignsInAgainAndTimesIt() = runTest {
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source

        assertTrue(data.sources.check(source.id) >= Duration.ZERO)

        xtream.accountStatus = "Expired"
        expect<XtreamException.NotActive> { data.sources.check(source.id) }
    }

    @Test fun testConnectionAsksAPlaylistsServerForIt() = runTest {
        val source = data.sources.addM3u(playlist, "Extras").source
        data.sources.check(source.id)

        xtream.playlistCode = 404
        expect<PlaylistRefused> { data.sources.check(source.id) }
    }

    @Test fun playbackForThisSourceIsKeptAndANewGuideOffsetMovesTheGuide() = runTest {
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source
        data.importer.importChannels(source.id)
        data.importer.importGuide(source.id)
        val arena = data.channels.get("${source.id}:9105")!!.epgKey!!
        val day = Instant.parse("2026-10-21T00:00:00Z")
        assertEquals(Instant.parse("2026-10-21T19:45:00Z"), data.guide.forChannel(arena, day, day.plus(Duration.ofDays(1))).first().start)

        val chosen = SourcePlayback(streamFormat = "m3u8", bufferChoice = 4, audioOutputChoice = 2, epgOffsetMinutes = 60, catchupOffsetMinutes = 0)
        data.sources.setPlayback(source.id, chosen)

        assertEquals(chosen, data.sources.get(source.id)!!.playback)
        assertEquals("m3u8", data.sources.get(source.id)!!.streamFormat)
        // An hour later, without loading the guide again.
        assertEquals(Instant.parse("2026-10-21T20:45:00Z"), data.guide.forChannel(arena, day, day.plus(Duration.ofDays(1))).first().start)

        // The next guide refresh reads the offset too.
        data.importer.importGuide(source.id)
        assertEquals(Instant.parse("2026-10-21T20:45:00Z"), data.guide.forChannel(arena, day, day.plus(Duration.ofDays(1))).first().start)
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
