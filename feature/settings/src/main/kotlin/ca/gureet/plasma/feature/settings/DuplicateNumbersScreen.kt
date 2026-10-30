// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.KeyFooter
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaColumn
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.DuplicateRule
import ca.gureet.plasma.core.design.R as DesignR

private enum class DuplicateTab { Renumbered, Merged }

/** The left column ends above the footer. */
private const val COLUMN_BOTTOM_PX = 976

/**
 * Duplicate Numbers (board 30, brief 4.30): different channels the playlists gave the same number,
 * which one kept it and where the other one went. OK on a conflict moves to its buttons: Keep,
 * Choose Another Number, or Swap; Back returns to the list. The Merged tab lists channels merged
 * from several sources, and OK there offers Unmerge. Menu (or long-press OK) switches the fix rule
 * and the order; yellow sorts too.
 *
 * Each choice shows at once, and goes to [actions] to be kept; a new [state] from the app takes
 * over when it comes.
 */
@Composable
fun DuplicateNumbersScreen(
    state: DuplicateNumbersState,
    modifier: Modifier = Modifier,
    initialConflict: String? = null,
    actions: DuplicateNumbersActions = NoActions,
    /** Where Back goes, when it isn't Settings › Channels: first-time setup's guide step (#85). */
    backLabel: String? = null,
) {
    var current by remember(state) { mutableStateOf(state) }
    var tab by rememberSaveable { mutableStateOf(DuplicateTab.Renumbered) }
    var sort by rememberSaveable { mutableStateOf(DuplicateSort.Number) }
    // Until a row has focus, the first one shown.
    var focusedConflict by rememberSaveable { mutableStateOf(initialConflict) }
    var focusedMerged by rememberSaveable { mutableStateOf<String?>(null) }
    var panelHasFocus by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var choosingFor by remember { mutableStateOf<String?>(null) }

    val conflicts = current.conflicts.sortedWith(
        if (sort == DuplicateSort.Number) compareBy({ it.number }, { it.newNumber }) else compareBy { it.mover.name.lowercase() },
    )
    val merged = current.merged.sortedWith(
        if (sort == DuplicateSort.Number) compareBy { it.number } else compareBy { it.name.lowercase() },
    )
    val conflict = conflicts.firstOrNull { it.id == focusedConflict } ?: conflicts.firstOrNull()
    val mergedChannel = merged.firstOrNull { it.id == focusedMerged } ?: merged.firstOrNull()

    val row = remember { FocusRequester() }
    val panel = remember { FocusRequester() }
    val tabFocus = remember { DuplicateTab.entries.associateWith { FocusRequester() } }
    val tabs = remember { FocusRequester() }

    // Focus goes to the row, or to the tabs when the list is empty: to the one showing.
    val hasRow = if (tab == DuplicateTab.Renumbered) conflict != null else mergedChannel != null
    val listFocus = if (hasRow) row else tabs
    val latestListFocus by rememberUpdatedState(listFocus)

    // At first, and after a change to the list once the list has caught up.
    var refocus by remember { mutableIntStateOf(0) }
    LaunchedEffect(refocus) {
        withFrameNanos { }
        latestListFocus.requestFocus()
    }
    fun onKey(event: KeyEvent): Boolean {
        if (optionsOpen || choosingFor != null) return false
        val up = event.type == KeyEventType.KeyUp
        return when (event.key) {
            Key.ProgramYellow -> {
                if (up) sort = sort.next()
                true
            }
            Key.Menu -> {
                if (up) optionsOpen = true
                true
            }
            // Back from the buttons goes back to their row; from the list it leaves the screen.
            Key.Back, Key.Escape -> {
                if (panelHasFocus && up) row.requestFocus()
                panelHasFocus
            }
            else -> false
        }
    }

    Box(modifier.fillMaxSize().onPreviewKeyEvent(::onKey)) {
        SecondaryHeader(backLabel ?: stringResource(R.string.channels), stringResource(R.string.duplicate_numbers))

        Column(Modifier.offset(96.px, 146.px).width(1060.px).height((COLUMN_BOTTOM_PX - 146).px)) {
            PlasmaText(
                introText(current),
                plasmaStyle(23f, 400, lineHeight = 1.42f),
                Modifier.width(1000.px),
                color = PlasmaColors.TextSoft,
                maxLines = 3,
            )
            // Coming up from the list, focus lands on the tab that's showing.
            Row(
                Modifier.padding(top = 22.px).focusRequester(tabs).focusRestoringGroup(tabFocus.getValue(tab)),
                horizontalArrangement = Arrangement.spacedBy(8.px),
            ) {
                CountTab(
                    stringResource(R.string.renumbered),
                    current.conflicts.size,
                    tab == DuplicateTab.Renumbered,
                    Modifier.focusRequester(tabFocus.getValue(DuplicateTab.Renumbered)),
                ) { tab = DuplicateTab.Renumbered }
                CountTab(
                    stringResource(R.string.merged),
                    current.mergedCount,
                    tab == DuplicateTab.Merged,
                    Modifier.focusRequester(tabFocus.getValue(DuplicateTab.Merged)),
                ) { tab = DuplicateTab.Merged }
            }
            // 4 px of room above the first row for the focused row's lift.
            val listModifier = Modifier.padding(top = 18.px).weight(1f).fillMaxWidth()
            val listPadding = PaddingValues(top = 4.px, bottom = 24.px)
            when (tab) {
                DuplicateTab.Renumbered -> PlasmaColumn(listModifier, gap = 8.px, contentPadding = listPadding, focusFallback = row) {
                    items(conflicts, key = { it.id }) { item ->
                        ConflictRow(
                            conflict = item,
                            onClick = { panel.requestFocus() },
                            onLongClick = { optionsOpen = true },
                            onFocused = { focusedConflict = item.id },
                            modifier = if (item.id == conflict?.id) Modifier.focusRequester(row) else Modifier,
                        )
                    }
                }
                DuplicateTab.Merged -> PlasmaColumn(listModifier, gap = 8.px, contentPadding = listPadding) {
                    items(merged, key = { it.id }) { item ->
                        MergedRow(
                            channel = item,
                            onClick = { panel.requestFocus() },
                            onLongClick = { optionsOpen = true },
                            onFocused = { focusedMerged = item.id },
                            modifier = if (item.id == mergedChannel?.id) Modifier.focusRequester(row) else Modifier,
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .offset(1196.px, 150.px)
                .width(628.px)
                .onFocusChanged { panelHasFocus = it.hasFocus }
                .focusGroup(),
        ) {
            when (tab) {
                DuplicateTab.Renumbered -> if (conflict != null) {
                    ConflictPanel(
                        conflict = conflict,
                        focus = panel,
                        onKeep = {
                            current = current.keep(conflict.id)
                            actions.onKeep(conflict)
                            refocus++
                        },
                        onChoose = { choosingFor = conflict.id },
                        onSwap = {
                            current = current.swap(conflict.id)
                            // The conflict is the other channel's now.
                            focusedConflict = conflict.keeperId
                            actions.onSwap(conflict)
                            refocus++
                        },
                    )
                }
                DuplicateTab.Merged -> if (mergedChannel != null) {
                    MergedPanel(
                        channel = mergedChannel,
                        focus = panel,
                        onUnmerge = {
                            val next = merged.getOrNull(merged.indexOf(mergedChannel) + 1) ?: merged.getOrNull(merged.indexOf(mergedChannel) - 1)
                            current = current.unmerge(mergedChannel)
                            focusedMerged = next?.id
                            actions.onUnmerge(mergedChannel)
                            refocus++
                        },
                    )
                }
            }
        }

        KeyFooter(
            colorKeys = listOf(ColorKey.Yellow to stringResource(DesignR.string.key_sort)),
            keys = listOf(
                FooterKey("OK", stringResource(if (tab == DuplicateTab.Renumbered) R.string.key_change_number else R.string.key_unmerge)),
                FooterKey(
                    PlasmaIcons.Menu,
                    stringResource(if (current.rule == DuplicateRule.AddThousands) R.string.key_fix_rule_add else R.string.key_fix_rule_next),
                ),
            ),
        )

        SheetHost(visible = optionsOpen, returnFocus = listFocus) {
            @Composable
            fun pick(rule: DuplicateRule) = SheetOption(
                stringResource(if (rule == DuplicateRule.AddThousands) R.string.rule_add else R.string.rule_next),
                onClick = {
                    current = current.withRule(rule)
                    actions.onRule(rule)
                },
                checked = current.rule == rule,
            )
            @Composable
            fun order(order: DuplicateSort) = SheetOption(
                stringResource(if (order == DuplicateSort.Number) R.string.sort_number else R.string.sort_name),
                onClick = { sort = order },
                checked = sort == order,
            )
            OptionsSheet(
                title = stringResource(R.string.fix_rule),
                subtitle = stringResource(R.string.fix_rule_text),
                onDismiss = { optionsOpen = false },
                options = listOf(
                    pick(DuplicateRule.AddThousands),
                    pick(DuplicateRule.NextFree),
                    SheetOption.Divider,
                    order(DuplicateSort.Number),
                    order(DuplicateSort.Name),
                ),
            )
        }

        val choosing = current.conflicts.firstOrNull { it.id == choosingFor }
        SheetHost(visible = choosing != null, returnFocus = listFocus) {
            if (choosing != null) {
                NumberChooser(
                    conflict = choosing,
                    ownerOf = current::owner,
                    onUse = { number ->
                        current = current.choose(choosing.id, number)
                        actions.onChoose(choosing, number)
                        choosingFor = null
                    },
                    onDismiss = { choosingFor = null },
                )
            }
        }
    }
}

private fun DuplicateSort.next() = if (this == DuplicateSort.Number) DuplicateSort.Name else DuplicateSort.Number

private val NoActions = DuplicateNumbersActions()

@Composable
private fun introText(state: DuplicateNumbersState): String {
    val count = state.conflicts.size
    val found = if (count == 0) stringResource(R.string.duplicates_none) else pluralStringResource(R.plurals.duplicates_found, count, count)
    val rule = when {
        count == 0 -> null
        state.rule == DuplicateRule.AddThousands -> pluralStringResource(R.plurals.duplicates_rule_add, count)
        else -> pluralStringResource(R.plurals.duplicates_rule_next, count)
    }
    val merged = stringResource(R.string.duplicates_merged_instead)
    return if (rule == null) "$found $merged" else stringResource(R.string.duplicates_intro, found, rule, merged)
}

/** "Renumbered 4": a tab with its count, the selected one on a `.12` pill (board 30). */
@Composable
private fun CountTab(label: String, count: Int, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    PlasmaSurface(
        onClick = onSelect,
        modifier = modifier.height(46.px).onFocusChanged { if (it.isFocused) onSelect() },
        style = FocusStyle.Pill,
        shape = PlasmaShapes.Tab,
        selected = selected,
        contentAlignment = Alignment.Center,
    ) {
        val focused = LocalFocused.current
        Row(Modifier.padding(horizontal = 20.px), horizontalArrangement = Arrangement.spacedBy(10.px), verticalAlignment = Alignment.CenterVertically) {
            PlasmaText(
                label,
                plasmaStyle(22f, if (selected || focused) 600 else 500),
                color = when {
                    focused -> PlasmaColors.OnFocus
                    selected -> PlasmaColors.Text
                    else -> PlasmaColors.Text2
                },
            )
            PlasmaText(
                count.toString(),
                plasmaStyle(22f, 500, tabular = true),
                color = when {
                    focused -> PlasmaColors.OnFocus3
                    selected -> PlasmaColors.Text2
                    else -> PlasmaColors.Text3
                },
            )
        }
    }
}

/** A conflict: the number, then the channel that kept it and the one that moved (140 px). */
@Composable
private fun ConflictRow(
    conflict: NumberConflict,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usedTwice = if (conflict.uses == 2) stringResource(R.string.used_twice) else pluralStringResource(R.plurals.used_times, conflict.uses, conflict.uses)
    val keeps = stringResource(R.string.keeps_number, conflict.number)
    val now = stringResource(R.string.now_number, conflict.newNumber)
    val description = listOf(
        "${conflict.number} $usedTwice",
        "${conflict.keeper.name}, ${conflict.keeper.source}, $keeps",
        "${conflict.mover.name}, ${conflict.mover.source}, $now",
    ).joinToString(". ")
    NumberRow(
        number = conflict.number.toString(),
        caption = usedTwice,
        height = 140.px,
        onClick = onClick,
        onLongClick = onLongClick,
        onFocused = onFocused,
        modifier = modifier.semantics { contentDescription = description },
    ) { focused ->
        Column(verticalArrangement = Arrangement.spacedBy(12.px)) {
            ChannelLine(conflict.keeper.name, conflict.keeper.logo, conflict.keeper.source, keeps, strong = false, focused = focused)
            ChannelLine(conflict.mover.name, conflict.mover.logo, conflict.mover.source, now, strong = true, focused = focused)
        }
    }
}

/** A merged channel: its number, then its logo, name and sources in play order (92 px). */
@Composable
private fun MergedRow(
    channel: MergedChannel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources = playOrder(channel.sources)
    NumberRow(
        number = channel.number.toString(),
        caption = null,
        height = 92.px,
        onClick = onClick,
        onLongClick = onLongClick,
        onFocused = onFocused,
        modifier = modifier.semantics { contentDescription = "${channel.number}, ${channel.name}, $sources" },
    ) { focused ->
        ChannelLine(channel.name, channel.logo, sources, trailing = null, strong = false, focused = focused)
    }
}

@Composable
private fun NumberRow(
    number: String,
    caption: String?,
    height: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
    content: @Composable (focused: Boolean) -> Unit,
) {
    PlasmaSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(height),
        color = PlasmaColors.Surface1,
        onFocusChanged = { if (it) onFocused() },
        contentAlignment = Alignment.CenterStart,
    ) {
        val focused = LocalFocused.current
        Row(
            Modifier.padding(start = 18.px, end = 28.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.px),
        ) {
            Column(Modifier.width(112.px), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.px)) {
                PlasmaText(
                    number,
                    plasmaStyle(if (caption != null) 40f else 32f, 600, trackingPx = -0.6f, lineHeight = 1.05f, tabular = true),
                    color = if (focused) PlasmaColors.OnFocus else PlasmaColors.Text,
                )
                if (caption != null) PlasmaText(caption, plasmaStyle(19f, 500), color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text3)
            }
            Box(Modifier.size(1.px, if (caption != null) 96.px else 60.px).background(if (focused) PlasmaColors.black(0.12f) else PlasmaColors.white(0.08f)))
            Box(Modifier.weight(1f)) { content(focused) }
        }
    }
}

/** A logo plate, the channel's name and source, and what happened to its number. */
@Composable
private fun ChannelLine(name: String, logo: String?, source: String, trailing: String?, strong: Boolean, focused: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.px)) {
        LogoPlate(
            logo,
            name,
            108.px,
            52.px,
            logoSize = LogoSize.Small,
            // Plates use `surface3` on unfocused rows so they stay visible on `surface1`.
            color = if (focused) PlasmaColors.Plate else PlasmaColors.Surface3,
            shape = PlasmaShapes.SmallPlate,
            fallbackStyle = plasmaStyle(15f, 600, lineHeight = 1.12f),
            fallbackColor = PlasmaColors.TextSoft3,
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.px)) {
            PlasmaText(name, plasmaStyle(24f, 600), Modifier.alignByBaseline(), color = if (focused) PlasmaColors.OnFocus else PlasmaColors.Text)
            PlasmaText(source, plasmaStyle(20f, 500), Modifier.alignByBaseline(), color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text3)
        }
        if (trailing != null) {
            PlasmaText(
                trailing,
                plasmaStyle(if (strong) 22f else 21f, if (strong) 600 else 500, tabular = true),
                color = when {
                    strong && focused -> PlasmaColors.OnFocus
                    strong -> PlasmaColors.Text
                    focused -> PlasmaColors.OnFocus2
                    else -> PlasmaColors.Text2
                },
            )
        }
    }
}

