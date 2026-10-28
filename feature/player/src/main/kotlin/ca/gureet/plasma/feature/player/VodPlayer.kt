// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.SelectKeys
import ca.gureet.plasma.core.design.component.CenteredKeyFooter
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.SheetDivider
import ca.gureet.plasma.core.design.component.SheetPanel
import ca.gureet.plasma.core.design.component.SheetRow
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.component.sheetIconTint
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.player.PictureSize
import ca.gureet.plasma.player.VideoFrame
import ca.gureet.plasma.player.VideoSlot
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/** An episode's place and name: "S2, E4 · Dead Reckoning". */
@Immutable
data class VodEpisode(val season: Int, val number: Int, val title: String?)

/**
 * The next episode, near the end of one (brief 4.9 and 5.9): a card at the bottom right, counting
 * down to it once the episode ends, when Auto-Play Next Episode is on.
 */
@Immutable
data class UpNext(
    val episode: VodEpisode,
    val still: String?,
    /** Seconds until it plays by itself; null while the episode still plays, or with Auto-Play off. */
    val countdown: Int? = null,
)

/** A movie or an episode in the player (brief 4.9's VOD variant). */
@Immutable
data class VodPlayerUiState(
    /** The movie's title, or the series'. */
    val title: String,
    /** The episode playing; null for a movie. */
    val episode: VodEpisode? = null,
    /** A movie's year and genres: "2026 · Thriller". */
    val meta: List<String> = emptyList(),
    /** 4K, HDR, 5.1… */
    val badges: List<String> = emptyList(),
    /** Shown while the picture opens: the backdrop, or a poster or still. */
    val art: String? = null,
    /** The player's key for it; null on sample data, where [art] stands in for the picture. */
    val key: String? = null,
    val positionMs: Long,
    /** 0 until the engine knows. */
    val lengthMs: Long,
    val paused: Boolean = false,
    val status: PlayerStatus = PlayerStatus.Playing,
    /** Its tracks, engine and format, Picture Size and Sleep Timer, as Player Options shows them. */
    val playback: PlaybackInfo,
    val upNext: UpNext? = null,
)

class VodPlayerActions(
    val onBack: () -> Unit = {},
    val onPause: (paused: Boolean) -> Unit = {},
    /** Jump to [toMs] in. */
    val onSeek: (toMs: Long) -> Unit = {},
    val onFromStart: () -> Unit = {},
    /** Next Episode, and Up Next's card: null when there's no next episode. */
    val onNext: (() -> Unit)? = null,
    /** Back on Up Next's card: the next episode won't play by itself. */
    val onCancelNext: () -> Unit = {},
    /** Try Again on the error card. */
    val onRetry: () -> Unit = {},
    val onSwitchPlayer: (() -> Unit)? = null,
    val onAudio: ((id: String) -> Unit)? = null,
    val onSubtitles: ((id: String?) -> Unit)? = null,
    val onPictureSize: ((size: PictureSize) -> Unit)? = null,
    /** Sleep Timer: it stops [after] this long; End of Program is the end of the movie. */
    val onSleepTimer: ((choice: SleepChoice, after: Duration?) -> Unit)? = null,
)

/** What's over a movie's picture. */
private enum class VodOverlay { None, Controls, Options, Tracks, Sizes, Sleep }

/**
 * A movie or an episode playing (brief 4.9's VOD variant): the picture, and over it the overlay,
 * with the title or the episode, a bar from the start to the end, and the controls; or Player
 * Options. Left and Right jump 10 s, more when held; OK and the media keys pause. Near an
 * episode's end, Up Next offers the next one.
 */
