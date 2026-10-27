// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A source's own settings (brief 4.20 and 5.16), as their rows store them. */
class SourceSettingsTest {
    @Test fun keysNameTheirSource() {
        assertEquals("source.buffer:home", SourceSettings.key(SourceSettings.BUFFER, "home"))
        assertEquals("home", SourceSettings.sourceOf("source.buffer:home"))
        // Settings' own choices aren't a source's.
        assertNull(SourceSettings.sourceOf("buffer"))
        assertNull(SourceSettings.sourceOf("source.buffer"))
    }

    @Test fun eachRowChangesOnlyItsOwnSetting() {
        val playback = SourcePlaybackInfo()

        assertEquals(playback.copy(streamFormat = "m3u8"), SourceSettings.chosen(playback, "source.format:home", 1))
        assertEquals(playback, SourceSettings.chosen(playback.copy(streamFormat = "m3u8"), "source.format:home", 0))
        // The first option is "Same as Playback settings"; Settings' own options follow it.
        assertEquals(playback.copy(bufferChoice = 3), SourceSettings.chosen(playback, "source.buffer:home", 4))
        assertEquals(playback, SourceSettings.chosen(playback.copy(bufferChoice = 3), "source.buffer:home", 0))
        assertEquals(playback.copy(audioOutputChoice = 2), SourceSettings.chosen(playback, "source.audio:home", 3))
        assertEquals(playback.copy(epgOffsetMinutes = -120), SourceSettings.chosen(playback, "source.guideOffset:home", 10))
        assertEquals(playback.copy(catchupOffsetMinutes = 60), SourceSettings.chosen(playback, "source.catchupOffset:home", 13))
        assertEquals(playback.copy(engineChoice = 1), SourceSettings.chosen(playback, "source.engine:home", 2))
        assertEquals(playback, SourceSettings.chosen(playback.copy(engineChoice = 1), "source.engine:home", 0))
        // Same as Playback settings, On, Off.
        assertEquals(playback.copy(useHub = true), SourceSettings.chosen(playback, "source.hub:home", 1))
        assertEquals(playback.copy(useHub = false), SourceSettings.chosen(playback, "source.hub:home", 2))
        assertEquals(playback, SourceSettings.chosen(playback.copy(useHub = false), "source.hub:home", 0))
    }

    @Test fun typedRowsChangeOnlyTheirOwnSettings() {
        val playback = SourcePlaybackInfo()

        assertEquals(playback.copy(userAgent = "Custom/1.0"), SourceSettings.typed(playback, "source.userAgent:home", listOf(" Custom/1.0 ")))
        // Empty is the usual one.
        assertEquals(playback, SourceSettings.typed(playback.copy(userAgent = "Custom/1.0"), "source.userAgent:home", listOf("")))
        assertEquals(
            playback.copy(referrer = "http://lists.example.com/", extraHeaders = "X-Token: abc"),
            SourceSettings.typed(playback, "source.headers:home", listOf("http://lists.example.com/", "X-Token: abc")),
        )
        assertEquals(playback.copy(udpxy = "192.168.1.1:4022"), SourceSettings.typed(playback, "source.udpxy:home", listOf("192.168.1.1:4022")))
    }

    @Test fun offsetsReadAsTheirOption() {
        assertEquals(25, SourceSettings.OFFSET_HOURS.size)
        assertEquals(12, SourceSettings.offsetIndex(0))
        assertEquals(14, SourceSettings.offsetIndex(120))
        assertEquals(0, SourceSettings.offsetIndex(-720))
        // An offset that isn't on offer reads as none.
        assertEquals(12, SourceSettings.offsetIndex(90))
        assertEquals(12, SourceSettings.offsetIndex(24 * 60))
    }
}
