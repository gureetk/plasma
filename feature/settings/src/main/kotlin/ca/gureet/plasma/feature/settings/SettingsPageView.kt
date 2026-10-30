// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.SelectKeys
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.KeyFooter
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaColumn
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.keepInView
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.R as DesignR

/** The list starts at y 150 and scrolls when a page has more rows than fit. */
private const val LIST_TOP_PX = 150

/**
 * One page of Settings in the tvOS pattern (brief 4.19): the focused row's detail on the left, the
 * rows on the right. OK on a row opens its page, its options on the right, or flips its switch. In
 * a section that moves ([SettingSection.onMove]), holding OK or pressing Menu on a row lifts it to
 * be put in another place, as a favorite is in Live TV.
 */
@Composable
internal fun SettingsPageView(
    spec: PageSpec,
    values: SettingsValues,
    onOpen: (SettingsPage) -> Unit,
    onGo: (SettingsDestination) -> Unit,
    initialItem: String?,
    modifier: Modifier = Modifier,
) {
    val items = spec.items
    var focusedId by rememberSaveable { mutableStateOf(initialItem ?: items.first().id) }
    val focused = items.firstOrNull { it.id == focusedId } ?: items.first()
    var sheetFor by remember { mutableStateOf<SettingItem?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<SettingAction.Menu?>(null) }
    var explain by remember { mutableStateOf<SettingAction.Explain?>(null) }
    // A setting being typed in, on a page of its own over this one.
    var typing by remember { mutableStateOf<SettingAction.Type?>(null) }
    // A sheet of a row's own, such as Jellyfin's sign-in.
    var ownSheet by remember { mutableStateOf<SettingAction.Sheet?>(null) }
    val start = remember { FocusRequester() }
    val sheetReturn = remember { FocusRequester() }
    val startId = remember { focusedId }
    val listState = rememberLazyListState()

    // Move: the section moving, the row being carried, the order it's carried through, and the
    // section's own order when it lifted.
    var moveSection by remember { mutableStateOf<Int?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var moveOrder by remember { mutableStateOf<List<String>?>(null) }
    var moveBase by remember { mutableStateOf<List<String>?>(null) }
    // OK is down, and a row lifted by holding it waits for it to come up before OK can drop it.
    val press = remember { MovePress() }

    // Once a row is dropped, the section keeps the moved order until its own order changes.
    val ownOrder = moveSection?.let { spec.sections.getOrNull(it) }?.items?.map { it.id }
    LaunchedEffect(ownOrder) {
        if (moving == null && moveBase != null && ownOrder != moveBase) {
            moveSection = null
            moveOrder = null
            moveBase = null
        }
    }

    /** [section]'s rows as they show: in the order they're carried in while one moves. */
    fun rowsOf(index: Int, section: SettingSection): List<SettingItem> {
        val order = moveOrder?.takeIf { index == moveSection && (moving != null || section.items.map { it.id } == moveBase) } ?: return section.items
        val byId = section.items.associateBy { it.id }
        return order.mapNotNull(byId::get).takeIf { it.size == section.items.size } ?: section.items
    }

    val shown = spec.sections.mapIndexed { i, section -> rowsOf(i, section) }
    // The list's items, in the order the list has them, for keeping a moving row in view.
    val keys = buildList {
        if (spec.header != null) add("header")
        spec.sections.forEachIndexed { i, section ->
            if (section.divider) add("divider-$i")
            if (section.label != null) add("label-$i")
            shown[i].forEach { add(it.id) }
        }
    }

    /** Holding OK or Menu on a row in a section that moves: it lifts where it is. */
    fun startMove(section: Int, id: String) {
        val order = spec.sections[section].items.map { it.id }
        if (order.size < 2) return
        moveSection = section
        moveOrder = order
        moveBase = order
        moving = id
        press.awaitRelease = press.okDown
    }

    /** Up or Down: it trades places with its neighbor, and the list keeps it in view. */
    fun moveBy(delta: Int) {
        val order = moveOrder ?: return
        val from = order.indexOf(moving ?: return)
        val to = from + delta
        if (from < 0 || to !in order.indices) return
        listState.keepInView(keys.indexOf(order.first()) + to, keys.size)
        moveOrder = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** OK: it stays where it is, and the page keeps the order. */
    fun drop() {
        val section = moveSection ?: return
        val order = moveOrder ?: return
        moving = null
        if (order != moveBase) {
            spec.sections.getOrNull(section)?.onMove?.invoke(order)
        } else {
            moveSection = null
            moveOrder = null
            moveBase = null
        }
    }

    /** Back: it goes back where it was, and the list keeps it in view there. */
    fun cancelMove() {
        val id = moving ?: return
        val base = moveBase ?: return
        listState.keepInView(keys.indexOf(moveOrder?.first() ?: base.first()) + base.indexOf(id), keys.size)
        moving = null
        moveSection = null
        moveOrder = null
        moveBase = null
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        start.requestFocus()
    }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown
                val released = event.type == KeyEventType.KeyUp
                if (event.key in SelectKeys) press.okDown = down
                if (moving == null) return@onPreviewKeyEvent false
                // While a row moves, Up and Down carry it, OK drops it and Back puts it back;
                // nothing else acts until it's dropped (board 04's move mode).
                when {
                    event.key == Key.DirectionUp -> if (down) moveBy(-1)
                    event.key == Key.DirectionDown -> if (down) moveBy(+1)
                    event.key in SelectKeys -> if (released) {
                        // The press that lifted it, let go.
                        if (press.awaitRelease) press.awaitRelease = false else drop()
                    }
                    event.key == Key.Back || event.key == Key.Escape -> if (released) cancelMove()
                }
                true
            },
    ) {
        SecondaryHeader(spec.back, spec.title)
        DetailPanel(focused.detail, spec.style, Modifier.offset(96.px, spec.detailTop))

        PlasmaColumn(
            modifier = Modifier
                .offset(964.px, LIST_TOP_PX.px)
                .size(860.px, spec.listBottom - LIST_TOP_PX.px),
            state = listState,
            gap = spec.metrics.gap,
            pivot = null,
            context = spec.metrics.height,
            focusFallback = start,
        ) {
            spec.header?.let { header -> item(key = "header") { header() } }
            spec.sections.forEachIndexed { i, section ->
                if (section.divider) item(key = "divider-$i") { ListDivider() }
                val rows = shown[i]
                val carried = rows.firstOrNull { it.id == moving }.takeIf { i == moveSection }
                if (section.label != null) {
                    item(key = "label-$i") {
                        // "Moving New in Series · Press OK to drop it here" takes the label's place.
                        if (carried != null) {
                            MovingLabel(carried.label, spec.metrics, first = i == 0 && spec.header == null)
                        } else {
                            SectionLabel(section.label, spec.metrics, first = i == 0 && spec.header == null)
                        }
                    }
                }
                items(rows, key = { it.id }) { item ->
                    val place = rows.indexOf(item)
                    SettingsRow(
                        label = item.label,
                        value = item.value,
                        subtitle = item.subtitle,
                        icon = item.icon,
                        leadingIcon = item.leadingIcon,
                        status = item.status,
                        chevron = item.chevron,
                        dimmed = item.dimmed,
                        labelPx = item.labelPx,
                        metrics = spec.metrics,
                        onFocused = { focusedId = item.id },
                        onClick = {
                            when (val action = item.action) {
                                is SettingAction.Open -> onOpen(action.page)
                                is SettingAction.Go -> onGo(action.destination)
                                is SettingAction.Run -> action.run()
                                is SettingAction.Explain -> {
                                    sheetFor = item
                                    explain = action
                                    sheetOpen = true
                                }
                                is SettingAction.Choose -> {
                                    sheetFor = item
                                    sheetOpen = true
                                }
                                is SettingAction.Toggle -> values.set(action.key, !action.on)
                                is SettingAction.Menu -> {
                                    sheetFor = item
                                    menu = action
                                    sheetOpen = true
                                }
                                is SettingAction.Type -> {
                                    sheetFor = item
                                    typing = action
                                }
                                is SettingAction.Sheet -> {
                                    sheetFor = item
                                    ownSheet = action
                                }
                                SettingAction.None -> Unit
                            }
                        },
                        onLongClick = if (section.onMove != null && section.items.size > 1) ({ startMove(i, item.id) }) else null,
                        moving = item.id == moving,
                        canMoveUp = place > 0,
                        canMoveDown = place < rows.lastIndex,
                        steppedBack = moving != null && item.id != moving,
                        modifier = Modifier
                            .then(if (item.id == startId) Modifier.focusRequester(start) else Modifier)
                            .then(if (item.id == sheetFor?.id) Modifier.focusRequester(sheetReturn) else Modifier),
                    )
                }
            }
        }

        // The footer's links make way for a sheet's key hints, which sit where they do; while a
        // row moves, the keys that move it, as on Live TV and Apps.
        if (moving != null) {
            KeyFooter(
                colorKeys = emptyList(),
                keys = listOf(
                    FooterKey("OK", stringResource(DesignR.string.key_drop)),
                    FooterKey(PlasmaIcons.Back, stringResource(DesignR.string.key_cancel)),
                ),
            )
        } else if (!sheetOpen && typing == null && ownSheet == null) {
            spec.footer?.invoke()
        }

        // Back on the row once it's saved or left.
        SheetHost(visible = typing != null, returnFocus = sheetReturn) {
            typing?.let { entry -> TypedSetting(spec.title, entry, onDone = { typing = null }) }
        }

        SheetHost(visible = ownSheet != null, returnFocus = sheetReturn) {
            ownSheet?.content?.invoke { ownSheet = null }
        }

        // A choice that opens another sheet, such as Rename's typing, leaves focus to it.
        SheetHost(visible = sheetOpen, returnFocus = sheetReturn, handBack = { typing == null && ownSheet == null }) {
            val item = sheetFor ?: return@SheetHost
            val explaining = explain
            if (item.action is SettingAction.Explain && explaining != null) {
                ExplainSheet(explaining, onDismiss = { sheetOpen = false })
                return@SheetHost
            }
            val shown = menu
            if (item.action is SettingAction.Menu && shown != null) {
                // A fresh sheet for each menu, so a confirmation opens on its own first choice.
                key(shown) {
                    OptionsSheet(
                        title = shown.title,
                        subtitle = shown.subtitle,
                        onDismiss = { sheetOpen = false },
                        alignment = Alignment.CenterEnd,
                        width = 640.px,
                        options = shown.options.map { option ->
                            SheetOption(
                                option.label,
                                value = option.value,
                                onClick = {
                                    val next = option.next
                                    val own = option.sheet
                                    val type = option.type
                                    when {
                                        next != null -> {
                                            menu = next
                                            sheetOpen = true
                                        }
                                        own != null -> ownSheet = own
                                        type != null -> typing = type
                                        else -> option.run()
                                    }
                                },
                            )
                        },
                    )
                }
                return@SheetHost
            }
            // The row's current options: a choice made since the page was built shows on reopening.
            val choose = (items.firstOrNull { it.id == item.id }?.action ?: item.action) as? SettingAction.Choose ?: return@SheetHost
            OptionsSheet(
                title = item.label,
                subtitle = choose.note,
                onDismiss = { sheetOpen = false },
                alignment = Alignment.CenterEnd,
                width = 640.px,
                options = choose.options.mapIndexed { i, label ->
                    SheetOption(label, onClick = { values.choose(choose.key, i) }, checked = i == choose.selected)
                },
            )
        }
    }
}

/** Whether OK is down, and whether the row it lifted waits for it to come up. */
private class MovePress {
    var okDown = false
    var awaitRelease = false
}

/** The left half: the icon tile, the title, a line or two about the setting, and its cards. */
@Composable
private fun DetailPanel(detail: SettingDetail, style: PageStyle, modifier: Modifier) {
    Column(modifier.width(760.px), horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(detail.icon, style)
        PlasmaText(
            detail.title,
            plasmaStyle(38f, 600, trackingPx = -0.5f),
            Modifier.padding(top = style.titleGap),
            align = TextAlign.Center,
        )
        detail.subline?.let { PlasmaText(it, plasmaStyle(23f, 500), Modifier.padding(top = 8.px), color = ca.gureet.plasma.core.design.PlasmaColors.Text2) }
        detail.status?.let { StatusLine(it, Modifier.padding(top = 14.px)) }
        detail.text?.let { DetailText(it, style) }
        detail.extra?.invoke(this)
    }
}
