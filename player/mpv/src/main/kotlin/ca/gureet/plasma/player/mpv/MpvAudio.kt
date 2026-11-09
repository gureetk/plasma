// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.mpv

import ca.gureet.plasma.player.AudioCodec
import ca.gureet.plasma.player.SoundOut
import ca.gureet.plasma.player.SurroundFormat

/** mpv's (FFmpeg's) name for an audio codec, as far as passing it through goes. */
internal fun mpvCodec(name: String): AudioCodec = when (name.lowercase().removePrefix("spdif_")) {
    "ac3" -> AudioCodec.Ac3
    "eac3" -> AudioCodec.EAc3
    "dts" -> AudioCodec.Dts
    "truehd", "mlp" -> AudioCodec.TrueHd
    else -> AudioCodec.Other
}

/**
 * mpv's `audio-spdif` for the [formats] the TV or sound system takes untouched (brief 5.4.6): only
 * those, so mpv decodes the rest rather than sending a format the sound system can't play.
 * Dolby Atmos rides inside Dolby Digital Plus and TrueHD; a system that takes DTS-HD takes DTS.
 */
internal fun spdifCodecs(formats: Set<SurroundFormat>): String = buildList {
    if (SurroundFormat.DolbyDigital in formats) add("ac3")
    if (SurroundFormat.DolbyDigitalPlus in formats) add("eac3")
    if (SurroundFormat.Dts in formats || SurroundFormat.DtsHdMa in formats) add("dts")
    if (SurroundFormat.DtsHdMa in formats) add("dts-hd")
    if (SurroundFormat.DolbyTrueHd in formats) add("truehd")
}.joinToString(",")

/**
 * How mpv's sound leaves it, by `audio-out-params/format` (#102): "spdif-ac3" and the rest pass
 * Dolby or DTS through as they came, or for sound that wasn't Dolby Digital, carry what Encode
 * Surround made of it; anything else is decoded. Null before mpv says.
 */
internal fun soundOut(format: String, codec: AudioCodec): SoundOut? = when {
    format.isEmpty() -> null
    !format.startsWith(SPDIF) -> SoundOut.Decoded
    format == "${SPDIF}ac3" && codec != AudioCodec.Ac3 -> SoundOut.DolbyDigital
    else -> SoundOut.Passthrough
}

private const val SPDIF = "spdif-"

/**
 * Encode Surround to Dolby Digital (brief 5.4.6, #102): mpv's own filter encodes sound of 3
 * channels or more to Dolby Digital at 640 kb/s, wrapped for S/PDIF; stereo goes by untouched.
 */
internal const val ENCODE_SURROUND = "lavcac3enc=tospdif=yes:bitrate=640:minch=3"
