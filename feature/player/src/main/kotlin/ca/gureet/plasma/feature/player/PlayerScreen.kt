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
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.digit
import ca.gureet.plasma.player.VideoSlot
import kotlinx.coroutines.delay

/**
 * The full-screen player (brief 4.9–4.11): the video, and over it the overlay (OK), Player Options
 * (Menu or long-press OK), or the zap banner after a channel change (Up/Down, CH ±, digits).
 * Reminders pop up at the top right and take focus, while channel and number keys keep working.
 *
 * Until the engines arrive in M1, the picture is the channel's still and playback is simulated.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    initialOverlay: PlayerOverlay = PlayerOverlay.Zap,
    autoHide: Boolean = true,
) {
    val clock = LocalPlasmaClock.current
    val now = remember(clock) { clock.now() }
    var channelId by remember { mutableStateOf(state.currentChannelId) }
    var overlay by remember { mutableStateOf(initialOverlay) }
    var playback by remember { mutableStateOf(state.playback) }
    var keyPresses by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf(state.reminder) }
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
    // Number entry tunes 1.5 s after the last digit (4.11).
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(PlasmaMotion.NUMBER_ENTRY_TUNE_MS)
        state.channels.firstOrNull { it.number == digits }?.let(::tune)
        delay(if (state.channels.any { it.number == digits }) 0 else PlasmaMotion.NUMBER_ENTRY_TUNE_MS)
        digits = ""
    }
    // With nothing on screen, or just the banner, the player itself takes the keys.
    LaunchedEffect(overlay) {
        if (overlay == PlayerOverlay.None || (overlay == PlayerOverlay.Zap && reminder == null)) {
            withFrameNanos { }
            root.requestFocus()
        }
    }

    BackHandler(enabled = overlay != PlayerOverlay.None || digits.isNotEmpty()) {
        digits = ""
        overlay = PlayerOverlay.None
    }
    BackHandler(enabled = overlay == PlayerOverlay.None && digits.isEmpty(), onBack = actions.onBack)

    val zapKeys = overlay == PlayerOverlay.None || overlay == PlayerOverlay.Zap
    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) keyPresses++
                false
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val digit = event.key.digit()
                when {
                    digit != null -> {
                        digits = (digits + digit).takeLast(4)
                        overlay = PlayerOverlay.Zap
                        true
                    }
                    event.key == Key.ChannelUp -> { zap(+1); true }
                    event.key == Key.ChannelDown -> { zap(-1); true }
                    zapKeys && event.key == Key.DirectionUp -> { zap(+1); true }
                    zapKeys && event.key == Key.DirectionDown -> { zap(-1); true }
                    zapKeys && event.key == Key.DirectionLeft -> { actions.onChannelList(); true }
                    zapKeys && event.key in setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter) -> {
                        if (digits.isNotEmpty()) {
                            state.channels.firstOrNull { it.number == digits }?.let(::tune)
                            digits = ""
                        } else {
                            overlay = PlayerOverlay.Controls
                        }
                        true
                    }
                    zapKeys && (event.key == Key.Menu || event.key == Key.Info) -> {
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
            )
            PlayerOverlay.Options -> PlayerOptions(
                channel = channel,
                now = now,
                playback = playback,
                favoriteIndex = favorites[channel.id],
                favoritesCount = state.favoritesCount,
                onStartOver = {
                    val program = channel.programAt(now)
                    if (program != null) playback = playback.copy(behindLiveMs = now.toEpochMilli() - program.start.toEpochMilli())
                    overlay = PlayerOverlay.Controls
                },
                onToggleFavorite = {
                    favorites = if (channel.id in favorites) favorites - channel.id else favorites + (channel.id to favorites.size + 1)
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
                digits = digits,
                match = state.channels.firstOrNull { it.number == digits },
            )
            PlayerOverlay.None -> if (digits.isNotEmpty()) {
                NumberBox(channel, digits, state.channels.firstOrNull { it.number == digits })
            }
        }

        reminder?.let { r ->
            if (overlay != PlayerOverlay.Options) {
                ReminderToast(
                    reminder = r,
                    now = now,
                    onSwitch = {
                        state.channels.firstOrNull { it.id == r.channelId }?.let(::tune)
                        reminder = null
                    },
                    onDismiss = { reminder = null },
                )
            }
        }
    }
}
