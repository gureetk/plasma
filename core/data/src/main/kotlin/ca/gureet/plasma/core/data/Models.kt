// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.channels.NumberReason
import java.time.Instant

enum class SourceType(val id: String) {
    Xtream("xtream");

    companion object {
        fun of(id: String): SourceType = entries.firstOrNull { it.id == id } ?: Xtream
    }
}

/** A provider account as the screens show it. Its password never leaves the data layer. */
data class Source(
    val id: String,
    val type: SourceType,
    val name: String,
    /** The server's address, without credentials. */
    val server: String,
    val username: String?,
    val priority: Int,
    val enabled: Boolean,
    val status: String?,
    val expiresAt: Instant?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val streamFormat: String,
    val channelsRefreshedAt: Instant?,
    val guideRefreshedAt: Instant?,
    /** Why the last refresh failed, redacted; null when it worked. */
    val lastError: String?,
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
)

/** A provider's channel group. */
data class ChannelGroup(
    val id: String,
    val sourceId: String,
    val name: String,
    val hidden: Boolean,
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
) {
    fun isOn(at: Instant): Boolean = !start.isAfter(at) && end.isAfter(at)
}

/** What's on a channel now and what's next (brief 5.3). */
data class NowNext(val now: Programme?, val next: Programme?)
