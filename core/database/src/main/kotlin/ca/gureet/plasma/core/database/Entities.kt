// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
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
    /** The IANA zone the portal writes guide times in, from Advanced; null for this TV's (brief 4.21). */
    val portalTimeZone: String? = null,
    /** How often a playlist's channels refresh, in hours, from Advanced: null is every 12, 0 only when asked (brief 4.21). */
    val refreshHours: Int? = null,
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
    /** Player Engine for this source, as its option's index in Settings; null is "Same as Playback settings" (4.20). */
    val engineChoice: Int? = null,
    /** Use Stream Hub for this source; null is "Same as Playback settings" (4.20). */
    val useHub: Boolean? = null,
    /** The referrer this source's streams are asked for with (5.1). */
    val referrer: String? = null,
    /** Extra headers for its streams, encrypted: they can carry a token (5.1). */
    val extraHeadersEnc: String? = null,
    /** A udpxy proxy for a playlist's multicast channels, "192.168.1.1:4022" (5.4.10). */
    val udpxy: String? = null,
    // Refreshes (5.1 and 5.3).
    val channelsRefreshedAt: Long? = null,
    val guideRefreshedAt: Long? = null,
    /** The guide import whose programmes show; a newer one shows only once it's complete. */
    val guideGeneration: Long? = null,
    /** When the movies and series last loaded (brief 6.4: weekly, or when asked). */
    val vodRefreshedAt: Long? = null,
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

/**
 * The user's choice of number for a channel, kept across refreshes (brief 6.3, `NumberOverride`).
 * One made on Duplicate Numbers (4.30) keeps the number the channel wanted, which another channel
 * has or had.
 */
@Entity(tableName = "number_overrides", primaryKeys = ["sourceId", "channelKey"])
data class NumberOverrideEntity(
    val sourceId: String,
    val channelKey: String,
    val number: Int,
    val setAt: Long,
    val wantedNumber: Int? = null,
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
 * of its own (5.4.3), its own sound (5.4.6), the tracks and Picture Size picked on it (4.10). Per
 * channel, not per profile. Times are Unix ms.
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
    /** Its own volume level, −6 to +6 dB, on decoded sound. */
    @ColumnInfo(defaultValue = "0") val volumeOffsetDb: Int = 0,
    /** Its own Audio Output: DecodeToPcm after Decode This Channel; null for the source's or Settings'. */
    val audioOutput: String? = null,
    /**
     * The sound picked on it in Audio and Subtitles (4.10 and 5.4.6), as "language|codec|channels|name";
     * null for the language rules.
     */
    val audioTrack: String? = null,
    /** The subtitles picked on it, the same way, or "off"; null for Subtitles and Teletext's own pick. */
    val subtitleTrack: String? = null,
    /** Its Picture Size (4.10): Fill, Zoom, Wide or Standard; null to fit. */
    val pictureSize: String? = null,
)

/** What a [VodCategoryEntity] holds, and what a [ResumePointEntity] or [MyListEntity] is of. */
object VodKinds {
    const val MOVIE = "movie"
    const val SERIES = "series"

    /** A series' episode, where it was left (brief 5.9). */
    const val EPISODE = "episode"
}

/** A category of movies or series (brief 6.3, `VodCategory`). */
@Entity(
    tableName = "vod_categories",
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceId")],
)
data class VodCategoryEntity(
    /** "sourceId:movie:remoteId" or "sourceId:series:remoteId". */
    @PrimaryKey val id: String,
    val sourceId: String,
    /** "movie" or "series". */
    val kind: String,
    val remoteId: String,
    val name: String,
    /** The provider's order. */
    val position: Int,
    /** The import that last saw it; what an import doesn't see goes once it succeeds. */
    val generation: Long,
)

