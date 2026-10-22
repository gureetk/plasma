// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion

/**
 * The graphite fill of the Home dock tiles, All Apps and See All (board 01): a soft top-to-bottom
 * gradient with a hairline highlight along the top edge, both brighter when focused. Put it first
 * inside a [ca.gureet.plasma.core.design.PlasmaSurface] with `FocusStyle.Tile`.
 */
@Composable
fun GraphiteFill(modifier: Modifier = Modifier, focused: Boolean = LocalFocused.current) {
    val top by animateColorAsState(
        if (focused) PlasmaColors.GraphiteFocusedTop else PlasmaColors.GraphiteTop,
        PlasmaMotion.colour(),
        label = "graphiteTop",
    )
    val bottom by animateColorAsState(
        if (focused) PlasmaColors.GraphiteFocusedBottom else PlasmaColors.GraphiteBottom,
        PlasmaMotion.colour(),
        label = "graphiteBottom",
    )
    val highlight = PlasmaColors.white(if (focused) 0.16f else 0.07f)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .drawWithContent {
                drawContent()
                drawLine(highlight, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1f)
            },
    )
}
