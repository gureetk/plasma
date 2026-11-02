// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.media3

import androidx.annotation.OptIn
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stereo Downmix in the Android player's sound sink (Settings › Audio, brief 5.4.6). */
@OptIn(UnstableApi::class)
class Media3AudioTest {
    @Test fun everyChannelCountHasAMix() {
        for (channels in 1..MAX_CHANNELS) {
            val matrix = ChannelMixingMatrix(channels, minOf(channels, 2), downmixToStereo(channels))
            assertEquals(minOf(channels, 2), matrix.outputChannelCount)
        }
        // Mono and stereo pass as they are, so the sink leaves them alone.
        assertTrue(ChannelMixingMatrix(1, 1, downmixToStereo(1)).isIdentity)
        assertTrue(ChannelMixingMatrix(2, 2, downmixToStereo(2)).isIdentity)
    }

    @Test fun fivePointOneMixesDownAsFfmpegDoes() {
        // Front left, front right, center, LFE, back left, back right.
        val matrix = ChannelMixingMatrix(6, 2, downmixToStereo(6))
        fun share(channel: Int) = matrix.getMixingCoefficient(channel, 0) to matrix.getMixingCoefficient(channel, 1)

        assertEquals(1f to 0f, share(0))
        assertEquals(0f to 1f, share(1))
        assertEquals(0.7071f to 0.7071f, share(2))
        assertEquals(0f to 0f, share(3))
        assertEquals(0.7071f to 0f, share(4))
        assertEquals(0f to 0.7071f, share(5))
    }

    @Test fun leftAndRightGetAsMuchAsEachOther() {
        for (channels in 3..MAX_CHANNELS) {
            val matrix = ChannelMixingMatrix(channels, 2, downmixToStereo(channels))
            val left = (0 until channels).sumOf { matrix.getMixingCoefficient(it, 0).toDouble() }
            val right = (0 until channels).sumOf { matrix.getMixingCoefficient(it, 1).toDouble() }
            assertEquals("$channels channels", left, right, 0.0001)
        }
    }
}
