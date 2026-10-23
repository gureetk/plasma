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
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.SelectKeys
import ca.gureet.plasma.core.design.component.NumberEntry
import ca.gureet.plasma.core.design.component.NumberEntryEffect
import ca.gureet.plasma.core.design.component.ReminderToast
import ca.gureet.plasma.core.design.component.digit
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.player.VideoSlot
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

    val index = state.channels.indexOfFirst { it.id == channelId }.coerceAtLeast(0)
    val channel = state.channels[index]

    fun tune(target: PlayerChannel) {
        channelId = target.id
        playback = playback.copy(behindLiveMs = 0, paused = false)
        overlay = PlayerOverlay.Zap
        actions.onTune(target.id)
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

    // The overlay hides after 5 s and the zap banner after 4 s without a key press (2.6).
    LaunchedEffect(overlay, keyPresses, autoHide) {
        if (!autoHide) return@LaunchedEffect
        val hideAfter = when (overlay) {
            PlayerOverlay.Controls -> PlasmaMotion.OVERLAY_HIDE_MS
            PlayerOverlay.Zap -> PlasmaMotion.ZAP_BANNER_HIDE_MS
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
                if (it.type == KeyEventType.KeyDown) keyPresses++
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
                    zapKeys && event.key == Key.DirectionUp -> { zap(+1); true }
                    zapKeys && event.key == Key.DirectionDown -> { zap(-1); true }
                    zapKeys && event.key == Key.DirectionLeft -> { actions.onChannelList(); true }
                    zapKeys && event.key == Key.DirectionRight -> {
                        timelineFirst = true
                        overlay = PlayerOverlay.Controls
                        true
                    }
                    zapKeys && event.key in SelectKeys -> {
                        if (entry.isActive) {
                            entry.finish(find, ::tune)
                        } else {
                            timelineFirst = false
                            overlay = PlayerOverlay.Controls
                        }
                        true
                    }
                    zapKeys && (event.key == Key.Menu || event.key == Key.Info) -> {
                        timelineFirst = false
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
                onPlayPause = { playback = playback.copy(paused = !playback.paused) },
                onSeek = { delta ->
                    val program = channel.programAt(now)
                    val maxBehind = program?.let { now.toEpochMilli() - it.start.toEpochMilli() } ?: 0L
                    playback = playback.copy(behindLiveMs = (playback.behindLiveMs - delta).coerceIn(0L, maxBehind))
                },
                onStartOver = {
                    val program = channel.programAt(now)
                    if (program != null) playback = playback.copy(behindLiveMs = now.toEpochMilli() - program.start.toEpochMilli())
                },
                onGoLive = { playback = playback.copy(behindLiveMs = 0, paused = false) },
                onOptions = { overlay = PlayerOverlay.Options },
                onMultiview = actions.onMultiview,
                onChannelList = actions.onChannelList,
                focusTimeline = timelineFirst,
            )
            PlayerOverlay.Options -> PlayerOptions(
                channel = channel,
                now = now,
                // How the stream is doing, and where it comes from, as the app reports them now.
                playback = playback.copy(health = state.playback.health, source = state.playback.source),
                favoriteIndex = favorites[channel.id],
                favoritesCount = state.favoritesCount,
                onStartOver = {
                    val program = channel.programAt(now)
                    if (program != null) playback = playback.copy(behindLiveMs = now.toEpochMilli() - program.start.toEpochMilli())
                    overlay = PlayerOverlay.Controls
                },
                onToggleFavorite = {
                    val favorite = channel.id !in favorites
                    favorites = if (favorite) favorites + (channel.id to favorites.size + 1) else favorites - channel.id
                    actions.onFavorite(channel.id, favorite)
                },
                onSwitchPlayer = {
                    playback = playback.copy(engine = playback.nextEngine, nextEngine = playback.engine)
                },
            )
            PlayerOverlay.Zap -> ZapBanner(
                channel = channel,
                above = state.channels[(index + 1).mod(state.channels.size)],
                below = state.channels[(index - 1).mod(state.channels.size)],
                now = now,
                entry = entry,
                match = find(entry.digits),
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
