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

/**
 * The width of the boards' thin outlines (badges, cards). Their CSS asks for 1.5 px, but the
 * browser that drew the approved images rounds a border down to whole pixels, so they show 1 px,
 * and the images win on visuals.
 */
val Hairline: Dp = 1.px
