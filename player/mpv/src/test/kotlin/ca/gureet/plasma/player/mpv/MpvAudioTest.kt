// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.mpv

import ca.gureet.plasma.player.AudioCodec
import ca.gureet.plasma.player.SoundOut
import ca.gureet.plasma.player.SurroundFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/** mpv's names for formats that pass through (brief 5.4.6). */
class MpvAudioTest {
    @Test fun formatsThatPassThrough() {
        assertEquals(AudioCodec.Ac3, mpvCodec("ac3"))
        assertEquals(AudioCodec.Ac3, mpvCodec("spdif_ac3"))
        assertEquals(AudioCodec.EAc3, mpvCodec("eac3"))
        assertEquals(AudioCodec.Dts, mpvCodec("dts"))
        assertEquals(AudioCodec.TrueHd, mpvCodec("truehd"))
        assertEquals(AudioCodec.Other, mpvCodec("aac"))
    }

    @Test fun onlyWhatTheSoundSystemTakesPassesThrough() {
        // A soundbar on optical: Dolby Digital and DTS.
        assertEquals("ac3,dts", spdifCodecs(setOf(SurroundFormat.DolbyDigital, SurroundFormat.Dts)))
        // A receiver that takes everything.
        assertEquals("ac3,eac3,dts,dts-hd,truehd", spdifCodecs(SurroundFormat.entries.toSet()))
        // Decoded sound only: mpv passes nothing.
        assertEquals("", spdifCodecs(emptySet()))
    }

    @Test fun howTheSoundLeavesMpv() {
        assertEquals(null, soundOut("", AudioCodec.Ac3))
        assertEquals(SoundOut.Decoded, soundOut("s16", AudioCodec.Other))
        assertEquals(SoundOut.Decoded, soundOut("floatp", AudioCodec.EAc3))
        assertEquals(SoundOut.Passthrough, soundOut("spdif-ac3", AudioCodec.Ac3))
        assertEquals(SoundOut.Passthrough, soundOut("spdif-eac3", AudioCodec.EAc3))
        // AAC 5.1, or DTS on a Dolby-only system, as Encode Surround sends it (#102).
        assertEquals(SoundOut.DolbyDigital, soundOut("spdif-ac3", AudioCodec.Other))
        assertEquals(SoundOut.DolbyDigital, soundOut("spdif-ac3", AudioCodec.Dts))
    }
}
