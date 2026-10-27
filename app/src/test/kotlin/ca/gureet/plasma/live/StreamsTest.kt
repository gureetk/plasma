// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.SourcePlayback
import ca.gureet.plasma.core.model.HdrMovies
import ca.gureet.plasma.core.model.PlayerEngineChoice
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.player.EngineId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A stream as a source's own Playback for This Source asks for it (brief 4.20, 5.1 and 5.4.10),
 * and the engine it plays with (5.4.3).
 */
class StreamsTest {
    @Test fun multicastGoesThroughTheSourcesUdpxyProxy() {
        assertEquals("http://192.168.1.1:4022/udp/239.1.1.1:1234", throughUdpxy("udp://@239.1.1.1:1234", "192.168.1.1:4022"))
        assertEquals("http://192.168.1.1:4022/rtp/239.1.1.2:5000", throughUdpxy("rtp://@239.1.1.2:5000", "http://192.168.1.1:4022/"))
        assertEquals("http://192.168.1.1:4022/udp/239.1.1.1:1234", throughUdpxy("UDP://239.1.1.1:1234", "192.168.1.1:4022"))
        // Anything else, or a source without a proxy, stays as it is.
        assertEquals("http://line.example.org/live/1.ts", throughUdpxy("http://line.example.org/live/1.ts", "192.168.1.1:4022"))
        assertEquals("udp://@239.1.1.1:1234", throughUdpxy("udp://@239.1.1.1:1234", null))
        assertEquals("udp://@239.1.1.1:1234", throughUdpxy("udp://@239.1.1.1:1234", " "))
    }

    @Test fun theSourcesReferrerAndHeadersFillInUnderTheChannelsOwn() {
        val playback = SourcePlayback(referrer = "http://lists.example.com/", extraHeaders = "X-Token: abc; Origin: http://lists.example.com\nbad line")
        val own = mapOf("User-Agent" to "VLC/3.0.21", "referer" to "http://channel.example.com/")

        assertEquals(
            mapOf("User-Agent" to "VLC/3.0.21", "referer" to "http://channel.example.com/", "X-Token" to "abc", "Origin" to "http://lists.example.com"),
            sourceHeaders(own, playback, "Referer"),
        )
        assertEquals(mapOf("Referer" to "http://lists.example.com/"), sourceHeaders(emptyMap(), SourcePlayback(referrer = "http://lists.example.com/"), "Referer"))
        assertEquals(own, sourceHeaders(own, SourcePlayback(), "Referer"))
    }

    @Test fun playerEngineOptionsNameTheirEngines() {
        assertEquals(EngineId.Vlc, engineOf(0))
        assertEquals(EngineId.Mpv, engineOf(1))
        assertEquals(EngineId.Media3, engineOf(2))
        assertNull(engineOf(7))
    }

    @Test fun dolbyVisionAndHdr10PlusMoviesPlayWithTheAndroidPlayer() {
        assertEquals(EngineId.Media3, hdrEngine("Dolby Vision", HdrMovies.AndroidPlayer))
        assertEquals(EngineId.Media3, hdrEngine("HDR10+", HdrMovies.AndroidPlayer))
        // HDR10 and HLG play as live TV does, and so does everything with Same as Player Engine.
        assertNull(hdrEngine("HDR10", HdrMovies.AndroidPlayer))
        assertNull(hdrEngine("HLG", HdrMovies.AndroidPlayer))
        assertNull(hdrEngine(null, HdrMovies.AndroidPlayer))
        assertNull(hdrEngine("Dolby Vision", HdrMovies.SameAsPlayerEngine))
    }

    @Test fun aMoviesEngineIsTheHdrRulesThenItsSourcesThenSettings() {
        val defaults = SettingsChoices()
        assertEquals(EngineId.Media3, vodEngine("Dolby Vision", defaults, sourceEngine = EngineId.Mpv))
        assertEquals(EngineId.Mpv, vodEngine("HDR10", defaults, sourceEngine = EngineId.Mpv))
        assertEquals(EngineId.Vlc, vodEngine(null, defaults, sourceEngine = null))
        val androidPlayer = SettingsChoices(mapOf(SettingKeys.PLAYER_ENGINE to PlayerEngineChoice.AndroidPlayer.ordinal, SettingKeys.HDR to HdrMovies.SameAsPlayerEngine.ordinal))
        assertEquals(EngineId.Media3, vodEngine(null, androidPlayer, sourceEngine = null))
        val sameAsEngine = SettingsChoices(mapOf(SettingKeys.HDR to HdrMovies.SameAsPlayerEngine.ordinal))
        assertEquals(EngineId.Vlc, vodEngine("Dolby Vision", sameAsEngine, sourceEngine = null))
    }
}
