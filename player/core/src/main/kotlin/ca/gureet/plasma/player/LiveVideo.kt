// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import androidx.compose.runtime.staticCompositionLocalOf
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
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

    /** Where an archive key starts; null for live TV. */
    fun archiveStart(key: String): Instant? =
        key.substringAfterLast('@', "").takeIf { it.length >= MIN_SECONDS_DIGITS && it.all(Char::isDigit) }?.toLongOrNull()?.let(Instant::ofEpochSecond)

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

    /** A program from the archive played to its end (catch-up, brief 5.5). */
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

    fun attach(view: SurfaceView)

    fun detach(view: SurfaceView)

    /**
     * Shows [channelId], or stops when it's null. Showing the channel that's already playing only
     * changes [muted]: Home's picture is muted by default, the player's isn't.
     */
    fun show(channelId: String?, muted: Boolean)
}

/** The app's live video; null in previews and tests, which show the sample stills instead. */
val LocalLiveVideo = staticCompositionLocalOf<LiveVideo?> { null }
