// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.R
import ca.gureet.plasma.feature.player.PlayerStatus
import ca.gureet.plasma.feature.player.StreamHealth
import ca.gureet.plasma.player.AudioCodec
import ca.gureet.plasma.player.AudioInfo
import ca.gureet.plasma.player.MediaTrack
import ca.gureet.plasma.player.VideoInfo
import ca.gureet.plasma.player.audioCodecOf
import java.util.Locale

/** The words Player Options' stream info needs, from the app's strings. */
internal class StreamWords(val frameRate: (rate: String) -> String, val passthrough: String, val decoded: String)

/**
 * Player Options' stream info (brief 4.10): "1920×1080 · 25 fps · H.264 · AC-3 5.1 passthrough",
 * as far as the engine knows the stream, and how its sound leaves the box; empty before it knows.
 */
internal fun streamFormat(video: VideoInfo?, sound: MediaTrack?, audio: AudioInfo?, passthrough: Boolean, words: StreamWords): String {
    val parts = mutableListOf<String>()
    if (video != null && video.width > 0 && video.height > 0) parts += "${video.width}×${video.height}"
    video?.frameRate?.takeIf { it > 0f }?.let { parts += words.frameRate(rateText(it)) }
    videoCodecName(video?.codec)?.let { parts += it }
    val codec = audioCodecName(sound?.codec) ?: audio?.codec?.let(::audioCodecName)
    if (codec != null) {
        val layout = (sound?.channels ?: audio?.channels)?.let(::channelLayout)
        parts += listOfNotNull(codec, layout, if (passthrough) words.passthrough else words.decoded).joinToString(" ")
    }
    return parts.joinToString(" · ")
}

@Composable
internal fun streamFormat(video: VideoInfo?, sound: MediaTrack?, audio: AudioInfo?, passthrough: Boolean): String {
    val resources = LocalResources.current
    val words = StreamWords({ resources.getString(R.string.stream_frame_rate, it) }, stringResource(R.string.sound_passthrough), stringResource(R.string.sound_decoded))
    return streamFormat(video, sound, audio, passthrough, words)
}

/**
 * The stream info's dot (brief 4.10): red on an error; amber while the stream opens, reconnects or
 * buffers, and just after another engine took over from one that couldn't play it; else green.
 */
internal fun streamHealth(status: PlayerStatus, afterFallback: Boolean): StreamHealth = when {
    status is PlayerStatus.Failed -> StreamHealth.Error
    status != PlayerStatus.Playing || afterFallback -> StreamHealth.Warning
    else -> StreamHealth.Good
}

/** "8.2 Mb/s", "640 kb/s"; empty when it isn't known. */
@Composable
internal fun bitrateText(bitsPerSecond: Long?): String = when {
    bitsPerSecond == null || bitsPerSecond <= 0 -> ""
    bitsPerSecond >= MEGA -> stringResource(R.string.bitrate_mbps, String.format(Locale.US, "%.1f", bitsPerSecond / MEGA.toDouble()))
    else -> stringResource(R.string.bitrate_kbps, (bitsPerSecond / KILO).toString())
}

private const val MEGA = 1_000_000L
private const val KILO = 1_000L

/** 25 → "25", 29.97 → "29.97", 50.0 → "50". */
private fun rateText(rate: Float): String {
    val rounded = Math.round(rate * 100) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
}

/** A picture format by its usual name: "H.264", "HEVC", "MPEG-2". */
internal fun videoCodecName(codec: String?): String? = when (val name = codec?.trim()?.lowercase()) {
    null, "" -> null
    "h264", "avc1", "avc" -> "H.264"
    "hevc", "h265", "hev1", "hvc1" -> "HEVC"
    "mpgv", "mp2v", "mpeg2video", "mpeg2" -> "MPEG-2"
    "mp4v", "mpeg4" -> "MPEG-4"
    "av01", "av1" -> "AV1"
    "vp90", "vp9" -> "VP9"
    "wvc1", "vc1", "vc-1" -> "VC-1"
    else -> name.uppercase()
}

/** A sound format by its short name, as the stream info gives it: "AC-3", "AAC". */
internal fun audioCodecName(codec: String?): String? {
    val name = codec?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    audioCodecName(audioCodecOf(name))?.let { return it }
    return when {
        name.startsWith("aac") || name == "mp4a" -> "AAC"
        name in setOf("mpga", "mp2", "mp2a", "mpa") -> "MP2"
        name == "mp3" -> "MP3"
        name == "opus" -> "Opus"
        name == "flac" -> "FLAC"
        name.startsWith("pcm") || name in setOf("araw", "lpcm") -> "PCM"
        else -> name.uppercase()
    }
}

private fun audioCodecName(codec: AudioCodec): String? = when (codec) {
    AudioCodec.Ac3 -> "AC-3"
    AudioCodec.EAc3 -> "E-AC-3"
    AudioCodec.Dts -> "DTS"
    AudioCodec.TrueHd -> "TrueHD"
    AudioCodec.Other -> null
}

/** "5.1", "2.0": channels as the stream info gives them. */
private fun channelLayout(channels: Int): String = when (channels) {
    1 -> "1.0"
    2 -> "2.0"
    3 -> "2.1"
    6 -> "5.1"
    7 -> "6.1"
    8 -> "7.1"
    else -> "$channels ch"
}
