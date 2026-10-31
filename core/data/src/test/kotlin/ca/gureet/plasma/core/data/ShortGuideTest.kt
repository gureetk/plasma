// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.network.PlasmaHttp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** `get_short_epg`, the guide's fallback for now and next (brief 5.3), against the fake Xtream server. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ShortGuideTest {
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

    private fun shortEpgCalls() = xtream.requests.count { it.url.queryParameter("action") == "get_short_epg" }

    @Test fun aChannelTheGuideMissesHasItsServersNowAndNext() = runTest {
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source
        data.importer.importChannels(source.id)
        val courtside = data.channels.channels.first().first { it.name == "Courtside+" }

        data.shortGuide.fetch(courtside)

        // What's over is left out; the titles come out of Base64.
        val programmes = data.shortGuide.programmes.value.getValue(courtside.id)
        assertEquals(listOf("City Lights v Northport", "Courtside Tonight"), programmes.map { it.title })
        assertEquals(Instant.parse("2026-10-21T20:30:00Z"), programmes.first().start)
        assertEquals(Instant.parse("2026-10-21T23:00:00Z"), programmes.first().end)

        // Asked again within the half hour, it isn't asked of the server again.
        data.shortGuide.fetch(courtside)
        assertEquals(1, shortEpgCalls())
    }

    @Test fun onlyXtreamServersAreAsked() = runTest {
        val source = data.sources.addM3u("${xtream.baseUrl}get.php?username=viewer&password=s3cret&type=m3u_plus&output=ts").source
        data.importer.importChannels(source.id)
        val courtside = data.channels.channels.first().first { it.name == "Courtside+" }

        data.shortGuide.fetch(courtside)

        assertNull(data.shortGuide.programmes.value[courtside.id])
        assertEquals(0, shortEpgCalls())
    }
}
