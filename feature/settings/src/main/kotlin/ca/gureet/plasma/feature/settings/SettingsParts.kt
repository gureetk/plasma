// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.Hairline
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.GraphiteIconTile
import ca.gureet.plasma.core.design.component.MoveArrow
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.outline
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.R as DesignR

/**
 * The two sizes of the Settings pattern (brief 4.19): Settings itself and its first pages have a
 * 220 px icon tile, the pages under them a 180 px one with lighter text.
 */
internal enum class PageStyle(
    val tile: Dp,
    val tileRadius: Dp,
    val icon: Dp,
    val iconStroke: Float,
    val titleGap: Dp,
    val textSizePx: Float,
    val textLineHeight: Float,
    val textGap: Dp,
    val textWidth: Dp,
    val textColor: Color,
    /** Whether the description's lines are balanced (boards 27–34) or filled one by one (19–22). */
    val balanced: Boolean,
) {
    /** Boards 19 and 20. */
    Large(220.px, 48.px, 112.px, 1.2f, 34.px, 23f, 1.4f, 12.px, 600.px, PlasmaColors.Text2, balanced = false),

    /** Board 22: the same tile, with roomier text. */
    Relaxed(220.px, 48.px, 108.px, 1.2f, 34.px, 24f, 1.5f, 14.px, 620.px, PlasmaColors.Text2, balanced = false),

    /** Boards 27–29, 31, 32 and 34. */
    Compact(180.px, 40.px, 92.px, 1.3f, 28.px, 23f, 1.4f, 12.px, 660.px, PlasmaColors.TextSoft, balanced = true),

    /** Board 23: Settings' tile, with a subline and lighter text under it. */
    Setup(220.px, 48.px, 112.px, 1.2f, 34.px, 23f, 1.4f, 18.px, 640.px, PlasmaColors.TextSoft, balanced = false),
}

/**
 * The rows' sizes on each kind of page. Focused rows are a little taller and keep a few pixels
 * clear above and below, as the boards draw them.
 */
@Immutable
internal class RowMetrics(
    val height: Dp,
    val focusedHeight: Dp,
    val focusedMargin: Dp,
    /** Rows with a line under the label. */
    val subtitleHeight: Dp,
    val labelPx: Float,
    val focusedLabelPx: Float,
    val padding: Dp,
    val gap: Dp,
    /** Space under a section label, and above every section label but the first. */
    val labelGap: Dp,
    val sectionGap: Dp,
    /** The line under a label: its size, and the space between them. */
    val subtitlePx: Float = 19f,
    val subtitleGap: Dp = 3.px,
) {
    companion object {
        /** Settings itself (board 19): 66 px rows with icons, 68 focused. */
        val Main = RowMetrics(66.px, 68.px, 0.px, 84.px, 25f, 25f, 24.px, 5.px, 10.px, 26.px)

        /** The pages (boards 27–29, 31, 32, 34): 62 px rows, 68 focused, 84 with a subtitle. */
        val Page = RowMetrics(62.px, 68.px, 2.px, 84.px, 24f, 25f, 24.px, 5.px, 10.px, 26.px)

        /** Launcher & Home (board 22): 82 px rows, 88 focused. */
        val Roomy = RowMetrics(82.px, 88.px, 3.px, 92.px, 25f, 26f, 26.px, 6.px, 12.px, 34.px)

        /** Portals & Playlists (board 20): sources at 92 px with a subtitle, options at 80. */
        val Sources = RowMetrics(80.px, 84.px, 0.px, 92.px, 25f, 26f, 26.px, 6.px, 12.px, 34.px, subtitlePx = 20f)

        /** Home Screen setup (board 23): every row has a subtitle, 92 px and 96 focused. */
        val Setup = RowMetrics(92.px, 96.px, 2.px, 92.px, 26f, 26f, 26.px, 6.px, 12.px, 30.px, subtitlePx = 20f, subtitleGap = 4.px)
    }
}

/** A status dot's meaning (brief 2.9): green connected, grey standby, amber a warning. */
internal enum class Tone { Ok, Idle, Warn }

@Immutable
internal data class Status(val text: String, val tone: Tone)

private fun Tone.dot(onWhite: Boolean): Color = when (this) {
    Tone.Ok -> if (onWhite) PlasmaColors.OkOnFocus else PlasmaColors.Ok
    Tone.Idle -> PlasmaColors.Text4
    Tone.Warn -> PlasmaColors.Warn
}

