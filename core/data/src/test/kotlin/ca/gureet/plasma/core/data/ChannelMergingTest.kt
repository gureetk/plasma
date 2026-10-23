// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
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
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Merge Duplicate Channels (brief 5.1) on one server added twice: as an Xtream login, and as the
 * playlist the same server hands out.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChannelMergingTest {
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

    private suspend fun bothSources(): Pair<Source, Source> {
        val login = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret", "Home").source
        data.importer.importChannels(login.id)
        val playlist = data.sources.addM3u("${xtream.baseUrl}get.php?username=viewer&password=s3cret&type=m3u_plus", "Backup").source
        data.importer.importChannels(playlist.id)
        return login to playlist
    }

    @Test fun theSameChannelFromTwoSourcesIsOneWithBothAsCandidates() = runTest {
        val (login, playlist) = bothSources()

        val channels = data.channels.channels.first()

        // Five channels, not ten, under the primary source's numbers.
        assertEquals(listOf(101, 105, 107, 108, 109), channels.map { it.number })
        val arena = channels.first { it.name == "Arena Sports 1" }
        assertEquals("${login.id}:9105", arena.id)
        assertEquals(listOf("${login.id}:9105", "${playlist.id}:id:9105"), arena.candidates)
        assertTrue(arena.isMerged)
        // Without a guide id, the name and quality match: Courtside+ is one too.
        assertEquals(2, channels.first { it.name == "Courtside+" }.candidates.size)
        assertTrue(channels.all { it.sourceId == login.id })
    }

    @Test fun mergingCanBeTurnedOff() = runTest {
        bothSources()

        data.settings.choose(SettingKeys.MERGE_DUPLICATES, 1)

        val channels = data.channels.channels.first()
        assertEquals(10, channels.size)
        assertFalse(channels.any { it.isMerged })
    }

    @Test fun aSourceTurnedOffLeavesItsChannelsOutOfTheMerge() = runTest {
        val (_, playlist) = bothSources()

        data.sources.setEnabled(playlist.id, false)

        assertTrue(data.channels.channels.first().none { it.isMerged })
    }
}
