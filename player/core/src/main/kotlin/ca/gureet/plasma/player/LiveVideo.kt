// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import androidx.compose.runtime.staticCompositionLocalOf
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.ChannelTracks
import ca.gureet.plasma.stream.ConnectionBudget
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * What the video layer plays, by key: a channel's id for live TV, or one of its programs from the
 * provider's archive (catch-up, brief 5.5), as "channelId@start" with the start in epoch seconds,
 * the id the screens give the program.
 */
object StreamKeys {
    fun archive(channelId: String, start: Instant): String = "$channelId@${start.epochSecond}"

    /** A movie's key: `vod:movie:` and its id. */
    fun movie(id: String): String = "$VOD${KIND_MOVIE}:$id"

    /** An episode's key: `vod:episode:` and its id. */
    fun episode(id: String): String = "$VOD${KIND_EPISODE}:$id"

    /** A Jellyfin server's movie or episode (brief 5.19): `vod:jellyfin:` and the server's id for it. */
    fun jellyfin(id: String): String = "$VOD${KIND_JELLYFIN}:$id"

    /** A movie or an episode, which plays from a file rather than a broadcast (brief 5.9). */
    fun isVod(key: String): Boolean = key.startsWith(VOD)

    /** "movie", "episode" or "jellyfin", and the id, of a [isVod] key; null for anything else. */
    fun vodOf(key: String): Pair<String, String>? {
        if (!isVod(key)) return null
        val rest = key.removePrefix(VOD)
        val kind = rest.substringBefore(':')
        return (kind to rest.substringAfter(':', "")).takeIf { kind in setOf(KIND_MOVIE, KIND_EPISODE, KIND_JELLYFIN) && it.second.isNotEmpty() }
    }

    const val KIND_MOVIE = "movie"
    const val KIND_EPISODE = "episode"
    const val KIND_JELLYFIN = "jellyfin"
    private const val VOD = "vod:"

    /** Where an archive key starts; null for live TV, and for movies and episodes. */
    fun archiveStart(key: String): Instant? =
        if (isVod(key)) null else key.substringAfterLast('@', "").takeIf { it.length >= MIN_SECONDS_DIGITS && it.all(Char::isDigit) }?.toLongOrNull()?.let(Instant::ofEpochSecond)

    fun channelOf(key: String): String = if (archiveStart(key) != null) key.substringBeforeLast('@') else key

    /** Epoch seconds have had ten digits since 2001: a channel named "News@24" is still live TV. */
    private const val MIN_SECONDS_DIGITS = 9
}

/** A channel's stream, resolved when it's played (brief 6.5). */
data class StreamTarget(
    /** The key it plays under: the channel, or one of its programs from the archive ([StreamKeys]). */
    val channelId: String,
    /** The provider's URL. It carries credentials, so it goes through the hub and never into a log. */
    val url: String,
    /** The source's user agent, referrer and extra headers (brief 5.1). */
    val headers: Map<String, String> = emptyMap(),
    /** Live MPEG-TS goes through the stream hub; HLS goes straight to the engine (5.4.2). */
    val throughHub: Boolean = true,
    /** The account's connection limit, when it has one (5.1). */
    val budget: ConnectionBudget? = null,
    /** The source's own Buffer Size, over Settings' (Playback for This Source, 4.20). */
    val buffer: BufferSize? = null,
    /** The source's own Audio Output, as the engine takes it (Auto already settled), over Settings'. */
    val audioOutput: AudioOutput? = null,
    /** The source's own Player Engine, over Settings' (Playback for This Source, 5.4.3). */
    val engine: EngineId? = null,
    /**
     * The source's own Use Stream Hub, over Settings' Play Live TV Through Stream Hub (4.20);
     * a stream that can't go through the hub ([throughHub] false) doesn't, whatever it says.
     */
    val useHub: Boolean? = null,
    /** A radio channel: there's no picture to wait for (5.4.3). */
    val radio: Boolean = false,
)

/**
 * A channel's own sound (brief 5.4.6): its volume level, an Audio Output over the source's, and
 * the tracks picked on it.
 */
