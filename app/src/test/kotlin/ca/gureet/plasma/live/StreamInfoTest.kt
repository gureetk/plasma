// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.feature.player.PlayerProblem
import ca.gureet.plasma.feature.player.PlayerStatus
import ca.gureet.plasma.feature.player.StreamHealth
import ca.gureet.plasma.feature.player.StreamStats
import ca.gureet.plasma.player.AudioCodec
import ca.gureet.plasma.player.AudioInfo
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.EngineStats
import ca.gureet.plasma.player.MediaTrack
import ca.gureet.plasma.player.PlayerStats
import ca.gureet.plasma.player.SoundOut
import ca.gureet.plasma.player.TuneTiming
import ca.gureet.plasma.player.TuneTimings
import ca.gureet.plasma.player.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** Player Options' stream info (brief 4.10). */
class StreamInfoTest {
    private val words = StreamWords({ "$it fps" }, passthrough = "passthrough", decoded = "decoded", encoded = "as Dolby Digital")

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

    @Test fun surroundEncodedToDolbyDigitalSaysSo() {
        val sound = MediaTrack("1", codec = "aac", channels = 6)

        // Encode Surround (#102), as mpv reports it.
        assertEquals("AAC 5.1 as Dolby Digital", streamFormat(null, sound, AudioInfo(AudioCodec.Other, 2, SoundOut.DolbyDigital), passthrough = true, words))
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

    /** Show Stream Stats (Settings › Playback › Advanced, 5.4.9). */
    @Test fun streamStatsAddWhatTheEngineAndTheHubKnow() {
        val stats = PlayerStats(EngineStats(version = "3.6.2", decoder = "mediacodec", hardware = true, droppedFrames = 12, bufferMs = 2_140), reconnects = 2)

        assertEquals(
            StreamStats(
                engine = "VLC 3.6.2",
                decoder = "mediacodec",
                hardware = true,
                format = "1920×1080 · 25 fps",
                bitrate = "8.2 Mb/s",
                buffer = "2.1 s",
                droppedFrames = 12,
                source = "Home Portal",
                reconnects = 2,
            ),
            streamStats("VLC", stats, "1920×1080 · 25 fps", "8.2 Mb/s", "Home Portal"),
        )
        // Before the engine has said anything: the engine's name, and what Player Options has.
        assertEquals(StreamStats(engine = "mpv", format = "", bitrate = "", source = ""), streamStats("mpv", null, "", "", ""))
        // How long pictures took to come up (#127): the last of each, and the zaps' 95th percentile.
        val timings = TuneTimings()
            .with(TuneTiming(TuneTiming.Kind.Zap, "ch105", EngineId.Vlc, 600))
            .with(TuneTiming(TuneTiming.Kind.Zap, "ch106", EngineId.Vlc, 1_400))
            .with(TuneTiming(TuneTiming.Kind.Fallback, "ch106", EngineId.Mpv, 2_100))
        assertEquals(
            StreamStats(engine = "mpv", format = "", bitrate = "", source = "", zapMs = 1_400, zapP95Ms = 1_400, zaps = 2, fallbackMs = 2_100),
            streamStats("mpv", null, "", "", "", timings),
        )
    }
}
