// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Carrying a stream's clock straight on across a jump (#138), as the hub does before anything keeps the stream. */
class TsClockSmootherTest {
    /** A smoother whose packets come at [nowMs], and what it hands on. */
    private class Smoothed {
        var nowMs = 0L
        val smoother = TsClockSmoother { nowMs * 1_000_000 }
        private val out = ByteArrayOutputStream()
        val bytes: ByteArray get() = out.toByteArray()

        fun feed(stream: ByteArray, chunk: Int = stream.size) {
            var at = 0
            while (at < stream.size) {
                val count = minOf(chunk, stream.size - at)
                // The smoother rewrites in place: a copy, as a connection's buffer would be.
                val copy = stream.copyOfRange(at, at + count)
                smoother.feed(copy, count) { bytes, offset, length -> out.write(bytes, offset, length) }
                at += count
            }
        }
    }

    /**
     * [count] frames of a program from [fromMs] on its clock, a frame each 40 ms: the clock, then a
     * video PES whose DTS runs 800 ms ahead of it (and its PTS 80 ms after that), and a sound PES 300 ms ahead.
     */
    private fun frames(fromMs: Long, count: Int, marked: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        repeat(count) { index ->
            val ms = fromMs + index * FRAME_MS
            out.write(Ts.pcr(Ts.VIDEO_PID, ms * 90, marked = marked && index == 0))
            out.write(pes(Ts.VIDEO_PID, VIDEO, pts = (ms + 880) * 90, dts = (ms + 800) * 90))
            out.write(pes(Ts.AUDIO_PID, AUDIO, pts = (ms + 300) * 90))
        }
        return out.toByteArray()
    }

