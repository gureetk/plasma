// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.diagnostics

import ca.gureet.plasma.core.network.ReportScrubber
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.time.ZoneOffset
import java.util.zip.ZipInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plasma's log, the players' log and Export Diagnostics' bundle (brief 5.17). */
class LogsTest {
    private val dir = Files.createTempDirectory("logs").toFile()

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    private fun text(log: RotatingLog) = String(log.read(), Charsets.UTF_8)

    @Test fun aLogKeepsTwoFilesAndDropsTheOldest() {
        // Two lines of 11 bytes fit in a file.
        val log = RotatingLog(dir, "plasma", maxBytes = 25)

        log.append("one 456789\n")
        log.append("two 456789\n")
        log.append("three 6789\n")
        log.append("four 56789\n")
        assertEquals("one 456789\ntwo 456789\nthree 6789\nfour 56789\n", text(log))
        log.append("five 56789\n")

        assertEquals("three 6789\nfour 56789\nfive 56789\n", text(log))
        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test fun aLogCarriesOnInTheNewerFileAfterARestart() {
        RotatingLog(dir, "plasma", maxBytes = 100).apply {
            append("before\n")
            close()
        }

        val again = RotatingLog(dir, "plasma", maxBytes = 100)
        again.append("after\n")

        assertEquals("before\nafter\n", text(again))
    }

    @Test fun plasmasLogStampsLinesTakesOutCredentialsAndCountsRepeats() {
        val log = DiagnosticLog(RotatingLog(dir, "plasma", maxBytes = 10_000), clock = { 1_793_652_060_000L }, zone = { ZoneOffset.UTC })

        log.note("player", "Opening http://line.example.org/live/viewer/s3cret/105.ts")
        log.note("error", "timeout")
        log.note("error", "timeout")
        log.note("error", "timeout")
        log.note("player", "Stopped")

        assertEquals(
            """
            2026-11-02 20:41:00.000 [player] Opening http://line.example.org/live/***/***/105.ts
            2026-11-02 20:41:00.000 [error] timeout
            2026-11-02 20:41:00.000 [log] The line before came 2 more times
            2026-11-02 20:41:00.000 [player] Stopped

            """.trimIndent(),
            String(log.read(), Charsets.UTF_8),
        )
    }

    @Test fun thePlayersLogTakesWhatTheCommandWritesWithoutCredentials() {
        val log = RotatingLog(dir, "engines", maxBytes = 10_000)
        val capture = EngineLogCapture(log, listOf("printf", "VLC: open http://line.example.org/movie/viewer/s3cret/77.mkv\\nmpv: playing\\n"))

        capture.start()
        val deadline = System.currentTimeMillis() + 5_000
        while (capture.running && System.currentTimeMillis() < deadline) Thread.sleep(20)

        assertFalse(capture.running)
        assertEquals("VLC: open http://line.example.org/movie/***/***/77.mkv\nmpv: playing\n", text(log))
    }

    @Test fun theBundleHoldsEachFileMadeSafeToShare() {
        val scrubber = ReportScrubber(secrets = listOf("viewer"), hosts = listOf("line.example.org"))
        val zip = diagnosticsZip(
            listOf(ReportFile("plasma.log", "Opening http://line.example.org:8080/live/x/y/1.ts as viewer"), ReportFile("players.log", "")),
            scrubber::scrub,
        )

        val entries = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entries[entry.name] = String(input.readBytes(), Charsets.UTF_8)
            }
        }

        assertEquals(listOf("plasma.log", "players.log"), entries.keys.toList())
        assertEquals("Opening http://server-1:8080/live/***/***/1.ts as ***", entries["plasma.log"])
        assertTrue(entries.values.none { "line.example.org" in it || "viewer" in it })
    }
}
