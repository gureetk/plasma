// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** Local catch-up on storage (brief 5.5): chunks that play on their own, read back from any moment. */
class CatchUpRingTest {
    private val folder: File = Files.createTempDirectory("catchup").toFile()
    private var now = 1_000_000L
    private val tables = ByteArray(2 * PACKET) { 0x40 }

    @After fun tidy() {
        folder.deleteRecursively()
    }

    /** One second of made-up stream, [packets] packets numbered from [from], a keyframe first. */
    private fun second(from: Int, packets: Int = 10): ByteArray = ByteArray(packets * PACKET) { ((from + it / PACKET) % 200).toByte() }

    /** Writes [seconds] seconds, a keyframe at the start of each, a second apart. */
    private fun CatchUpWriter.writeSeconds(seconds: Int, from: Int = 0): Int {
        var packet = from
        repeat(seconds) {
            val bytes = second(packet)
            write(bytes, 0, bytes.size, keyframes = listOf(0), tables = tables)
            packet += 10
            now += 1_000
        }
        return packet
    }

    private fun writer(chunkMs: Long = 10_000) = CatchUpWriter(folder, { now }, chunkMs = chunkMs, indexMs = 2_000)

    private fun CatchUpReader.readAll(): ByteArray {
        val out = ByteArrayOutputStream()
        val into = ByteArray(4 * PACKET)
        use {
            while (true) {
                val count = read(into, 0)
                if (count < 0) break
                out.write(into, 0, count)
            }
        }
        return out.toByteArray()
    }

    @Test fun writesChunksThatBeginWithTheTablesAndAKeyframe() {
        writer().use { writer ->
            // Nothing's kept before the tables are known.
            val early = second(0)
            writer.write(early, 0, early.size, keyframes = listOf(0), tables = null)
            now += 1_000
            writer.writeSeconds(25, from = 10)
        }

        val chunks = CatchUpRing.chunks(folder)
        // A chunk about every 10 s, cut at a keyframe.
        assertEquals(listOf(1_001_000L, 1_011_000L, 1_021_000L), chunks.map { it.start })
        val first = chunks.first()
        assertArrayEquals(tables, first.file.readBytes().copyOf(tables.size))
        assertArrayEquals(second(10), first.file.readBytes().copyOfRange(tables.size, tables.size + 10 * PACKET))
        val index = first.read()
        assertEquals(tables.size, index.tables)
        // Indexed every 2 s.
        assertEquals(listOf(1_001_000L, 1_003_000L, 1_005_000L, 1_007_000L, 1_009_000L), index.times.toList())
        assertEquals(tables.size.toLong(), index.offsets.first())
        assertEquals(1_001_000L..1_025_000L, CatchUpRing.window(folder))
    }

    @Test fun readsFromTheKeyframeBeforeAMomentToTheEnd() {
        writer().use { it.writeSeconds(25) }

        // From 4.5 s in: the keyframe at 4 s, after a copy of the tables; until 13.5 s, the keyframe after it.
        val read = CatchUpRing.open(folder, from = 1_004_500, to = 1_013_500)!!.readAll()

        val expected = ByteArrayOutputStream().apply {
            write(tables)
            for (s in 4 until 10) write(second(s * 10))
            // The next chunk starts with its own tables.
            write(tables)
            for (s in 10 until 14) write(second(s * 10))
        }.toByteArray()
        assertEquals(expected.size, read.size)
        assertArrayEquals(expected, read)
    }

    @Test fun aGapEndsAChunkAndCounts() {
        writer().use { writer ->
            writer.writeSeconds(5)
            writer.gap()
            // Five minutes without the stream.
            now += 300_000
            writer.writeSeconds(5, from = 50)
        }

        val chunks = CatchUpRing.chunks(folder)
        assertEquals(2, chunks.size)
        assertEquals(1_305_000L, chunks[1].start)
        // From 4 s (the last indexed moment) plus an index's worth, to the next chunk.
        assertEquals(1_305_000L - 1_006_000L, CatchUpRing.missingMs(folder, 1_000_000, 1_400_000))
        // What's read skips it.
        val read = CatchUpRing.open(folder, from = 1_000_000, to = 1_400_000)!!.readAll()
        assertEquals(2 * tables.size + 100 * PACKET, read.size)
    }

