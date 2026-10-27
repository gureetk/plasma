// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

/** A recording read while it's written, as chase play does (brief 5.6). */
class GrowingFileTest {
    private val folder: File = Files.createTempDirectory("chase").toFile()
    private val file = File(folder, "Recording.ts")

    @After fun tidy() {
        folder.deleteRecursively()
    }

    private fun GrowingFileReader.readAll(): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(1_000)
        while (true) {
            val count = read(chunk, 50)
            if (count < 0) return out.toByteArray()
            out.write(chunk, 0, maxOf(count, 0))
        }
    }

    @Test fun whatsThereIsReadAndThenItWaitsForMore() {
        val stream = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264), Ts.h264(key = true), Ts.h264(key = false))
        file.writeBytes(stream)
        val done = AtomicBoolean(false)
        GrowingFileReader(file, 0, done = done::get, pollMs = 5).use { reader ->
            val chunk = ByteArray(10_000)
            assertEquals(stream.size, reader.read(chunk, 50))
            // Nothing more yet: it waits, then says so.
            assertEquals(0, reader.read(chunk, 30))
            // More comes; then it's done, and the end is the end.
            val more = Ts.stream(Ts.h264(key = false))
            file.appendBytes(more)
            assertEquals(more.size, reader.read(chunk, 50))
            done.set(true)
            assertEquals(-1, reader.read(chunk, 50))
        }
    }

    @Test fun partwayInTheTablesComeFirst() {
        val start = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264), Ts.h264(key = false))
        val keyframe = Ts.h264(key = true)
        file.writeBytes(start + keyframe)
        val tables = tablesOf(file)
        assertArrayEquals(Ts.pat() + Ts.pmt(TsScanner.TYPE_H264), tables)

        val read = GrowingFileReader(file, start.size.toLong(), tables, done = { true }, pollMs = 5).use { it.readAll() }
        assertArrayEquals(Ts.pat() + Ts.pmt(TsScanner.TYPE_H264) + keyframe, read)

        // From the start, the file has its own.
        assertArrayEquals(start + keyframe, GrowingFileReader(file, 0, tables, done = { true }, pollMs = 5).use { it.readAll() })
    }

    @Test fun whileItsWrittenOnlyWholePacketsGo() {
        val packets = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264), Ts.h264(key = true))
        file.writeBytes(packets.copyOf(188 + 100))
        val done = AtomicBoolean(false)
        GrowingFileReader(file, 0, done = done::get, pollMs = 5).use { reader ->
            val chunk = ByteArray(10_000)
            assertEquals(188, reader.read(chunk, 50))
            // Part of a packet waits for the rest.
            assertEquals(0, reader.read(chunk, 20))
            file.appendBytes(packets.copyOfRange(288, 376))
            assertEquals(188, reader.read(chunk, 50))
            assertArrayEquals(packets.copyOfRange(188, 376), chunk.copyOf(188))
            // Once it's done, what's left goes, whole or not.
            file.appendBytes(packets.copyOfRange(376, 400))
            done.set(true)
            assertEquals(24, reader.read(chunk, 50))
            assertEquals(-1, reader.read(chunk, 50))
        }
    }

    @Test fun aLongWaitSendsANullPacketSoTheEngineKeepsListening() {
        val start = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264))
        file.writeBytes(start)
        GrowingFileReader(file, 0, done = { false }, pollMs = 5, fillAfterMs = 40).use { reader ->
            val chunk = ByteArray(10_000)
            assertEquals(start.size, reader.read(chunk, 50))
            assertEquals(188, reader.read(chunk, 1_000))
            assertEquals(0x47, chunk[0].toInt())
            // On the null PID, 0x1FFF.
            assertEquals(0x1FFF, ((chunk[1].toInt() and 0x1F) shl 8) or (chunk[2].toInt() and 0xFF))
            // Then the recording carries on.
            val more = Ts.h264(key = true)
            file.appendBytes(more)
            assertEquals(188, reader.read(chunk, 1_000))
            assertArrayEquals(more, chunk.copyOf(188))
        }
    }

    @Test fun aPacketBrokenOffIsPassedOverAndTheNullPacketWaitsForTheNextStart() {
        // The source dropped partway through a packet, and the recording started again.
        val broken = Ts.h264(key = false).copyOf(100)
        val after = Ts.stream(Ts.h264(key = true), Ts.audio())
        file.writeBytes(Ts.pat() + broken + after)
        GrowingFileReader(file, 0, done = { false }, pollMs = 5, fillAfterMs = 40).use { reader ->
            val chunk = ByteArray(10_000)
            // All of it goes, the broken packet too: it's whole packets from the next start on.
            assertEquals(188 + 100 + after.size, reader.read(chunk, 50))
            assertEquals(188, reader.read(chunk, 1_000))
            assertEquals(0x1F, chunk[1].toInt() and 0x1F)
        }
    }

    @Test fun tablesBiggerThanTheBufferGoInParts() {
        val start = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264))
        val keyframe = Ts.h264(key = true)
        file.writeBytes(start + keyframe)
        val tables = Ts.pat() + Ts.pmt(TsScanner.TYPE_H264)
        GrowingFileReader(file, start.size.toLong(), tables, done = { true }, pollMs = 5).use { reader ->
            val chunk = ByteArray(300)
            assertEquals(300, reader.read(chunk, 50))
            assertArrayEquals(tables.copyOf(300), chunk)
            assertEquals(76, reader.read(chunk, 50))
            assertArrayEquals(tables.copyOfRange(300, 376), chunk.copyOf(76))
            assertEquals(188, reader.read(chunk, 50))
            assertArrayEquals(keyframe, chunk.copyOf(188))
        }
    }

    @Test fun aFileWithoutTablesHasNone() {
        file.writeBytes(Ts.stream(Ts.h264(key = true)))
        assertNull(tablesOf(file))
        assertNull(tablesOf(File(folder, "Missing.ts")))
    }
}