/** The right panel for a conflict: where the moved channel came from, its numbers, and the choices. */
@Composable
private fun ColumnScope.ConflictPanel(
    conflict: NumberConflict,
    focus: FocusRequester,
    onKeep: () -> Unit,
    onChoose: () -> Unit,
    onSwap: () -> Unit,
) {
    PanelHeading(stringResource(R.string.source_and_type, conflict.mover.source, conflict.mover.sourceType.label()), conflict.mover.name)
    Row(
        Modifier
            .padding(top = 22.px)
            .fillMaxWidth()
            .height(92.px)
            .background(PlasmaColors.Surface1, PlasmaShapes.Row)
            .padding(horizontal = 26.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.px),
    ) {
        val numbers = plasmaStyle(56f, 600, trackingPx = -1f, tabular = true)
        PlasmaText(conflict.number.toString(), numbers, color = PlasmaColors.Text4)
        PlasmaIcon(PlasmaIcons.ArrowRight, 40.px, tint = PlasmaColors.Text4, stroke = 2.2f)
        PlasmaText(conflict.newNumber.toString(), numbers)
    }
    val reason = when (conflict.reason) {
        KeepReason.Primary -> R.string.keeps_primary
        KeepReason.SourceOrder -> R.string.keeps_source_order
        KeepReason.PlaylistOrder -> R.string.keeps_playlist_order
        KeepReason.HadItFirst -> R.string.keeps_first
        KeepReason.Swapped -> R.string.keeps_swapped
    }
    PanelText(stringResource(reason, conflict.keeper.name, conflict.keeper.source, conflict.number, conflict.newNumber, conflict.mover.name))
    PanelButtons {
        PanelButton(stringResource(R.string.keep_number, conflict.newNumber), onKeep, Modifier.focusRequester(focus))
        PanelButton(stringResource(R.string.choose_number), onChoose)
        PanelButton(stringResource(R.string.swap_numbers, conflict.keeper.name), onSwap)
    }
    PanelNote(stringResource(R.string.duplicates_note))
}