private fun Tone.text(): Color = when (this) {
    Tone.Ok -> PlasmaColors.OkText
    Tone.Idle -> PlasmaColors.Text2
    Tone.Warn -> PlasmaColors.WarnText
}

/** The tile at the top of the detail panel: a graphite square with the setting's icon. */
@Composable
internal fun IconTile(icon: PlasmaIcons, style: PageStyle) {
    GraphiteIconTile(icon, style.tile, style.tileRadius, style.icon, style.iconStroke)
}

/** A paragraph in the detail panel: the setting's description, or a note under a card. */
@Composable
internal fun DetailText(text: String, style: PageStyle, modifier: Modifier = Modifier) {
    PlasmaText(
        text,
        plasmaStyle(style.textSizePx, 400, lineHeight = style.textLineHeight)
            .copy(lineBreak = if (style.balanced) LineBreak.Heading else LineBreak.Simple),
        modifier.padding(top = style.textGap).width(style.textWidth),
        color = style.textColor,
        align = TextAlign.Center,
        maxLines = 6,
    )
}

/** The small print under a detail card (20 px, `text3`). */
@Composable
internal fun DetailNote(text: String, modifier: Modifier = Modifier, top: Dp = 18.px) {
    PlasmaText(
        text,
        plasmaStyle(20f, 500, lineHeight = 1.4f).copy(lineBreak = LineBreak.Heading),
        modifier.padding(top = top).width(640.px),
        color = PlasmaColors.Text3,
        align = TextAlign.Center,
        maxLines = 4,
    )
}

/** "● Connected · 42 ms", in the status's own color. */
@Composable
internal fun StatusLine(status: Status, modifier: Modifier = Modifier, sizePx: Float = 22f, weight: Int = 600) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
        Box(Modifier.size(10.px).background(status.tone.dot(onWhite = false), RoundedCornerShape(5.px)))
        PlasmaText(status.text, plasmaStyle(sizePx, weight), color = status.tone.text())
    }
}

/** An outlined card in the detail panel (not focusable): 640 px, a 1.5 px `.1` outline. */
@Composable
internal fun OutlinedCard(
    modifier: Modifier = Modifier,
    top: Dp = 28.px,
    horizontal: Dp = 24.px,
    topPadding: Dp = 22.px,
    bottomPadding: Dp = 22.px,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .padding(top = top)
            .width(640.px)
            .outline(Hairline, PlasmaColors.white(0.1f), PlasmaShapes.Card)
            .padding(start = horizontal, end = horizontal, top = topPadding, bottom = bottomPadding),
        content = content,
    )
}

/** A small section label over a group of rows (21 px, weight 600, `text3`). */
@Composable
internal fun SectionLabel(text: String, metrics: RowMetrics, first: Boolean) {
    PlasmaText(
        text,
        plasmaStyle(21f, 600),
        Modifier.padding(start = 22.px, top = if (first) 0.px else metrics.sectionGap - metrics.gap, bottom = metrics.labelGap - metrics.gap),
        color = PlasmaColors.Text3,
    )
}

/**
 * A section's label while one of its rows moves: "Moving New in Series · Press OK to drop it here",
 * as Live TV's list heading says it.
 */
@Composable
internal fun MovingLabel(name: String, metrics: RowMetrics, first: Boolean) {
    Row(
        Modifier.padding(start = 22.px, top = if (first) 0.px else metrics.sectionGap - metrics.gap, bottom = metrics.labelGap - metrics.gap),
        horizontalArrangement = Arrangement.spacedBy(12.px),
    ) {
        PlasmaText(stringResource(DesignR.string.moving, name), plasmaStyle(21f, 600), Modifier.alignByBaseline(), color = PlasmaColors.Text)
        PlasmaText(stringResource(DesignR.string.moving_hint), plasmaStyle(21f, 500), Modifier.alignByBaseline(), color = PlasmaColors.Text3)
    }
}

/** The rule between the two halves of Settings (board 19). */
@Composable
internal fun ListDivider() {
    Box(Modifier.padding(horizontal = 26.px, vertical = 6.px).fillMaxWidth().height(1.px).background(PlasmaColors.Divider))
}

/**
 * A settings row: an icon on Settings itself, the label (and a line under it), and on the right
 * the current value with a chevron, or a status dot before it. Switches have no chevron. Focused,
 * the row fills white and grows a little.
 */
