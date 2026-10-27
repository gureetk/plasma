// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import ca.gureet.plasma.stream.StreamHubTest.Companion.counterOf
import ca.gureet.plasma.stream.StreamHubTest.Companion.tsPackets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

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

    @Test fun aReaderBehindTheMemoryReadsFromTheRewindBuffer() {
        val folder = Files.createTempDirectory("rewind").toFile()
        try {
            val ring = Ring(capacity = 10 * Ring.TS_PACKET)
            val spill = Spill(folder, keepMs = 3_600_000, maxBytes = Long.MAX_VALUE)
            ring.behind = spill
            val cursor = ring.cursor(0)
            val packets = tsPackets(25)
            // The rewind buffer gets each chunk first, as the upstream writes it.
            for (packet in 0 until 25) {
                spill.write(packet.toLong() * Ring.TS_PACKET, packets, packet * Ring.TS_PACKET, Ring.TS_PACKET)
                ring.write(packets, packet * Ring.TS_PACKET, Ring.TS_PACKET)
            }

            // Every packet comes, in order, though memory holds only the last ten.
            val into = ByteArray(Ring.TS_PACKET)
            for (packet in 0 until 25) {
                assertEquals(Ring.TS_PACKET, cursor.read(into, 0))
                assertEquals(packet, counterOf(into, packet = 0))
            }
            assertEquals(0, cursor.read(into, 0))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun memoryHandsOverWhatItHolds() {
        val ring = Ring(capacity = 10 * Ring.TS_PACKET)
        val packets = tsPackets(25)
        ring.write(packets, 0, packets.size)

        val (from, held) = ring.held()

        assertEquals(15L * Ring.TS_PACKET, from)
        assertEquals(10 * Ring.TS_PACKET, held.size)
        assertEquals(15, counterOf(held, packet = 0))
        assertEquals(24, counterOf(held, packet = 9))
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