/** The right panel for a merged channel: its sources in play order, and Unmerge. */
@Composable
private fun ColumnScope.MergedPanel(channel: MergedChannel, focus: FocusRequester, onUnmerge: () -> Unit) {
    PanelHeading(pluralStringResource(R.plurals.merged_from, channel.sources.size, channel.sources.size), channel.name)
    Column(
        Modifier
            .padding(top = 22.px)
            .fillMaxWidth()
            .background(PlasmaColors.Surface1, PlasmaShapes.Row)
            .padding(horizontal = 26.px, vertical = 22.px),
        verticalArrangement = Arrangement.spacedBy(12.px),
    ) {
        channel.sources.forEachIndexed { i, source ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.px), verticalAlignment = Alignment.CenterVertically) {
                PlasmaText((i + 1).toString(), plasmaStyle(24f, 600, tabular = true), color = PlasmaColors.Text4)
                PlasmaText(source, plasmaStyle(24f, 500))
            }
        }
    }
    PanelText(stringResource(R.string.merged_plays, channel.sources.first()))
    PanelButtons {
        PanelButton(stringResource(R.string.unmerge), onUnmerge, Modifier.focusRequester(focus))
    }
    PanelNote(stringResource(R.string.unmerge_note))
}

@Composable
private fun PanelHeading(line: String, title: String) {
    PlasmaText(line, plasmaStyle(20f, 600), color = PlasmaColors.Text3)
    PlasmaText(title, plasmaStyle(38f, 600, trackingPx = -0.5f), Modifier.padding(top = 8.px))
}

