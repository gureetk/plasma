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

    // ---- The stream's clock, for Show Freeze Watch (the owner's ask of 2 November) ----------------

    /** A scanner whose packets come at [now], in ms, and a feed of [packets] each [everyMs] apart, the clock [stepMs] on each. */
    private class Clocked {
        var nowMs = 0L
        val scanner = TsScanner { nowMs * 1_000_000 }
        var pcrMs = 0L

        fun feed(packets: Int, everyMs: Long = 40, stepMs: Long = 40, marked: Boolean = false) {
            repeat(packets) { index ->
                val packet = Ts.pcr(Ts.VIDEO_PID, pcrMs * 90, marked = marked && index == 0)
                scanner.feed(packet, 0, packet.size)
                nowMs += everyMs
                pcrMs += stepMs
            }
        }
    }

    @Test fun aSteadyClockNeverJumps() {
        val clocked = Clocked()
        clocked.scanner.feed(Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264)), 0, 2 * 188)
        clocked.feed(packets = 100)
        // Sent in bursts: a second's worth at once, then nothing for a second.
        repeat(5) {
            clocked.feed(packets = 25, everyMs = 0)
            clocked.nowMs += 1_000
        }
        // Nothing for 15 s, and the clock 15 s on: a gap, not a jump.
        clocked.nowMs += 15_000
        clocked.pcrMs += 15_000
        clocked.feed(packets = 10)

        assertEquals(0, clocked.scanner.clockJumps)
        assertNull(clocked.scanner.lastJump)
    }

    @Test fun aClockThatGoesBackOrRacesAheadHasJumped() {
        val clocked = Clocked()
        clocked.feed(packets = 50)

        // The provider's stream started again: its clock from near 0.
        clocked.pcrMs = 400
        clocked.feed(packets = 10)
        assertEquals(1, clocked.scanner.clockJumps)
        assertEquals(TsScanner.ClockJump(ms = 400 - 49 * 40L, marked = false), clocked.scanner.lastJump)

        // Twelve seconds on, in the time one packet takes, and marked by the stream.
        clocked.pcrMs += 12_000
        clocked.feed(packets = 10, marked = true)
        assertEquals(2, clocked.scanner.clockJumps)
        assertEquals(TsScanner.ClockJump(ms = 12_040, marked = true), clocked.scanner.lastJump)
    }

    @Test fun twoStreamsJoinedByAReconnectCount() {
        val clocked = Clocked()
        clocked.feed(packets = 20)
        // Half a packet, then the connection starts again on a stream whose clock is elsewhere.
        val half = Ts.pcr(Ts.VIDEO_PID, 0)
        clocked.scanner.feed(half, 0, 90)
        clocked.scanner.resync()
        clocked.pcrMs = 3_600_000
        clocked.feed(packets = 5)

        assertEquals(1, clocked.scanner.clockJumps)
        assertTrue(clocked.scanner.lastJump!!.ms > 3_000_000)
    }

    @Test fun theClockWrapsWithoutJumping() {
        val clocked = Clocked()
        // 2^33 ticks of 90 kHz: about 26.5 hours, then it starts from 0.
        clocked.pcrMs = (1L shl 33) / 90 - 200
        clocked.feed(packets = 20)
        assertEquals(0, clocked.scanner.clockJumps)
    }

    @Test fun nothingIsFoundBeforeThePmt() {
        val scanner = TsScanner()
        val stream = Ts.stream(Ts.h264(key = true))
        val found = mutableListOf<Long>()
        scanner.feed(stream, 0, stream.size) { found += it }
        assertEquals(emptyList<Long>(), found)
        assertNull(scanner.pat)
    }

    // ---- Scrambled streams (brief 6.9) ------------------------------------------------------------

    /** A scanner whose clock is [nowMs], after a PAT and a PMT with a picture or without, for [scrambledForMs]. */
    private class Watched(scrambledForMs: Long = TsScanner.SCRAMBLED_FOR_MS, picture: Boolean = true) {
        var nowMs = 0L
        val scanner = TsScanner(scrambledForMs) { nowMs * 1_000_000 }

        init {
            feed(Ts.pat(), Ts.pmt(if (picture) TsScanner.TYPE_H264 else null))
        }

        fun feed(vararg packets: ByteArray) = packets.forEach { scanner.feed(it, 0, it.size) }

        /** [count] packets of the picture, scrambled or not, 1 ms apart. */
        fun video(count: Int, scrambled: Boolean = true) = repeat(count) {
            feed(Ts.h264(key = false).let { if (scrambled) Ts.scrambled(it) else it })
            nowMs++
        }
    }

    @Test fun aStreamScrambledFor5sIsScrambled() {
        val watched = Watched()
        watched.video(TsScanner.SCRAMBLE_WINDOW)
        assertFalse("Not until it has stayed so", watched.scanner.scrambled)
        watched.nowMs += 4_000
        assertFalse(watched.scanner.scrambled)
        watched.nowMs += 1_000
        assertTrue(watched.scanner.scrambled)
    }

    @Test fun aDescramblerThatStartsLateLeavesItClear() {
        val watched = Watched()
        watched.video(TsScanner.SCRAMBLE_WINDOW)
        watched.nowMs += 3_000
        // The keys came: the picture comes clear.
        watched.video(200, scrambled = false)
        watched.nowMs += 10_000
        assertFalse(watched.scanner.scrambled)
    }

    @Test fun aFewScrambledPacketsDontMakeItSo() {
        val watched = Watched(scrambledForMs = 0)
        repeat(200) {
            watched.video(10, scrambled = false)
            watched.video(1)
        }
        assertFalse(watched.scanner.scrambled)
    }

    @Test fun thePicturesPacketsAreTheOnesThatCount() {
        val watched = Watched(scrambledForMs = 0)
        repeat(TsScanner.SCRAMBLE_WINDOW) {
            watched.video(1)
            // Sound and teletext in the clear, as some channels send them, and the tables, the
            // service information, padding and the clock.
            watched.feed(Ts.audio(), Ts.audio(), Ts.packet(0x0103, ByteArray(8)), Ts.pat(), Ts.pmt(TsScanner.TYPE_H264), Ts.packet(0x0011, ByteArray(8)), Ts.packet(0x1FFF, ByteArray(0)), Ts.pcr(Ts.VIDEO_PID, 0))
        }
        assertTrue(watched.scanner.scrambled)
    }

    @Test fun aRadioChannelGoesByItsSound() {
        val watched = Watched(scrambledForMs = 0, picture = false)
        repeat(TsScanner.SCRAMBLE_WINDOW) {
            // The sound scrambled; the tables, the service information and padding don't count.
            watched.feed(Ts.scrambled(Ts.audio()), Ts.pat(), Ts.pmt(type = null), Ts.packet(0x0011, ByteArray(8)), Ts.packet(0x1FFF, ByteArray(0)))
        }
        assertTrue(watched.scanner.scrambled)
    }

    @Test fun aNewConnectionStartsTheWatchAgain() {
        val watched = Watched(scrambledForMs = 0)
        watched.video(TsScanner.SCRAMBLE_WINDOW)
        assertTrue(watched.scanner.scrambled)

        watched.scanner.resync()
        assertFalse(watched.scanner.scrambled)
        watched.video(TsScanner.SCRAMBLE_WINDOW - 1)
        assertFalse("Not until a window's worth has come again", watched.scanner.scrambled)
        watched.video(1)
        assertTrue(watched.scanner.scrambled)
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

    /** A packet on [pid] with only an adaptation field carrying the program's clock, [ticks] of 90 kHz; [marked] as a discontinuity. */
    fun pcr(pid: Int, ticks: Long, marked: Boolean = false): ByteArray {
        val base = ticks and ((1L shl 33) - 1)
        val packet = ByteArray(188) { 0xFF.toByte() }
        packet[0] = 0x47
        packet[1] = ((pid shr 8) and 0x1F).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = 0x20
        packet[4] = 183.toByte()
        packet[5] = ((if (marked) 0x80 else 0) or 0x10).toByte()
        packet[6] = (base shr 25).toByte()
        packet[7] = (base shr 17).toByte()
        packet[8] = (base shr 9).toByte()
        packet[9] = (base shr 1).toByte()
        packet[10] = (((base and 1L) shl 7) or 0x7EL).toByte()
        packet[11] = 0
        return packet
    }

    /** [packet] as a conditional-access system leaves it: marked scrambled with the even key. */
    fun scrambled(packet: ByteArray): ByteArray = packet.copyOf().also { it[3] = ((it[3].toInt() and 0x3F) or 0x80).toByte() }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
