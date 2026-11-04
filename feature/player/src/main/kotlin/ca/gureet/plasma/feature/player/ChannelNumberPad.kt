// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.LocalLeaving
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.MAX_CHANNEL_DIGITS
import ca.gureet.plasma.core.design.component.NumberPad
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.digit
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.R as DesignR

/**
 * Hold OK for Number Pad (Settings › Remote & Buttons; the owner's #105): a channel's number typed
 * on the screen, for a remote without number keys. The keys are the PIN screen's (board 25). The
 * number shows with the channel it tunes, as typing on a remote does (board 11), in the list being
 * watched, so Favorites count from 1 there; Watch tunes it. Nothing tunes by itself, as moving
 * between keys takes longer than Number Entry Delay. The remote's number keys type too, and Back
 * closes it, which the player sees to.
 *
 * @param current the channel on screen, whose number shows until one's typed.
 * @param find the channel with a number in the list being watched, or null.
 */
@Composable
internal fun ChannelNumberPad(
    current: PlayerChannel,
    find: (String) -> PlayerChannel?,
    onWatch: (PlayerChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by remember { mutableStateOf("") }
    val match = typed.takeIf { it.isNotEmpty() }?.let(find)
    val first = remember { FocusRequester() }
    // Fading out, it lets focus and the keys go at once.
    val leaving = LocalLeaving.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.requestFocus()
    }
    Column(
        modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (leaving || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val digit = event.key.digit()
                when {
                    digit != null -> {
                        typed = (typed + digit).take(MAX_CHANNEL_DIGITS)
                        true
                    }
                    event.key == Key.Backspace || event.key == Key.Delete -> {
                        typed = typed.dropLast(1)
                        true
                    }
                    else -> false
                }
            }
            // Focus stays on the pad until it's closed.
            .focusProperties { onExit = { if (!leaving) cancelFocusChange() } }
            .focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PlasmaText(stringResource(R.string.number_pad_title), plasmaStyle(48f, 600, trackingPx = -1f))
        Box(
            Modifier
                .padding(top = 40.px)
                .size(320.px, 92.px)
                .background(PlasmaColors.Surface1, PlasmaShapes.Row),
            contentAlignment = Alignment.Center,
        ) {
            PlasmaText(
                typed.ifEmpty { current.number },
                plasmaStyle(56f, 600, trackingPx = -1f, tabular = true),
                color = if (typed.isEmpty()) PlasmaColors.Text4 else PlasmaColors.Text,
                align = TextAlign.Center,
            )
        }
        // What the number tunes, with its logo, as the box does when typing on a remote.
        Row(
            Modifier.padding(top = 28.px).height(48.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.px),
        ) {
            when {
                typed.isEmpty() -> PlasmaText(stringResource(DesignR.string.number_hint), plasmaStyle(24f, 500), color = PlasmaColors.Text3)
                match == null -> PlasmaText(stringResource(DesignR.string.no_channel, typed), plasmaStyle(24f, 500, tabular = true), color = PlasmaColors.WarnText)
                else -> {
                    LogoImage(match.logo, match.name, LogoSize.Inline)
                    PlasmaText(match.name, plasmaStyle(26f, 600), maxLines = 1)
                }
            }
        }
        NumberPad(
            onDigit = { typed = (typed + it).take(MAX_CHANNEL_DIGITS) },
            onDelete = { typed = typed.dropLast(1) },
            modifier = Modifier.padding(top = 36.px),
            first = first,
        )
        // The button keeps its place while there's nothing to watch, so nothing jumps.
        Box(Modifier.padding(top = 32.px).height(68.px), contentAlignment = Alignment.Center) {
            if (match != null) PlasmaButton(stringResource(R.string.number_pad_watch), onClick = { onWatch(match) })
        }
    }
}
