// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import org.junit.Assert.assertNull

/** An upstream's rewind buffer (brief 5.5), fed by hand: no connection is made. */
class UpstreamTest {
    private val folder = Files.createTempDirectory("rewind").toFile()
    private var now = 1_000_000L

    private val upstream = Upstream(
        UpstreamRequest("http://media.home.lan/live/1.ts"),
        OkHttpClient(),
        HubConfig(
            ringBytes = 100 * PACKET,
            clock = { now },
            rewind = { RewindSettings(RewindMode.WhenPaused, keepMs = 3_600_000, maxBytes = Long.MAX_VALUE, folder = folder) },
        ),
    )

    @After fun tidy() {
        upstream.close()
        folder.deleteRecursively()
    }

    /** One second of stream: a keyframe, then nine audio packets. */
    private fun second(): ByteArray = Ts.stream(Ts.h264(key = true), *Array(9) { Ts.audio() })

    private fun keep(bytes: ByteArray) = upstream.keep(bytes, bytes.size)

    @Test fun aPauseWritesWhatMemoryHoldsAsItCameIn() {
        keep(Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264)) + second())
        for (second in 1 until 10) {
            now += 1_000
            keep(second())
        }
        assertTrue("Nothing is written before a pause", folder.listFiles().isNullOrEmpty())
        // Memory holds the last hundred packets: all but the tables.
        val since = upstream.rewindSince
        assertEquals(1_000_000L, since)

        now += 500
        assertTrue(upstream.hold())

        val rewind = Rewind(upstream.spill!!)
        // Segments from each other keyframe, at the time it came in.
        assertEquals(1_000_000L, rewind.since)
        assertEquals(2L * PACKET, rewind.positionAt(1_001_000))
        assertEquals(22L * PACKET, rewind.positionAt(1_003_000))
        assertEquals(82L * PACKET, rewind.positionAt(1_009_500))
        assertEquals(5, folder.listFiles()!!.single().listFiles()!!.size)

        // And on from there, as it comes.
        now += 500
        keep(second())
        assertEquals(112L * PACKET, upstream.spill!!.written)
        assertEquals(102L * PACKET, rewind.positionAt(now))
    }

    @Test fun withoutABufferThereIsNoRewinding() {
        val off = Upstream(UpstreamRequest("http://media.home.lan/live/2.ts"), OkHttpClient(), HubConfig(clock = { now }))
        off.keep(second(), second().size)
        assertEquals(null, off.rewindSince)
        assertEquals(false, off.hold())
        off.close()
    }

    private companion object {
        const val PACKET = TsScanner.PACKET
    }

    // ---- Fast Zap (brief 5.4.8) ------------------------------------------------------------------

    @Test fun anEngineComingToAChannelKeptReadyStartsAtAKeyframeAboutFourSecondsBack() {
        keep(Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264)) + second())
        val keyframes = mutableListOf(2L * PACKET)
        for (second in 1 until 8) {
            now += 1_000
            keyframes += upstream.ring.written
            keep(second())
        }

        // Seven seconds held: the keyframe that came in four seconds before the newest.
        assertEquals(keyframes[3], upstream.joinPoint(4_000))
    }

    @Test fun withLessHeldThanThatItStartsAtTheOldestKeyframe() {
        keep(Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264)) + second())
        now += 1_000
        keep(second())

        assertEquals(2L * PACKET, upstream.joinPoint(4_000))
    }

    @Test fun radioHasNoJoinPoint() {
        keep(Ts.stream(Ts.pat(), Ts.pmt(null), *Array(20) { Ts.audio() }))

        assertNull(upstream.joinPoint(4_000))
    }

    // ---- Opening again in sync --------------------------------------------------------------------

    /**
     * Eight seconds of stream, each a keyframe, the program's clock and eight audio packets, a
     * second apart; the clock goes back to 0 at second [jumpAt], as when the provider's stream
     * starts again. Where each second's keyframe is.
     */
    private fun eightSeconds(jumpAt: Int? = null): List<Long> {
        keep(Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264)))
        val keyframes = mutableListOf<Long>()
        for (second in 0 until 8) {
            if (second > 0) now += 1_000
            val clockMs = if (jumpAt != null && second >= jumpAt) (second - jumpAt) * 500L else 10_000 + second * 500L
            keyframes += upstream.ring.written
            keep(Ts.stream(Ts.h264(key = true), Ts.pcr(Ts.VIDEO_PID, clockMs * 90), *Array(8) { Ts.audio() }))
        }
        return keyframes
    }

    @Test fun openingAgainInSyncStartsAtTheKeyframeThatFarBack() {
        val keyframes = eightSeconds()

        // The newest data came in at 7 s: the keyframe that came in at 5 s, two seconds before.
        assertEquals(keyframes[5], upstream.rejoinPoint(2_000))
    }

    @Test fun openingAgainInSyncNeverStartsBeforeTheClockJumped() {
        val keyframes = eightSeconds(jumpAt = 6)

        // Two seconds back is before the jump, found in the clock's packet just after second 6's
        // keyframe: the first keyframe after it, though it's nearer live.
        assertEquals(keyframes[7], upstream.rejoinPoint(2_000))
    }

    @Test fun openingAgainInSyncAfterAnOlderJumpGoesAsFarBackAsAsked() {
        val keyframes = eightSeconds(jumpAt = 2)

        assertEquals(keyframes[5], upstream.rejoinPoint(2_000))
    }

    @Test fun openingAgainInSyncFromTheRewindBufferNeverStartsBeforeTheClockJumped() {
        val keyframes = eightSeconds(jumpAt = 4)
        now += 500
        assertTrue(upstream.hold())

        // After the first, the buffer's segments start at the keyframes of seconds 2, 4 and 6. Two
        // seconds back from now (7.5 s) is in the one from second 4, which starts before the jump:
        // from 6, then.
        assertEquals(keyframes[6], upstream.rejoinPoint(2_000))
        // Far enough back, it's the first after the jump all the same.
        assertEquals(keyframes[6], upstream.rejoinPoint(6_000))
    }

    @Test fun radioHasNoPointToOpenAgainAt() {
        keep(Ts.stream(Ts.pat(), Ts.pmt(null), *Array(20) { Ts.audio() }))

        assertNull(upstream.rejoinPoint(2_000))
    }
}