@Composable
fun VodPlayerScreen(state: VodPlayerUiState, actions: VodPlayerActions, modifier: Modifier = Modifier, autoHide: Boolean = true) {
    val now = rememberNow().value.toInstant()
    var overlay by remember { mutableStateOf(VodOverlay.Controls) }
    var keyPresses by remember { mutableIntStateOf(0) }
    // Where Left and Right have moved to, until it settles and the player jumps there.
    var target by remember { mutableStateOf<Long?>(null) }
    var timelineFirst by remember { mutableStateOf(false) }
    var upNextCancelled by remember(state.upNext?.episode) { mutableStateOf(false) }
    val root = remember { FocusRequester() }
    val upNextFocus = remember { FocusRequester() }
    val upNext = state.upNext?.takeIf { !upNextCancelled && actions.onNext != null }
    val showUpNext = upNext != null && overlay == VodOverlay.None && state.status !is PlayerStatus.Failed

    fun seekBy(deltaMs: Long) {
        val length = state.lengthMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        target = ((target ?: state.positionMs) + deltaMs).coerceIn(0L, length)
    }

    fun playPause() = actions.onPause(!state.paused)

    /** End of Program, for a movie: what's left of it, if a minute or more. */
    fun movieLeft(): Duration? =
        Duration.ofMillis(state.lengthMs - state.positionMs).takeIf { state.lengthMs > 0 && it >= MINUTE }

    LaunchedEffect(target) {
        val to = target ?: return@LaunchedEffect
        delay(SETTLE_MS)
        actions.onSeek(to)
        target = null
    }
    // The overlay hides after 5 s without a key press (2.6).
    LaunchedEffect(overlay, keyPresses, autoHide) {
        if (!autoHide || overlay != VodOverlay.Controls) return@LaunchedEffect
        delay(PlasmaMotion.OVERLAY_HIDE_MS)
        overlay = VodOverlay.None
    }
    LaunchedEffect(overlay, showUpNext) {
        if (overlay != VodOverlay.None) return@LaunchedEffect
        withFrameNanos { }
        if (showUpNext) upNextFocus.requestFocus() else root.requestFocus()
    }

    BackHandler(enabled = overlay != VodOverlay.None) { overlay = VodOverlay.None }
    BackHandler(enabled = overlay == VodOverlay.None, onBack = actions.onBack)

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) keyPresses++
                // Menu, or holding OK, opens Player Options over whatever shows (brief 3.6).
                val holdingOk = it.key in SelectKeys && it.nativeKeyEvent.repeatCount > 0
                if (it.type == KeyEventType.KeyDown && (holdingOk || it.key == Key.Menu) && overlay !in VodOverOptions) {
                    overlay = VodOverlay.Options
                    return@onPreviewKeyEvent true
                }
                if (it.type == KeyEventType.KeyDown && mediaKey(it, ::playPause, ::seekBy, actions.onNext)) return@onPreviewKeyEvent true
                if (it.key != Key.Back && it.key != Key.Escape) return@onPreviewKeyEvent false
                if (it.type == KeyEventType.KeyUp) {
                    when {
                        overlay in VodOverOptions -> overlay = VodOverlay.Options
                        overlay != VodOverlay.None -> overlay = VodOverlay.None
                        showUpNext -> {
                            upNextCancelled = true
                            actions.onCancelNext()
                        }
                        else -> actions.onBack()
                    }
                }
                true
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || overlay != VodOverlay.None) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        seekBy(if (event.key == Key.DirectionLeft) -stepFor(event) else stepFor(event))
                        timelineFirst = true
                        overlay = VodOverlay.Controls
                        true
                    }
                    in SelectKeys, Key.DirectionUp, Key.DirectionDown, Key.Info -> {
                        timelineFirst = false
                        overlay = VodOverlay.Controls
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(root)
            .focusable(),
    ) {
        val backdrop = overlay == VodOverlay.Options || overlay in VodOverOptions
        VideoSlot(VideoFrame(still = state.art, channelId = state.key, pictureSize = state.playback.pictureSize), Modifier.fillMaxSize(), backdrop = backdrop)
        val shownPosition = target ?: state.positionMs

        when (overlay) {
            VodOverlay.Controls -> VodControls(
                state = state,
                position = shownPosition,
                scrubbing = target != null,
                onPlayPause = ::playPause,
                onSeekBy = ::seekBy,
                onFromStart = actions.onFromStart,
                onNext = actions.onNext,
                onTracks = { overlay = VodOverlay.Tracks },
                onOptions = { overlay = VodOverlay.Options },
                focusTimeline = timelineFirst,
            )
            VodOverlay.Options -> VodOptions(
                state = state,
                onTracks = { overlay = VodOverlay.Tracks },
                onPictureSize = { overlay = VodOverlay.Sizes },
                onSwitchPlayer = { actions.onSwitchPlayer?.invoke() },
                onSleepTimer = { overlay = VodOverlay.Sleep },
                now = now,
            )
            VodOverlay.Tracks -> TracksSheet(
                channel = state.title,
                tracks = state.playback.tracks,
                onAudio = { id ->
                    overlay = VodOverlay.None
                    if (id != state.playback.tracks.audioId) actions.onAudio?.invoke(id)
                },
                onSubtitles = { id ->
                    overlay = VodOverlay.None
                    if (id != state.playback.tracks.subtitleId) actions.onSubtitles?.invoke(id)
                },
                onDismiss = { overlay = VodOverlay.Options },
            )
            VodOverlay.Sizes -> PictureSizeSheet(
                channel = state.title,
                size = state.playback.pictureSize,
                onPick = { size ->
                    overlay = VodOverlay.None
                    actions.onPictureSize?.invoke(size)
                },
                onDismiss = { overlay = VodOverlay.Options },
            )
            VodOverlay.Sleep -> SleepTimerSheet(
                channel = state.title,
                sleep = state.playback.sleep,
                programLeft = movieLeft(),
                now = now,
                onPick = { choice ->
                    overlay = VodOverlay.None
                    val after = when (choice) {
                        SleepChoice.Off -> null
                        SleepChoice.EndOfProgram -> movieLeft()
                        else -> choice.minutes?.let(Duration::ofMinutes)
                    }
                    actions.onSleepTimer?.invoke(choice, after)
                },
                onDismiss = { overlay = VodOverlay.Options },
            )
            VodOverlay.None -> Unit
        }

        if (showUpNext && upNext != null) {
            UpNextCard(upNext, onPlay = { actions.onNext?.invoke() }, modifier = Modifier.focusRequester(upNextFocus))
        }

        when (val status = state.status) {
            PlayerStatus.Opening -> if (overlay != VodOverlay.Options) LoadingRing()
            PlayerStatus.Reconnecting -> if (overlay != VodOverlay.Options) ReconnectingPill()
            is PlayerStatus.Failed -> if (overlay != VodOverlay.Options) PlayerErrorCard(state.title, status.problem, actions.onRetry, movie = true)
            PlayerStatus.Playing -> Unit
        }
    }
}

