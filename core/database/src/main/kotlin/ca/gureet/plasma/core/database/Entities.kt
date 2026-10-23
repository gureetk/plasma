// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.ColumnInfo
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
    /** "xtream", "m3u" or "stalker". */
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
    // A Stalker portal's box (brief 7.1).
    /** The MAC address the provider registered, encrypted: the portal knows the account by it. */
    val macEnc: String? = null,
    /** The MAG model the box presents as; null for the usual MAG250. */
    val stbModel: String? = null,
    /** The registered box's own serial number, device ids and signature, for a portal that checks them. */
    val serialNumber: String? = null,
    val deviceId: String? = null,
    val deviceId2: String? = null,
    val signature: String? = null,
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
    /** Buffer Size for this source, as its option's index in Settings; null is "Same as Playback settings" (4.20). */
    val bufferChoice: Int? = null,
    /** Audio Output for this source, as its option's index in Settings; null is "Same as Playback settings" (4.20). */
    val audioOutputChoice: Int? = null,
    /** How far a playlist's catch-up times are moved, for a server that reads them in its own time zone (7.3). */
    @ColumnInfo(defaultValue = "0") val catchupOffsetMinutes: Int = 0,
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
    /**
     * A playlist channel's own address, or a portal channel's `cmd`, encrypted with the data key;
     * Xtream's are built when it plays.
     */
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

/**
 * Remember What Works per Channel (brief 5.1 and 6.3, `ChannelSourceMemory`): for a merged
 * channel, the candidate to try first and why, and how its first candidate did on its last five
 * tunes. It's per channel, not per profile, because it describes the provider. Times are Unix ms.
 */
@Entity(tableName = "channel_source_memory")
data class ChannelSourceMemoryEntity(
    /** The merged channel: the id it had when this was first kept, one of its candidates' ids. */
    @PrimaryKey val mergedChannelId: String,
    /** The candidate to try first; null while only the first candidate's tunes are being counted. */
    val preferredChannelId: String?,
    /** FAILOVER, REPEATED_FAILURES or USER. */
    val reason: String?,
    val learnedAt: Long?,
    /** When the next tune tries the original order once: [learnedAt] plus 7 days. */
    val recheckAfter: Long?,
    /** The first candidate's last five tunes, oldest first: "1" it played, "0" it failed. */
    val recentResults: String,
)

/**
 * How one channel plays (brief 6.3, `ChannelPlayback`): the engine it plays with when it has one
 * of its own (5.4.3). Per channel, not per profile. Times are Unix ms.
 */
@Entity(tableName = "channel_playback")
data class ChannelPlaybackEntity(
    /** The channel, by the id it plays under: a merged channel's is its primary source's. */
    @PrimaryKey val channelId: String,
    /** VLC, Mpv or Media3; null to start with the source's or Settings' engine. */
    val engine: String?,
    /** USER, picked in Switch Player, or FALLBACK, the one that played after the first couldn't. */
    val engineReason: String?,
    val engineSetAt: Long?,
)
