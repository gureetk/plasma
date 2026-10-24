// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.SourcePlayback
import ca.gureet.plasma.player.EngineId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A stream as a source's own Playback for This Source asks for it (brief 4.20, 5.1 and 5.4.10). */
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
}
