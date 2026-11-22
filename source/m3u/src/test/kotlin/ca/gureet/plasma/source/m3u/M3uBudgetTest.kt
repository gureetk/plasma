// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Brief 6.7's budget for a playlist: 100,000 lines parsed in under 15 s on a TV box (#127). A test
 * machine is faster than the TV, so this only shows the reader isn't the reason a TV misses it;
 * the TV's own time is in its diagnostics log.
 */
class M3uBudgetTest {
    @Test fun readsAPlaylistOfAHundredThousandLinesWithinItsBudget() {
        val playlist = madeUpPlaylist(entries = 50_000)
        assertEquals(100_001, playlist.count { it == '\n'.code.toByte() })

        var read = 0
        val started = System.nanoTime()
        M3uReader.read(ByteArrayInputStream(playlist)) { read++ }
        val millis = (System.nanoTime() - started) / 1_000_000

        println("M3U budget: 100,000 lines (${playlist.size / 1_000_000} MB) read in $millis ms")
        assertEquals(50_000, read)
        assertTrue("100,000 lines took $millis ms", millis < 15_000)
    }

    /** A made-up playlist as providers send them: an `#EXTINF` line and an address for each entry, a tenth of them movies. */
    private fun madeUpPlaylist(entries: Int): ByteArray = buildString(entries * 260) {
        append("#EXTM3U url-tvg=\"https://guide.example/epg.xml.gz\"\n")
        for (i in 1..entries) {
            val movie = i % 10 == 0
            val group = if (movie) "Movies | Drama" else "Group ${i % 300}"
            append("#EXTINF:-1 tvg-id=\"channel$i.example\" tvg-name=\"Channel $i\" tvg-logo=\"https://img.example/logos/$i.png\" ")
            append("tvg-chno=\"$i\" group-title=\"$group\",Channel $i HD\n")
            append(if (movie) "http://provider.example/movie/user/pass/$i.mkv\n" else "http://provider.example/live/user/pass/$i.ts\n")
        }
    }.toByteArray()
}
