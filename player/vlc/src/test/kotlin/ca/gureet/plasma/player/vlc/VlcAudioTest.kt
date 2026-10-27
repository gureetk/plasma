// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.vlc

import ca.gureet.plasma.player.AudioCodec
import org.junit.Assert.assertEquals
import org.junit.Test

/** A channel's level as libVLC's volume (brief 5.4.6), and its codes for formats that pass through. */
class VlcAudioTest {
    @Test fun aLevelIsAVolume() {
        assertEquals(100, vlcVolume(100, 0))
        // Quieter scales the sound: −6 dB is half.
        assertEquals(50, vlcVolume(100, -6))
        assertEquals(71, vlcVolume(100, -3))
        // Louder is gain on top, as the cube: +3 dB is 112, +6 dB 126.
        assertEquals(112, vlcVolume(100, 3))
        assertEquals(126, vlcVolume(100, 6))
        // Muted stays muted.
        assertEquals(0, vlcVolume(0, 6))
    }

    @Test fun formatsThatPassThrough() {
        assertEquals(AudioCodec.Ac3, vlcCodec("a52 "))
        assertEquals(AudioCodec.EAc3, vlcCodec("eac3"))
        assertEquals(AudioCodec.Dts, vlcCodec("dts "))
        assertEquals(AudioCodec.TrueHd, vlcCodec("mlp "))
        assertEquals(AudioCodec.Other, vlcCodec("mp4a"))
        assertEquals(AudioCodec.Other, vlcCodec(null))
    }
}