@Composable
internal fun SettingsRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    metrics: RowMetrics,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: PlasmaIcons? = null,
    leadingIcon: PlasmaIcons? = null,
    status: Tone? = null,
    chevron: Boolean = true,
    dimmed: Boolean = false,
    labelPx: Float? = null,
    onFocused: () -> Unit = {},
    /** Holding OK or pressing Menu: in a section that moves, the row lifts to be moved. */
    onLongClick: (() -> Unit)? = null,
    /** Being moved: arrows above and below it where Up and Down can carry it (as in Live TV's Favorites). */
    moving: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    /** Another row is being moved: this one steps back. */
    steppedBack: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val base = if (subtitle != null) metrics.subtitleHeight else metrics.height
    val height by animateDpAsState(if (focused) base + (metrics.focusedHeight - metrics.height) else base, label = "rowHeight")
    val margin by animateDpAsState(if (focused) metrics.focusedMargin else 0.px, label = "rowMargin")
    val named = listOfNotNull(label, subtitle, value).joinToString(", ")
    val description = if (moving) stringResource(DesignR.string.moving_named, named) else named
    Box(Modifier.zIndex(if (moving) 2f else 0f)) {
        SettingsRowSurface(
            label, value, onClick, metrics, subtitle, icon, leadingIcon, status, chevron, labelPx, onLongClick,
            modifier = modifier
                .padding(vertical = margin)
                .fillMaxWidth()
                .height(height)
                .alpha(
                    when {
                        steppedBack -> STEPPED_BACK
                        dimmed && !focused -> 0.45f
                        else -> 1f
                    },
                )
                .semantics { contentDescription = description },
            onFocusChanged = {
                focused = it
                if (it) onFocused()
            },
        )
        if (moving) {
            // Where Up and Down can carry it, astride its edges at its start.
            if (canMoveUp) MoveArrow(PlasmaIcons.ChevronUp, stringResource(DesignR.string.move_up), Modifier.align(Alignment.TopStart).offset(18.px, (-20).px))
            if (canMoveDown) MoveArrow(PlasmaIcons.ChevronDown, stringResource(DesignR.string.move_down), Modifier.align(Alignment.BottomStart).offset(18.px, 20.px))
        }
    }
}

/** Rows step back to this while another one moves (board 04's move mode). */
private const val STEPPED_BACK = 0.6f

@Composable
private fun SettingsRowSurface(
    label: String,
    value: String?,
    onClick: () -> Unit,
    metrics: RowMetrics,
    subtitle: String?,
    icon: PlasmaIcons?,
    leadingIcon: PlasmaIcons?,
    status: Tone?,
    chevron: Boolean,
    labelPx: Float?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    onFocusChanged: (Boolean) -> Unit,
) {
    PlasmaSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        style = FocusStyle.Row,
        shape = PlasmaShapes.Row,
        color = PlasmaColors.Surface1,
        onFocusChanged = onFocusChanged,
        contentAlignment = Alignment.CenterStart,
    ) {
        val isFocused = LocalFocused.current
        val secondary = if (isFocused) PlasmaColors.OnFocus2 else PlasmaColors.Text2
        Row(
            Modifier.padding(horizontal = metrics.padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (icon != null) 18.px else 14.px),
        ) {
            if (icon != null) PlasmaIcon(icon, 26.px, tint = if (isFocused) PlasmaColors.OnFocus else PlasmaColors.Text2, stroke = 1.9f)
            if (leadingIcon != null) PlasmaIcon(leadingIcon, 24.px, tint = if (isFocused) PlasmaColors.OnFocus else PlasmaColors.Text, stroke = 2.2f)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subtitleGap)) {
                PlasmaText(
                    label,
                    plasmaStyle(labelPx ?: if (isFocused) metrics.focusedLabelPx else metrics.labelPx, if (isFocused) 600 else 500),
                )
                if (subtitle != null) {
                    PlasmaText(
                        subtitle,
                        plasmaStyle(metrics.subtitlePx, 500),
                        color = if (isFocused) PlasmaColors.OnFocus3 else PlasmaColors.Text3,
                    )
                }
            }
            if (value != null || chevron) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
                    if (status != null) {
                        Box(Modifier.size(10.px).background(status.dot(onWhite = isFocused), RoundedCornerShape(5.px)))
                    }
                    if (value != null) PlasmaText(value, plasmaStyle(22f, 500, tabular = true), color = secondary)
                    if (chevron) PlasmaIcon(PlasmaIcons.ChevronRight, 20.px, tint = secondary, stroke = 2.4f)
                }
            }
        }
    }
}
