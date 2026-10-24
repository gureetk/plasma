// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.channels.NumberReason
import java.time.Instant

enum class SourceType(val id: String) {
    Xtream("xtream"),
    M3u("m3u"),
    Stalker("stalker");

    /** Its provider has movies and series: an Xtream account or a Stalker portal (brief 5.9). */
    val hasCatalog: Boolean get() = this == Xtream || this == Stalker

    companion object {
        fun of(id: String): SourceType = entries.firstOrNull { it.id == id } ?: Xtream
    }
}

/** A provider account as the screens show it. Its password never leaves the data layer. */
data class Source(
    val id: String,
    val type: SourceType,
    val name: String,
    /** The server's address, without credentials; for a playlist, its server's. */
    val server: String,
    val username: String?,
    val priority: Int,
    val enabled: Boolean,
    val status: String?,
    val expiresAt: Instant?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val streamFormat: String,
    /** The source's own user agent, when it needs one (brief 5.1). */
    val userAgent: String?,
    val channelsRefreshedAt: Instant?,
    val guideRefreshedAt: Instant?,
    /** Why the last refresh failed, redacted; null when it worked. */
    val lastError: String?,
    /** When its movies and series last loaded (brief 6.4). */
    val vodRefreshedAt: Instant? = null,
    /** The Xtream server's own time zone, which its archive addresses are written in (brief 7.2). */
    val serverTimeZone: String? = null,
    /** The stream formats the account offers ("m3u8", "ts"). */
    val outputFormats: List<String> = emptyList(),
    /** Playback for This Source (brief 4.20). */
    val playback: SourcePlayback = SourcePlayback(streamFormat = streamFormat),
    /** A Stalker portal's MAC address, for Settings to show. */
    val mac: String? = null,
    /** How often its channels refresh, in hours: null is every 12, 0 only with Refresh Now (brief 4.21). */
    val refreshHours: Int? = null,
) {
    val isPrimary: Boolean get() = priority == 0
}

/** A live channel. */
data class Channel(
    /** "sourceId:key", stable across refreshes. */
    val id: String,
    val sourceId: String,
    val key: String,
    val name: String,
    val number: Int,
    val numberReason: NumberReason,
    val logo: String?,
    val groupId: String?,
    val groupName: String?,
    /** The guide channel it's matched to, if any. */
    val epgKey: String?,
    val catchupDays: Int?,
    val isRadio: Boolean,
    val hidden: Boolean,
    val locked: Boolean,
    /** The provider's guide id, which the same channel from another source shares. */
    val tvgId: String? = null,
    /**
     * The channels to play it from, best first: itself, or with Merge Duplicate Channels the same
     * channel from every source that has it (brief 5.1). Failover walks them in this order.
     */
    val candidates: List<String> = listOf(id),
    /**
     * The number it wanted and another channel kept: for one numbering moved, or one the user
     * gave a number on Duplicate Numbers (brief 4.30).
     */
    val wantedNumber: Int? = null,
    /** Its place in its source's list. */
    val position: Int = 0,
    /** Locked through its group, a locked or adult one, rather than on its own (brief 5.12). */
    val lockedWithGroup: Boolean = false,
) {
    /** The same channel comes from more than one source. */
    val isMerged: Boolean get() = candidates.size > 1
}

/** A provider's channel group. */
data class ChannelGroup(
    val id: String,
    val sourceId: String,
    val name: String,
    val hidden: Boolean,
    /** Locked with the parental PIN, or chosen not to be; null follows Lock Adult Groups (brief 5.12). */
    val locked: Boolean? = null,
)

/** A programme in the guide. */
data class Programme(
    val epgKey: String,
    val start: Instant,
    val end: Instant,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val category: String?,
    val icon: String?,
    val season: Int?,
    val episode: Int?,
    val isNew: Boolean,
    /** Its age rating as the guide writes it, "TV-MA" or "16" (brief 5.12). */
    val rating: String? = null,
) {
    fun isOn(at: Instant): Boolean = !start.isAfter(at) && end.isAfter(at)
}

/** What's on a channel now and what's next (brief 5.3). */
data class NowNext(val now: Programme?, val next: Programme?)

/**
 * Playback for This Source (brief 4.20 and 5.1): settings that override Playback and Audio for one
 * source's channels. A null choice is "Same as Playback settings".
 */
data class SourcePlayback(
    /** Xtream's Stream Format: "ts" or "m3u8". */
    val streamFormat: String = "ts",
    /** Buffer Size, as its option's index in Settings. */
    val bufferChoice: Int? = null,
    /** Audio Output, as its option's index in Settings. */
    val audioOutputChoice: Int? = null,
    /** Moves the guide's times, for a guide that's off by some hours. */
    val epgOffsetMinutes: Int = 0,
    /** Moves a playlist's catch-up times, for a server that reads them in its own time zone (7.3). */
    val catchupOffsetMinutes: Int = 0,
    /** Player Engine, as its option's index in Settings. */
    val engineChoice: Int? = null,
    /** Use Stream Hub: on, off, or null for Settings'. */
    val useHub: Boolean? = null,
    /** The user agent its servers are told, or null for the usual (VLC's, or a MAG box's for a portal). */
    val userAgent: String? = null,
    val referrer: String? = null,
    /** Extra headers as typed: "Name: value; Name: value". */
    val extraHeaders: String? = null,
    /** A udpxy proxy for multicast, "192.168.1.1:4022". */
    val udpxy: String? = null,
)
