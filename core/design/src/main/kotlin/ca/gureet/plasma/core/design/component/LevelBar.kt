// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.R
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/**
 * A bar over the playing video for a level, in the audio delay slider's pattern (brief 4.35):
 * "Volume level · Northline News · +3 dB" above the steps from [min] to [max], with a tick at 0.
 * It has focus while it's up: ◀ ▶ change [value] a step at a time, OK keeps it ([onKeep]), Back
 * puts it back ([onCancel]).
 *
 * With a [note], the level can't apply now, as when the sound passes through to a receiver: the
 * note stands in for the steps, and [action] is a button with focus, such as Decode This Channel.
 * Back closes it. Once the level can apply, focus goes back to the bar.
 */
@Composable
fun LevelBar(
    label: String,
    subject: String,
    value: Int,
    valueText: String,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    onKeep: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    val stepping = note == null
    // The button has focus while there is one; then the bar has it.
    val button = !stepping && action != null
    LaunchedEffect(button) {
        withFrameNanos { }
        focus.requestFocus()
    }
    Box(modifier.fillMaxSize()) {
        // The zap banner's bottom shade, so the bar reads over a busy picture or a list.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    520f / 1080f to PlasmaColors.black(0f),
                    720f / 1080f to PlasmaColors.black(0.68f),
                    900f / 1080f to PlasmaColors.black(0.92f),
                    1f to PlasmaColors.black(0.94f),
                ),
            ),
        )
        Column(
            Modifier
                .offset(360.px, 760.px)
                .width(1200.px)
                .boxShadow(PlasmaColors.black(0.55f), offsetY = 30.px, blur = 70.px, cornerRadius = 22.px)
                // The toast's fill over the ground, so a list behind it doesn't show through.
                .background(PlasmaColors.Bg, PlasmaShapes.Toast)
                .background(PlasmaColors.Toast, PlasmaShapes.Toast)
                .onKeyEvent { event ->
                    val up = event.type == KeyEventType.KeyUp
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (!up && stepping && value > min) onChange(value - 1)
                            true
                        }
                        Key.DirectionRight -> {
                            if (!up && stepping && value < max) onChange(value + 1)
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (up) if (stepping) onKeep() else onCancel()
                            true
                        }
                        Key.Back, Key.Escape -> {
                            if (up) onCancel()
                            true
                        }
                        // Focus stays on the bar while it's up.
                        Key.DirectionUp, Key.DirectionDown -> true
                        else -> false
                    }
                }
                .then(
                    if (button) {
                        Modifier
                    } else {
                        Modifier.focusRequester(focus).semantics { contentDescription = "$label, $subject, $valueText" }.focusable()
                    },
                )
                .padding(horizontal = 40.px, vertical = 30.px),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlasmaText(label, plasmaStyle(22f, 500), color = PlasmaColors.Text3)
                PlasmaText(" · ", plasmaStyle(22f, 500), color = PlasmaColors.Text4)
                PlasmaText(subject, plasmaStyle(24f, 600), Modifier.weight(1f), maxLines = 1)
                if (stepping) PlasmaText(valueText, plasmaStyle(34f, 600, trackingPx = -0.5f, tabular = true))
            }
            if (stepping) {
                Steps(value, min, max, Modifier.padding(top = 24.px).fillMaxWidth().height(36.px))
            } else {
                PlasmaText(note.orEmpty(), plasmaStyle(23f, 400, lineHeight = 1.4f), Modifier.padding(top = 18.px), color = PlasmaColors.TextSoft, maxLines = 2)
            }
            Row(Modifier.padding(top = 22.px), horizontalArrangement = Arrangement.spacedBy(28.px), verticalAlignment = Alignment.CenterVertically) {
                if (stepping) {
                    KeyHint("◀ ▶", stringResource(R.string.key_change), tone = HintTone.Video)
                    KeyHint("OK", stringResource(R.string.key_keep), tone = HintTone.Video)
                    KeyHint(PlasmaIcons.Back, stringResource(R.string.key_cancel), tone = HintTone.Video)
                } else {
                    if (action != null) {
                        PlasmaButton(action, onAction, Modifier.focusRequester(focus), height = 60.px, textSizePx = 22f, pivot = TransformOrigin(0f, 0.5f))
                    }
                    KeyHint(PlasmaIcons.Back, stringResource(R.string.key_close), tone = HintTone.Video)
                }
            }
        }
    }
}

/** The steps from [min] to [max] on a line, with a tick at 0, filled from 0 to [value]. */
@Composable
private fun Steps(value: Int, min: Int, max: Int, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val span = (max - min).coerceAtLeast(1)
        fun at(step: Int): Dp = maxWidth * ((step - min).toFloat() / span)
        val knob = 24.px
        // The line, the steps on it, and the tick at 0.
        Box(Modifier.fillMaxWidth().height(4.px).background(PlasmaColors.white(0.16f), PlasmaShapes.Pill))
        for (step in min..max) {
            if (step == 0) continue
            Box(Modifier.offset(at(step) - 3.px).size(6.px).background(PlasmaColors.white(0.28f), CircleShape))
        }
        Box(Modifier.offset(at(0) - 1.px).size(2.px, 22.px).background(PlasmaColors.white(0.6f)))
        // From 0 to the level.
        val from = minOf(0, value)
        val to = maxOf(0, value)
        if (from != to) Box(Modifier.offset(at(from)).width(at(to) - at(from)).height(4.px).background(PlasmaColors.Text, PlasmaShapes.Pill))
        Box(Modifier.offset(at(value) - knob / 2).size(knob).background(PlasmaColors.Text, CircleShape))
    }
}
