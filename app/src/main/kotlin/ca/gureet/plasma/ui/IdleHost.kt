// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import ca.gureet.plasma.core.design.Overlay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Whether Plasma's screensaver is over the screen: what's under it can wait meanwhile, as Home's
 * banner's photos do (#171).
 */
val LocalScreensaverShowing = compositionLocalOf { false }

/** The key that woke the screensaver, so its key-up is used up too. Not state: nothing redraws for it. */
private class WakingKey {
    var key: Key? = null
}

/**
 * Shows [screensaver] over [content] after [timeoutMs] without a key press (the in-app idle screen,
 * brief 5.14), unless [enabled] is false, as while something plays. The key that wakes Plasma
 * only wakes it. A new [nudge] counts as a key press: Home, which Plasma hears as an opening.
 */
@Composable
fun IdleHost(
    enabled: Boolean,
    timeoutMs: Long,
    screensaver: @Composable () -> Unit,
    nudge: Any? = null,
    content: @Composable () -> Unit,
) {
    var idle by remember { mutableStateOf(false) }
    val presses = remember { Channel<Unit>(Channel.CONFLATED) }
    val waking = remember { WakingKey() }
    LaunchedEffect(nudge) {
        if (nudge != null) presses.trySend(Unit)
    }
    LaunchedEffect(enabled, timeoutMs) {
        idle = false
        if (!enabled) return@LaunchedEffect
        while (true) {
            if (withTimeoutOrNull(timeoutMs) { presses.receive() } == null) {
                idle = true
                presses.receive()
                idle = false
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                presses.trySend(Unit)
                when {
                    idle && event.type == KeyEventType.KeyDown -> {
                        waking.key = event.key
                        true
                    }
                    event.type == KeyEventType.KeyUp && event.key == waking.key -> {
                        waking.key = null
                        true
                    }
                    else -> false
                }
            },
    ) {
        CompositionLocalProvider(LocalScreensaverShowing provides idle) { content() }
        // It fades in over the screen and out again as a key wakes Plasma (brief 2.6), the
        // screen underneath taking the keys at once.
        Overlay(idle, rise = false) { screensaver() }
    }
}
