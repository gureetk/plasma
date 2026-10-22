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
 * There's no separate primary colour; the primary action is the button that has focus by default.
 */
@Composable
fun PlasmaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PlasmaIcons? = null,
    iconStroke: Float? = null,
    height: Dp = 68.px,
    textSizePx: Float = 23f,
    horizontalPadding: Dp = 26.px,
    unfocusedColor: Color = Color(0x24FFFFFF),
    onLongClick: (() -> Unit)? = null,
) {
    PlasmaSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(height),
        style = FocusStyle.Button,
        shape = PlasmaShapes.Button,
        color = unfocusedColor,
        contentAlignment = Alignment.Center,
    ) {
        val focused = LocalFocused.current
        Row(
            Modifier.padding(start = if (icon != null) horizontalPadding - 4.px else horizontalPadding, end = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.px),
        ) {
            if (icon != null) PlasmaIcon(icon, 24.px, stroke = iconStroke ?: icon.defaultStroke)
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
    unfocusedColor: Color = Color(0x24FFFFFF),
    shape: androidx.compose.ui.graphics.Shape = PlasmaShapes.Button,
) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.size(size).semantics { this.contentDescription = contentDescription },
        style = FocusStyle.Button,
        shape = shape,
        color = unfocusedColor,
        contentAlignment = Alignment.Center,
    ) {
        PlasmaIcon(icon, iconSize, stroke = stroke, tint = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.Text)
    }
}