/** Play/Pause, ⏩, ⏪ and ⏭ on the remote work whatever's showing; true when [event] was one. */
private fun mediaKey(event: KeyEvent, playPause: () -> Unit, seekBy: (Long) -> Unit, next: (() -> Unit)?): Boolean {
    when (event.key) {
        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> playPause()
        Key.MediaFastForward -> seekBy(FAST_MS)
        Key.MediaRewind -> seekBy(-FAST_MS)
        Key.MediaNext -> next?.invoke() ?: return false
        else -> return false
    }
    return true
}

/** 10 s a press; held, the steps grow (brief 4.9). */
private fun stepFor(event: KeyEvent): Long {
    val repeats = event.nativeKeyEvent.repeatCount
    return when {
        repeats >= LONG_HOLD -> 60_000L
        repeats >= HOLD -> 30_000L
        else -> 10_000L
    }
}

/** The lists Player Options opens, which Back returns from to it. */
private val VodOverOptions = setOf(VodOverlay.Tracks, VodOverlay.Sizes, VodOverlay.Sleep)

/**
 * The overlay over a movie (board 09's VOD variant): the title, or the series and episode, its
 * badges, the bar from its start to its end, and the controls. Play/Pause has focus, or the bar
 * when Left or Right opened it.
 */
@Composable
private fun VodControls(
    state: VodPlayerUiState,
    position: Long,
    scrubbing: Boolean,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onFromStart: () -> Unit,
    onNext: (() -> Unit)?,
    onTracks: () -> Unit,
    onOptions: () -> Unit,
    focusTimeline: Boolean,
) {
    val playPause = remember { FocusRequester() }
    val bar = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (focusTimeline) bar.requestFocus() else playPause.requestFocus()
    }
    Box(Modifier.fillMaxSize()) {
        OverlayScrim()
        Row(Modifier.offset(96.px, 730.px).width(1728.px), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                PlasmaText(state.title, plasmaStyle(52f, 600, trackingPx = -1.2f, lineHeight = 1.06f), color = Color.White)
                val line = state.episode?.let { episodeLine(it) } ?: state.meta.joinToString(" · ").takeIf { it.isNotEmpty() }
                if (line != null) {
                    PlasmaText(line, plasmaStyle(24f, 400), Modifier.padding(top = 10.px), color = PlasmaColors.TextSoft)
                }
            }
            if (state.badges.isNotEmpty()) {
                Row(Modifier.padding(bottom = 4.px), horizontalArrangement = Arrangement.spacedBy(8.px)) { for (badge in state.badges) VideoBadge(badge) }
            }
        }
        SeekBar(
            positionMs = position,
            lengthMs = state.lengthMs,
            scrubbing = scrubbing,
            onSeekBy = onSeekBy,
            modifier = Modifier
                .offset(96.px, 880.px)
                .width(1728.px)
                .focusRequester(bar)
                .focusProperties { down = playPause },
        )
        Row(Modifier.offset(96.px, 944.px).width(1728.px), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.px), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(PlasmaIcons.StartOver, stringResource(R.string.from_the_beginning), onFromStart, iconSize = 26.px)
                RoundButton(PlasmaIcons.SkipBack, stringResource(R.string.back_10), { onSeekBy(-10_000) }, iconSize = 30.px, label = "10")
                RoundButton(
                    if (state.paused) PlasmaIcons.Play else PlasmaIcons.Pause,
                    stringResource(if (state.paused) R.string.play else R.string.pause),
                    onPlayPause,
                    size = 76.px,
                    iconSize = 30.px,
                    modifier = Modifier.focusRequester(playPause),
                )
                RoundButton(PlasmaIcons.SkipForward, stringResource(R.string.forward_10), { onSeekBy(10_000) }, iconSize = 30.px, label = "10")
                if (onNext != null) PillButton(PlasmaIcons.ArrowRight, stringResource(R.string.next_episode_button), onNext)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.px), verticalAlignment = Alignment.CenterVertically) {
                audioName(state.playback.tracks)?.let { PillButton(PlasmaIcons.Volume, it, onTracks) }
                PillButton(PlasmaIcons.Subtitles, subtitlesName(state.playback.tracks) ?: stringResource(R.string.subtitles_off), onTracks)
                SquareButton(PlasmaIcons.Menu, stringResource(R.string.player_options), onOptions)
            }
        }
    }
}

