// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.FileVideo
import ca.gureet.plasma.core.data.SourcePlayback
import ca.gureet.plasma.core.data.SourceType
import ca.gureet.plasma.core.model.ExtraHeaders
import ca.gureet.plasma.core.model.HdrMovies
import ca.gureet.plasma.core.model.PlayerEngineChoice
import ca.gureet.plasma.core.model.ProbeMovies
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.media3.VideoRange

/**
 * A multicast address through the source's udpxy proxy (brief 5.4.10): `udp://@239.1.1.1:1234`
 * becomes `http://192.168.1.1:4022/udp/239.1.1.1:1234`, and `rtp://` goes to `/rtp/`. Anything
 * else, or a source without a proxy, stays as it is.
 */
internal fun throughUdpxy(url: String, proxy: String?): String {
    val base = proxy?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return url
    val scheme = url.substringBefore("://", "").lowercase()
    if (scheme != "udp" && scheme != "rtp") return url
    val group = url.substringAfter("://").removePrefix("@")
    val server = if ("://" in base) base else "http://$base"
    return "$server/$scheme/$group"
}

/**
 * The headers a source's streams are asked for with (brief 5.1): its referrer and extra headers,
 * under [own], which already has the user agent and anything the channel asks for itself
 * (`#EXTVLCOPT`, 5.4.11) and wins.
 */
internal fun sourceHeaders(own: Map<String, String>, playback: SourcePlayback, referer: String): Map<String, String> {
    val taken = own.keys.mapTo(HashSet()) { it.lowercase() }
    val added = LinkedHashMap<String, String>()
    playback.referrer?.let { added[referer] = it }
    playback.extraHeaders?.let(ExtraHeaders::parse)?.forEach { (name, value) -> added[name] = value }
    return own + added.filterKeys { it.lowercase() !in taken }
}

/** Player Engine's option [index] (Settings › Playback, 5.4.3) as the engine it names. */
internal fun engineOf(index: Int): EngineId? = when (PlayerEngineChoice.entries.getOrNull(index)) {
    PlayerEngineChoice.Vlc -> EngineId.Vlc
    PlayerEngineChoice.Mpv -> EngineId.Mpv
    PlayerEngineChoice.AndroidPlayer -> EngineId.Media3
    null -> null
}

/**
 * HDR and Dolby Vision Movies (brief 5.4.3, rule 3): a movie or episode in Dolby Vision or HDR10+,
 * as its provider's probe of the file says, plays with the Android player, unless the setting is
 * Same as Player Engine. VLC and mpv show neither (5.4.1).
 */
internal fun hdrEngine(hdr: String?, choice: HdrMovies): EngineId? =
    EngineId.Media3.takeIf { choice == HdrMovies.AndroidPlayer && hdr in ANDROID_PLAYER_HDR }

/**
 * The engine a movie or episode plays with (5.4.3), as File Info says (4.14): the HDR rule, then
 * its source's Player Engine, then Settings'.
 */
internal fun vodEngine(hdr: String?, choices: SettingsChoices, sourceEngine: EngineId?): EngineId =
    hdrEngine(hdr, choices.hdrMovies) ?: sourceEngine ?: engineOf(choices.playerEngine.ordinal) ?: EngineId.Vlc

/**
 * Check Movies for Dolby Vision (#113): whether a movie or episode its provider didn't probe, from a
 * source of [type] whose account allows [maxConnections] at once, has the start of its file read
 * first. Reading it opens the movie twice. By default that's only where the account allows two
 * connections or more: an Xtream account that doesn't say has no limit, while portals never say,
 * so they count as one.
 */
internal fun mayProbe(choice: ProbeMovies, type: SourceType, maxConnections: Int?): Boolean = when (choice) {
    ProbeMovies.Always -> true
    ProbeMovies.Never -> false
    ProbeMovies.WithTwoConnections -> (maxConnections ?: if (type == SourceType.Xtream) Int.MAX_VALUE else 1) >= 2
}

/** What the start of a file says of its picture, in File Info's words for HDR (brief 4.14); SDR has none. */
internal fun hdrName(range: VideoRange): String? = when (range) {
    VideoRange.DolbyVision -> FileVideo.DOLBY_VISION
    VideoRange.Hdr10 -> HDR10
    VideoRange.Hlg -> HLG
    VideoRange.Sdr -> null
}

private const val HDR10 = "HDR10"
private const val HLG = "HLG"

private val ANDROID_PLAYER_HDR = setOf(FileVideo.DOLBY_VISION, FileVideo.HDR10_PLUS)
