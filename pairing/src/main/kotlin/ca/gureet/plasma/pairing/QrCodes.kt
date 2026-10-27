// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

import ca.gureet.plasma.core.model.QrMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** QR codes for the pairing pages' addresses (brief 5.15). */
object QrCodes {
    /** [text], an address in plain ASCII, as a QR code with medium error correction, without its quiet zone. */
    fun encode(text: String): QrMatrix {
        val code = Encoder.encode(text, ErrorCorrectionLevel.M)
        val matrix = code.matrix
        val size = matrix.width
        return QrMatrix(size, BooleanArray(size * size) { i -> matrix.get(i % size, i / size).toInt() == 1 })
    }
}