/** A movie (brief 6.3, `Movie`). Its plot, cast and file details come with its detail page. */
@Entity(
    tableName = "movies",
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceId"), Index("categoryId"), Index("addedAt")],
)
data class MovieEntity(
    /** "sourceId:remoteId", stable across refreshes, so where it was left and My List survive them. */
    @PrimaryKey val id: String,
    val sourceId: String,
    /** The provider's id: Xtream's stream id. */
    val remoteId: String,
    val categoryId: String?,
    val name: String,
    val poster: String?,
    /** When the provider added it, in Unix ms, if it says. */
    val addedAt: Long?,
    /** Out of 10. */
    val rating: Float?,
    val year: Int?,
    /** The file's own extension, such as "mkv", which its address ends with. */
    val extension: String?,
    /** The provider's order. */
    val position: Int,
    val generation: Long,
    /** A portal's command for it (Stalker), which becomes an address at play time; null for Xtream. */
    val cmd: String? = null,
    /** The portal names its file apart, and is asked for it before the link (Stalker). */
    @ColumnInfo(defaultValue = "0") val fileApart: Boolean = false,
)

/** A series (brief 6.3, `Series`). Its seasons and episodes come with its detail page. */
@Entity(
    tableName = "series",
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceId"), Index("categoryId"), Index("updatedAt")],
)
data class SeriesEntity(
    /** "sourceId:remoteId", stable across refreshes. */
    @PrimaryKey val id: String,
    val sourceId: String,
    /** The provider's id: Xtream's series id. */
    val remoteId: String,
    val categoryId: String?,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    /** "Drama, Crime", as the provider writes it. */
    val genre: String?,
    val year: Int?,
    /** Out of 10. */
    val rating: Float?,
    /** When its latest episode arrived, in Unix ms, if the provider says. */
    val updatedAt: Long?,
    /** A YouTube video id. */
    val trailer: String?,
    /** The provider's order. */
    val position: Int,
    val generation: Long,
    /**
     * How a portal gives the series' episodes (Stalker): [SeriesLayout.WITH_TITLE],
     * [SeriesLayout.BY_SEASON] or [SeriesLayout.SERIES_MODULE]; null for Xtream.
     */
    val layout: String? = null,
)

/** How a Stalker portal gives a series' episodes, as [SeriesEntity.layout] keeps it. */
object SeriesLayout {
    /** Listed with the title in its video library, all from the title's one command. */
    const val WITH_TITLE = "title"

    /** Asked for a season at a time, each episode its own file. */
    const val BY_SEASON = "seasons"

    /** In the portal's `series` module: each season a command for its episodes. */
    const val SERIES_MODULE = "module"
}

/** Movies' names, for Search (brief 5.10): kept in step with [MovieEntity] by Room. */
@Fts4(contentEntity = MovieEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "movies_fts")
data class MovieFtsEntity(val name: String)

/** Series' names, for Search. */
@Fts4(contentEntity = SeriesEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "series_fts")
data class SeriesFtsEntity(val name: String)

/** How many titles a category has. */
data class CategoryCount(val categoryId: String?, val count: Int)

/** A file's picture, as the provider probed it (brief 4.14, File Info). */
data class VideoFacts(
    /** ffprobe's name: "hevc", "h264". */
    val codec: String?,
    /** "Main 10". */
    val profile: String?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    /** "Dolby Vision", "HDR10+", "HDR10" or "HLG"; null for SDR or unknown. */
    val hdr: String?,
)

/** A file's main sound, as the provider probed it. */
data class AudioFacts(
    /** ffprobe's name: "eac3", "aac". */
    val codec: String?,
    val channels: Int?,
    /** ISO 639-2, as the file tags it: "eng". */
    val language: String?,
    /** Bits a second. */
    val bitRate: Int?,
)

/**
 * A movie's details from its provider (brief 5.9), read when its page opens. They go with the
 * movie when a refresh no longer lists it.
 */
@Entity(
    tableName = "movie_details",
    foreignKeys = [ForeignKey(entity = MovieEntity::class, parentColumns = ["id"], childColumns = ["movieId"], onDelete = ForeignKey.CASCADE)],
)
data class MovieDetailsEntity(
    @PrimaryKey val movieId: String,
    val plot: String?,
    /** "Name, Name", as the provider writes it. */
    val cast: String?,
    val director: String?,
    /** "Drama, Mystery", as the provider writes it. */
    val genre: String?,
    val durationSecs: Int?,
    /** A larger poster than the catalog's, when there is one. */
    val poster: String?,
    val backdrop: String?,
    /** A YouTube video id. */
    val trailer: String?,
    val year: Int?,
    /** Out of 10. */
    val rating: Float?,
    /** The age rating: "13+". */
    val age: String?,
    @Embedded(prefix = "video_") val video: VideoFacts?,
    @Embedded(prefix = "audio_") val audio: AudioFacts?,
    /** When they were read, in Unix ms. */
    val fetchedAt: Long,
)

