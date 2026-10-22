// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

/**
 * A CSS `box-shadow` (offset, blur, colour) drawn only outside a rounded rectangle, for the boards'
 * translucent panels: an elevation shadow would show through their glassy fill. Blur and offset
 * are in board pixels as written on the boards (`0 40px 90px rgba(0,0,0,.6)`).
 */
fun Modifier.boxShadow(color: Color, offsetY: Dp, blur: Dp, cornerRadius: Dp): Modifier = drawBehind {
    val radius = cornerRadius.toPx()
    val outline = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
    }
    clipPath(outline, ClipOp.Difference) {
        drawIntoCanvas { canvas ->
            // CSS blur is twice the Gaussian sigma; Android's blur radius is about sigma ÷ 0.58.
            val sigma = blur.toPx() / 2f
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = color.toArgb()
                if (sigma > 0f) maskFilter = BlurMaskFilter(sigma / 0.57735f, BlurMaskFilter.Blur.NORMAL)
            }
            val dy = offsetY.toPx()
            canvas.nativeCanvas.drawRoundRect(0f, dy, size.width, size.height + dy, radius, radius, paint)
        }
    }
}

