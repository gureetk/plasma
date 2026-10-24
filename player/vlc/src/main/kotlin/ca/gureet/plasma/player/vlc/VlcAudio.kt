// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.vlc

import ca.gureet.plasma.player.AudioCodec
import ca.gureet.plasma.player.MediaTrack
import org.videolan.libvlc.interfaces.IMedia
import kotlin.math.pow
import kotlin.math.roundToInt

/** libVLC's highest volume: twice the sound. */
private const val MAX_BOOSTED_VOLUME = 200

/**
 * libVLC's volume for [percent] (0 to 100) with a channel's level of [levelDb] (brief 5.4.6). Up to
 * 100, VLC's Android output scales the sound by the volume; above 100, it adds gain as the cube of
 * the volume, up to 200. To be checked on a TV with a test tone, as the brief asks.
 */
internal fun vlcVolume(percent: Int, levelDb: Int): Int {
    if (percent <= 0) return 0
    val amplitude = percent / 100.0 * 10.0.pow(levelDb / 20.0)
    val volume = if (amplitude <= 1.0) amplitude else amplitude.pow(1.0 / 3)
    return (volume * 100).roundToInt().coerceIn(0, MAX_BOOSTED_VOLUME)
}

/** A libVLC four-letter code, as far as passing it through goes. */
internal fun vlcCodec(fourcc: String?): AudioCodec = when (fourcc?.trim()?.lowercase()) {
    "a52", "a52b" -> AudioCodec.Ac3
    "eac3" -> AudioCodec.EAc3
    "dts", "dtsh", "dtsc", "dtse" -> AudioCodec.Dts
    "mlp", "trhd" -> AudioCodec.TrueHd
    else -> AudioCodec.Other
}

/** A track libVLC can switch to, [id], with what the media says about it, [described]. */
internal fun mediaTrack(id: Int, described: IMedia.Track?): MediaTrack = MediaTrack(
    id = id.toString(),
    language = described?.language?.trim()?.takeIf { it.isNotEmpty() },
    title = described?.description?.trim()?.takeIf { it.isNotEmpty() },
    codec = described?.codec?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
    channels = (described as? IMedia.AudioTrack)?.channels?.takeIf { it > 0 },
)
