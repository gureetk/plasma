// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.feature.player.PlayerProblem
import ca.gureet.plasma.feature.player.PlayerStatus
import ca.gureet.plasma.feature.player.StreamHealth
import ca.gureet.plasma.player.AudioCodec
import ca.gureet.plasma.player.AudioInfo
import ca.gureet.plasma.player.MediaTrack
import ca.gureet.plasma.player.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** Player Options' stream info (brief 4.10). */
class StreamInfoTest {
    private val words = StreamWords({ "$it fps" }, passthrough = "passthrough", decoded = "decoded")

    @Test fun itSaysThePictureAndHowTheSoundLeaves() {
        val video = VideoInfo(1920, 1080, frameRate = 25f, codec = "h264")
        val sound = MediaTrack("1", language = "eng", codec = "a52 ", channels = 6)

        assertEquals("1920×1080 · 25 fps · H.264 · AC-3 5.1 passthrough", streamFormat(video, sound, null, passthrough = true, words))
    }

    @Test fun mpvsNamesReadTheSame() {
        val video = VideoInfo(1280, 720, frameRate = 59.94f, codec = "hevc")
        val sound = MediaTrack("2", codec = "aac", channels = 2)

        assertEquals("1280×720 · 59.94 fps · HEVC · AAC 2.0 decoded", streamFormat(video, sound, null, passthrough = false, words))
    }

    @Test fun whatIsntKnownYetIsLeftOut() {
        assertEquals("", streamFormat(null, null, null, passthrough = false, words))
        // The engine's word on the sound when the track list doesn't name it.
        assertEquals("E-AC-3 5.1 passthrough", streamFormat(null, null, AudioInfo(AudioCodec.EAc3, 6), passthrough = true, words))
        assertEquals("MPEG-2", streamFormat(VideoInfo(0, 0, codec = "mpgv"), null, AudioInfo(AudioCodec.Other), passthrough = false, words))
    }

    @Test fun theDotIsAmberWhileOpeningAndJustAfterAFallback() {
        assertEquals(StreamHealth.Good, streamHealth(PlayerStatus.Playing, afterFallback = false))
        assertEquals(StreamHealth.Warning, streamHealth(PlayerStatus.Playing, afterFallback = true))
        // Opening covers an engine buffering once it has played.
        assertEquals(StreamHealth.Warning, streamHealth(PlayerStatus.Opening, afterFallback = false))
        assertEquals(StreamHealth.Warning, streamHealth(PlayerStatus.Reconnecting, afterFallback = false))
        assertEquals(StreamHealth.Error, streamHealth(PlayerStatus.Failed(PlayerProblem.CantPlay), afterFallback = true))
    }
}
