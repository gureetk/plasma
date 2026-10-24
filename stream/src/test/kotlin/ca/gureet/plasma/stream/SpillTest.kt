// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The rewind buffer on storage (brief 5.5): segments cut at keyframes, read back by position and time. */
class SpillTest {
    private val folder: File = Files.createTempDirectory("rewind").toFile()
    private var now = 1_000_000L

    @After fun tidy() {
        folder.deleteRecursively()
    }

    /** [count] packets of made-up stream from [position], each byte saying where it is. */
    private fun chunk(position: Long, count: Int): ByteArray = ByteArray(count * PACKET) { ((position + it) % 251).toByte() }

    private fun spill(keepMs: Long = 60_000, maxBytes: Long = Long.MAX_VALUE) = Spill(folder, keepMs, maxBytes) { now }

    @Test fun cutsAtKeyframesAboutTwoSecondsApartAndReadsBack() {
        val spill = spill()
        var position = 0L
        // A keyframe every second; ten packets a second.
        repeat(6) { second ->
            val bytes = chunk(position, 10)
            spill.write(position, bytes, 0, bytes.size, keyframes = listOf(position))
            position += bytes.size
            now += 1_000
            if (second == 0) assertEquals(0L, spill.oldest)
        }

        assertEquals(0L, spill.oldest)
        assertEquals(position, spill.written)
        // Segments at 0 s, 2 s and 4 s: the keyframes at 1, 3 and 5 s came too soon.
        assertEquals(0L, spill.positionAt(1_000_000 + 1_999))
        assertEquals(20L * PACKET, spill.positionAt(1_000_000 + 2_000))
        assertEquals(40L * PACKET, spill.positionAt(1_000_000 + 59_000))

        // Any byte comes back as it went in, across segments.
        val into = ByteArray(5 * PACKET)
        val read = spill.read(18L * PACKET, into)
        assertEquals(2 * PACKET, read)
        assertArrayEquals(chunk(18L * PACKET, 2), into.copyOf(read))
        val next = spill.read(20L * PACKET, into)
        assertArrayEquals(chunk(20L * PACKET, 5), into.copyOf(next))
    }

    @Test fun saysWhenEachByteCameIn() {
        val spill = spill()
        val first = chunk(0, 10)
        spill.write(0, first, 0, first.size, keyframes = listOf(0))
        now += 2_000
        val second = chunk(first.size.toLong(), 10)
        spill.write(first.size.toLong(), second, 0, second.size, keyframes = listOf(first.size.toLong()))

        assertEquals(1_000_000L, spill.timeAt(0))
        // Halfway through the first segment, halfway to the second's start.
        assertEquals(1_001_000L, spill.timeAt(5L * PACKET))
        assertEquals(1_002_000L, spill.timeAt(10L * PACKET))
        assertNull(spill.timeAt(99_999))
    }

    @Test fun aStreamWithoutKeyframesIsStillCut() {
        val spill = spill()
        var position = 0L
        repeat(10) {
            val bytes = chunk(position, 10)
            spill.write(position, bytes, 0, bytes.size)
            position += bytes.size
            now += 1_000
        }
        // Cut every four seconds or so, at a packet: a seek goes to the segment its time is in.
        assertEquals(3, folder.listFiles()!!.size)
        assertEquals(80L * PACKET, spill.positionAt(now))
        assertEquals(40L * PACKET, spill.positionAt(1_000_000 + 5_000))
        assertEquals(0L, spill.positionAt(0))
    }

    @Test fun aSeekBeforeTheFirstKeyframeLandsOnIt() {
        val spill = spill()
        // Memory's bytes start mid-picture: the first segment isn't a keyframe.
        val first = chunk(0, 10)
        spill.write(0, first, 0, first.size)
        now += 2_000
        val second = chunk(10L * PACKET, 10)
        spill.write(10L * PACKET, second, 0, second.size, keyframes = listOf(10L * PACKET))

        assertEquals(10L * PACKET, spill.positionAt(1_000_000))
        assertEquals(10L * PACKET, spill.positionAt(0))
    }

    @Test fun writesBytesAtTheTimeTheyCameIn() {
        val spill = spill()
        // Memory written out at a pause: each part with the time it came in, not now.
        var position = 0L
        for (second in 0 until 6) {
            val bytes = chunk(position, 10)
            spill.write(position, bytes, 0, bytes.size, keyframes = listOf(position), time = 990_000L + second * 1_000)
            position += bytes.size
        }

        assertEquals(990_000L, spill.oldestTime)
        assertEquals(20L * PACKET, spill.positionAt(992_500))
        assertEquals(40L * PACKET, spill.positionAt(994_000))
    }

    @Test fun storageThatGoesStopsItQuietly() {
        val spill = spill()
        val bytes = chunk(0, 10)
        spill.write(0, bytes, 0, bytes.size, keyframes = listOf(0))
        // A USB drive taken out.
        folder.deleteRecursively()
        now += 5_000
        val more = chunk(bytes.size.toLong(), 10)
        spill.write(bytes.size.toLong(), more, 0, more.size, keyframes = listOf(bytes.size.toLong()))

        // What it held before is all it says it has; nothing more is written.
        assertEquals(0L, spill.oldest)
        assertEquals(bytes.size.toLong(), spill.written)
        val later = chunk(2L * bytes.size, 10)
        spill.write(2L * bytes.size, later, 0, later.size)
        assertEquals(bytes.size.toLong(), spill.written)
    }

    @Test fun letsTheOldestGoBeyondItsLength() {
        val spill = spill(keepMs = 10_000)
        var position = 0L
        repeat(30) {
            val bytes = chunk(position, 10)
            spill.write(position, bytes, 0, bytes.size, keyframes = listOf(position))
            position += bytes.size
            now += 1_000
        }
        // Ten seconds and the segment they begin in, from a keyframe.
        assertEquals(180L * PACKET, spill.oldest)
        assertEquals(1_018_000L, spill.oldestTime)
        assertEquals(-1, spill.read(0, ByteArray(PACKET)))
        assertEquals(position, spill.written)
    }

    @Test fun andBeyondItsSpace() {
        val spill = spill(maxBytes = 45L * PACKET)
        var position = 0L
        repeat(10) {
            val bytes = chunk(position, 10)
            spill.write(position, bytes, 0, bytes.size, keyframes = listOf(position))
            position += bytes.size
            now += 1_000
        }
        // Segments of twenty packets; the newest two fit.
        assertEquals(60L * PACKET, spill.oldest)
    }

    @Test fun aGapStartsAfresh() {
        val spill = spill()
        val first = chunk(0, 10)
        spill.write(0, first, 0, first.size, keyframes = listOf(0))
        // The memory ran over: the next bytes held start later.
        val later = chunk(5_000L * PACKET, 10)
        spill.write(5_000L * PACKET, later, 0, later.size)

        assertEquals(5_000L * PACKET, spill.oldest)
        assertEquals(-1, spill.read(0, ByteArray(PACKET)))
    }

    @Test fun closingLetsEverythingGo() {
        val spill = spill()
        val bytes = chunk(0, 10)
        spill.write(0, bytes, 0, bytes.size, keyframes = listOf(0))
        spill.close()
        assertFalse(folder.exists())
        assertEquals(-1, spill.read(0, ByteArray(PACKET)))
    }

    private companion object {
        const val PACKET = TsScanner.PACKET
    }
}