/** "S2, E4 · Dead Reckoning", or "S2, E4" without a title of its own. */
@Composable
private fun episodeLine(episode: VodEpisode): String =
    episode.title?.let { stringResource(R.string.episode_line, episode.season, episode.number, it) }
        ?: stringResource(R.string.episode_code, episode.season, episode.number)

/**
 * From the movie's start to its end: what's been played in white and the playhead, with where it
 * is in a bubble while it's focused or moving, and what's played and left under it. Left and Right
 * move it 10 s, more when held; the player jumps there once it settles.
 */
@Composable
private fun SeekBar(positionMs: Long, lengthMs: Long, scrubbing: Boolean, onSeekBy: (Long) -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val knob by animateFloatAsState(if (focused) 1.2f else 1f, label = "vodKnob")
    val played = if (lengthMs > 0) (positionMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f
    val description = stringResource(R.string.timeline)
    Column(
        modifier
            .semantics { contentDescription = description }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeekBy(-stepFor(event)); true }
                    Key.DirectionRight -> { onSeekBy(stepFor(event)); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        Box(Modifier.fillMaxWidth().height(6.px)) {
            Box(Modifier.fillMaxSize().background(PlasmaColors.white(0.22f), RoundedCornerShape(3.px)))
            Box(Modifier.fillMaxWidth(played).fillMaxHeight().background(Color.White, RoundedCornerShape(3.px)))
            Box(
                Modifier
                    .centeredAt(played)
                    .offset(y = (-11).px)
                    .size(28.px)
                    .scale(knob)
                    .shadow(7.px, CircleShape)
                    .background(Color.White, CircleShape),
            )
            if (focused || scrubbing) {
                Box(
                    Modifier
                        .centeredAt(played)
                        .offset(y = (-62).px)
                        .height(38.px)
                        .background(Color.White, RoundedCornerShape(9.px))
                        .padding(horizontal = 14.px),
                    contentAlignment = Alignment.Center,
                ) {
                    PlasmaText(clockDuration(positionMs), plasmaStyle(19f, 600, tabular = true), color = PlasmaColors.OnFocus)
                }
            }
        }
        Row(Modifier.padding(top = 14.px).fillMaxWidth()) {
            val label = plasmaStyle(20f, 500, tabular = true)
            PlasmaText(clockDuration(positionMs), label, color = PlasmaColors.Text2)
            Spacer(Modifier.weight(1f))
            if (lengthMs > 0) PlasmaText(stringResource(R.string.time_left, clockDuration((lengthMs - positionMs).coerceAtLeast(0))), label, color = PlasmaColors.Text2)
        }
    }
}

/**
 * Up Next (brief 4.9): the next episode's still and name at the bottom right, and once the episode
 * has ended, "Next episode in 8 s". OK plays it now; Back keeps it from playing by itself.
 */
@Composable
private fun UpNextCard(upNext: UpNext, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val name = episodeLine(upNext.episode)
    PlasmaSurface(
        onClick = onPlay,
        modifier = modifier
            .offset(1344.px, 808.px)
            .size(480.px, 176.px)
            .semantics { contentDescription = name },
        style = FocusStyle.Row,
        shape = PlasmaShapes.Row,
        color = PlasmaColors.SheetFill,
    ) {
        val focused = LocalFocused.current
        Row(Modifier.fillMaxSize().padding(18.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.px)) {
            PlasmaImage(upNext.still, Modifier.size(176.px, 99.px).clip(PlasmaShapes.SmallPlate), placeholder = PlasmaColors.VideoWell)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.px)) {
                PlasmaText(stringResource(R.string.up_next), plasmaStyle(17f, 700, trackingPx = 1.2f), color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text3)
                PlasmaText(name, plasmaStyle(22f, 600), color = if (focused) PlasmaColors.OnFocus else PlasmaColors.Text, maxLines = 2)
                val countdown = upNext.countdown
                PlasmaText(
                    if (countdown != null) stringResource(R.string.next_episode_in, countdown) else stringResource(R.string.play_now),
                    plasmaStyle(19f, 500, tabular = true),
                    color = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.Text2,
                )
            }
        }
    }
}

