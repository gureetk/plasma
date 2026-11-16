// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

/** mpv's own warnings and errors in Plasma's log (brief 5.17), a few a minute from each of its parts. */
class EngineLinesTest {
    private var now = 0L
    private val noted = mutableListOf<String>()
    private val lines = EngineLines(note = { noted += it }, now = { now }, perMinute = 3)

    @Test fun eachLineSaysWhichPartOfMpvSaidItAndHowBadly() {
        lines.line("cplayer", "warn", "Invalid video timestamp: 1210.000 -> 1188.000")
        lines.line("ffmpeg/demuxer", "error", "mpegts: Packet corrupt (stream = 0, dts = 108900000).")

        assertEquals(
            listOf(
                "cplayer warn: Invalid video timestamp: 1210.000 -> 1188.000",
                "ffmpeg/demuxer error: mpegts: Packet corrupt (stream = 0, dts = 108900000).",
            ),
            noted,
        )
    }

    @Test fun aFloodFromOnePartIsCutShortAndCountedWithoutCuttingTheOthers() {
        repeat(10) { lines.line("ffmpeg/video", "error", "h264: error while decoding MB $it") }
        lines.line("cplayer", "warn", "Invalid video timestamp")
        assertEquals(4, noted.size)
        assertEquals("cplayer warn: Invalid video timestamp", noted.last())

        // A minute on, the count of what was left out comes first.
        now += 60_000
        lines.line("cplayer", "warn", "Invalid video timestamp")

        assertEquals("ffmpeg/video: 7 more lines left out (at most 3 a minute)", noted[4])
        assertEquals("cplayer warn: Invalid video timestamp", noted[5])
        assertEquals(6, noted.size)
        // And the part flooding has a new minute of its own.
        lines.line("ffmpeg/video", "error", "h264: error while decoding MB 10")
        assertEquals("ffmpeg/video error: h264: error while decoding MB 10", noted.last())
    }
}
