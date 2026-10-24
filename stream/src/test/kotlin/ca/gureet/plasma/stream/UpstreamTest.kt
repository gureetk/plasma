// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

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
}
