// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.R
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/**
 * The glass panel of a sheet (board 10): 28 px corners, a soft shadow, a hairline along the top.
 * Give it its width and place it.
 */
@Composable
fun SheetPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .boxShadow(PlasmaColors.black(0.6f), offsetY = 40.px, blur = 90.px, cornerRadius = 28.px)
            .background(PlasmaColors.SheetFill, PlasmaShapes.Sheet)
            .drawWithContent {
                drawContent()
                drawLine(PlasmaColors.white(0.08f), Offset(28.px.toPx(), 0.5f), Offset(size.width - 28.px.toPx(), 0.5f))
            }
            .padding(start = 30.px, end = 30.px, top = 30.px, bottom = 26.px),
        content = content,
    )
}

/** An icon's tint on a sheet row: soft on the glass, dark on the focused (white) row. */
@Composable
fun sheetIconTint(): Color = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.TextSoft

/** A row in a sheet: an icon, the label, and the current value on the right. 62 px, 64 when focused. */
@Composable
fun SheetRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val height by animateDpAsState(if (focused) 64.px else 62.px, PlasmaMotion.focus(), label = "sheetRowHeight")
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height),
        style = FocusStyle.Row,
        shape = PlasmaShapes.Row,
        onFocusChanged = { focused = it },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.padding(horizontal = 22.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.px),
        ) {
            icon?.invoke()
            PlasmaText(label, plasmaStyle(24f, if (focused) 600 else 500), Modifier.weight(1f))
            if (value != null) {
                PlasmaText(value, plasmaStyle(21f, 500, tabular = true), color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text2)
            }
        }
    }
}

/** A thin rule between groups of sheet rows. */
@Composable
fun SheetDivider(modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 22.px, vertical = 8.px).fillMaxWidth().height(1.px).background(PlasmaColors.Divider))
}

/** One choice in an [OptionsSheet]. A null [onClick] draws a divider instead of a row. */
@Immutable
class SheetOption(
    val label: String,
    val onClick: (() -> Unit)?,
    val value: String? = null,
    val icon: PlasmaIcons? = null,
) {
    companion object {
        val Divider = SheetOption("", onClick = null)
    }
}

/**
 * Shows [sheet] while [visible], and when it closes puts focus back on [returnFocus]: the item the
 * sheet was opened for. Keep [returnFocus] on that item until then; if the item is gone (hidden,
 * removed), focus stays where the screen puts it.
 */
@Composable
fun SheetHost(visible: Boolean, returnFocus: FocusRequester, sheet: @Composable () -> Unit) {
    var wasVisible by remember { mutableStateOf(false) }
    if (visible) {
        wasVisible = true
        sheet()
    }
    LaunchedEffect(visible) {
        if (!visible && wasVisible) {
            wasVisible = false
            withFrameNanos { }
            returnFocus.requestFocus()
        }
    }
}

/**
 * What Menu or long-press OK opens (brief 3.6): a sheet over the dimmed screen with a heading and
 * the choices for the focused item. The first choice has focus; focus stays in the sheet, and
 * Back closes it. Choosing a row runs it and closes the sheet.
 *
 * @param leading art beside the heading: a logo plate, a poster, an app's banner.
 */
@Composable
fun OptionsSheet(
    title: String,
    options: List<SheetOption>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    width: Dp = 720.px,
    leading: (@Composable () -> Unit)? = null,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.requestFocus()
    }
    Box(
        modifier
            .fillMaxSize()
            .background(PlasmaColors.black(0.5f))
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SheetPanel(Modifier.width(width)) {
            Row(
                Modifier.padding(horizontal = 10.px),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.px),
            ) {
                leading?.invoke()
                Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
                    PlasmaText(title, plasmaStyle(30f, 600, trackingPx = -0.4f))
                    if (subtitle != null) PlasmaText(subtitle, plasmaStyle(21f, 500, tabular = true), color = PlasmaColors.Text2)
                }
            }
            Box(Modifier.padding(top = 22.px, bottom = 14.px).fillMaxWidth().height(1.px).background(PlasmaColors.white(0.1f)))
            Column(
                // Focus stays in the sheet until it closes.
                Modifier.focusProperties { onExit = { cancelFocusChange() } }.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.px),
            ) {
                val firstRow = options.indexOfFirst { it.onClick != null }
                options.forEachIndexed { i, option ->
                    val onClick = option.onClick
                    if (onClick == null) {
                        SheetDivider()
                    } else {
                        SheetRow(
                            label = option.label,
                            value = option.value,
                            onClick = {
                                onDismiss()
                                onClick()
                            },
                            modifier = if (i == firstRow) Modifier.focusRequester(first) else Modifier,
                            icon = option.icon?.let { icon -> { PlasmaIcon(icon, 24.px, tint = sheetIconTint()) } },
                        )
                    }
                }
            }
        }
        CenteredKeyFooter(
            keys = listOf(
                FooterKey("OK", stringResource(R.string.key_select)),
                FooterKey(PlasmaIcons.Back, stringResource(R.string.key_close)),
            ),
            modifier = Modifier.align(Alignment.TopStart),
            tone = HintTone.Screen,
        )
    }
}
