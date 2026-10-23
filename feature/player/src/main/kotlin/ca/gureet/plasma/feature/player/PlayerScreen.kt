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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.SelectKeys
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.NumberEntry
import ca.gureet.plasma.core.design.component.NumberEntryEffect
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.ReminderToast
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.digit
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.player.VideoSlot
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
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
        playback = playback.copy(paused = !playback.paused)
        actions.onPause(playback.paused)
    }

    fun zap(delta: Int) = tune(state.channels[(index + delta).mod(state.channels.size)])

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
                        // Back from Switch Source's list goes back to Player Options.
                        overlay == PlayerOverlay.Sources -> overlay = PlayerOverlay.Options
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
        VideoSlot(channel.frame, Modifier.fillMaxSize(), backdrop = overlay == PlayerOverlay.Options)

        when (overlay) {
            PlayerOverlay.Controls -> PlayerControls(
                channel = channel,
                now = now,
                playback = playback,
                onPlayPause = ::playPause,
                onSeek = { delta ->
                    val program = channel.programAt(position)
                    val maxBehind = program?.let { now.toEpochMilli() - it.start.toEpochMilli() } ?: 0L
                    playback = playback.copy(behindLiveMs = (playback.behindLiveMs - delta).coerceIn(0L, maxBehind))
                },
                onStartOver = ::startOver,
                onGoLive = ::goLive,
                onOptions = { overlay = PlayerOverlay.Options },
                onMultiview = actions.onMultiview,
                onChannelList = actions.onChannelList,
                focusTimeline = timelineFirst,
                focusStartOver = startOverFirst,
            )
            PlayerOverlay.Options -> PlayerOptions(
                channel = channel,
                now = now,
                // How the stream is doing, where it comes from and what plays it, as the app reports them now.
                playback = playback.copy(
                    health = state.playback.health,
                    source = state.playback.source,
                    engine = if (actions.onSwitchPlayer != null) state.playback.engine else playback.engine,
                    nextEngine = if (actions.onSwitchPlayer != null) state.playback.nextEngine else playback.nextEngine,
                ),
                favoriteIndex = favorites[channel.id],
                favoritesCount = state.favoritesCount,
                onStartOver = {
                    startOver()
                    startOverFirst = false
                    overlay = PlayerOverlay.Controls
                },
                onToggleFavorite = {
                    val favorite = channel.id !in favorites
                    favorites = if (favorite) favorites + (channel.id to favorites.size + 1) else favorites - channel.id
                    actions.onFavorite(channel.id, favorite)
                },
                onSwitchPlayer = {
                    val switch = actions.onSwitchPlayer
                    if (switch != null) switch() else playback = playback.copy(engine = playback.nextEngine, nextEngine = playback.engine)
                },
                onSwitchSource = { overlay = PlayerOverlay.Sources },
            )
            PlayerOverlay.Sources -> {
                val worksBest = stringResource(R.string.works_best)
                val sources = state.playback.sources.map { source ->
                    SheetOption(
                        source.name,
                        onClick = {
                            overlay = PlayerOverlay.None
                            if (!source.playing) actions.onSwitchSource(source.key)
                        },
                        icon = PlasmaIcons.SwitchSource,
                        checked = source.playing,
                        tag = if (source.remembered != null) worksBest else null,
                        note = source.remembered?.let { rememberedAgo(it, now) },
                    )
                }
                // Remember What Works per Channel: Forget What Works ends the list (brief 4.35).
                val forget = if (state.playback.sources.any { it.remembered != null }) {
                    listOf(
                        SheetOption.Divider,
                        SheetOption(
                            stringResource(R.string.forget_what_works),
                            onClick = {
                                overlay = PlayerOverlay.None
                                actions.onForgetSource()
                            },
                            icon = PlasmaIcons.History,
                        ),
                    )
                } else {
                    emptyList()
                }
                OptionsSheet(
                    title = stringResource(R.string.switch_source),
                    subtitle = channel.name,
                    onDismiss = { overlay = PlayerOverlay.Options },
                    options = sources + forget,
                )
            }
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
            PlayerStatus.Reconnecting -> if (overlay != PlayerOverlay.Options) ReconnectingPill()
            is PlayerStatus.Failed -> if (overlay != PlayerOverlay.Options) PlayerErrorCard(channel.name, status.problem, actions.onRetry)
            else -> Unit
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

/** The hint needs the program to have started at least 5 minutes ago, and to have 10 left (4.11). */
private val LATE: Duration = Duration.ofMinutes(5)
private val LEFT: Duration = Duration.ofMinutes(10)

/** "Remembered 3 days ago": when Remember What Works per Channel learned a source (brief 4.35). */
@Composable
private fun rememberedAgo(at: Instant, now: Instant): String {
    val zone = LocalPlasmaClock.current.zone
    val days = ChronoUnit.DAYS.between(at.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate()).toInt()
    return when {
        days <= 0 -> stringResource(R.string.remembered_today)
        days == 1 -> stringResource(R.string.remembered_yesterday)
        else -> pluralStringResource(R.plurals.remembered_days_ago, days, days)
    }
}
