// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.SelectKeys
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.NumberEntry
import ca.gureet.plasma.core.design.component.NumberEntryEffect
import ca.gureet.plasma.core.design.component.ReminderToast
import ca.gureet.plasma.core.design.component.SwitchSourceSheet
import ca.gureet.plasma.core.design.component.digit
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.player.VideoSlot
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * The full-screen player (brief 4.9–4.11): the video, and over it the overlay (OK), Player Options
 * (Menu or long-press OK), or the zap banner after a channel change (Up/Down, CH ±, digits).
 * Reminders pop up at the top right and take focus, while channel and number keys keep working.
 *
 * On sample data the picture is the channel's still and playback is simulated; with live video the
 * picture plays, while pause and rewind wait for the rewind buffer (M6).
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    initialOverlay: PlayerOverlay = PlayerOverlay.Zap,
    autoHide: Boolean = true,
) {
    // The live player runs for hours, so its idea of now moves on each minute.
    val now = rememberNow().value.toInstant()
    val clock = LocalPlasmaClock.current
    var channelId by remember { mutableStateOf(state.currentChannelId) }
    var overlay by remember { mutableStateOf(initialOverlay) }
    // Right opens the overlay on the timeline rather than on Play/Pause (brief 3.6).
    var timelineFirst by remember { mutableStateOf(false) }
    var playback by remember { mutableStateOf(state.playback) }
    var keyPresses by remember { mutableIntStateOf(0) }
    val entry = remember { NumberEntry() }
    var reminder by remember { mutableStateOf(state.reminder) }
    // A reminder coming due while the player is open (brief 5.7), and gone once the app is done with it.
    LaunchedEffect(state.reminder) { reminder = state.reminder }
    var favorites by remember { mutableStateOf(state.channels.filter { it.favoriteIndex != null }.associate { it.id to it.favoriteIndex!! }) }
    // The row Down opens under the overlay's controls (the owner's ask, after TiviMate's).
    var moreOpen by remember { mutableStateOf(false) }
    LaunchedEffect(overlay) { if (overlay != PlayerOverlay.Controls) moreOpen = false }
    val root = remember { FocusRequester() }
    // Tuning in late on a channel with an archive offers Watch from the Start, once a program (4.11).
    var hint by remember { mutableStateOf<PlayerProgram?>(null) }
    val offered = remember { mutableSetOf<String>() }
    var startOverFirst by remember { mutableStateOf(false) }
    // The app says where the picture is when it moves it: Start Over, Go Live, an archive that ended.
    LaunchedEffect(state.playback.behindLiveMs) { playback = playback.copy(behindLiveMs = state.playback.behindLiveMs) }

    val index = state.channels.indexOfFirst { it.id == channelId }.coerceAtLeast(0)
    val channel = state.channels[index]
    // Where the picture is: behind live after Start Over, or in a program from the archive.
    val position = now.minusMillis(playback.behindLiveMs)

    /** The program on [target], when it started at least 5 minutes ago, has 10 left, and its start is in the archive. */
    fun lateStart(target: PlayerChannel): PlayerProgram? {
        if (!state.offerStartOver || !target.catchUp) return null
        val program = target.programAt(now) ?: return null
        if (Duration.between(program.start, now) < LATE || Duration.between(now, program.end) < LEFT) return null
        return program.takeIf { offered.add("${target.id}/${program.id}") }
    }

    fun tune(target: PlayerChannel) {
        channelId = target.id
        playback = playback.copy(behindLiveMs = 0, paused = false)
        overlay = PlayerOverlay.Zap
        hint = lateStart(target)
        actions.onTune(target.id)
    }

    /** Start Over, Watch from the Start, green, or the hint: the program from its start, from the archive (brief 5.5). */
    fun startOver() {
        val program = channel.programAt(position) ?: return
        // It needs the provider's archive (brief 3.6, E).
        if (!channel.catchUp) return
        hint = null
        playback = playback.copy(behindLiveMs = Duration.between(program.start, now).toMillis().coerceAtLeast(0), paused = false)
        actions.onStartOver(channel.id, program.start)
    }

    fun goLive() {
        playback = playback.copy(behindLiveMs = 0, paused = false)
        actions.onGoLive(channel.id)
    }

    fun playPause() {
        val pause = actions.onPause
        // Live TV pauses only where the app can keep the stream (5.5): it says whether it did.
        if (pause != null) pause(!state.playback.paused) else playback = playback.copy(paused = !playback.paused)
    }

    fun zap(delta: Int) = tune(state.channels[(index + delta).mod(state.channels.size)])

    /** Add to or Remove from Favorites, in Player Options or the row under the controls. */
    fun toggleFavorite() {
        val favorite = channel.id !in favorites
        favorites = if (favorite) favorites + (channel.id to favorites.size + 1) else favorites - channel.id
        actions.onFavorite(channel.id, favorite)
    }

    // What the app reports now: the stream's health and format, where it comes from, what plays
    // it, its tracks and Picture Size. On sample data, the choices change here instead.
    val shown = playback.copy(
        paused = if (actions.onPause != null) state.playback.paused else playback.paused,
        health = state.playback.health,
        source = state.playback.source,
        engine = if (actions.onSwitchPlayer != null) state.playback.engine else playback.engine,
        nextEngine = if (actions.onSwitchPlayer != null) state.playback.nextEngine else playback.nextEngine,
        tracks = if (actions.onAudio != null) state.playback.tracks else playback.tracks,
        pictureSize = if (actions.onPictureSize != null) state.playback.pictureSize else playback.pictureSize,
        pictureAspect = state.playback.pictureAspect,
        format = state.playback.format,
        bitrate = state.playback.bitrate,
        buffer = state.playback.buffer,
        sleep = if (actions.onSleepTimer != null) state.playback.sleep else playback.sleep,
    )

    /** Audio and Subtitles (4.10): the sound track [id] plays. */
    fun pickAudio(id: String) {
        val pick = actions.onAudio
        if (pick != null) pick(id) else playback = playback.copy(tracks = playback.tracks.copy(audioId = id))
    }

    /** Audio and Subtitles: the subtitle track [id] shows, or none. */
    fun pickSubtitles(id: String?) {
        val pick = actions.onSubtitles
        if (pick != null) pick(id) else playback = playback.copy(tracks = playback.tracks.copy(subtitleId = id))
    }

    /** What's left of the program at the picture's position now, for Sleep Timer's End of Program. */
    fun programLeft(at: Instant): Duration? {
        val position = at.minusMillis(playback.behindLiveMs)
        return channel.programAt(position)?.let { Duration.between(position, it.end) }?.takeIf { it >= MINUTE }
    }

    /** Sleep Timer (4.10): the channel stops after [choice], from the clock's now rather than the minute's. */
    fun setSleep(choice: SleepChoice) {
        val at = clock.now()
        val after = when (choice) {
            SleepChoice.Off -> null
            SleepChoice.EndOfProgram -> programLeft(at)
            else -> choice.minutes?.let(Duration::ofMinutes)
        }
        val set = actions.onSleepTimer
        if (set != null) {
            set(choice, after)
        } else {
            playback = playback.copy(sleep = after?.let { SleepInfo(choice, at.plus(it)) })
        }
    }

    /** The remote's Audio key: the next sound track (brief 3.7). */
    fun nextAudio() {
        val next = actions.onNextAudio
        if (next != null) return next()
        val tracks = shown.tracks
        if (tracks.audio.size < 2) return
        val at = tracks.audio.indexOfFirst { it.id == tracks.audioId }
        pickAudio(tracks.audio[(at + 1).mod(tracks.audio.size)].id)
    }

    /** The remote's Subtitles key: subtitles on or off. */
    fun nextSubtitles() {
        val next = actions.onNextSubtitles
        if (next != null) return next()
        val tracks = shown.tracks
        if (tracks.subtitleId != null) pickSubtitles(null) else tracks.subtitles.firstOrNull()?.let { pickSubtitles(it.id) }
    }

    /** Switch Now or Dismiss. A channel outside the list being zapped through opens on its own. */
    fun closeReminder(switch: Boolean) {
        val closing = reminder ?: return
        reminder = null
        if (switch) {
            val target = state.channels.firstOrNull { it.id == closing.channelId }
            if (target != null) tune(target) else actions.onOpenChannel(closing.channelId)
        }
        actions.onReminderDone()
    }

    // The overlay hides after 5 s and the zap banner after 4 s without a key press (2.6); the
    // banner with the hint stays up for 8 s (4.11).
    LaunchedEffect(overlay, keyPresses, autoHide, hint) {
        if (overlay != PlayerOverlay.Zap) hint = null
        if (!autoHide) return@LaunchedEffect
        val hideAfter = when (overlay) {
            PlayerOverlay.Controls -> PlasmaMotion.OVERLAY_HIDE_MS
            PlayerOverlay.Zap -> if (hint != null) PlasmaMotion.ZAP_HINT_HIDE_MS else PlasmaMotion.ZAP_BANNER_HIDE_MS
            else -> return@LaunchedEffect
        }
        delay(hideAfter)
        overlay = PlayerOverlay.None
    }
    // A typed number tunes 1.5 s after the last digit, or on OK (4.11).
    val find = { number: String -> state.channels.firstOrNull { it.number == number } }
    NumberEntryEffect(entry, find, ::tune)
    // With nothing on screen, or just the banner, the player itself takes the keys; a reminder
    // takes them while it shows.
    val reminderShowing = reminder != null && overlay != PlayerOverlay.Options
    LaunchedEffect(overlay, reminderShowing) {
        if ((overlay == PlayerOverlay.None || overlay == PlayerOverlay.Zap) && !reminderShowing) {
            withFrameNanos { }
            root.requestFocus()
        }
    }

    BackHandler(enabled = overlay != PlayerOverlay.None || entry.isActive) {
        entry.clear()
        overlay = PlayerOverlay.None
    }
    BackHandler(enabled = overlay == PlayerOverlay.None && !entry.isActive, onBack = actions.onBack)

    val zapKeys = overlay == PlayerOverlay.None || overlay == PlayerOverlay.Zap
    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    keyPresses++
                    // Keys other than OK, green and ◀◀ behave as usual and dismiss the hint (4.11).
                    if (hint != null && it.key !in SelectKeys && it.key != Key.ProgramGreen && it.key != Key.MediaRewind) hint = null
                }
                // OK tunes a number being typed, before a reminder's Switch Now can take it (4.11).
                if (entry.isActive && it.key in SelectKeys && it.type == KeyEventType.KeyDown) {
                    if (it.nativeKeyEvent.repeatCount == 0) entry.finish(find, ::tune)
                    return@onPreviewKeyEvent true
                }
                // Holding OK opens Player Options, as Menu does (brief 3.6).
                if (it.key in SelectKeys && it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount > 0) {
                    if (overlay != PlayerOverlay.Options) {
                        entry.clear()
                        overlay = PlayerOverlay.Options
                    }
                    return@onPreviewKeyEvent true
                }
                // Back hides what's showing, then leaves the player (brief 3.5). It's taken here,
                // before focus would use it to step out of the overlay's buttons, so each press
                // does one visible thing.
                if (it.key != Key.Back && it.key != Key.Escape) return@onPreviewKeyEvent false
                if (it.type == KeyEventType.KeyUp) {
                    when {
                        // Back from Switch Source's list, Audio and Subtitles, Picture Size or Sleep
                        // Timer goes back to Player Options.
                        overlay in OverOptions -> overlay = PlayerOverlay.Options
                        // Back from the row under the controls goes back to the controls.
                        overlay == PlayerOverlay.Controls && moreOpen -> moreOpen = false
                        entry.isActive || overlay != PlayerOverlay.None -> {
                            entry.clear()
                            overlay = PlayerOverlay.None
                        }
                        reminder != null -> closeReminder(switch = false)
                        else -> actions.onBack()
                    }
                }
                true
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val digit = event.key.digit()
                when {
                    digit != null -> {
                        entry.type(digit)
                        overlay = PlayerOverlay.Zap
                        true
                    }
                    event.key == Key.ChannelUp -> { zap(+1); true }
                    event.key == Key.ChannelDown -> { zap(-1); true }
                    // The remote's Audio and Subtitles keys, where it has them (brief 3.7).
                    event.key == Key.MediaAudioTrack -> { nextAudio(); true }
                    event.key == Key.Captions -> { nextSubtitles(); true }
                    // Green is Start Over in the player (brief 3.7); ◀◀ is too, while the hint shows.
                    event.key == Key.ProgramGreen && overlay != PlayerOverlay.Options -> { startOver(); true }
                    event.key == Key.MediaRewind && hint != null -> { startOver(); true }
                    zapKeys && event.key == Key.DirectionUp -> { zap(+1); true }
                    zapKeys && event.key == Key.DirectionDown -> { zap(-1); true }
                    // Left and Right move between a reminder's buttons while it shows.
                    zapKeys && reminderShowing && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> false
                    zapKeys && event.key == Key.DirectionLeft -> { actions.onChannelList(); true }
                    zapKeys && event.key == Key.DirectionRight -> {
                        timelineFirst = true
                        startOverFirst = false
                        overlay = PlayerOverlay.Controls
                        true
                    }
                    zapKeys && event.key in SelectKeys -> {
                        if (entry.isActive) {
                            entry.finish(find, ::tune)
                        } else {
                            // With the hint up, OK opens the overlay on Start Over, so a second OK starts it.
                            timelineFirst = false
                            startOverFirst = hint != null
                            overlay = PlayerOverlay.Controls
                        }
                        true
                    }
                    zapKeys && (event.key == Key.Menu || event.key == Key.Info) -> {
                        timelineFirst = false
                        startOverFirst = false
                        overlay = if (event.key == Key.Menu) PlayerOverlay.Options else PlayerOverlay.Controls
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(root)
            .focusable(),
    ) {
        // Player Options, and the lists it opens, sit over the blurred picture (board 10).
        val backdrop = overlay == PlayerOverlay.Options || overlay in OverOptions
        VideoSlot(channel.frame.copy(pictureSize = shown.pictureSize), Modifier.fillMaxSize(), backdrop = backdrop)

        when (overlay) {
            PlayerOverlay.Controls -> PlayerControls(
                channel = channel,
                now = now,
                playback = shown,
                onPlayPause = ::playPause,
                onSeek = { delta ->
                    val program = channel.programAt(position)
                    // As far back as the program's start in the archive, or the rewind buffer's reach (5.5).
                    val toStart = program?.let { now.toEpochMilli() - it.start.toEpochMilli() } ?: 0L
                    val maxBehind = if (channel.catchUp) toStart else minOf(toStart, state.playback.rewindReachMs)
                    val behind = (playback.behindLiveMs - delta).coerceIn(0L, maxBehind.coerceAtLeast(0))
                    if (behind != playback.behindLiveMs) {
                        playback = playback.copy(behindLiveMs = behind, paused = false)
                        actions.onSeek?.invoke(behind)
                    }
                },
                onStartOver = ::startOver,
                onGoLive = ::goLive,
                onOptions = { overlay = PlayerOverlay.Options },
                onMultiview = actions.onMultiview,
                onChannelList = actions.onChannelList,
                focusTimeline = timelineFirst,
                focusStartOver = startOverFirst,
                onTracks = { overlay = PlayerOverlay.Tracks },
                more = buildList {
                    val favorite = channel.id in favorites
                    add(MoreButton(PlasmaIcons.Star, stringResource(if (favorite) R.string.remove_from_favorites else R.string.add_to_favorites), ::toggleFavorite))
                    add(MoreButton(PlasmaIcons.ChannelList, stringResource(R.string.more_channel_list), actions.onChannelList))
                    actions.onSearch?.let { add(MoreButton(PlasmaIcons.Search, stringResource(R.string.more_search), it)) }
                    actions.onGuide?.let { add(MoreButton(PlasmaIcons.Guide, stringResource(R.string.more_guide), it)) }
                    add(MoreButton(PlasmaIcons.PictureSize, stringResource(R.string.picture_size)) { overlay = PlayerOverlay.Sizes })
                    add(MoreButton(PlasmaIcons.Moon, stringResource(R.string.sleep_timer)) { overlay = PlayerOverlay.Sleep })
                    add(MoreButton(PlasmaIcons.Menu, stringResource(R.string.player_options)) { overlay = PlayerOverlay.Options })
                },
                moreOpen = moreOpen,
                onMoreOpen = { moreOpen = it },
            )
            PlayerOverlay.Options -> PlayerOptions(
                channel = channel,
                now = now,
                playback = shown,
                favoriteIndex = favorites[channel.id],
                favoritesCount = state.favoritesCount,
                onStartOver = {
                    startOver()
                    startOverFirst = false
                    overlay = PlayerOverlay.Controls
                },
                onToggleFavorite = ::toggleFavorite,
                onSwitchPlayer = {
                    val switch = actions.onSwitchPlayer
                    if (switch != null) switch() else playback = playback.copy(engine = playback.nextEngine, nextEngine = playback.engine)
                },
                onSwitchSource = { overlay = PlayerOverlay.Sources },
                onRecord = { program ->
                    actions.onRecord(channel.id, program.id, !program.recording)
                    overlay = PlayerOverlay.None
                },
                onRecordSeries = { program ->
                    actions.onRecordSeries(channel.id, program.id, !program.seriesRule)
                    overlay = PlayerOverlay.None
                },
                onTracks = { overlay = PlayerOverlay.Tracks },
                onPictureSize = { overlay = PlayerOverlay.Sizes },
                onSleepTimer = { overlay = PlayerOverlay.Sleep },
            )
            PlayerOverlay.Tracks -> TracksSheet(
                channel = channel.name,
                tracks = shown.tracks,
                onAudio = { id ->
                    overlay = PlayerOverlay.None
                    if (id != shown.tracks.audioId) pickAudio(id)
                },
                onSubtitles = { id ->
                    overlay = PlayerOverlay.None
                    if (id != shown.tracks.subtitleId) pickSubtitles(id)
                },
                onDismiss = { overlay = PlayerOverlay.Options },
            )
            PlayerOverlay.Sizes -> PictureSizeSheet(
                channel = channel.name,
                size = shown.pictureSize,
                onPick = { size ->
                    overlay = PlayerOverlay.None
                    val pick = actions.onPictureSize
                    if (pick != null) pick(size) else playback = playback.copy(pictureSize = size)
                },
                onDismiss = { overlay = PlayerOverlay.Options },
            )
            PlayerOverlay.Sleep -> SleepTimerSheet(
                channel = channel.name,
                sleep = shown.sleep,
                programLeft = programLeft(now),
                now = now,
                onPick = { choice ->
                    overlay = PlayerOverlay.None
                    setSleep(choice)
                },
                onDismiss = { overlay = PlayerOverlay.Options },
            )
            PlayerOverlay.Sources -> SwitchSourceSheet(
                channel = channel.name,
                sources = state.playback.sources,
                onPick = { source ->
                    overlay = PlayerOverlay.None
                    if (!source.playing) actions.onSwitchSource(source.key)
                },
                onForget = {
                    overlay = PlayerOverlay.None
                    actions.onForgetSource()
                },
                onDismiss = { overlay = PlayerOverlay.Options },
            )
            PlayerOverlay.Zap -> ZapBanner(
                channel = channel,
                above = state.channels[(index + 1).mod(state.channels.size)],
                below = state.channels[(index - 1).mod(state.channels.size)],
                now = now,
                entry = entry,
                match = find(entry.digits),
                lateStart = hint,
            )
            PlayerOverlay.None -> if (entry.isActive) {
                NumberBox(channel, entry, find(entry.digits))
            }
        }

        when (val status = state.status) {
            PlayerStatus.Opening -> if (overlay != PlayerOverlay.Options) LoadingRing()
            PlayerStatus.Reconnecting -> if (overlay != PlayerOverlay.Options) ReconnectingPill()
            is PlayerStatus.Failed -> if (overlay != PlayerOverlay.Options) PlayerErrorCard(channel.name, status.problem, actions.onRetry)
            PlayerStatus.Playing -> Unit
        }

        reminder?.let { r ->
            if (overlay != PlayerOverlay.Options) {
                ReminderToast(
                    reminder = r,
                    now = now,
                    onSwitch = { closeReminder(switch = true) },
                    onDismiss = { closeReminder(switch = false) },
                )
            }
        }
    }
}

/** The lists Player Options opens, which Back returns from to it. */
private val OverOptions = setOf(PlayerOverlay.Sources, PlayerOverlay.Tracks, PlayerOverlay.Sizes, PlayerOverlay.Sleep)

/** End of Program needs a minute of the program left. */
private val MINUTE: Duration = Duration.ofMinutes(1)

/** The hint needs the program to have started at least 5 minutes ago, and to have 10 left (4.11). */
private val LATE: Duration = Duration.ofMinutes(5)
private val LEFT: Duration = Duration.ofMinutes(10)