/**
 * Player Options for a movie (board 10): its sound and subtitles, Picture Size, the player and
 * Sleep Timer, and how the stream is doing at the bottom.
 */
@Composable
private fun VodOptions(
    state: VodPlayerUiState,
    onTracks: () -> Unit,
    onPictureSize: () -> Unit,
    onSwitchPlayer: () -> Unit,
    onSleepTimer: () -> Unit,
    now: Instant,
) {
    val first = remember { FocusRequester() }
    val playback = state.playback
    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(PlasmaColors.black(0.25f))) {
        SheetPanel(Modifier.offset(560.px, 112.px).width(800.px)) {
            Column(Modifier.padding(horizontal = 10.px), verticalArrangement = Arrangement.spacedBy(4.px)) {
                PlasmaText(state.title, plasmaStyle(30f, 600, trackingPx = -0.4f))
                val meta = listOfNotNull(
                    state.episode?.let { episodeLine(it) },
                    state.lengthMs.takeIf { it > 0 }?.let { stringResource(R.string.time_of, clockDuration(state.positionMs), clockDuration(it)) },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) PlasmaText(meta, plasmaStyle(21f, 500, tabular = true), color = PlasmaColors.Text2)
            }
            Box(Modifier.padding(top = 22.px, bottom = 14.px).fillMaxWidth().height(1.px).background(PlasmaColors.white(0.1f)))
            Column(Modifier.focusRestoringGroup(first), verticalArrangement = Arrangement.spacedBy(4.px)) {
                SheetRow(
                    icon = { PlasmaIcon(PlasmaIcons.Volume, 24.px, tint = sheetIconTint()) },
                    label = stringResource(R.string.audio_and_subtitles),
                    value = listOfNotNull(audioName(playback.tracks), subtitlesName(playback.tracks) ?: stringResource(R.string.off)).joinToString(" · "),
                    onClick = onTracks,
                    modifier = Modifier.focusRequester(first),
                )
                SheetRow(
                    icon = { PlasmaIcon(PlasmaIcons.PictureSize, 24.px, tint = sheetIconTint()) },
                    label = stringResource(R.string.picture_size),
                    value = playback.pictureAspect?.let { stringResource(R.string.size_and_shape, pictureSizeName(playback.pictureSize), shapeName(it)) }
                        ?: pictureSizeName(playback.pictureSize),
                    onClick = onPictureSize,
                )
                SheetDivider()
                SheetRow(
                    icon = { PlasmaIcon(PlasmaIcons.SwitchPlayer, 24.px, tint = sheetIconTint()) },
                    label = stringResource(R.string.switch_player),
                    value = playback.nextEngine.takeIf { it.isNotEmpty() }?.let { "${playback.engine} → $it" } ?: playback.engine,
                    onClick = onSwitchPlayer,
                )
                SheetRow(
                    icon = { PlasmaIcon(PlasmaIcons.Moon, 24.px, tint = sheetIconTint()) },
                    label = stringResource(R.string.sleep_timer),
                    value = playback.sleep?.let { sleepLeft(it, now) } ?: stringResource(R.string.off),
                    onClick = onSleepTimer,
                )
            }
            Row(
                Modifier
                    .padding(top = 18.px)
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()
                        drawLine(PlasmaColors.white(0.1f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.px.toPx())
                    }
                    .padding(start = 22.px, end = 22.px, top = 16.px),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(9.px).background(
                        when (playback.health) {
                            StreamHealth.Good -> PlasmaColors.Ok
                            StreamHealth.Warning -> PlasmaColors.Warn
                            StreamHealth.Error -> PlasmaColors.Live
                        },
                        CircleShape,
                    ),
                )
                PlasmaText(
                    listOf(playback.engine, playback.format).filter { it.isNotEmpty() }.joinToString(" · "),
                    plasmaStyle(19f, 500, tabular = true),
                    Modifier.padding(start = 10.px),
                    color = PlasmaColors.Text3,
                )
            }
        }
        CenteredKeyFooter(
            keys = listOf(
                FooterKey("OK", stringResource(R.string.key_select)),
                FooterKey(PlasmaIcons.Back, stringResource(R.string.key_back_to_program)),
            ),
        )
    }
}

/** Left and Right settle this long before the player jumps, so a held key makes one jump. */
private const val SETTLE_MS = 600L

/** ⏩ and ⏪. */
private const val FAST_MS = 30_000L

/** Key repeats before Left and Right take bigger steps. */
private const val HOLD = 8
private const val LONG_HOLD = 20

/** End of Program needs a minute of the movie left. */
private val MINUTE: Duration = Duration.ofMinutes(1)