    @Test fun letsTheOldestGoByAgeAndBySize() {
        writer().use { it.writeSeconds(35) }
        val sizes = CatchUpRing.chunks(folder).map { it.size }

        // Keep 20 s at 1 035 s: the chunk that ended by 1 015 s goes.
        CatchUpRing.trim(folder, keepMs = 20_000, now = 1_035_000)
        assertEquals(listOf(1_010_000L, 1_020_000L, 1_030_000L), CatchUpRing.chunks(folder).map { it.start })

        // Room for two chunks: the oldest goes first, and the newest, being written, stays.
        CatchUpRing.trimTo(listOf(folder), maxBytes = sizes[1] + sizes[3])
        assertEquals(listOf(1_020_000L, 1_030_000L), CatchUpRing.chunks(folder).map { it.start })
        assertTrue(CatchUpRing.size(folder) <= sizes[1] + sizes[3])
    }

    @Test fun nothingBeforeOrAfterWhatsHeld() {
        assertNull(CatchUpRing.open(folder, 0, 1_000))
        writer().use { it.writeSeconds(5) }
        assertNull(CatchUpRing.open(folder, from = 900_000, to = 1_000_000))
    }

    @Test fun aProgrammeStillOnIsFollowedAsItsWritten() {
        val writer = writer(chunkMs = 60_000)
        writer.writeSeconds(3)
        val reader = CatchUpRing.open(folder, from = 1_000_000, to = 1_010_000) { now }!!
        val into = ByteArray(64 * PACKET)
        val first = reader.read(into, 0)
        assertEquals(tables.size + 30 * PACKET, first)
        // Nothing more yet.
        assertEquals(0, reader.read(into, 0))
        writer.writeSeconds(2, from = 30)
        assertEquals(20 * PACKET, reader.read(into, 0))
        // Past its end, and the index past it: done.
        writer.writeSeconds(8, from = 50)
        var rest = 0
        while (true) {
            val count = reader.read(into, 0)
            if (count <= 0) break
            rest += count
        }
        // From 5 s to the keyframe indexed after 10 s, at 12 s.
        assertEquals(70 * PACKET, rest)
        writer.close()
        reader.close()
    }

    @Test fun withTheScannerEachChunkBeginsWithTheStreamsOwnTables() {
        val scanner = TsScanner()
        val writer = writer(chunkMs = 2_000)
        var fed = 0L
        /** Feeds [bytes] as the service does: keyframes found in them, and the tables once known. */
        fun feed(bytes: ByteArray) {
            val keyframes = ArrayList<Int>()
            val base = fed
            scanner.feed(bytes, 0, bytes.size) { position -> (position - base).takeIf { it in 0 until bytes.size }?.let { keyframes += it.toInt() } }
            fed += bytes.size
            writer.write(bytes, 0, bytes.size, keyframes, scanner.tables())
            now += 1_000
        }
        // Before the tables, nothing's kept; then a second at a time, a keyframe in each.
        feed(Ts.stream(Ts.h264(key = true), Ts.audio()))
        feed(Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264), Ts.h264(key = true), Ts.audio()))
        feed(Ts.stream(Ts.audio(), Ts.h264(key = true), Ts.h264(key = false)))
        feed(Ts.stream(Ts.audio(), Ts.h264(key = true), Ts.audio()))
        writer.close()

        val chunks = CatchUpRing.chunks(folder)
        assertEquals(listOf(1_001_000L, 1_003_000L), chunks.map { it.start })
        val tables = scanner.tables()!!
        for (chunk in chunks) {
            val bytes = chunk.file.readBytes()
            assertArrayEquals(tables, bytes.copyOf(tables.size))
            // A keyframe straight after.
            assertArrayEquals(Ts.h264(key = true), bytes.copyOfRange(tables.size, tables.size + PACKET))
            assertEquals(tables.size, chunk.read().tables)
        }
    }

    private companion object {
        const val PACKET = 188
    }
}
