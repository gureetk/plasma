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
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaColumn
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/** The list starts at y 150 and scrolls when a page has more rows than fit. */
private const val LIST_TOP_PX = 150

/**
 * One page of Settings in the tvOS pattern (brief 4.19): the focused row's detail on the left, the
 * rows on the right. OK on a row opens its page, its options on the right, or flips its switch.
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
    val start = remember { FocusRequester() }
    val sheetReturn = remember { FocusRequester() }
    val startId = remember { focusedId }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        start.requestFocus()
    }

    Box(modifier.fillMaxSize()) {
        SecondaryHeader(spec.back, spec.title)
        DetailPanel(focused.detail, spec.style, Modifier.offset(96.px, spec.detailTop))

        PlasmaColumn(
            modifier = Modifier
                .offset(964.px, LIST_TOP_PX.px)
                .size(860.px, spec.listBottom - LIST_TOP_PX.px),
            gap = spec.metrics.gap,
            pivot = null,
            context = spec.metrics.height,
            focusFallback = start,
        ) {
            spec.header?.let { header -> item(key = "header") { header() } }
            spec.sections.forEachIndexed { i, section ->
                if (section.divider) item(key = "divider-$i") { ListDivider() }
                if (section.label != null) {
                    item(key = "label-$i") { SectionLabel(section.label, spec.metrics, first = i == 0 && spec.header == null) }
                }
                items(section.items, key = { it.id }) { item ->
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
                                SettingAction.None -> Unit
                            }
                        },
                        modifier = Modifier
                            .then(if (item.id == startId) Modifier.focusRequester(start) else Modifier)
                            .then(if (item.id == sheetFor?.id) Modifier.focusRequester(sheetReturn) else Modifier),
                    )
                }
            }
        }

        spec.footer?.invoke()

        SheetHost(visible = sheetOpen, returnFocus = sheetReturn) {
            val item = sheetFor ?: return@SheetHost
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
                                onClick = {
                                    val next = option.next
                                    if (next != null) {
                                        menu = next
                                        sheetOpen = true
                                    } else {
                                        option.run()
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
