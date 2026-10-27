// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.focus.onFocusChanged
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

/**
 * A channel Local Catch-up Channels lists (4.31): one without catch-up of its own, from
 * [sourceName], which allows [limit] connections at once (null for no limit). [on] when Plasma
 * records it all the time.
 */
@Immutable
data class LocalChannelChoice(val id: String, val number: Int, val name: String, val sourceName: String, val limit: Int?, val on: Boolean)

/**
 * Local Catch-up Channels (4.31): the channels without catch-up of their own, ticked when Plasma
 * records them all the time (5.5). The line under the title says what the focused one costs before
 * it's ticked: "Uses 1 of your 2 Home Portal connections all the time." One that would leave no
 * connection to watch with can't be ticked. OK ticks or unticks, and the sheet stays open; Back
 * closes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LocalChannelsSheet(choices: List<LocalChannelChoice>, onToggle: (channelId: String, on: Boolean) -> Unit, onClose: () -> Unit) {
    var ticked by remember { mutableStateOf(choices.filter { it.on }.mapTo(HashSet()) { it.id }.toSet()) }
    var focusedId by remember { mutableStateOf(choices.firstOrNull()?.id) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (choices.isNotEmpty()) first.requestFocus()
    }

    /** Connections of [choice]'s source that the others ticked hold. */
    fun heldBesides(choice: LocalChannelChoice): Int = choices.count { it.id != choice.id && it.sourceName == choice.sourceName && it.id in ticked }

    /** Ticking [choice] would leave its source no connection to watch with. */
    fun leavesNone(choice: LocalChannelChoice): Boolean = choice.id !in ticked && choice.limit != null && heldBesides(choice) + 1 >= choice.limit

    val focused = choices.firstOrNull { it.id == focusedId }
    val cost = when {
        focused == null -> stringResource(R.string.local_channels_empty)
        leavesNone(focused) -> stringResource(R.string.local_no_spare_text, focused.sourceName)
        focused.limit == null -> stringResource(R.string.local_cost_unlimited, focused.sourceName)
        else -> stringResource(R.string.local_cost, heldBesides(focused) + 1, focused.limit, focused.sourceName)
    }
    val noSpare = stringResource(R.string.local_no_spare)
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
                PlasmaText(stringResource(R.string.local_channels_row), plasmaStyle(30f, 600, trackingPx = -0.4f))
                PlasmaText(cost, plasmaStyle(21f, 500, lineHeight = 1.35f, tabular = true), color = PlasmaColors.Text2, maxLines = 3)
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
                itemsIndexed(choices, key = { _, choice -> choice.id }) { index, choice ->
                    val on = choice.id in ticked
                    val refused = leavesNone(choice)
                    SheetRow(
                        label = choice.name,
                        value = null,
                        onClick = {
                            if (!refused) {
                                ticked = if (on) ticked - choice.id else ticked + choice.id
                                onToggle(choice.id, !on)
                            }
                        },
                        modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .onFocusChanged { if (it.hasFocus) focusedId = choice.id },
                        icon = {
                            val numberColor = if (LocalFocused.current) PlasmaColors.OnFocus3 else PlasmaColors.Text2
                            PlasmaText(choice.number.toString(), plasmaStyle(21f, 600, tabular = true), Modifier.width(64.px), color = numberColor)
                        },
                        checked = on,
                        note = if (refused) noSpare else null,
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
