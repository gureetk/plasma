// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/** A provider account or playlist (brief 6.3, `Source`). The password is stored encrypted. */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    /** "xtream" or "m3u"; "stalker" follows (M3). */
    val type: String,
    val name: String,
    /** The server's address, without credentials; for a playlist, the address of its server. */
    val server: String,
    val username: String?,
    /** Encrypted with the Android Keystore (brief 6.10). */
    val passwordEnc: String?,
    /** A playlist's address, encrypted: it often carries a username and password. */
    val playlistUrlEnc: String? = null,
    /** The guide addresses typed in for a playlist, encrypted, one per line. */
    val guideUrlsEnc: String? = null,
    /** The guide addresses the playlist names itself (`url-tvg`), encrypted, one per line. */
    val playlistGuideUrlsEnc: String? = null,
    /** 0 is the primary source; others follow in order. */
    val priority: Int,
    val enabled: Boolean = true,
    val addedAt: Long,
    // The account, as the provider last reported it (5.1).
    val status: String? = null,
    val expiresAt: Long? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int? = null,
    /** Comma-separated, such as "m3u8,ts". */
    val outputFormats: String = "",
    val serverTimeZone: String? = null,
    // Per-source settings (5.1).
    /** "ts" or "m3u8" (Xtream Stream Format, 5.4.8). */
    val streamFormat: String = "ts",
    val userAgent: String? = null,
    val epgOffsetMinutes: Int = 0,
    // Refreshes (5.1 and 5.3).
    val channelsRefreshedAt: Long? = null,
    val guideRefreshedAt: Long? = null,
    /** The guide import whose programmes show; a newer one shows only once it's complete. */
    val guideGeneration: Long? = null,
    /** Why the last refresh failed, redacted; null when it worked. */
    val lastError: String? = null,
)

/** A provider's channel group (brief 6.3, `ChannelGroup`). */
@Entity(
    tableName = "channel_groups",
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceId")],
)
data class GroupEntity(
    /** "sourceId:remoteId". */
    @PrimaryKey val id: String,
    val sourceId: String,
    val remoteId: String,
    val name: String,
    /** The provider's order. */
    val position: Int,
    val hidden: Boolean = false,
    /** The import that last saw it; older ones are removed when an import succeeds. */
    val generation: Long,
)

/** A live channel (brief 6.3, `Channel`). */
@Entity(
    tableName = "channels",
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceId"), Index("groupId"), Index("number"), Index("epgKey")],
)
data class ChannelEntity(
    /** "sourceId:key", stable across refreshes so favorites and history survive them. */
    @PrimaryKey val id: String,
    val sourceId: String,
    /** Stable within the source: the Xtream stream id, or a playlist entry's key (`M3uKeys`). */
    val key: String,
    val groupId: String?,
    val name: String,
    /** The provider's order. */
    val position: Int,
    /** The playlist's own number (Xtream `num`). */
    val playlistNumber: Int?,
    /** The number Plasma gave it (brief 5.2). */
    val number: Int,
    /** "Playlist", "Auto", "DuplicateFix" or "User". */
    val numberReason: String,
    /** For a duplicate that was moved, the number it wanted. */
    val wantedNumber: Int?,
    val logo: String?,
    /** The guide id the provider gives it (Xtream `epg_channel_id`). */
    val tvgId: String?,
    /** The guide channel it was matched to: "sourceId:xmltv id", lowercased. */
    val epgKey: String?,
    val catchupDays: Int?,
    val isRadio: Boolean,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val generation: Long,
    /** A playlist channel's own address, encrypted with the data key; Xtream's are built when it plays. */
    val streamUrlEnc: String? = null,
    /** From the playlist's `#EXTVLCOPT` lines (brief 7.3). */
    val userAgent: String? = null,
    val referrer: String? = null,
    /** The playlist's catch-up mode and template (7.3), for catch-up in M2; the template is encrypted. */
    val catchupType: String? = null,
    val catchupSourceEnc: String? = null,
)

/** A programme in the guide (brief 6.3, `Programme`). Times are Unix ms, UTC. */
@Entity(
    tableName = "programmes",
    indices = [Index(value = ["epgKey", "startAt"]), Index(value = ["sourceId", "generation"]), Index("endAt")],
)
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val sourceId: String,
    val epgKey: String,
    val startAt: Long,
    val endAt: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val category: String?,
    val icon: String?,
    val season: Int?,
    val episode: Int?,
    val isNew: Boolean,
    /** The guide import that wrote it; a new import replaces the old one only when it succeeds. */
    val generation: Long,
)

/**
 * The guide's titles and subtitles, word by word, for Search (brief 5.10). Its rows are the
 * programmes table's own, kept in step by the triggers Room makes for it.
 */
@Fts4(contentEntity = ProgrammeEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "programmes_fts")
data class ProgrammeFtsEntity(
    val title: String,
    val subtitle: String?,
)

/** A search a result was opened from, for Search's suggestions (brief 5.10, `SearchHistory`). */
@Entity(tableName = "search_history", primaryKeys = ["profileId", "text"])
data class SearchHistoryEntity(
    val profileId: String,
    val text: String,
    /** Unix ms. */
    val at: Long,
)

/** A favorite channel, in the user's order (brief 6.3, `Favorite`). */
@Entity(tableName = "favorites", primaryKeys = ["profileId", "channelId"])
data class FavoriteEntity(
    val profileId: String,
    val channelId: String,
    /** Also its number when Favorites count from 1. */
    val position: Int,
)

/** A channel someone watched (brief 6.3, `RecentChannel`). */
@Entity(tableName = "recent_channels", primaryKeys = ["profileId", "channelId"])
data class RecentChannelEntity(
    val profileId: String,
    val channelId: String,
    val watchedAt: Long,
)

/** A programme someone asked to be reminded of (brief 5.7). Times are Unix ms. */
@Entity(tableName = "reminders", indices = [Index("startAt")])
data class ReminderEntity(
    /** "channelId@start in epoch seconds": one reminder per programme. */
    @PrimaryKey val id: String,
    val profileId: String,
    val channelId: String,
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val createdAt: Long,
)

/** The user's choice of number for a channel, kept across refreshes (brief 6.3, `NumberOverride`). */
@Entity(tableName = "number_overrides", primaryKeys = ["sourceId", "channelKey"])
data class NumberOverrideEntity(
    val sourceId: String,
    val channelKey: String,
    val number: Int,
    val setAt: Long,
)
