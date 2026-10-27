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
 * A group Locked Groups lists (5.12): whether its channels need the PIN, and whether that's
 * because it's [adult], by Lock Adult Groups' words. [source] names its source when there are
 * several, which may have groups of the same name.
 */
@Immutable
data class LockedGroupInfo(val id: String, val name: String, val locked: Boolean, val adult: Boolean = false, val source: String? = null)

/**
 * Locked Channels (5.12): the channels that always need the PIN, ticked. OK unticks one, which
 * unlocks it, or ticks it again, and the sheet stays open; Back closes it. Channels are locked from
 * their options in Live TV; with none locked, the row has nothing to open.
 */
@Composable
internal fun LockedChannelsSheet(locked: List<LockedChannelInfo>, onLock: (channelId: String, locked: Boolean) -> Unit, onClose: () -> Unit) {
    LockListSheet(
        title = stringResource(R.string.locked_channels),
        text = stringResource(R.string.locked_channels_sheet_text),
        rows = locked.map { LockRow(it.id, it.name, locked = true, lead = it.number.toString()) },
        onLock = onLock,
        onClose = onClose,
    )
}

/**
 * Locked Groups (5.12): every group, ticked when its channels need the PIN, adult ones tagged.
 * OK locks or unlocks one, an adult one too, and the sheet stays open; Back closes it.
 */
@Composable
internal fun LockedGroupsSheet(groups: List<LockedGroupInfo>, onLock: (groupId: String, locked: Boolean) -> Unit, onClose: () -> Unit) {
    val adult = stringResource(R.string.group_adult)
    LockListSheet(
        title = stringResource(R.string.locked_groups),
        text = stringResource(R.string.locked_groups_sheet_text),
        rows = groups.map { group -> LockRow(group.id, group.name, group.locked, tag = if (group.adult) adult else null, note = group.source) },
        onLock = onLock,
        onClose = onClose,
    )
}

/**
 * A group Kid-Safe Groups lists (5.11): whether kids profiles see its channels, and whether that's
 * [byWords], Kids Words. [source] names its source when there are several.
 */
@Immutable
data class KidSafeGroupInfo(val id: String, val name: String, val kidSafe: Boolean, val byWords: Boolean = false, val source: String? = null)

/**
 * Kid-Safe Groups (5.11): the groups that aren't locked, ticked when kids profiles see their
 * channels, those with a kids word in their name tagged. OK adds or takes out one, and the sheet
 * stays open; Back closes it.
 */
@Composable
internal fun KidSafeGroupsSheet(groups: List<KidSafeGroupInfo>, onKidSafe: (groupId: String, kidSafe: Boolean) -> Unit, onClose: () -> Unit) {
    val kids = stringResource(R.string.group_kids)
    LockListSheet(
        title = stringResource(R.string.kid_safe_groups),
        text = stringResource(R.string.kid_safe_groups_sheet_text),
        rows = groups.map { group -> LockRow(group.id, group.name, group.kidSafe, tag = if (group.byWords) kids else null, note = group.source) },
        onLock = onKidSafe,
        onClose = onClose,
    )
}

/** A row of a lock list: [lead] before its name, a channel's number; a [tag] and a [note] after it. */
@Immutable
internal data class LockRow(val id: String, val name: String, val locked: Boolean, val lead: String? = null, val tag: String? = null, val note: String? = null)

/**
 * A sheet of things locked with the PIN (5.12), or kid-safe (5.11), ticked when they are. OK flips
 * one, and the sheet stays open with the rows as they were when it opened; Back closes it.
 */
@Composable
private fun LockListSheet(title: String, text: String, rows: List<LockRow>, onLock: (id: String, locked: Boolean) -> Unit, onClose: () -> Unit) {
    // The rows as they were when it opened: one flipped stays, to flip back.
    val shown = remember { rows }
    var ticked by remember { mutableStateOf(shown.filter { it.locked }.mapTo(HashSet()) { it.id }.toSet()) }
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
                PlasmaText(title, plasmaStyle(30f, 600, trackingPx = -0.4f))
                PlasmaText(text, plasmaStyle(21f, 500, lineHeight = 1.35f), color = PlasmaColors.Text2, maxLines = 3)
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
                itemsIndexed(shown, key = { _, row -> row.id }) { index, row ->
                    val on = row.id in ticked
                    SheetRow(
                        label = row.name,
                        value = null,
                        onClick = {
                            ticked = if (on) ticked - row.id else ticked + row.id
                            onLock(row.id, !on)
                        },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        icon = row.lead?.let { lead ->
                            @Composable {
                                val leadColor = if (LocalFocused.current) PlasmaColors.OnFocus3 else PlasmaColors.Text2
                                PlasmaText(lead, plasmaStyle(21f, 600, tabular = true), Modifier.width(64.px), color = leadColor)
                            }
                        },
                        checked = on,
                        tag = row.tag,
                        note = row.note,
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
