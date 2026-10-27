// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/**
 * How many Multiview tiles the TV's video decoders can take (brief 5.8). Android's count of
 * decoder instances is a hint, not a promise: a tile whose decoder doesn't start shows its error
 * card instead ([TilePlayer]).
 */
object DecoderCapacity {
    /** The most tiles, and the fewest Multiview falls back to. */
    const val MAX_TILES = 4
    const val MIN_TILES = 2

    /**
     * The tiles to offer: as many H.264 decoders as the hardware says it runs at once, most
     * channels being H.264, between [MIN_TILES] and [MAX_TILES]. A 4K channel is one tile at most
     * anyway (5.8).
     */
    fun tiles(): Int = (hardwareInstances(AVC) ?: MAX_TILES).coerceIn(MIN_TILES, MAX_TILES)

    /** How many decoders for [mime] can run at once, by the best hardware decoder; null when there's none. */
    private fun hardwareInstances(mime: String): Int? = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { type -> type.equals(mime, ignoreCase = true) } && isHardware(it) }
            .maxOfOrNull { it.getCapabilitiesForType(mime).maxSupportedInstances }
    } catch (failed: RuntimeException) {
        null
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
        }

    private const val AVC = "video/avc"
}
