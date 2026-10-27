// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.mpv

import ca.gureet.plasma.player.AudioCodec
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
}
