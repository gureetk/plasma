// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.network.PlasmaHttp
import ca.gureet.plasma.source.stalker.StalkerException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** Stalker portals (brief 7.1) through the add-source pipeline, against a fake portal. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StalkerSourceTest {
    private val portal = FakeStalkerPortal()
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
        portal.close()
        settingsDir.deleteRecursively()
    }

    private suspend fun addAndImport(): Source {
        val source = data.sources.addStalker(portal.baseUrl, "00-1a-79-4c-2e-91", "Home Portal").source
        data.importer.importChannels(source.id)
        return source
    }

    @Test fun advancedDetailsAreKeptAndSignInEveryTime() = runTest {
        portal.wantsLogin = "family:s3cret"
        val details = SourceDetails(
            userAgent = "Custom agent",
            stbModel = "MAG254",
            serialNumber = "SN0123456789A",
            deviceId = "DEV1",
            deviceId2 = "DEV2",
            signature = "SIG",
            timeZone = "America/Toronto",
            login = "family",
            password = "s3cret",
        )
        val source = data.sources.addStalker(portal.baseUrl, "00:1A:79:4C:2E:91", "Home Portal", details).source

        val stored = db.sources().get(source.id)!!
        assertEquals("MAG254", stored.stbModel)
        assertEquals("America/Toronto", stored.portalTimeZone)
        assertEquals("family", stored.username)
        // The login's password is sealed, as every password is.
        assertEquals("sealed:terc3s", stored.passwordEnc)

        // Edit shows them all but the password.
        val editable = data.sources.editable(source.id)!!
        assertEquals(details.copy(password = null), editable.details)
        assertEquals("", editable.username)

        // Each sign-in after that, as after the app starts again, is the registered box's, with the login.
        portal.requests.clear()
        val restarted = SourceRepository(db, FakeSecretBox(), PlasmaHttp.client("Plasma test"), clock)
        restarted.stalkerClient(source.id).connect()
        assertEquals(listOf("handshake", "get_profile", "do_auth", "get_main_info"), portal.actions())
        val profile = portal.requests.first { it.url.queryParameter("action") == "get_profile" }
        assertEquals("MAG254", profile.url.queryParameter("stb_type"))
        assertEquals("SN0123456789A", profile.url.queryParameter("sn"))
        assertEquals("DEV1", profile.url.queryParameter("device_id"))
        assertEquals("Custom agent", profile.headers["User-Agent"])
        assertTrue(profile.headers["Cookie"]!!.endsWith("timezone=America%2FToronto"))
    }

    @Test fun editKeepsThePortalsPasswordWhenNoneIsTyped() = runTest {
        portal.wantsLogin = "family:s3cret"
        val source = data.sources.addStalker(portal.baseUrl, "00:1A:79:4C:2E:91", "Home Portal", SourceDetails(login = "family", password = "s3cret")).source

        data.sources.updateStalker(source.id, portal.baseUrl, "00:1A:79:4C:2E:91", "Living Room", SourceDetails(login = "family", password = ""))

        assertEquals("Living Room", data.sources.get(source.id)?.name)
        assertEquals("sealed:terc3s", db.sources().get(source.id)?.passwordEnc)

        // A wrong one says so, and the source stays as it was.
        try {
            data.sources.keepingOnFailure(source.id) {
                data.sources.updateStalker(source.id, portal.baseUrl, "00:1A:79:4C:2E:91", "Wrong", SourceDetails(login = "family", password = "nope"))
            }
            fail("A wrong login was kept")
        } catch (expected: StalkerException.WrongLogin) {
            assertEquals("Living Room", data.sources.get(source.id)?.name)
        }
    }

    @Test fun addsAPortalWithItsMacAddressSealed() = runTest {
        val added = data.sources.addStalker(portal.baseUrl, "00-1a-79-4c-2e-91", "Home Portal")
        val source = added.source

        assertTrue(added.isNew)
        assertEquals(SourceType.Stalker, source.type)
        assertEquals("Home Portal", source.name)
        assertEquals("Active", source.status)
        assertEquals(Instant.parse("2027-03-12T00:00:00Z"), source.expiresAt)
        assertEquals("sealed:19:E2:C4:97:A1:00", db.sources().get(source.id)?.macEnc)
        assertEquals("00:1A:79:4C:2E:91", data.sources.get(source.id)?.mac)
        // The timezone cookie is the TV's zone, by name.
        assertTrue(portal.requests.first().headers["Cookie"]!!.endsWith("timezone=UTC"))

        // The same portal and box again is the same source.
        val again = data.sources.addStalker(portal.baseUrl.replace("/c/", "/portal.php"), "00:1A:79:4C:2E:91")
        assertFalse(again.isNew)
        assertEquals(source.id, again.source.id)
        assertEquals(1, data.sources.all().size)
    }

    @Test fun aBoxThePortalDoesntKnowOrAnAddressThatIsntOneSavesNothing() = runTest {
        expect<StalkerException.Refused> { data.sources.addStalker(portal.baseUrl, "00:1A:79:00:00:01") }
        expect<InvalidMacAddress> { data.sources.addStalker(portal.baseUrl, "00:1A:79") }
        expect<InvalidServerAddress> { data.sources.addStalker("not a portal", "00:1A:79:4C:2E:91") }
        portal.blocked = true
        expect<StalkerException.Blocked> { data.sources.addStalker(portal.baseUrl, "00:1A:79:4C:2E:91") }
        assertTrue(data.sources.all().isEmpty())
    }

    @Test fun importsChannelsInTheirGenres() = runTest {
        val source = addAndImport()

        val channels = data.channels.channels.first()
        assertEquals(listOf(101, 105, 108), channels.map { it.number })
        val arena = channels.first { it.name == "Arena Sports 1" }
        assertEquals("${source.id}:105", arena.id)
        assertEquals("Sports", arena.groupName)
        assertEquals(7, arena.catchupDays)
        assertEquals(listOf("Sports", "News"), data.channels.groups.first().map { it.name })
        // The channel's command is kept for play time, encrypted.
        assertEquals("ffrt http://localhost/ch/105_", data.channels.streamOf(arena.id)?.url)
        assertTrue(db.channels().get(arena.id)?.streamUrlEnc != "ffrt http://localhost/ch/105_")
    }

    @Test fun theGuideComesFromThePortal() = runTest {
        val source = addAndImport()

        val result = data.importer.importGuide(source.id)

        assertEquals(GuideImport(programmes = 2, matchedChannels = 2), result)
        val arena = data.channels.get("${source.id}:105")!!
        assertEquals("${source.id}:portal:105", arena.epgKey)
        val day = Instant.parse("2026-10-21T00:00:00Z")
        val programmes = data.guide.forChannel(arena.epgKey!!, day, day.plus(Duration.ofDays(1)))
        assertEquals(listOf("Harbour City v Redmoor"), programmes.map { it.title })
        assertEquals(Instant.parse("2026-10-21T19:45:00Z"), programmes.single().start)

        // A portal with nothing in its guide this time keeps the guide it had.
        val guide = portal.guide
        portal.guide = "false"
        assertEquals(GuideImport(programmes = 0, matchedChannels = 0), data.importer.importGuide(source.id))
        assertEquals(1, data.guide.forChannel(arena.epgKey!!, day, day.plus(Duration.ofDays(1))).size)
        portal.guide = guide

        // The guide's time offset moves the portal's guide too.
        data.sources.setPlayback(source.id, data.sources.get(source.id)!!.playback.copy(epgOffsetMinutes = -60))
        data.importer.importGuide(source.id)
        assertEquals(Instant.parse("2026-10-21T18:45:00Z"), data.guide.forChannel(arena.epgKey!!, day, day.plus(Duration.ofDays(1))).single().start)
    }

    @Test fun programmesPlayFromThePortalsArchive() = runTest {
        val source = addAndImport()
        data.importer.importGuide(source.id)

        val stream = data.archive.streamOf("${source.id}:105", Instant.parse("2026-10-21T19:45:00Z"))

        assertEquals("http://stream.example/archive/9001.ts?token=tok1", stream?.url)
        assertTrue(stream!!.userAgent!!.contains("MAG200"))
    }

    @Test fun oneSignInServesTheImportsAndTheLinks() = runTest {
        val source = addAndImport()
        val portalClient = data.sources.stalkerClient(source.id)

        assertEquals("http://stream.example/live/105.ts?token=tok1", portalClient.createLink("ffrt http://localhost/ch/105_"))
        assertEquals(1, portal.actions().count { it == "handshake" })
    }

    @Test fun editTestAndTheFormKeepTheBox() = runTest {
        val source = addAndImport()

        assertEquals(EditableSource(SourceType.Stalker, "Home Portal", portal.baseUrl, username = "", guideUrls = "", mac = "00:1A:79:4C:2E:91"), data.sources.editable(source.id))
        assertTrue(data.sources.check(source.id) >= Duration.ZERO)

        val edited = data.sources.updateStalker(source.id, portal.baseUrl, "00:1A:79:4C:2E:91", "Living Room")
        assertEquals(source.id, edited.id)
        assertEquals("Living Room", edited.name)
        expect<StalkerException.Refused> { data.sources.updateStalker(source.id, portal.baseUrl, "00:1A:79:00:00:01", null) }
        assertEquals("00:1A:79:4C:2E:91", data.sources.get(source.id)?.mac)
    }

    @Test fun theTvHasOneMacAddressForPortals() = runTest {
        val mac = data.settings.deviceMac()

        assertTrue(mac, mac.startsWith("00:1A:79:"))
        assertEquals(mac, data.settings.deviceMac())
        assertNotNull(mac)
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
