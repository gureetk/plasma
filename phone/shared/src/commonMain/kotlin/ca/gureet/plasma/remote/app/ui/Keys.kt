// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import ca.gureet.plasma.remote.app.settings.LocalPhoneSettings
import ca.gureet.plasma.remote.app.tv.TvLink
import ca.gureet.plasma.remote.protocol.RemoteKey

/** Whether a key is held now, for its pressed look. */
@Composable
fun rememberPressed(): MutableState<Boolean> = remember { mutableStateOf(false) }

/**
 * A key of the TV's remote (brief 5.21): down as the finger touches it and up as it lets go, so a
 * long-press OK and a held D-pad repeat on the TV as with its own remote. A light tap of haptics
 * on each press (5.22). A screen reader's activation presses it and lets it go at once.
 */
@Composable
fun Modifier.remoteKey(tv: TvLink, key: RemoteKey, label: String, pressed: MutableState<Boolean>, enabled: Boolean = true): Modifier {
    val haptics = LocalHapticFeedback.current
    val hapticsOn by rememberUpdatedState(LocalPhoneSettings.current?.haptics ?: true)
    return this
        .semantics {
            role = Role.Button
            contentDescription = label
            if (enabled) {
                onClick {
                    tv.key(key, down = true)
                    tv.key(key, down = false)
                    true
                }
            }
        }
        .pointerInput(tv, key, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false).consume()
                pressed.value = true
                if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                tv.key(key, down = true)
                try {
                    waitForUpOrCancellation()?.consume()
                } finally {
                    pressed.value = false
                    tv.key(key, down = false)
                }
            }
        }
}

/** A tap that isn't a remote key (Go Live, a digit, a tab), with the same haptics. */
@Composable
fun Modifier.tap(label: String, enabled: Boolean = true, pressed: MutableState<Boolean>? = null, action: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val hapticsOn by rememberUpdatedState(LocalPhoneSettings.current?.haptics ?: true)
    val act by rememberUpdatedState(action)
    return this
        .semantics {
            role = Role.Button
            contentDescription = label
            if (enabled) {
                onClick {
                    act()
                    true
                }
            }
        }
        .pointerInput(label, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed?.value = true
                val up = try {
                    waitForUpOrCancellation()
                } finally {
                    pressed?.value = false
                }
                if (up != null) {
                    up.consume()
                    if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    act()
                }
            }
        }
}

/** Taps on a sheet's own surface go nowhere, so they don't reach what's behind it. */
fun Modifier.swallowTaps(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        waitForUpOrCancellation()?.consume()
    }
}

/**
 * A tap, or a finger held for the phone's long-press time, each with its own action and the same
 * haptics: a title's row opens its page with a tap and plays with a hold (#230). A screen reader's
 * activation taps, and its long-press action, named [holdLabel], holds. A row scrolled past does
 * neither.
 */
@Composable
fun Modifier.tapOrHold(label: String, holdLabel: String, pressed: MutableState<Boolean>? = null, onTap: () -> Unit, onHold: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val hapticsOn by rememberUpdatedState(LocalPhoneSettings.current?.haptics ?: true)
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    return this
        .semantics {
            role = Role.Button
            contentDescription = label
            onClick {
                tap()
                true
            }
            onLongClick(holdLabel) {
                hold()
                true
            }
        }
        .pointerInput(label) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed?.value = true
                try {
                    var scrolled = false
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation().also { if (it == null) scrolled = true } }
                    when {
                        up != null -> {
                            up.consume()
                            if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            tap()
                        }
                        // The list took the finger to scroll.
                        scrolled -> Unit
                        // Held: it plays, and lifting the finger afterwards does nothing more.
                        else -> {
                            if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            hold()
                            waitForUpOrCancellation()?.consume()
                        }
                    }
                } finally {
                    pressed?.value = false
                }
            }
        }
}
