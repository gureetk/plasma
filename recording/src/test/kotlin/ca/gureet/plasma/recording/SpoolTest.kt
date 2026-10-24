// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** A recording on its way to the network folder, kept on this TV until the share has it (brief 5.6). */
class SpoolTest {
    private val folder: File = Files.createTempDirectory("spool").toFile()
    private val bytes = ByteArray(2_500) { (it % 251).toByte() }

    @After fun tidy() {
        folder.deleteRecursively()
    }

    private fun spool() = Spool(File(folder, "home-105"), segmentBytes = 1_000)

    @Test fun whatsWrittenIsReadBackAcrossSegments() {
        spool().use { spool ->
            spool.write(bytes, 0, 1_200)
            spool.write(bytes, 1_200, 1_300)

            assertEquals(2_500L, spool.length)
            // Named for where they start.
            assertEquals(listOf("0000000000000000.seg", "0000000000001000.seg", "0000000000002000.seg"), spool.folder.list()!!.sorted())
            val into = ByteArray(5_000)
            // One segment at a time.
            assertEquals(200, spool.read(800, into, 0, 5_000))
            assertArrayEquals(bytes.copyOfRange(800, 1_000), into.copyOf(200))
            assertEquals(500, spool.read(2_000, into, 0, 5_000))
            assertArrayEquals(bytes.copyOfRange(2_000, 2_500), into.copyOf(500))
            // Nothing there yet.
            assertEquals(0, spool.read(2_500, into, 0, 10))
        }
    }

    @Test fun whatTheShareHasGoesButNotTheSegmentBeingWritten() {
        spool().use { spool ->
            spool.write(bytes, 0, 2_500)

            spool.trimBelow(1_999)
            assertEquals(1_000L, spool.oldest)
            assertEquals(-1, spool.read(500, ByteArray(10), 0, 10))
            assertEquals(10, spool.read(1_500, ByteArray(10), 0, 10))

            spool.trimBelow(10_000)
            assertEquals(2_000L, spool.oldest)
            assertEquals(500L, spool.keptBytes)
        }
    }

    @Test fun itCarriesOnAfterPlasmaStops() {
        spool().use { it.write(bytes, 0, 1_200) }

        spool().use { spool ->
            assertEquals(1_200L, spool.length)
            spool.write(bytes, 1_200, 300)

            assertEquals(1_500L, spool.length)
            val into = ByteArray(300)
            assertEquals(300, spool.read(1_200, into, 0, 300))
            assertArrayEquals(bytes.copyOfRange(1_200, 1_500), into)
            // The last segment carried on.
            assertEquals(2, spool.folder.list()!!.count { it.endsWith(".seg") })
        }
    }

    @Test fun deletedItsAllGone() {
        val spool = spool()
        spool.write(bytes, 0, 10)
        spool.index.writeText("0 1\n")

        spool.delete()

        assertFalse(spool.folder.exists())
        assertEquals(-1, spool.read(0, ByteArray(10), 0, 10))
    }
}
