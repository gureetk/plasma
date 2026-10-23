// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * A border as the boards' CSS draws one: it takes up room, and the content (and any padding after
 * this) sits inside it. Compose's own border is painted over the content without moving it.
 */
fun Modifier.outline(width: Dp, color: Color, shape: Shape): Modifier = border(width, color, shape).padding(width)
