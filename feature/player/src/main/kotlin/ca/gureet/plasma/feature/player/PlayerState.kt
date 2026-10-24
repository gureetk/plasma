// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import ca.gureet.plasma.core.design.component.ReminderInfo
import ca.gureet.plasma.core.design.component.SourceChoice
import ca.gureet.plasma.player.VideoFrame
import java.time.Instant

data class PlayerProgram(
    val id: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val subtitle: String? = null,
    /** HD, 50 FPS, 5.1, CC… */
    val badges: List<String> = emptyList(),
    val recording: Boolean = false,
    /** For "Record Every City League Match": the series or competition, when the guide has one. */
    val series: String? = null,
)

data class PlayerChannel(
    val id: String,
    /** The number in the current list (Favorites count from 1). */
    val number: String,
    val name: String,
    val logo: String?,
    val frame: VideoFrame,
    val catchUp: Boolean,
    val uhd: Boolean,
    val hdr: Boolean,
    /** Its place in Favorites ("Favorite 3 of 24"), or null. */
    val favoriteIndex: Int?,
    val programs: List<PlayerProgram>,
) {
    fun programAt(time: Instant): PlayerProgram? = programs.firstOrNull { !it.start.isAfter(time) && it.end.isAfter(time) }
    fun programAfter(time: Instant): PlayerProgram? = programs.firstOrNull { it.start.isAfter(time) }
}

/** Green when healthy, amber while buffering or just after a fallback, red on errors (4.10). */
enum class StreamHealth { Good, Warning, Error }

/** How playback is going. In M0 it's a stand-in; the player reports it from M1. */
data class PlaybackInfo(
    /** How far behind live the picture is (timeshift, 5.5). Zero at the live edge. */
    val behindLiveMs: Long,
    val paused: Boolean,
    val audio: String,
    /** The subtitle track, or null when they're off. */
    val subtitles: String?,
    val engine: String,
    val nextEngine: String,
    /** "1080i · H.264 · AC-3 5.1 passthrough" */
    val format: String,
    val bitrate: String,
    val buffer: String,
    val source: String,
    val nextSource: String?,
    /** The sources the channel plays from, for Switch Source; empty when it has only one. */
    val sources: List<SourceChoice> = emptyList(),
    val pictureSize: String,
    val sleepTimer: String?,
    val health: StreamHealth,
)

data class PlayerUiState(
    /** The list CH ± moves through, in order. */
    val channels: List<PlayerChannel>,
    val currentChannelId: String,
    val playback: PlaybackInfo,
    val favoritesCount: Int,
    val reminder: ReminderInfo? = null,
    /** How the channel on screen is playing, for the live player (M1); sample stills always play. */
    val status: PlayerStatus = PlayerStatus.Playing,
    /** Settings › Playback › Offer Watch from the Start: the hint for tuning in late (brief 4.11). */
    val offerStartOver: Boolean = true,
)

/** How the channel on screen is playing (brief 5.4.8 and 5.4.9). */
sealed interface PlayerStatus {
    data object Playing : PlayerStatus

    data object Opening : PlayerStatus

    /** The stream dropped: the last frame stays up with "Reconnecting…" (5.4.8). */
    data object Reconnecting : PlayerStatus

    data class Failed(val problem: PlayerProblem) : PlayerStatus
}

/** Why a channel can't play, in the words the error card uses (5.4.9). */
enum class PlayerProblem {
    ConnectionLimit,
    Refused,
    SourceUnavailable,
    NotVideo,
    CantPlay,
    NoStream,
}

/** What's drawn over the video. */
enum class PlayerOverlay {
    /** Just the picture. */
    None,

    /** The info block, timeshift bar and controls (board 09). */
    Controls,

    /** Player Options (board 10). */
    Options,

    /** Switch Source's list, over Player Options (brief 4.10 and 5.1). */
    Sources,

    /** The zap banner after a channel change (board 11). */
    Zap,
}

class PlayerActions(
    val onBack: () -> Unit = {},
    val onChannelList: () -> Unit = {},
    val onMultiview: () -> Unit = {},
    val onTune: (channelId: String) -> Unit = {},
    /** Try Again on the error card. */
    val onRetry: () -> Unit = {},
    /** Add to or Remove from Favorites in Player Options. */
    val onFavorite: (channelId: String, favorite: Boolean) -> Unit = { _, _ -> },
    /** Switch Now on a reminder for a channel that isn't in the list being zapped through. */
    val onOpenChannel: (channelId: String) -> Unit = {},
    /** The reminder toast closed: Switch Now, Dismiss or Back. */
    val onReminderDone: () -> Unit = {},
    /** Start Over or Watch from the Start: the program on [channelId] from its [start], from the archive (brief 5.5). */
    val onStartOver: (channelId: String, start: Instant) -> Unit = { _, _ -> },
    /** Go Live: back to the live edge. */
    val onGoLive: (channelId: String) -> Unit = {},
    /** Play/Pause. */
    val onPause: (paused: Boolean) -> Unit = {},
    /** Switch Source: the channel plays from the source [key] stands for. */
    val onSwitchSource: (key: String) -> Unit = {},
    /** Forget What Works, in Switch Source: the channel goes back to its own order. */
    val onForgetSource: () -> Unit = {},
    /**
     * Switch Player: the next engine plays the channel, and [PlaybackInfo.engine] follows. Without
     * it, as in previews, the labels just swap.
     */
    val onSwitchPlayer: (() -> Unit)? = null,
)
