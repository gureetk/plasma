// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.ChannelTracks
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.model.TrackPick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** What each channel keeps of its own: its engine (brief 5.4.3), sound (5.4.6), tracks and Picture Size (4.10). */
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

    @Test fun aChannelKeepsItsOwnSoundUntilReset() = runTest {
        assertEquals(ChannelSound("home:101"), playback.soundOf("home:101"))

        playback.setLevel("home:101", 3)
        playback.setLevel("home:110", -9)
        playback.decode("home:104")
        playback.rememberEngine("home:101", "Mpv", EngineReason.User)

        assertEquals(ChannelSound("home:101", levelDb = 3), playback.soundOf("home:101"))
        // Levels stop at −6 and +6 dB.
        assertEquals(-6, playback.soundOf("home:110").levelDb)
        assertEquals(ChannelSound("home:104", decoded = true), playback.soundOf("home:104"))
        assertEquals(listOf("home:101", "home:104", "home:110"), playback.sounds.first().map { it.channelId })

        // Forgetting the players leaves the sound; Reset leaves the player.
        playback.forgetEngines()
        assertEquals(3, playback.soundOf("home:101").levelDb)
        playback.rememberEngine("home:101", "Mpv", EngineReason.User)
        playback.resetSound("home:101")
        assertEquals("Mpv", playback.engineFor("home:101"))
        assertEquals(0, playback.soundOf("home:101").levelDb)
        assertEquals(2, playback.resetSounds())
        assertTrue(playback.sounds.first().isEmpty())
    }

    @Test fun aChannelKeepsTheTracksPickedOnIt() = runTest {
        assertNull(playback.tracksOf("home:105"))

        playback.pickAudio("home:105", TrackPick("spa", "a52", 6, "Español | Dolby"))
        playback.pickSubtitles("home:105", null)
        playback.pickSubtitles("home:108", TrackPick("eng", "dvbs"))
        playback.setLevel("home:108", 2)

        assertEquals(ChannelTracks(audio = TrackPick("spa", "a52", 6, "Español | Dolby"), subtitlesOff = true), playback.tracksOf("home:105"))
        assertEquals(ChannelTracks(subtitles = TrackPick("eng", "dvbs")), playback.tracksOf("home:108"))
        // A level is kept beside them, and Reset leaves them.
        playback.resetSound("home:108")
        assertEquals(ChannelTracks(subtitles = TrackPick("eng", "dvbs")), playback.tracksOf("home:108"))
    }

    @Test fun aChannelKeepsItsPictureSize() = runTest {
        playback.setPictureSize("home:110", "Zoom")
        playback.setPictureSize("home:105", "Fill")
        playback.setPictureSize("home:105", null)

        assertEquals(mapOf("home:110" to "Zoom"), playback.pictureSizes.first())
    }

    @Test fun removingASourceForgetsItsChannels() = runTest {
        playback.rememberEngine("home:105", "Mpv", EngineReason.User)
        playback.rememberEngine("backup:105", "Mpv", EngineReason.User)

        db.channelPlayback().deleteForSource("home:")

        assertNull(playback.engineFor("home:105"))
        assertEquals("Mpv", playback.engineFor("backup:105"))
    }
}
