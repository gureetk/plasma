// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.gureet.plasma.core.design.LocalContentColor

private val vectorCache = HashMap<Pair<PlasmaIcons, Float>, ImageVector>()

/** Builds (once) the vector for [icon] with strokes [stroke] units wide on its own grid. */
fun PlasmaIcons.vector(stroke: Float = defaultStroke): ImageVector = synchronized(vectorCache) {
    vectorCache.getOrPut(this to stroke) {
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = (24f * viewportHeight / viewportWidth).dp,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        ).apply {
            for (path in paths) {
                addPath(
                    pathData = addPathNodes(path.data),
                    fill = if (path.fill) SolidColor(Color.White) else null,
                    stroke = if (path.stroke) SolidColor(Color.White) else null,
                    strokeLineWidth = if (path.stroke) stroke else 0f,
                    strokeLineCap = if (roundCaps) StrokeCap.Round else StrokeCap.Butt,
                    strokeLineJoin = if (roundJoins) StrokeJoin.Round else StrokeJoin.Miter,
                )
            }
        }.build()
    }
}

/**
 * A line icon from the boards, [size] across, tinted with [tint] (the surrounding content colour
 * by default, so it turns dark on a focused surface). [stroke] is in the icon's 24-unit grid,
 * as on the boards' `stroke-width`.
 */
@Composable
fun PlasmaIcon(
    icon: PlasmaIcons,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    stroke: Float = icon.defaultStroke,
    height: Dp = size * (icon.viewportHeight / icon.viewportWidth),
) {
    val color = if (tint != Color.Unspecified) tint else LocalContentColor.current
    val vector = remember(icon, stroke) { icon.vector(stroke) }
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = null,
        modifier = modifier.size(size, height),
        colorFilter = ColorFilter.tint(color),
    )
}
