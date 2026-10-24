// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.CenteredKeyFooter
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.HintTone
import ca.gureet.plasma.core.design.component.SheetPanel
import ca.gureet.plasma.core.design.component.SheetRow
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.R as DesignR

/** A channel Locked Channels lists (5.12): its number and name. */
@Immutable
data class LockedChannelInfo(val id: String, val number: Int, val name: String)

/**
 * Locked Channels (5.12): the channels that always need the PIN, ticked. OK unticks one, which
 * unlocks it, or ticks it again, and the sheet stays open; Back closes it. Channels are locked from
 * their options in Live TV; with none locked, the row has nothing to open.
 */
@Composable
internal fun LockedChannelsSheet(locked: List<LockedChannelInfo>, onLock: (channelId: String, locked: Boolean) -> Unit, onClose: () -> Unit) {
    // The channels as they were when it opened: one unlocked stays, unticked, to tick again.
    val channels = remember { locked }
    var ticked by remember { mutableStateOf(channels.mapTo(HashSet()) { it.id }.toSet()) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.requestFocus()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(PlasmaColors.black(0.5f))
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) onClose()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        SheetPanel(Modifier.padding(horizontal = 96.px).width(720.px).heightIn(max = 820.px)) {
            Column(Modifier.padding(horizontal = 10.px), verticalArrangement = Arrangement.spacedBy(4.px)) {
                PlasmaText(stringResource(R.string.locked_channels), plasmaStyle(30f, 600, trackingPx = -0.4f))
                PlasmaText(
                    stringResource(R.string.locked_channels_sheet_text),
                    plasmaStyle(21f, 500, lineHeight = 1.35f),
                    color = PlasmaColors.Text2,
                    maxLines = 3,
                )
            }
            Box(Modifier.padding(top = 22.px, bottom = 14.px).fillMaxWidth().height(1.px).background(PlasmaColors.white(0.1f)))
            LazyColumn(
                Modifier
                    .weight(1f, fill = false)
                    // Focus stays in the sheet until it closes.
                    .focusProperties { onExit = { cancelFocusChange() } }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.px),
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    val on = channel.id in ticked
                    SheetRow(
                        label = channel.name,
                        value = null,
                        onClick = {
                            ticked = if (on) ticked - channel.id else ticked + channel.id
                            onLock(channel.id, !on)
                        },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        icon = {
                            val numberColor = if (LocalFocused.current) PlasmaColors.OnFocus3 else PlasmaColors.Text2
                            PlasmaText(channel.number.toString(), plasmaStyle(21f, 600, tabular = true), Modifier.width(64.px), color = numberColor)
                        },
                        checked = on,
                    )
                }
            }
        }
        CenteredKeyFooter(
            keys = listOf(
                FooterKey("OK", stringResource(DesignR.string.key_select)),
                FooterKey(PlasmaIcons.Back, stringResource(DesignR.string.key_close)),
            ),
            modifier = Modifier.align(Alignment.TopStart),
            tone = HintTone.Screen,
        )
    }
}
