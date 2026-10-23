// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodesTest {
    @Test fun aPairingAddressIsASmallCodeWithItsThreeFinderSquares() {
        val qr = QrCodes.encode("http://192.168.1.42:8484/pair?code=7KQ4MZ")

        // A small version: 21 modules a side, plus 4 a version.
        assertTrue(qr.size in 21..37 && (qr.size - 21) % 4 == 0)
        val last = qr.size - 1
        for ((x, y) in listOf(0 to 0, last - 6 to 0, 0 to last - 6)) {
            // A finder: a dark ring, a light ring, a dark centre.
            assertTrue(qr[x, y] && qr[x + 6, y + 6])
            assertFalse(qr[x + 1, y + 1])
            assertTrue(qr[x + 3, y + 3])
        }
    }
}