    @Test fun aSteadyStreamGoesThroughAsItCame() {
        val smoothed = Smoothed()
        val stream = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264)) + frames(fromMs = 10_000, count = 50)
        smoothed.feedTimed(stream)

        assertArrayEquals(stream, smoothed.bytes)
        assertEquals(0, smoothed.smoother.jumps)
        assertNull(smoothed.smoother.lastJump)
    }

    @Test fun aJumpBackIsCarriedOnInTheClockAndEveryTimestamp() {
        val smoothed = Smoothed()
        smoothed.feedTimed(frames(fromMs = 3_600_000, count = 50))
        // The provider's side started over: its clock from 0.4 s.
        smoothed.feedTimed(frames(fromMs = 400, count = 20))

        val packets = packetsOf(smoothed.bytes)
        assertEquals(1, smoothed.smoother.jumps)
        assertEquals(TsScanner.ClockJump(ms = 400 - (3_600_000 + 49 * FRAME_MS), marked = false), smoothed.smoother.lastJump)
        // The clock, the video's DTS and PTS and the sound's PTS go on a frame at a time, straight through.
        assertSteps(packets.mapNotNull(::pcrOf), FRAME_MS * 90)
        assertSteps(packets.filter { pidOf(it) == Ts.VIDEO_PID }.mapNotNull { stampsOf(it)?.second }, FRAME_MS * 90)
        assertSteps(packets.filter { pidOf(it) == Ts.VIDEO_PID }.mapNotNull { stampsOf(it)?.first }, FRAME_MS * 90)
        assertSteps(packets.filter { pidOf(it) == Ts.AUDIO_PID }.mapNotNull { stampsOf(it)?.first }, FRAME_MS * 90)
        // And they keep their places relative to the clock.
        val last = packets.takeLast(3)
        assertEquals(800L * 90, stampsOf(last[1])!!.second - pcrOf(last[0])!!)
        assertEquals(300L * 90, stampsOf(last[2])!!.first - pcrOf(last[0])!!)
    }

    @Test fun aJumpForwardIsCarriedOnToo() {
        val smoothed = Smoothed()
        smoothed.feedTimed(frames(fromMs = 50_000, count = 30))
        // Twelve seconds on, in the time one frame takes.
        smoothed.feedTimed(frames(fromMs = 50_000 + 30 * FRAME_MS + 12_000, count = 30))

        val packets = packetsOf(smoothed.bytes)
        assertEquals(1, smoothed.smoother.jumps)
        assertEquals(12_000L + FRAME_MS, smoothed.smoother.lastJump!!.ms)
        assertSteps(packets.mapNotNull(::pcrOf), FRAME_MS * 90)
        assertSteps(packets.filter { pidOf(it) == Ts.VIDEO_PID }.mapNotNull { stampsOf(it)?.second }, FRAME_MS * 90)
    }

    @Test fun aGapInWhatCameIsNoJump() {
        val smoothed = Smoothed()
        smoothed.feedTimed(frames(fromMs = 50_000, count = 30))
        // Nothing for 15 s, and the clock 15 s on.
        smoothed.nowMs += 15_000
        val after = frames(fromMs = 50_000 + 30 * FRAME_MS + 15_000, count = 10)
        smoothed.feedTimed(after)

        assertEquals(0, smoothed.smoother.jumps)
        assertArrayEquals(frames(fromMs = 50_000, count = 30) + after, smoothed.bytes)
    }

    @Test fun eachPidCrossesTheJumpWhenItsOwnTimestampsDo() {
        val smoothed = Smoothed()
        smoothed.feedTimed(frames(fromMs = 3_600_000, count = 50))
        val lastMs = 3_600_000 + 49 * FRAME_MS
        // The clock jumps first; the video's last PES from before it comes after, then the new ones.
        val late = pes(Ts.VIDEO_PID, VIDEO, pts = (lastMs + FRAME_MS + 880) * 90, dts = (lastMs + FRAME_MS + 800) * 90)
        smoothed.feed(Ts.pcr(Ts.VIDEO_PID, 400L * 90) + late)
        smoothed.nowMs += FRAME_MS
        smoothed.feedTimed(frames(fromMs = 400 + FRAME_MS, count = 10))

        val video = packetsOf(smoothed.bytes).filter { pidOf(it) == Ts.VIDEO_PID }.mapNotNull { stampsOf(it)?.second }
        // The late one is as it came, and the new ones carry on from it.
        assertEquals((lastMs + FRAME_MS + 800) * 90, video[50])
        assertSteps(video, FRAME_MS * 90)
    }

    @Test fun oneBadClockAndThenTheClockAsItWasMovesNothing() {
        val smoothed = Smoothed()
        smoothed.feedTimed(frames(fromMs = 10_000, count = 20))
        // A clock damaged on its way, once.
        smoothed.feed(Ts.pcr(Ts.VIDEO_PID, 5_000_000L * 90))
        smoothed.feedTimed(frames(fromMs = 10_000 + 20 * FRAME_MS, count = 20))
        // Then the provider's side starts over.
        smoothed.feedTimed(frames(fromMs = 400, count = 20))

        val packets = packetsOf(smoothed.bytes)
        // The frames after the bad clock came through as they were...
        val frames = frames(fromMs = 10_000, count = 20) + Ts.pcr(Ts.VIDEO_PID, 0) + frames(fromMs = 10_000 + 20 * FRAME_MS, count = 20)
        assertArrayEquals(frames.copyOfRange(61 * TsClockSmoother.PACKET, frames.size), smoothed.bytes.copyOfRange(61 * TsClockSmoother.PACKET, frames.size))
        // ...and the start over is carried on in every PID, straight through.
        assertSteps(packets.filter { pidOf(it) == Ts.VIDEO_PID }.mapNotNull { stampsOf(it)?.second }, FRAME_MS * 90)
        assertSteps(packets.filter { pidOf(it) == Ts.AUDIO_PID }.mapNotNull { stampsOf(it)?.first }, FRAME_MS * 90)
    }

    @Test fun aPidWithAPesNowAndThenCrossesTheJumpToo() {
        val smoothed = Smoothed()
        // Subtitles every two seconds, 500 ms ahead of the clock.
        fun withSubtitles(fromMs: Long, count: Int) = ByteArrayOutputStream().apply {
            repeat(count / 50) { second ->
                write(frames(fromMs + second * 50 * FRAME_MS, 50))
                write(pes(SUBTITLE_PID, PRIVATE, pts = (fromMs + (second + 1) * 50 * FRAME_MS + 500) * 90))
            }
        }.toByteArray()
        smoothed.feedTimed(withSubtitles(fromMs = 3_600_000, count = 200))
        smoothed.feedTimed(withSubtitles(fromMs = 400, count = 200))

        val subtitles = packetsOf(smoothed.bytes).filter { pidOf(it) == SUBTITLE_PID }.mapNotNull { stampsOf(it)?.first }
        assertSteps(subtitles, 50 * FRAME_MS * 90)
    }

    @Test fun aMarkedJumpIsCarriedOnAndLosesItsMark() {
        val smoothed = Smoothed()
        smoothed.feedTimed(frames(fromMs = 7_200_000, count = 20))
        smoothed.feedTimed(frames(fromMs = 1_000, count = 5, marked = true))

        val clocks = packetsOf(smoothed.bytes).filter { pcrOf(it) != null }
        assertTrue(smoothed.smoother.lastJump!!.marked)
        assertFalse(clocks.any { it[5].toInt() and 0x80 != 0 })
        assertSteps(clocks.mapNotNull(::pcrOf), FRAME_MS * 90)
    }

    @Test fun chunksThatDontLineUpComeOutWholeAndInOrder() {
        val stream = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264)) + frames(fromMs = 3_600_000, count = 20) + frames(fromMs = 400, count = 20)
        val whole = Smoothed().apply { feed(stream) }
        val odd = Smoothed()
        var at = 0
        for (size in generateSequence(1) { it * 7 % 401 + 1 }) {
            if (at >= stream.size) break
            val count = minOf(size, stream.size - at)
            odd.feed(stream.copyOfRange(at, at + count))
            at += count
        }

        assertArrayEquals(whole.bytes, odd.bytes)
        assertEquals(stream.size, odd.bytes.size)
        assertEquals(1, odd.smoother.jumps)
    }

    @Test fun aReconnectToAStreamElsewhereIsCarriedOn() {
        val smoothed = Smoothed()
        smoothed.feedTimed(frames(fromMs = 10_000, count = 20))
        // Half a packet, then the connection starts again on a stream whose clock is an hour on.
        smoothed.feed(Ts.pcr(Ts.VIDEO_PID, 0).copyOf(90))
        smoothed.smoother.resync()
        smoothed.feedTimed(frames(fromMs = 3_610_000, count = 10))

        // The half packet never went on: every packet that did is whole.
        val packets = packetsOf(smoothed.bytes)
        assertEquals((20 + 10) * 3 * TsClockSmoother.PACKET, smoothed.bytes.size)
        assertEquals(1, smoothed.smoother.jumps)
        assertSteps(packets.mapNotNull(::pcrOf), FRAME_MS * 90)
    }

    @Test fun theClockWrapsWithoutJumping() {
        val smoothed = Smoothed()
        // 2^33 ticks of 90 kHz: about 26.5 hours, then it starts from 0.
        val stream = frames(fromMs = (1L shl 33) / 90 - 400, count = 30)
        smoothed.feedTimed(stream)

        assertEquals(0, smoothed.smoother.jumps)
        assertArrayEquals(stream, smoothed.bytes)
    }

    @Test fun whatIsntMpegTsGoesThroughAsItCame() {
        // An MP3 stream's frames, sync bytes and all.
        val mp3 = ByteArray(5_000) { if (it % 418 == 0) 0xFF.toByte() else (it * 31 % 251).toByte() }
        val smoothed = Smoothed().apply { feed(mp3, chunk = 1_024) }
        assertArrayEquals(mp3, smoothed.bytes)
    }

    @Test fun timestampsReadAndWriteAroundTheirMarkerBits() {
        val field = byteArrayOf(0x31, 0, 1, 0, 1)
        for (value in listOf(0L, 1L, 90_000L, (1L shl 32) + 12_345, (1L shl 33) - 1)) {
            TsClockSmoother.write(field, 0, value)
            assertEquals(value, TsClockSmoother.read(field, 0))
            // The '0011' prefix and the three marker bits stay.
            assertEquals(0x31, field[0].toInt() and 0xF1)
            assertEquals(1, field[2].toInt() and 1)
            assertEquals(1, field[4].toInt() and 1)
        }
        assertEquals(-200L, TsClockSmoother.wrapped(TsClockSmoother.wrap(-200)))
        assertEquals(500L, TsClockSmoother.wrapped((1L shl 33) + 500))
    }

    // ---- Helpers ----------------------------------------------------------------------------------

    /** Feeds [stream] a frame's three packets at a time, the time moving on a frame each. */
    private fun Smoothed.feedTimed(stream: ByteArray) {
        val frame = 3 * TsClockSmoother.PACKET
        var at = 0
        while (at < stream.size) {
            val count = minOf(frame, stream.size - at)
            feed(stream.copyOfRange(at, at + count))
            if (count == frame) nowMs += FRAME_MS
            at += count
        }
    }

    /** Each step from one value to the next is [step], give or take a tick. */
    private fun assertSteps(values: List<Long>, step: Long) {
        assertTrue("Too few values: $values", values.size > 2)
        values.zipWithNext { a, b ->
            val moved = TsClockSmoother.wrapped(b - a)
            assertTrue("Moved $moved, not $step, from $a to $b in $values", kotlin.math.abs(moved - step) <= 1)
        }
    }

    private fun packetsOf(bytes: ByteArray): List<ByteArray> = (0 until bytes.size / TsClockSmoother.PACKET).map {
        bytes.copyOfRange(it * TsClockSmoother.PACKET, (it + 1) * TsClockSmoother.PACKET)
    }

    private fun pidOf(packet: ByteArray): Int = ((packet[1].toInt() and 0x1F) shl 8) or (packet[2].toInt() and 0xFF)

    /** The clock's base in a packet with one. */
    private fun pcrOf(packet: ByteArray): Long? {
        if ((packet[3].toInt() shr 5) and 1 == 0 || packet[4].toInt() and 0xFF < 7 || packet[5].toInt() and 0x10 == 0) return null
        return ((packet[6].toLong() and 0xFF) shl 25) or ((packet[7].toLong() and 0xFF) shl 17) or
            ((packet[8].toLong() and 0xFF) shl 9) or ((packet[9].toLong() and 0xFF) shl 1) or ((packet[10].toLong() and 0xFF) shr 7)
    }

    /** A PES start's PTS and DTS (the PTS again without one). */
    private fun stampsOf(packet: ByteArray): Pair<Long, Long>? {
        if (packet[1].toInt() and 0x40 == 0 || packet[4].toInt() != 0 || packet[5].toInt() != 0 || packet[6].toInt() != 1) return null
        val pts = TsClockSmoother.read(packet, 4 + 9)
        val dts = if ((packet[4 + 7].toInt() shr 6) and 0x03 == 0x03) TsClockSmoother.read(packet, 4 + 14) else pts
        return pts to dts
    }

    /** A PES start on [pid] for [stream], with its PTS and maybe DTS, in 90 kHz ticks. */
    private fun pes(pid: Int, stream: Int, pts: Long, dts: Long? = null): ByteArray {
        val header = ByteArray(if (dts != null) 19 else 14)
        header[2] = 1
        header[3] = stream.toByte()
        header[6] = 0x80.toByte()
        header[7] = (if (dts != null) 0xC0 else 0x80).toByte()
        header[8] = (if (dts != null) 10 else 5).toByte()
        header[9] = (if (dts != null) 0x31 else 0x21).toByte()
        header[11] = 1
        header[13] = 1
        TsClockSmoother.write(header, 9, TsClockSmoother.wrap(pts))
        if (dts != null) {
            header[14] = 0x11
            header[16] = 1
            header[18] = 1
            TsClockSmoother.write(header, 14, TsClockSmoother.wrap(dts))
        }
        return Ts.packet(pid, header)
    }

    private companion object {
        const val FRAME_MS = 40L
        const val VIDEO = 0xE0
        const val AUDIO = 0xC0

        /** DVB subtitles' stream, private 1, on a PID of their own. */
        const val PRIVATE = 0xBD
        const val SUBTITLE_PID = 0x103
    }
}
