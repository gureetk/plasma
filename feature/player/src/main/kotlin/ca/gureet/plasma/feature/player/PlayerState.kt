// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import ca.gureet.plasma.core.design.component.ReminderInfo
import ca.gureet.plasma.core.design.component.SourceChoice
import ca.gureet.plasma.player.MediaTracks
import ca.gureet.plasma.player.PictureSize
import ca.gureet.plasma.player.VideoFrame
import java.time.Duration
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

/** Player Options › Sleep Timer's choices (brief 4.10). */
enum class SleepChoice(
    /** How long the timer runs, for the choices that count minutes. */
    val minutes: Long?,
) {
    Off(null),
    Minutes30(30),
    Minutes60(60),
    Minutes90(90),
    Minutes120(120),

    /** When the program on screen ends. */
    EndOfProgram(null),
}

/** A Sleep Timer that's set: what was picked, and when the channel stops. */
data class SleepInfo(val choice: SleepChoice, val at: Instant)

/** How playback is going. In M0 it's a stand-in; the player reports it from M1. */
data class PlaybackInfo(
    /** How far behind live the picture is (timeshift, 5.5). Zero at the live edge. */
    val behindLiveMs: Long,
    val paused: Boolean,
    /** How far back the rewind buffer reaches (5.5), for a channel without the provider's archive. */
    val rewindReachMs: Long = 0,
    /** The sound and subtitle tracks, and which are on, for Audio and Subtitles (4.10). */
    val tracks: MediaTracks = MediaTracks(),
    val engine: String,
    val nextEngine: String,
    /** "1080i · H.264 · AC-3 5.1 passthrough"; empty until it's known. */
    val format: String,
    /** "8.2 Mb/s"; empty when it isn't known. */
    val bitrate: String,
    /** "2.1 s"; empty when it isn't known. */
    val buffer: String,
    val source: String,
    val nextSource: String?,
    /** The sources the channel plays from, for Switch Source; empty when it has only one. */
    val sources: List<SourceChoice> = emptyList(),
    /** Picture Size for the channel on screen (4.10). */
    val pictureSize: PictureSize = PictureSize.Fit,
    /** The picture's own shape, width over height, once it's known: "Fit · 16:9". */
    val pictureAspect: Float? = null,
    /** Sleep Timer, when it's set (4.10). */
    val sleep: SleepInfo? = null,
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

    /** Audio and Subtitles: the sound and subtitle tracks, over Player Options (4.10). */
    Tracks,

    /** Picture Size's choices, over Player Options (4.10). */
    Sizes,

    /** Sleep Timer's choices, over Player Options (4.10). */
    Sleep,

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
    /**
     * Play/Pause: the app pauses or plays on, and says so in [PlaybackInfo.paused]. Null where the
     * screen keeps it to itself.
     */
    val onPause: ((paused: Boolean) -> Unit)? = null,
    /**
     * The picture moved to [behindLiveMs] behind live, by ±10 s or along the timeline (brief 5.5):
     * from the rewind buffer or the archive. Null where the screen keeps it to itself.
     */
    val onSeek: ((behindLiveMs: Long) -> Unit)? = null,
    /** Switch Source: the channel plays from the source [key] stands for. */
    val onSwitchSource: (key: String) -> Unit = {},
    /** Forget What Works, in Switch Source: the channel goes back to its own order. */
    val onForgetSource: () -> Unit = {},
    /**
     * Switch Player: the next engine plays the channel, and [PlaybackInfo.engine] follows. Without
     * it, as in previews, the labels just swap.
     */
    val onSwitchPlayer: (() -> Unit)? = null,
    /**
     * Audio and Subtitles, or the remote's Audio key: the sound track [id] plays, and
     * [PlaybackInfo.tracks] follows. Without it, as in previews, the sheet just ticks it.
     */
    val onAudio: ((id: String) -> Unit)? = null,
    /** Audio and Subtitles, or the Subtitles key: the subtitle track [id] shows, or none when it's null. */
    val onSubtitles: ((id: String?) -> Unit)? = null,
    /** Picture Size: the channel's picture shows as [size]. Without it, the row just changes. */
    val onPictureSize: ((size: PictureSize) -> Unit)? = null,
    /**
     * The remote's Audio key: the next sound track plays, with a notice of which (4.35). Without
     * it, the next one is ticked here.
     */
    val onNextAudio: (() -> Unit)? = null,
    /** The remote's Subtitles key: subtitles on or off, with a notice. */
    val onNextSubtitles: (() -> Unit)? = null,
    /**
     * Sleep Timer: the channel stops [after] this long, as [choice] says, and [PlaybackInfo.sleep]
     * follows; Off turns it off. Without it, as in previews, the row just changes.
     */
    val onSleepTimer: ((choice: SleepChoice, after: Duration?) -> Unit)? = null,
)
