// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

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
import ca.gureet.plasma.core.design.R
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/**
 * A channel number to choose, on a number pad like the PIN screen's (board 25, brief 4.30 and
 * 4.35): it says whether the typed number is free or which channel has it, and Use takes a free
 * one. [current] shows until something's typed. The remote's number keys type too; Back cancels.
 *
 * @param back the header's back label: where Back goes.
 */
@Composable
fun NumberChooser(
    back: String,
    title: String,
    subtitle: String,
    current: Int,
    ownerOf: (Int) -> String?,
    onUse: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by remember { mutableStateOf("") }
    val number = typed.toIntOrNull()?.takeIf { it > 0 }
    val owner = number?.let(ownerOf)
    val usable = number != null && owner == null && number != current
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.requestFocus()
    }

    Box(
        modifier
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
        SecondaryHeader(back, title = null)
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
            PlasmaText(title, plasmaStyle(48f, 600, trackingPx = -1f), Modifier.padding(top = 36.px))
            PlasmaText(subtitle, plasmaStyle(23f, 500, tabular = true), Modifier.padding(top = 14.px), color = PlasmaColors.Text2)
            Box(
                Modifier
                    .padding(top = 40.px)
                    .size(320.px, 92.px)
                    .background(PlasmaColors.Surface1, PlasmaShapes.Row),
                contentAlignment = Alignment.Center,
            ) {
                PlasmaText(
                    typed.ifEmpty { current.toString() },
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
                // A green dot for a free number, an amber one for a taken one.
                val (text, free) = when {
                    number == null -> stringResource(R.string.number_hint) to null
                    number == current -> stringResource(R.string.number_current, number) to null
                    owner != null -> stringResource(R.string.number_taken, number, owner) to false
                    else -> stringResource(R.string.number_free, number) to true
                }
                if (free != null) Box(Modifier.size(10.px).background(if (free) PlasmaColors.Ok else PlasmaColors.Warn, CircleShape))
                PlasmaText(
                    text,
                    plasmaStyle(22f, 500, tabular = true),
                    color = when (free) {
                        true -> PlasmaColors.OkText
                        false -> PlasmaColors.WarnText
                        null -> PlasmaColors.Text3
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
