// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.remote.app.resources.Res
import ca.gureet.plasma.remote.app.resources.key_down
import ca.gureet.plasma.remote.app.resources.key_left
import ca.gureet.plasma.remote.app.resources.key_ok
import ca.gureet.plasma.remote.app.resources.key_right
import ca.gureet.plasma.remote.app.resources.key_up
import ca.gureet.plasma.remote.app.resources.swipe_hint
import ca.gureet.plasma.remote.app.resources.swipe_pad_label
import ca.gureet.plasma.remote.app.settings.LocalPhoneSettings
import ca.gureet.plasma.remote.app.tv.TvLink
import ca.gureet.plasma.remote.protocol.RemoteKey
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

/**
 * The swipe pad (brief 5.22 and 4.35), in the D-pad's place when Settings says so: one rounded
 * panel to swipe on for the arrows, one arrow for each [STEP] of a swipe, so a long swipe moves
 * further. A tap is OK; held still for [HOLD_MS], it's a long OK, held down on the TV until the
 * finger lifts. "Swipe · tap for OK" shows until it's first used. A screen reader gets the five
 * keys as actions.
 */
@Composable
fun SwipePad(tv: TvLink, modifier: Modifier = Modifier) {
    val settings = LocalPhoneSettings.current
    val haptics = LocalHapticFeedback.current
    val hapticsOn by rememberUpdatedState(settings?.haptics ?: true)
    var used by remember { mutableStateOf(settings?.swipeHintSeen == true) }
    val hint by animateFloatAsState(if (used) 0f else 1f, tween(600))

    fun buzz() {
        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
    }

    fun press(key: RemoteKey) {
        buzz()
        tv.key(key, down = true)
        tv.key(key, down = false)
    }

    fun firstUse() {
        if (!used) {
            used = true
            settings?.swipeHintSeen()
        }
    }

    val label = stringResource(Res.string.swipe_pad_label)
    val actions = listOf(
        Res.string.key_up to RemoteKey.Up,
        Res.string.key_down to RemoteKey.Down,
        Res.string.key_left to RemoteKey.Left,
        Res.string.key_right to RemoteKey.Right,
        Res.string.key_ok to RemoteKey.Ok,
    ).map { (name, key) ->
        CustomAccessibilityAction(stringResource(name)) {
            press(key)
            true
        }
    }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxWidth().height(240.dp).clip(shape).background(RemoteColors.Surface1).border(1.dp, RemoteColors.Ring, shape)
            .semantics {
                contentDescription = label
                customActions = actions
            }
            .pointerInput(tv) {
                val step = STEP.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    firstUse()
                    val first = withTimeoutOrNull(HOLD_MS) { untilLiftedOrMoved(down.id, down.position, viewConfiguration.touchSlop) }
                    when (first) {
                        // Lifted before moving: a tap.
                        true -> press(RemoteKey.Ok)
                        // Still down and still: held, a long OK until the finger lifts.
                        null -> {
                            buzz()
                            tv.key(RemoteKey.Ok, down = true)
                            try {
                                untilLifted(down.id)
                            } finally {
                                tv.key(RemoteKey.Ok, down = false)
                            }
                        }
                        // Moved: an arrow for each step, whichever way it went furthest.
                        false -> {
                            var from = down.position
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                val moved = change.position - from
                                if (abs(moved.x) >= step && abs(moved.x) > abs(moved.y)) {
                                    press(if (moved.x > 0) RemoteKey.Right else RemoteKey.Left)
                                    from = change.position
                                } else if (abs(moved.y) >= step && abs(moved.y) > abs(moved.x)) {
                                    press(if (moved.y > 0) RemoteKey.Down else RemoteKey.Up)
                                    from = change.position
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(stringResource(Res.string.swipe_hint), Modifier.alpha(hint), style = textStyle(14.sp, color = RemoteColors.Text3))
    }
}

/** True once the finger lifts, false once it has moved past [slop]; it waits for either. */
private suspend fun AwaitPointerEventScope.untilLiftedOrMoved(id: PointerId, from: Offset, slop: Float): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return true
        if (!change.pressed) {
            change.consume()
            return true
        }
        if ((change.position - from).getDistance() > slop) return false
    }
}

private suspend fun AwaitPointerEventScope.untilLifted(id: PointerId) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return
        change.consume()
        if (!change.pressed) return
    }
}

/** How far a swipe goes for each arrow. */
private val STEP = 44.dp

/** How long a still finger is a tap; held longer, it's a long OK. */
private const val HOLD_MS = 450L
