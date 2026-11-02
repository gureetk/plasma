// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.media3

import androidx.annotation.OptIn
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi

/** The most channels Android hands a sound sink: 7.1. */
internal const val MAX_CHANNELS = 8

/**
 * Stereo Downmix in the Android player's sound sink (Settings › Audio, brief 5.4.6): decoded
 * surround sound mixed down to two channels, as mpv and VLC mix theirs. Mono and stereo pass as
 * they are.
 */
@OptIn(UnstableApi::class)
internal fun stereoDownmix(): ChannelMixingAudioProcessor = ChannelMixingAudioProcessor().apply {
    for (channels in 1..MAX_CHANNELS) {
        putChannelMixingMatrix(ChannelMixingMatrix(channels, minOf(channels, 2), downmixToStereo(channels)))
    }
}

/**
 * How much of each of [channels] goes to the left and the right, a row of two for each channel
 * in Android's order of channels ([ChannelMixingMatrix]'s layout); mono and stereo as they are.
 * The center and the surrounds go in at −3 dB, a back center at −6 dB to each side, and the LFE
 * not at all, as FFmpeg's downmix for mpv does: TV speakers can't play it, and it would only take
 * headroom.
 */
internal fun downmixToStereo(channels: Int): FloatArray {
    val layout = when (channels) {
        1 -> return floatArrayOf(FULL)
        2 -> listOf(FRONT_LEFT, FRONT_RIGHT)
        3 -> listOf(FRONT_LEFT, FRONT_RIGHT, CENTER)
        4 -> listOf(FRONT_LEFT, FRONT_RIGHT, BACK_LEFT, BACK_RIGHT)
        5 -> listOf(FRONT_LEFT, FRONT_RIGHT, CENTER, BACK_LEFT, BACK_RIGHT)
        6 -> listOf(FRONT_LEFT, FRONT_RIGHT, CENTER, LFE, BACK_LEFT, BACK_RIGHT)
        7 -> listOf(FRONT_LEFT, FRONT_RIGHT, CENTER, LFE, BACK_LEFT, BACK_RIGHT, BACK_CENTER)
        MAX_CHANNELS -> listOf(FRONT_LEFT, FRONT_RIGHT, CENTER, LFE, BACK_LEFT, BACK_RIGHT, SIDE_LEFT, SIDE_RIGHT)
        else -> throw IllegalArgumentException("No downmix for $channels channels")
    }
    return layout.flatMap { it.toList() }.toFloatArray()
}

private const val FULL = 1f
private const val MINUS_3_DB = 0.7071f
private const val MINUS_6_DB = 0.5f

// Each channel's share of the left and the right.
private val FRONT_LEFT = FULL to 0f
private val FRONT_RIGHT = 0f to FULL
private val CENTER = MINUS_3_DB to MINUS_3_DB
private val LFE = 0f to 0f
private val BACK_LEFT = MINUS_3_DB to 0f
private val BACK_RIGHT = 0f to MINUS_3_DB
private val BACK_CENTER = MINUS_6_DB to MINUS_6_DB
private val SIDE_LEFT = MINUS_3_DB to 0f
private val SIDE_RIGHT = 0f to MINUS_3_DB
