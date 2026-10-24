// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** A recording on its way to the network folder, played as it's written (chase play, brief 5.6). */
class SpoolSourceTest {
    private val root: File = Files.createTempDirectory("share").toFile()
    private val local: File = Files.createTempDirectory("spool").toFile()
    private val share = FolderShare(root)
    private val spool = Spool(File(local, "home-105"), segmentBytes = 1_000)
    private val bytes = ByteArray(3_000) { (it % 251).toByte() }
    private val path = "Arena Sports 1.ts.partial"
    private var now = 0L
    private var opened = 0

    @After fun tidy() {
        spool.close()
        root.deleteRecursively()
        local.deleteRecursively()
    }

    private fun source() = SpoolSource(spool, { opened++; share.openRead(path) }, retryMs = 1_000, clock = { now })

    @Test fun whatThisTvKeepsComesFromHereAndWhatsOlderFromTheShare() {
        spool.write(bytes, 0, 3_000)
        spool.trimBelow(2_000)
        File(root, path).writeBytes(bytes.copyOf(2_500))
        val into = ByteArray(5_000)

        source().use { source ->
            assertEquals(1_000, source.read(2_000, into, 0, 5_000))
            assertArrayEquals(bytes.copyOfRange(2_000, 3_000), into.copyOf(1_000))
            assertEquals(0, opened)

            assertEquals(2_000, source.read(500, into, 0, 5_000))
            assertArrayEquals(bytes.copyOfRange(500, 2_500), into.copyOf(2_000))
            // Opened once, and kept.
            assertEquals(500, source.read(2_000, into, 0, 500))
            assertEquals(1_000, source.read(1_000, into, 0, 1_000))
            assertEquals(1, opened)
        }
    }

    @Test fun aShareThatsAwayIsTriedAgainAfterAWhile() {
        spool.write(bytes, 0, 3_000)
        spool.trimBelow(2_000)
        File(root, path).writeBytes(bytes)
        share.away = true
        val into = ByteArray(100)

        source().use { source ->
            assertEquals(0, source.read(0, into, 0, 100))
            share.away = false
            // Not straight away.
            now = 999
            assertEquals(0, source.read(0, into, 0, 100))
            now = 1_000
            assertEquals(100, source.read(0, into, 0, 100))
            assertArrayEquals(bytes.copyOf(100), into)
        }
    }
}
