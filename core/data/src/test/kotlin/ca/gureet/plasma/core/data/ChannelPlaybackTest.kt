// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest

/** The engine each channel has of its own (brief 5.4.3). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChannelPlaybackTest {
    private lateinit var db: PlasmaDatabase
    private lateinit var playback: ChannelPlaybackRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playback = ChannelPlaybackRepository(db, FixedClock(Instant.parse("2026-10-21T20:41:00Z"), ZoneOffset.UTC))
    }

    @After fun tearDown() = db.close()

    @Test fun aChannelKeepsTheEngineItWasGivenUntilForgotten() = runTest {
        assertNull(playback.engineFor("home:105"))

        playback.rememberEngine("home:105", "Mpv", EngineReason.Fallback)
        playback.rememberEngine("home:108", "Vlc", EngineReason.User)
        playback.rememberEngine("home:108", "Mpv", EngineReason.User)

        assertEquals("Mpv", playback.engineFor("home:105"))
        assertEquals("Mpv", playback.engineFor("home:108"))
        assertEquals("USER", db.channelPlayback().get("home:108")?.engineReason)
        assertEquals(2, playback.forgetEngines())
        assertNull(playback.engineFor("home:105"))
        assertEquals(0, playback.forgetEngines())
    }

    @Test fun removingASourceForgetsItsChannels() = runTest {
        playback.rememberEngine("home:105", "Mpv", EngineReason.User)
        playback.rememberEngine("backup:105", "Mpv", EngineReason.User)

        db.channelPlayback().deleteForSource("home:")

        assertNull(playback.engineFor("home:105"))
        assertEquals("Mpv", playback.engineFor("backup:105"))
    }
}
