// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.GraphiteIconTile
import ca.gureet.plasma.core.design.component.MAX_CHANNEL_DIGITS
import ca.gureet.plasma.core.design.component.NumberPad
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.component.digit
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/**
 * Choose Another Number (brief 4.30): a number pad like the PIN screen's, over Duplicate Numbers.
 * It says whether the typed number is free, and Use takes it. The remote's number keys type too;
 * Back cancels.
 */
@Composable
internal fun NumberChooser(
    conflict: NumberConflict,
    ownerOf: (Int) -> String?,
    onUse: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val number = typed.toIntOrNull()?.takeIf { it > 0 }
    val owner = number?.let(ownerOf)
    val usable = number != null && owner == null && number != conflict.newNumber
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PlasmaColors.Bg)
            .onPreviewKeyEvent { event ->
                val digit = event.key.digit()
                when {
                    event.key == Key.Back || event.key == Key.Escape -> {
                        if (event.type == KeyEventType.KeyUp) onDismiss()
                        true
                    }
                    digit != null -> {
                        if (event.type == KeyEventType.KeyDown) typed = (typed + digit).take(MAX_CHANNEL_DIGITS)
                        true
                    }
                    event.key == Key.Backspace || event.key == Key.Delete -> {
                        if (event.type == KeyEventType.KeyDown) typed = typed.dropLast(1)
                        true
                    }
                    else -> false
                }
            },
    ) {
        SecondaryHeader(stringResource(R.string.duplicate_numbers), title = null)
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 150.px)
                // Focus stays here until Use or Back.
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GraphiteIconTile(PlasmaIcons.Hash, 120.px, 30.px, 54.px, stroke = 1.6f)
            PlasmaText(stringResource(R.string.choose_number), plasmaStyle(48f, 600, trackingPx = -1f), Modifier.padding(top = 36.px))
            PlasmaText(
                stringResource(R.string.choose_number_for, conflict.mover.name, conflict.mover.source, conflict.newNumber),
                plasmaStyle(23f, 500, tabular = true),
                Modifier.padding(top = 14.px),
                color = PlasmaColors.Text2,
            )
            Box(
                Modifier
                    .padding(top = 40.px)
                    .size(320.px, 92.px)
                    .background(PlasmaColors.Surface1, PlasmaShapes.Row),
                contentAlignment = Alignment.Center,
            ) {
                PlasmaText(
                    typed.ifEmpty { conflict.newNumber.toString() },
                    plasmaStyle(56f, 600, trackingPx = -1f, tabular = true),
                    color = if (typed.isEmpty()) PlasmaColors.Text4 else PlasmaColors.Text,
                    align = TextAlign.Center,
                )
            }
            NumberPad(
                onDigit = { typed = (typed + it).take(MAX_CHANNEL_DIGITS) },
                onDelete = { typed = typed.dropLast(1) },
                modifier = Modifier.padding(top = 44.px),
                first = first,
            )
            Row(
                Modifier.padding(top = 36.px).height(30.px),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.px),
            ) {
                val (text, dot) = when {
                    number == null -> stringResource(R.string.number_hint) to null
                    number == conflict.newNumber -> stringResource(R.string.number_current, number) to null
                    owner != null -> stringResource(R.string.number_taken, number, owner) to Tone.Warn
                    else -> stringResource(R.string.number_free, number) to Tone.Ok
                }
                if (dot != null) Box(Modifier.size(10.px).background(if (dot == Tone.Ok) PlasmaColors.Ok else PlasmaColors.Warn, CircleShape))
                PlasmaText(
                    text,
                    plasmaStyle(22f, 500, tabular = true),
                    color = when (dot) {
                        Tone.Ok -> PlasmaColors.OkText
                        Tone.Warn -> PlasmaColors.WarnText
                        else -> PlasmaColors.Text3
                    },
                )
            }
            // The button keeps its place while there's nothing to use, so nothing below it jumps.
            Box(Modifier.padding(top = 24.px).height(68.px), contentAlignment = Alignment.Center) {
                if (usable && number != null) PlasmaButton(stringResource(R.string.use_number, number), onClick = { onUse(number) })
            }
            PlasmaText(stringResource(R.string.number_keys_hint), plasmaStyle(21f, 500), Modifier.padding(top = 30.px), color = PlasmaColors.Text3)
        }
    }
}
