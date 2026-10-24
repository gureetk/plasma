// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.mpv

import ca.gureet.plasma.player.AudioCodec

/** mpv's (FFmpeg's) name for an audio codec, as far as passing it through goes. */
internal fun mpvCodec(name: String): AudioCodec = when (name.lowercase().removePrefix("spdif_")) {
    "ac3" -> AudioCodec.Ac3
    "eac3" -> AudioCodec.EAc3
    "dts" -> AudioCodec.Dts
    "truehd", "mlp" -> AudioCodec.TrueHd
    else -> AudioCodec.Other
}
