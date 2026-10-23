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
import kotlinx.coroutines.test.runTest

/** Where programs play from the archive (brief 5.5, 7.2 and 7.3), against the fake Xtream server. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ArchiveRepositoryTest {
    private val xtream = FakeXtreamServer()
    private val clock = FixedClock(Instant.parse("2026-10-21T20:41:00Z"), ZoneOffset.UTC)
    private lateinit var db: PlasmaDatabase
    private lateinit var settingsDir: File
    private lateinit var data: PlasmaData

    private val match = Instant.parse("2026-10-21T19:45:00Z")

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

    @Test fun xtreamArchivesAreWrittenInTheServersTimeZone() = runTest {
        val source = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source
        data.importer.importChannels(source.id)
        data.importer.importGuide(source.id)

        // Harbour City v Redmoor, 19:45–22:00 UTC: 20:45 in London, 135 minutes.
        val stream = data.archive.streamOf("${source.id}:9105", match)!!
        assertEquals("${xtream.baseUrl}timeshift/viewer/s3cret/135/2026-10-21:20-45/9105.ts", stream.url)

        // Northline News keeps no archive; Arena Sports 1's goes back seven days, and not ahead.
        assertNull(data.archive.streamOf("${source.id}:9101", match))
        assertNull(data.archive.streamOf("${source.id}:9105", match.minusSeconds(8 * 86_400)))
        assertNull(data.archive.streamOf("${source.id}:9105", Instant.parse("2026-10-21T22:00:00Z")))
    }

    @Test fun playlistsSayHowTheirArchivesAreReached() = runTest {
        xtream.playlistBody = """
            #EXTM3U url-tvg="${xtream.baseUrl}xmltv.php?username=viewer&password=s3cret"
            #EXTINF:-1 tvg-id="arena1.example" catchup="default" catchup-days="7" catchup-source="http://archive.example/arena1?start={utc}&end={utcend}",Arena Sports 1
            http://line.example.org/live/viewer/s3cret/9105.ts
            #EXTINF:-1 tvg-id="velocity.example" catchup-days="2",Velocity Motor
            http://line.example.org/live/viewer/s3cret/9107.ts
            #EXTINF:-1 tvg-id="northline.example",Northline News
            http://line.example.org/live/viewer/s3cret/9101.ts
        """.trimIndent()
        val source = data.sources.addM3u("${xtream.baseUrl}get.php?username=viewer&password=s3cret&type=m3u_plus&output=ts").source
        data.importer.importChannels(source.id)
        data.importer.importGuide(source.id)

        val end = Instant.parse("2026-10-21T22:00:00Z")
        assertEquals("http://archive.example/arena1?start=${match.epochSecond}&end=${end.epochSecond}", data.archive.streamOf("${source.id}:id:9105", match)!!.url)
        // No mode or template, but an Xtream-style address: its archive is beside it. No guide, so an hour.
        assertEquals("http://line.example.org/timeshift/viewer/s3cret/60/2026-10-21:19-45/9107.ts", data.archive.streamOf("${source.id}:id:9107", match)!!.url)
        assertNull(data.archive.streamOf("${source.id}:id:9101", match))
    }
}
