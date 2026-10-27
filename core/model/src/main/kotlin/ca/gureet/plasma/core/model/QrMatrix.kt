// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * A QR code's modules, [size] by [size], dark where true (brief 5.15). The quiet zone around it
 * isn't included: whatever draws it adds the white margin.
 */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    init {
        require(dark.size == size * size) { "A $size × $size code has ${size * size} modules" }
    }

    /** Whether the module in column [x] of row [y] is dark. */
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]

    override fun equals(other: Any?): Boolean = other is QrMatrix && other.size == size && other.dark.contentEquals(dark)

    override fun hashCode(): Int = 31 * size + dark.contentHashCode()
}
