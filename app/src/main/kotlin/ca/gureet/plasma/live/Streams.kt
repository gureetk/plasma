// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.SourcePlayback
import ca.gureet.plasma.core.model.ExtraHeaders
import ca.gureet.plasma.core.model.PlayerEngineChoice
import ca.gureet.plasma.player.EngineId

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
    // Not in Plasma yet (M4): live TV starts with VLC.
    PlayerEngineChoice.AndroidPlayer -> EngineId.Media3
    null -> null
}
