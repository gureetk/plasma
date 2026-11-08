// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

/**
 * Brief 6.7's budget for a guide: 150 MB of XMLTV, uncompressed, imported in under 90 s on a TV
 * box (#127). This times the reader alone, on a test machine faster than the TV, so it only shows
 * the reader isn't the reason a TV misses it; the TV's own time, with the database, is in its
 * diagnostics log.
 */
class XmltvBudgetTest {
    @Test fun readsA150MegabyteGuideWithinItsBudget() {
        val guide = MadeUpGuide(channels = 1_500, bytes = 150L * 1_000_000)
        var channels = 0
        var programmes = 0
        val started = System.nanoTime()
        XmltvReader().read(guide, onChannel = { channels++ }) { programmes++ }
        val millis = (System.nanoTime() - started) / 1_000_000

        println("XMLTV budget: ${guide.made / 1_000_000} MB, $programmes programmes, read in $millis ms")
        assertEquals(1_500, channels)
        assertEquals(guide.programmes, programmes)
        assertTrue("150 MB took $millis ms", millis < 90_000)
    }

    /**
     * A made-up guide of about [bytes], [channels] of them with programmes in turn, half an hour
     * each, as a provider's guide is laid out. It's made as it's read, so it's never in memory whole.
     */
    private class MadeUpGuide(private val channels: Int, private val bytes: Long) : InputStream() {
        var made = 0L
            private set
        var programmes = 0
            private set
        private var chunk = ByteArray(0)
        private var at = 0
        private var stage = 0

        private fun nextChunk(): Boolean {
            val text = when {
                stage == 0 -> {
                    stage = 1
                    buildString {
                        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv generator-info-name=\"made-up\">\n")
                        for (c in 1..channels) {
                            append("<channel id=\"channel$c.example\"><display-name lang=\"en\">Channel $c</display-name>")
                            append("<icon src=\"https://img.example/logos/$c.png\"/></channel>\n")
                        }
                    }
                }
                stage == 1 && made < bytes -> programme(programmes++)
                stage == 1 -> {
                    stage = 2
                    "</tv>\n"
                }
                else -> return false
            }
            chunk = text.toByteArray()
            at = 0
            made += chunk.size
            return true
        }

        private var slotTimes = -1 to ("" to "")

        private fun programme(n: Int): String {
            val channel = n % channels + 1
            val slot = n / channels
            // From 06:00 on 21 October 2026 (UTC), half an hour a programme; the times are made
            // once for each half hour, so making the guide costs little beside reading it.
            if (slotTimes.first != slot) {
                val start = 1_792_562_400L + slot * 1_800L
                slotTimes = slot to (xmltvTime(start) to xmltvTime(start + 1_800))
            }
            val (start, stop) = slotTimes.second
            return "<programme start=\"$start\" stop=\"$stop\" channel=\"channel$channel.example\">" +
                "<title lang=\"en\">Programme $slot on Channel $channel</title>" +
                "<sub-title lang=\"en\">Part $slot</sub-title>" +
                "<desc lang=\"en\">A made-up description of programme $slot, about as long as a provider's are, with a sentence or two more.</desc>" +
                "<category lang=\"en\">News</category><episode-num system=\"xmltv_ns\">0.$slot.</episode-num></programme>\n"
        }

        override fun read(): Int {
            if (at >= chunk.size && !nextChunk()) return -1
            return chunk[at++].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (at >= chunk.size && !nextChunk()) return -1
            val n = minOf(len, chunk.size - at)
            System.arraycopy(chunk, at, b, off, n)
            at += n
            return n
        }
    }
}

/** "20261021194500 +0000", from Unix seconds. */
private fun xmltvTime(seconds: Long): String {
    val time = java.time.Instant.ofEpochSecond(seconds).atOffset(java.time.ZoneOffset.UTC)
    return "%04d%02d%02d%02d%02d%02d +0000".format(time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute, time.second)
}
