// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import ca.gureet.plasma.stream.StreamHubTest.Companion.counterOf
import ca.gureet.plasma.stream.StreamHubTest.Companion.tsPackets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingTest {
    @Test fun findsTheLatestPatEvenWhenTheStreamIsntAligned() {
        val ring = Ring(capacity = 64 * Ring.TS_PACKET)
        // Three stray bytes first, as when a stream is joined mid-packet.
        ring.write(byteArrayOf(1, 2, 3), 0, 3)
        val packets = tsPackets(30, patEvery = 7)
        ring.write(packets, 0, packets.size)

        val pat = ring.latestPat()!!

        // Packets 0, 7, 14, 21 and 28 carry a PAT; the last starts after the three stray bytes.
        assertEquals(3L + 28 * Ring.TS_PACKET, pat)
    }

    @Test fun hasNoPatWithoutOne() {
        val ring = Ring(capacity = 16 * Ring.TS_PACKET)
        val packets = tsPackets(5, patEvery = 100).also { it[1] = 0x01 }
        ring.write(packets, 0, packets.size)
        assertNull(ring.latestPat())
    }

    @Test fun aReaderThatFallsBehindSkipsToTheOldestDataHeld() {
        val ring = Ring(capacity = 10 * Ring.TS_PACKET)
        val cursor = ring.cursor(0)
        val packets = tsPackets(25)
        ring.write(packets, 0, packets.size)

        val into = ByteArray(Ring.TS_PACKET)
        assertEquals(Ring.TS_PACKET, cursor.read(into, 0))

        // Only the last ten packets are still held.
        assertEquals(15, counterOf(into, packet = 0))
    }

    @Test fun readersSeeTheEndOnceTheyHaveEverything() {
        val ring = Ring(capacity = 4 * Ring.TS_PACKET)
        val cursor = ring.cursor(0)
        ring.write(tsPackets(1), 0, Ring.TS_PACKET)
        ring.end()

        val into = ByteArray(4 * Ring.TS_PACKET)
        assertEquals(Ring.TS_PACKET, cursor.read(into, 0))
        assertEquals(-1, cursor.read(into, 0))
    }
}
