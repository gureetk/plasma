// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * The boards are drawn at 1920 × 1080 px, and Android TV lays a 1080p screen out as 960 × 540 dp.
 * These helpers let code use the boards' pixel values directly: 96.px is 48 dp, 25.pxSp is 12.5 sp.
 */

/** A board pixel value as dp (px ÷ 2). */
val Int.px: Dp get() = (this / 2f).dp

/** A board pixel value as dp (px ÷ 2). */
val Float.px: Dp get() = (this / 2f).dp

/** A board pixel value as sp (px ÷ 2). */
val Int.pxSp: TextUnit get() = (this / 2f).sp

/** A board pixel value as sp (px ÷ 2). */
val Float.pxSp: TextUnit get() = (this / 2f).sp

/** Letter spacing given in board pixels for a font size given in board pixels, as em so it scales with the text. */
fun tracking(px: Float, fontPx: Float): TextUnit = (px / fontPx).em

/**
 * Places the element at ([x], [y]) in its parent at a fixed size, even when it's bigger than the
 * parent, like the boards' `position: absolute` images. (A plain `requiredSize` would centre an
 * oversized element instead.)
 */
fun Modifier.absolute(x: Dp, y: Dp, width: Dp, height: Dp): Modifier =
    wrapContentSize(Alignment.TopStart, unbounded = true).offset(x, y).requiredSize(width, height)

/**
 * Lets the element hang [vertical] past its top and bottom without making its row taller, like the
 * boards' negative margins (`margin: -4px 0` on the reminder toast's logo).
 */
fun Modifier.overhang(vertical: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val trim = vertical.roundToPx()
    layout(placeable.width, (placeable.height - 2 * trim).coerceAtLeast(0)) { placeable.place(0, -trim) }
}
