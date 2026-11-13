// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.Manifest
import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.core.model.PhotoOrder
import ca.gureet.plasma.recording.ShareEntry
import ca.gureet.plasma.recording.ShareFile
import ca.gureet.plasma.recording.ShareSession
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The viewer's own photos on the screensaver (#170). */
class ScreensaverPhotosTest {
    private val root: File = Files.createTempDirectory("photos").toFile()
    private val cache = File(Files.createTempDirectory("cache").toFile(), "screensaver")

    /** Photos named "tall…" are taller than wide; the rest fill the screen. */
    private fun photos(openShare: (NetworkFolder) -> ShareSession = { error("no share") }) =
        ScreensaverPhotos(cache, openShare, wide = { !it.name.startsWith("tall") }, pause = {})

    private fun photo(path: String, taken: Long): File = File(root, path).apply {
        parentFile?.mkdirs()
        writeText(path)
        setLastModified(taken)
    }

    @Test fun aFoldersPhotosShowAsTheyWereTakenWithThoseInsideIt() = runBlocking {
        photo("b.jpg", 3_000_000)
        photo("2025/a.JPG", 1_000_000)
        photo("2025/June/tall.heic", 2_000_000)
        // Not photos, or hidden.
        photo("notes.txt", 500_000)
        photo(".hidden.jpg", 500_000)
        photo(".thumbnails/c.jpg", 500_000)

        val shown = photos().show(root.path, null, everyMs = 180_000, PhotoOrder.Taken).take(4).toList()

        assertEquals(listOf("2025/a.JPG", "2025/June/tall.heic", "b.jpg", "2025/a.JPG"), shown.map { it!!.file.removePrefix("${root.path}/") })
        // A photo taller than wide shows whole.
        assertEquals(listOf(false, true, false, false), shown.map { it!!.whole })
    }

    @Test fun shuffledEachPhotoShowsOnceInTurn() = runBlocking {
        for (i in 1..6) photo("p$i.png", i * 1_000_000L)

        val shown = photos().show(root.path, null, everyMs = 60_000, PhotoOrder.Shuffled).take(6).toList()

        assertEquals((1..6).map { "p$it.png" }.toSet(), shown.map { File(it!!.file).name }.toSet())
    }

    @Test fun theNextShowingCarriesOnFromThePhotoShownLast() = runBlocking {
        for (i in 1..4) photo("p$i.jpg", i * 1_000_000L)
        // The first photo's time goes by at once; the second is still on screen when the screensaver ends.
        var pauses = 0
        val photos = ScreensaverPhotos(cache, wide = { true }, pause = { if (pauses++ > 0) awaitCancellation() })
        assertEquals(listOf("p1.jpg", "p2.jpg"), photos.show(root.path, null, everyMs = 180_000, PhotoOrder.Taken).take(2).toList().map { File(it!!.file).name })

        val again = photos.show(root.path, null, everyMs = 180_000, PhotoOrder.Taken).take(1).toList()

        assertEquals("p2.jpg", File(again.single()!!.file).name)
    }

    @Test fun withoutPhotosPlasmasPicturesShow() = runBlocking {
        assertNull(photos().show(File(root, "gone").path, null, everyMs = 180_000, PhotoOrder.Taken).take(1).toList().single())
        photo("notes.txt", 1)
        assertNull(photos().show(root.path, null, everyMs = 180_000, PhotoOrder.Taken).take(1).toList().single())
    }

    private val nas = NetworkFolder("nas.home.lan", "Media", "Photos", "maya", "s3cret")

    @Test fun photosOnTheNetworkAreCopiedHereToShow() = runBlocking {
        photo("Photos/01.jpg", 1_000_000)
        photo("Photos/2025/02.jpg", 2_000_000)

        val shown = photos { FolderShare(root) }.show("smb://nas.home.lan/Media/Photos", nas, everyMs = 180_000, PhotoOrder.Taken).take(2).toList()

        assertEquals(listOf("Photos/01.jpg", "Photos/2025/02.jpg"), shown.map { File(it!!.file).readText() })
        assertTrue(shown.all { File(it!!.file).parentFile == cache })
    }

    @Test fun onlyTheLastFewCopiesAreKept() = runBlocking {
        for (i in 1..30) photo("Photos/${"%02d".format(i)}.jpg", i * 1_000_000L)

        photos { FolderShare(root) }.show("smb://nas.home.lan/Media/Photos", nas, everyMs = 180_000, PhotoOrder.Taken).take(30).toList()

        assertEquals(ScreensaverPhotos.MAX_CACHED, cache.listFiles()!!.size)
    }

    @Test fun androidIsAskedToLetPlasmaSeePhotos() {
        assertEquals(Manifest.permission.READ_MEDIA_IMAGES, PhotoPlaces.photosPermission(33))
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, PhotoPlaces.photosPermission(30))
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, PhotoPlaces.photosPermission(28))
    }

    @Test fun aNetworkFoldersPlaceIsItsAddress() {
        assertEquals("smb://nas.home.lan/Media/Photos/2025", NetworkFolderLink.placeOf(NetworkFolder("nas.home.lan", "Media", "Photos/2025", "maya", "s3cret")))
        assertEquals("smb://nas.home.lan/Media", NetworkFolderLink.placeOf(NetworkFolder("nas.home.lan", "Media", "", "maya", "s3cret")))
    }

    /** A share that's a folder here: [base] holds its files. */
    private class FolderShare(private val base: File) : ShareSession {
        private fun at(path: String) = File(base, path)

        override fun folders(path: String) = at(path).listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.sorted()

        override fun files(path: String) = at(path).listFiles().orEmpty().filter { it.isFile }.map { it.name }

        override fun entries(path: String) = at(path).listFiles().orEmpty().filter { it.isFile }.map { ShareEntry(it.name, it.length(), it.lastModified()) }

        override fun read(path: String): InputStream = at(path).inputStream()

        override fun exists(path: String) = at(path).exists()

        override fun makeFolders(path: String) {
            at(path).mkdirs()
        }

        override fun write(path: String, input: InputStream) {
            at(path).outputStream().use { input.copyTo(it) }
        }

        override fun rename(from: String, to: String) {
            at(from).renameTo(at(to))
        }

        override fun delete(path: String) {
            at(path).delete()
        }

        override fun size(path: String): Long? = at(path).takeIf { it.isFile }?.length()

        override fun freeBytes(): Long? = null

        override fun openWrite(path: String): ShareFile = error("not here")

        override fun close() = Unit
    }
}
