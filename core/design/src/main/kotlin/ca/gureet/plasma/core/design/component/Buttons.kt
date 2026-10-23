// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/**
 * A button (brief 2.5): `.14` white when unfocused, white with dark text when focused, scale 1.05.
 * There's no separate primary color; the primary action is the button that has focus by default,
 * set a little larger (board 14's Resume: 25 px text, 28/34 px padding).
 *
 * A button that leads its row grows from its left edge ([pivot]) and keeps [TransformOrigin.Center]
 * otherwise, so the lift never covers its neighbour's label.
 */
@Composable
fun PlasmaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PlasmaIcons? = null,
    iconSize: Dp = 24.px,
    iconStroke: Float? = null,
    height: Dp = 68.px,
    textSizePx: Float = 23f,
    startPadding: Dp = if (icon != null) 22.px else 26.px,
    endPadding: Dp = 26.px,
    gap: Dp = 10.px,
    pivot: TransformOrigin = TransformOrigin.Center,
    unfocusedColor: Color = PlasmaColors.ButtonFill,
    onLongClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    PlasmaSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(height),
        style = FocusStyle.Button,
        shape = PlasmaShapes.Button,
        color = unfocusedColor,
        pivot = pivot,
        onFocusChanged = onFocusChanged,
        contentAlignment = Alignment.Center,
    ) {
        val focused = LocalFocused.current
        Row(
            Modifier.padding(start = startPadding, end = endPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (icon != null) PlasmaIcon(icon, iconSize, stroke = iconStroke ?: icon.defaultStroke)
            PlasmaText(text, plasmaStyle(textSizePx, if (focused) 600 else 500))
        }
    }
}

/** A square icon-only button, such as "+" (My List) or Multiview in the player. */
@Composable
fun PlasmaIconButton(
    icon: PlasmaIcons,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 68.px,
    iconSize: Dp = 24.px,
    stroke: Float = icon.defaultStroke,
    unfocusedColor: Color = PlasmaColors.ButtonFill,
    shape: Shape = PlasmaShapes.Button,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.size(size).semantics { this.contentDescription = contentDescription },
        style = FocusStyle.Button,
        shape = shape,
        color = unfocusedColor,
        onFocusChanged = onFocusChanged,
        contentAlignment = Alignment.Center,
    ) {
        PlasmaIcon(icon, iconSize, stroke = stroke, tint = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.Text)
    }
}

/**
 * The current choice with a ⌄, next to its yellow-key hint: "Newest first" on Movies (board 13),
 * the group on the Guide (board 08), how long the PIN unlocks for (board 25). OK opens the choices.
 */
@Composable
fun ChoiceButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textSizePx: Float = 22f,
    color: Color = PlasmaColors.SelectedSoft,
) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.height(52.px),
        style = FocusStyle.Button,
        shape = PlasmaShapes.Tab,
        color = color,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(start = 22.px, end = 18.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            PlasmaText(label, plasmaStyle(textSizePx, 500))
            PlasmaIcon(PlasmaIcons.ChevronDown, 18.px)
        }
    }
}

/**
 * A tab in a row of tabs: More Like This · Cast & Crew on a movie, the seasons of a series (boards
 * 14 and 16). The selected tab sits on a `.12` pill; focus fills it white.
 */
@Composable
fun TabPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.px,
    textSizePx: Float = 22f,
    horizontalPadding: Dp = 18.px,
    shape: Shape = PlasmaShapes.SmallTab,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.height(height),
        style = FocusStyle.Pill,
        shape = shape,
        selected = selected,
        onFocusChanged = onFocusChanged,
        contentAlignment = Alignment.Center,
    ) {
        val focused = LocalFocused.current
        PlasmaText(
            text,
            plasmaStyle(textSizePx, if (selected || focused) 600 else 500),
            Modifier.padding(horizontal = horizontalPadding),
            color = when {
                focused -> PlasmaColors.OnFocus
                selected -> PlasmaColors.Text
                else -> PlasmaColors.Text2
            },
        )
    }
}
