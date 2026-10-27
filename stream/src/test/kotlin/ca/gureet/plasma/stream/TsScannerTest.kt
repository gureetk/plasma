// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding where an engine can start in a live MPEG-TS stream (brief 5.5). */
class TsScannerTest {
    @Test fun findsH264KeyframesByTheirParameterSets() {
        val stream = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264), Ts.h264(key = true), Ts.h264(key = false), Ts.audio(), Ts.h264(key = true))
        val found = mutableListOf<Long>()

        TsScanner().feed(stream, 0, stream.size) { found += it }

        assertEquals(listOf(2L * 188, 5L * 188), found)
    }

    @Test fun findsHevcAndMpeg2Keyframes() {
        val hevc = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_HEVC), Ts.hevc(key = false), Ts.hevc(key = true))
        val found = mutableListOf<Long>()
        TsScanner().feed(hevc, 0, hevc.size) { found += it }
        assertEquals(listOf(3L * 188), found)

        val mpeg2 = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_MPEG2), Ts.mpeg2(key = true), Ts.mpeg2(key = false))
        found.clear()
        TsScanner().feed(mpeg2, 0, mpeg2.size) { found += it }
        assertEquals(listOf(2L * 188), found)
    }

    @Test fun takesTheBroadcastersMark() {
        val stream = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264), Ts.h264(key = false, randomAccess = true))
        val found = mutableListOf<Long>()
        TsScanner().feed(stream, 0, stream.size) { found += it }
        assertEquals(listOf(2L * 188), found)
    }

    @Test fun readsChunksThatDontLineUpAndSkipsWhatIsntAPacket() {
        // Two stray bytes before the first packet, then chunks of every size.
        val stream = byteArrayOf(0x12, 0x34) + Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264), Ts.h264(key = false), Ts.h264(key = true))
        val found = mutableListOf<Long>()
        val scanner = TsScanner()
        var at = 0
        var size = 1
        while (at < stream.size) {
            val count = minOf(size, stream.size - at)
            scanner.feed(stream, at, count) { found += it }
            at += count
            size = size % 97 + 1
        }

        assertEquals(listOf(2L + 3 * 188), found)
        assertArrayEquals(Ts.pat(), scanner.pat)
        assertArrayEquals(Ts.pmt(TsScanner.TYPE_H264), scanner.pmt)
    }

    @Test fun radioHasNoVideo() {
        val scanner = TsScanner()
        assertTrue("Until the PMT says", scanner.hasVideo)
        val stream = Ts.stream(Ts.pat(), Ts.pmt(type = null), Ts.audio())
        val found = mutableListOf<Long>()
        scanner.feed(stream, 0, stream.size) { found += it }
        assertFalse(scanner.hasVideo)
        assertEquals(emptyList<Long>(), found)
    }

    @Test fun nothingIsFoundBeforeThePmt() {
        val scanner = TsScanner()
        val stream = Ts.stream(Ts.h264(key = true))
        val found = mutableListOf<Long>()
        scanner.feed(stream, 0, stream.size) { found += it }
        assertEquals(emptyList<Long>(), found)
        assertNull(scanner.pat)
    }
}

/** Made-up MPEG-TS packets: a PAT, a PMT, and video and audio PES starts. */
internal object Ts {
    const val PMT_PID = 0x100
    const val VIDEO_PID = 0x101
    const val AUDIO_PID = 0x102

    fun stream(vararg packets: ByteArray): ByteArray = packets.fold(ByteArray(0)) { all, packet -> all + packet }

    /** A packet on [pid] carrying [payload], padded with stuffing; [randomAccess] marks a keyframe. */
    fun packet(pid: Int, payload: ByteArray, start: Boolean = true, randomAccess: Boolean = false): ByteArray {
        val packet = ByteArray(188) { 0xFF.toByte() }
        packet[0] = 0x47
        packet[1] = (((if (start) 0x40 else 0) or (pid shr 8)) and 0xFF).toByte()
        packet[2] = (pid and 0xFF).toByte()
        if (randomAccess) {
            // An adaptation field of two bytes: its flags, and one stuffing byte.
            packet[3] = 0x30
            packet[4] = 2
            packet[5] = 0x40
            payload.copyInto(packet, 7)
        } else {
            packet[3] = 0x10
            payload.copyInto(packet, 4)
        }
        return packet
    }

    fun pat(): ByteArray = packet(
        0,
        bytes(0x00, 0x00, 0xB0, 0x0D, 0x00, 0x01, 0xC1, 0x00, 0x00, 0x00, 0x01, 0xE0 or (PMT_PID shr 8), PMT_PID and 0xFF, 0x11, 0x22, 0x33, 0x44),
    )

    /** A PMT with audio, and video of [type] when it isn't null. */
    fun pmt(type: Int?): ByteArray {
        val streams = buildList {
            add(listOf(0x0F, 0xE0 or (AUDIO_PID shr 8), AUDIO_PID and 0xFF, 0xF0, 0x00))
            if (type != null) add(listOf(type, 0xE0 or (VIDEO_PID shr 8), VIDEO_PID and 0xFF, 0xF0, 0x00))
        }.flatten()
        val length = 9 + streams.size + 4
        val section = listOf(0x00, 0x02, 0xB0, length, 0x00, 0x01, 0xC1, 0x00, 0x00, 0xE0 or (VIDEO_PID shr 8), VIDEO_PID and 0xFF, 0xF0, 0x00) +
            streams + listOf(0x11, 0x22, 0x33, 0x44)
        return packet(PMT_PID, bytes(*section.toIntArray()))
    }

    /** The start of a video PES: its header, then [es]. */
    private fun pes(es: IntArray): ByteArray = bytes(0x00, 0x00, 0x01, 0xE0, 0x00, 0x00, 0x80, 0x80, 0x05, 0x21, 0x00, 0x01, 0x00, 0x01, *es)

    fun h264(key: Boolean, randomAccess: Boolean = false): ByteArray {
        // An access unit delimiter, then a sequence parameter set (a keyframe) or a plain slice.
        val nal = if (key) intArrayOf(0x00, 0x00, 0x00, 0x01, 0x09, 0xF0, 0x00, 0x00, 0x00, 0x01, 0x67, 0x64) else intArrayOf(0x00, 0x00, 0x00, 0x01, 0x09, 0xF0, 0x00, 0x00, 0x00, 0x01, 0x41, 0x9A)
        return packet(VIDEO_PID, pes(nal), randomAccess = randomAccess)
    }

    fun hevc(key: Boolean): ByteArray {
        // An access unit delimiter (35), then a VPS (32) or a plain slice (1).
        val nal = if (key) intArrayOf(0x00, 0x00, 0x01, 0x46, 0x01, 0x50, 0x00, 0x00, 0x01, 0x40, 0x01) else intArrayOf(0x00, 0x00, 0x01, 0x46, 0x01, 0x50, 0x00, 0x00, 0x01, 0x02, 0x01)
        return packet(VIDEO_PID, pes(nal))
    }

    fun mpeg2(key: Boolean): ByteArray {
        // A sequence header (B3), or straight to a picture (00).
        val es = if (key) intArrayOf(0x00, 0x00, 0x01, 0xB3, 0x2D, 0x02) else intArrayOf(0x00, 0x00, 0x01, 0x00, 0x00, 0x0F)
        return packet(VIDEO_PID, pes(es))
    }

    fun audio(): ByteArray = packet(AUDIO_PID, bytes(0x00, 0x00, 0x01, 0xC0, 0x00, 0x00, 0x80, 0x80, 0x05, 0x21, 0x00, 0x01, 0x00, 0x01, 0xFF, 0xF1))

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