@Composable
private fun PanelText(text: String) {
    PlasmaText(text, plasmaStyle(23f, 400, lineHeight = 1.42f), Modifier.padding(top = 22.px), color = PlasmaColors.TextSoft, maxLines = 4)
}

@Composable
private fun PanelNote(text: String) {
    PlasmaText(text, plasmaStyle(20f, 500, lineHeight = 1.4f), Modifier.padding(top = 22.px), color = PlasmaColors.Text3, maxLines = 3)
}

@Composable
private fun PanelButtons(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 26.px).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.px), content = content)
}

/** A full-width button in the panel: `.12` white, the label on the left (board 30). */
@Composable
private fun PanelButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(64.px),
        shape = PlasmaShapes.Button,
        color = PlasmaColors.Selected,
        contentAlignment = Alignment.CenterStart,
    ) {
        PlasmaText(
            label,
            plasmaStyle(23f, 600),
            Modifier.padding(horizontal = 26.px),
            color = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.Text,
        )
    }
}

/** "Home Portal, then Backup Line". */
@Composable
private fun playOrder(sources: List<String>): String =
    sources.drop(1).fold(sources.firstOrNull().orEmpty()) { list, next -> stringResource(R.string.then_list, list, next) }

@Composable
internal fun SourceType.label(): String = stringResource(
    when (this) {
        SourceType.Stalker -> R.string.type_stalker
        SourceType.Xtream -> R.string.type_xtream
        SourceType.M3U -> R.string.type_m3u
    },
)