/** What a series' provider says of it with its episodes (brief 5.9), read when its page opens. */
@Entity(
    tableName = "series_details",
    foreignKeys = [ForeignKey(entity = SeriesEntity::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE)],
)
data class SeriesDetailsEntity(
    @PrimaryKey val seriesId: String,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val backdrop: String?,
    val trailer: String?,
    val year: Int?,
    val rating: Float?,
    val age: String?,
    /** When it and the episodes were read, in Unix ms. */
    val fetchedAt: Long,
)

/** An episode of a series (brief 6.3, `Episode`), read with the series' page. */
@Entity(
    tableName = "episodes",
    foreignKeys = [ForeignKey(entity = SeriesEntity::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("seriesId")],
)
data class EpisodeEntity(
    /** "sourceId:remoteId", stable across refreshes, so where it was left survives them. */
    @PrimaryKey val id: String,
    val seriesId: String,
    /** The provider's id: the episode's own stream id. */
    val remoteId: String,
    /** 0 holds the specials. */
    val season: Int,
    val number: Int,
    val title: String?,
    val plot: String?,
    /** A still from the episode. */
    val still: String?,
    val durationSecs: Int?,
    /** The file's own extension, which its address ends with. */
    val extension: String?,
    /** When it first aired. */
    val year: Int?,
    /** When the provider added it, in Unix ms. */
    val addedAt: Long?,
    @Embedded(prefix = "video_") val video: VideoFacts?,
    @Embedded(prefix = "audio_") val audio: AudioFacts?,
    /** Its place in the series: the seasons in order, then each season's episodes. */
    val position: Int,
    /** A portal's command for it (Stalker), which becomes an address at play time; null for Xtream. */
    val cmd: String? = null,
    /** The episode [cmd] plays, when the command is a whole season's or series'. */
    val cmdEpisode: Int? = null,
)

/**
 * Where a profile left a movie or an episode, and whether it's watched (brief 5.9): saved as it
 * plays, watched at 92 % or when marked so. It stays when a refresh no longer lists the title, so
 * one that comes back carries on, and goes with its source.
 */
@Entity(
    tableName = "resume_points",
    primaryKeys = ["profileId", "kind", "contentId"],
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceId"), Index(value = ["profileId", "seriesId"]), Index(value = ["profileId", "updatedAt"])],
)
data class ResumePointEntity(
    val profileId: String,
    /** "movie" or "episode". */
    val kind: String,
    /** The movie's or the episode's id. */
    val contentId: String,
    val sourceId: String,
    /** The series an episode is of; null for a movie. */
    val seriesId: String?,
    val positionMs: Long,
    /** Its length as the player found it; 0 before it has played. */
    val durationMs: Long,
    val watched: Boolean,
    /** When it last played or was marked, in Unix ms. */
    val updatedAt: Long,
    /** Removed from Continue Watching (brief 4.1); playing it again brings it back. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)

/** A movie or series on a profile's My List (brief 5.9). It goes with its source. */
@Entity(
    tableName = "my_list",
    primaryKeys = ["profileId", "kind", "contentId"],
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceId")],
)
data class MyListEntity(
    val profileId: String,
    /** "movie" or "series". */
    val kind: String,
    val contentId: String,
    val sourceId: String,
    /** When it was added, in Unix ms: the newest first. */
    val addedAt: Long,
)

/**
 * A profile's Jellyfin sign-in (brief 5.19 and 6.3, `JellyfinAccount`): each profile can link its
 * own Jellyfin user. The token is stored encrypted (6.10).
 */
@Entity(tableName = "jellyfin_accounts")
data class JellyfinAccountEntity(
    @PrimaryKey val profileId: String,
    /** The address the server answered at. */
    val serverUrl: String,
    val serverId: String,
    val serverName: String,
    val userId: String,
    val userName: String,
    val accessTokenEnc: String,
    /** The device Plasma signed in as: one of its own for each sign-in. */
    val deviceId: String,
    /** When it was signed in, in Unix ms. */
    val signedInAt: Long,
)