data class ChannelAudio(
    /** [VolumeLevels.MIN_DB] to [VolumeLevels.MAX_DB]. */
    val levelDb: Int = 0,
    /** Decode This Channel, as the engine takes it; null for the source's or Settings'. */
    val output: AudioOutput? = null,
    /** The sound and subtitles picked on it in Audio and Subtitles, before the language rules. */
    val tracks: ChannelTracks? = null,
)

/** Finds a channel's stream: the source module builds its URL at play time (brief 6.5). */
fun interface StreamResolver {
    /** The stream for [key]: a channel live, or a program from its archive ([StreamKeys]). */
    suspend fun resolve(key: String): StreamTarget?

    /**
     * What to play [key] from, best first, each a key [resolve] takes: a merged channel's
     * candidates (brief 5.1), which failover walks in order. Just [key] by default.
     */
    suspend fun candidates(key: String): List<String> = listOf(key)

    /**
     * The engine [key]'s channel plays with when it has one of its own: picked in Switch Player,
     * or the one that worked after a fallback (brief 5.4.3).
     */
    suspend fun engineFor(key: String): EngineId? = null

    /** [key]'s channel's own volume level and Audio Output, when it has them (brief 5.4.6). */
    suspend fun audioFor(key: String): ChannelAudio? = null
}

/** Why a channel isn't playing (brief 5.4.9), for the message on screen. */
enum class LiveFailure {
    /** Every connection the account allows is in use (5.1). */
    ConnectionLimit,

    /** The provider turned the stream down: a wrong login, an expired account or too many connections. */
    Refused,

    /** No data came, or the server couldn't be reached, after the retries. */
    SourceUnavailable,

    /** The provider sent a web page instead of video. */
    NotVideo,

    /** Data arrived but the engine couldn't play it. */
    CantPlay,

    /** The channel has no stream Plasma can open. */
    NoStream,
}

/** What the live picture is doing. */
sealed interface LiveStatus {
    data object Idle : LiveStatus

    data class Opening(val channelId: String) : LiveStatus

    data class Playing(val channelId: String) : LiveStatus

    /** The stream dropped and the hub is reconnecting: the last frame stays up (brief 5.4.8). */
    data class Reconnecting(val channelId: String, val attempt: Int) : LiveStatus

    data class Failed(val channelId: String, val failure: LiveFailure) : LiveStatus

    /** A program from the archive, a movie or an episode played to its end (brief 5.5 and 5.9). */
    data class Ended(val channelId: String) : LiveStatus
}

/**
 * Live video for the video layer (brief 5.4.4 and 6.5). Screens say which channel their video slot
 * shows; this plays it in the one [SurfaceView], which moves and resizes without restarting, so a
 * preview going full screen never rebuffers.
 */
interface LiveVideo {
    val status: StateFlow<LiveStatus>

    /** The channel whose picture is on screen now; null while it opens, and when nothing plays. */
    val picture: StateFlow<String?>

    /**
     * The picture's display aspect ratio (width over height, sample aspect included), so the
     * layer can fit it inside its slot (Picture Size: Fit, brief 5.4.4). Null until it's known.
     */
    val aspectRatio: StateFlow<Float?>

    /**
     * The subtitles to draw over the picture, from an engine that hands them out rather than
     * drawing them (the Android player, brief 5.4.4).
     */
    val cues: StateFlow<List<SubtitleCue>>

    /** Draw into [view], with [subtitles] over it for engines that draw them apart (brief 5.4.4). */
    fun attach(view: SurfaceView, subtitles: SurfaceView? = null)

    fun detach(view: SurfaceView)

    /**
     * Shows [channelId], or stops when it's null. Showing the channel that's already playing only
     * changes [muted]: Home's picture is muted by default, the player's isn't.
     */
    fun show(channelId: String?, muted: Boolean)
}

/** The app's live video; null in previews and tests, which show the sample stills instead. */
val LocalLiveVideo = staticCompositionLocalOf<LiveVideo?> { null }
