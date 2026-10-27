// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A recording sent to the network folder as it's written (Record To › Network folder, brief 5.6). */
class ShareUploadTest {
    private val root: File = Files.createTempDirectory("share").toFile()
    private val local: File = Files.createTempDirectory("spool").toFile()
    private val share = FolderShare(root).apply { makeFolders("Other Recordings") }
    private val spool = Spool(File(local, "home-105"), segmentBytes = 1_000)
    private val bytes = ByteArray(10_000) { (it % 251).toByte() }
    private val path = "Other Recordings/Arena Sports 1.ts.partial"
    private val heard = AtomicInteger()

    @After fun tidy() {
        spool.close()
        root.deleteRecursively()
        local.deleteRecursively()
    }

    private fun upload(keepBytes: Long = 1_500) =
        ShareUpload(spool, path, open = { share }, away = { heard.incrementAndGet() }, keepBytes = keepBytes, retryMs = 20, pollMs = 5)

    private fun onShare(): ByteArray = File(root, path).readBytes()

    @Test fun whatsWrittenGoesToTheSameBytesThereAndThisTvKeepsTheLastFewMinutes() = runBlocking {
        val upload = upload()
        val over = AtomicBoolean(false)
        val sending = async(Dispatchers.IO) { upload.run(over::get) }

        spool.write(bytes, 0, 4_000)
        withTimeout(5_000) { while (upload.sent < 4_000) delay(5) }
        spool.write(bytes, 4_000, 6_000)
        over.set(true)

        assertTrue(sending.await())
        assertArrayEquals(bytes, onShare())
        assertEquals(10_000L, upload.sent)
        // The last 1,500 bytes stay, a segment at a time, and the one being written.
        assertEquals(8_000L, spool.oldest)
        assertEquals(0, heard.get())
    }

    @Test fun whileTheShareIsAwayTheRestWaitsHereAndGoesWhenItsBack() = runBlocking {
        share.away = true
        val upload = upload(keepBytes = 0)
        val over = AtomicBoolean(false)
        val sending = async(Dispatchers.IO) { upload.run(over::get) }

        spool.write(bytes, 0, 3_000)
        withTimeout(5_000) { while (heard.get() < 2) delay(5) }
        // Nothing let go of while the share hasn't got it.
        assertEquals(0L, spool.oldest)
        share.away = false
        over.set(true)

        assertTrue(sending.await())
        assertArrayEquals(bytes.copyOf(3_000), onShare())
    }

    @Test fun stillAwayAWhileAfterTheEndTheRestWaitsHere() = runBlocking {
        share.away = true
        spool.write(bytes, 0, 2_000)

        assertFalse(upload().run({ true }, giveUpMs = 50))

        assertEquals(2_000L, spool.length)
        assertEquals(0L, spool.oldest)
        assertTrue(heard.get() > 0)
    }

    @Test fun aShareThatLostItsLastWritesGetsThemAgain() = runBlocking {
        spool.write(bytes, 0, 3_000)
        assertTrue(upload().run({ true }))
        // The share dropped before the last of it was kept.
        RandomAccessFile(File(root, path), "rw").use { it.setLength(2_500) }

        assertTrue(upload().run({ true }))

        assertArrayEquals(bytes.copyOf(3_000), onShare())
    }

    @Test fun aStretchLetGoOfBeforeTheShareHadItStaysAGapThere() = runBlocking {
        spool.write(bytes, 0, 3_000)
        spool.trimBelow(2_000)

        assertTrue(upload(keepBytes = 10_000).run({ true }))

        val there = onShare()
        assertEquals(3_000, there.size)
        assertArrayEquals(ByteArray(2_000), there.copyOf(2_000))
        assertArrayEquals(bytes.copyOfRange(2_000, 3_000), there.copyOfRange(2_000, 3_000))
    }
}
