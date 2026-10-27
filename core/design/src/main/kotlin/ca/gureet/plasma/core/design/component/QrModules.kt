// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.model.QrMatrix
import kotlin.math.floor

/**
 * A QR code's modules in black, for a white plate (brief 5.15), with [quietZone] modules of white
 * around them. Each module is a whole number of pixels, so a camera sees clean edges; the code
 * sits in the middle of whatever's left over.
 */
@Composable
fun QrModules(matrix: QrMatrix, modifier: Modifier = Modifier, quietZone: Int = 0) {
    Canvas(modifier) {
        val cell = floor(size.minDimension / (matrix.size + 2 * quietZone)).coerceAtLeast(1f)
        val start = Offset((size.width - cell * matrix.size) / 2, (size.height - cell * matrix.size) / 2)
        val module = Size(cell, cell)
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) drawRect(PlasmaColors.Black, Offset(start.x + x * cell, start.y + y * cell), module)
            }
        }
    }
}
