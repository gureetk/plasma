// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.graphics.BitmapFactory
import android.media.ExifInterface
import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.core.model.PhotoOrder
import ca.gureet.plasma.recording.ShareSession
import ca.gureet.plasma.recording.SmbShares
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * The viewer's own photos for the screensaver (#170), from a folder on this TV or a USB drive
 * ("/storage/…"), or on the network ("smb://host/share/path", signed in to as its
 * [NetworkFolder] says): the photos in it and in the folders inside it, in the order chosen, one
 * at a time. One on the network is copied into [cache] first, which keeps the last few.
 *
 * A listing is kept for [listForMs], so the screensaver doesn't look again each time it shows,
 * and the next showing carries on from the photo shown last.
 */
class ScreensaverPhotos(
    private val cache: File,
    private val openShare: (NetworkFolder) -> ShareSession = SmbShares::open,
    private val clock: () -> Long = System::currentTimeMillis,
    private val listForMs: Long = LIST_FOR_MS,
    /** Whether a photo's wide enough to fill the screen. */
    private val wide: (File) -> Boolean = ::isWide,
    /** How a photo's time on screen, and a wait before looking again, are waited out. */
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    /** A photo: where it is (a file, or a path in its share), when it was taken, as its file says, and its size. */
    data class Photo(val path: String, val taken: Long, val size: Long)

    /** A photo on screen: its file on this TV, and whether it shows whole, being too narrow to fill the screen. */
    data class Shown(val file: String, val whole: Boolean)

    private class Listing(val photos: List<Photo>, val at: Long)

    private val listings = HashMap<String, Listing>()
    private val lastShown = HashMap<String, String>()
    private val seeds = HashMap<String, Long>()

    /**
     * The photos at [place] in [order], one after another, each for [minutes]: the one shown last
     * first. Null while there are none, or none can be had (the drive's unplugged, the server's
     * away), so Plasma's own pictures show; it looks again a while later.
     */
    fun show(place: String, folder: NetworkFolder?, minutes: Int, order: PhotoOrder): Flow<Shown?> = flow {
        while (true) {
            val photos = ordered(place, photos(place, folder), order)
            if (photos.isEmpty()) {
                emit(null)
                pause(RETRY_MS)
                continue
            }
            var at = photos.indexOfFirst { it.path == lastShown[place] }.coerceAtLeast(0)
            var misses = 0
            while (misses < minOf(photos.size, MAX_MISSES)) {
                val photo = photos[at]
                val shown = take(place, folder, photo)
                at = (at + 1) % photos.size
                if (shown == null) {
                    misses++
                    continue
                }
                misses = 0
                lastShown[place] = photo.path
                emit(shown)
                // The next one's here before its turn.
                if (photos.size > 1) take(place, folder, photos[at])
                pause(minutes * MS_PER_MINUTE)
                // Looked at again now and then, for photos added or taken away.
                if (clock() - (listings[place]?.at ?: 0) >= listForMs) break
            }
            if (misses > 0) {
                listings.remove(place)
                emit(null)
                pause(RETRY_MS)
            }
        }
    }.flowOn(Dispatchers.IO)

    /** The photos at [place], as last listed if that's recent. */
    private fun photos(place: String, folder: NetworkFolder?): List<Photo> {
        listings[place]?.takeIf { clock() - it.at < listForMs }?.let { return it.photos }
        val found = try {
            if (place.startsWith(SMB)) folder?.let(::listShare).orEmpty() else listFolder(File(place))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            emptyList()
        }
        // None found isn't kept: the drive may come back.
        if (found.isNotEmpty()) listings[place] = Listing(found, clock())
        return found
    }

    /** [photos] as they were taken, or shuffled the same way each time while Plasma runs. */
    private fun ordered(place: String, photos: List<Photo>, order: PhotoOrder): List<Photo> = when (order) {
        PhotoOrder.Taken -> photos.sortedWith(compareBy<Photo>({ it.taken }, { it.path }))
        PhotoOrder.Shuffled -> photos.sortedBy { it.path }.shuffled(Random(seeds.getOrPut(place) { Random.nextLong() }))
    }

    /** The photos in [root] and the folders inside it, a few levels down, up to [MAX_PHOTOS]. */
    private fun listFolder(root: File): List<Photo> {
        val found = ArrayList<Photo>()
        fun walk(folder: File, depth: Int) {
            for (file in folder.listFiles().orEmpty().sortedBy { it.name }) {
                if (found.size >= MAX_PHOTOS) return
                if (file.name.startsWith('.')) continue
                if (file.isDirectory) {
                    if (depth < MAX_DEPTH) walk(file, depth + 1)
                } else if (isPhoto(file.name)) {
                    found += Photo(file.path, file.lastModified(), file.length())
                }
            }
        }
        walk(root, 0)
        return found
    }

    /** The photos in [folder] on its share and the folders inside it, as [listFolder] finds them on this TV. */
    private fun listShare(folder: NetworkFolder): List<Photo> = openShare(folder).use { share ->
        val found = ArrayList<Photo>()
        fun walk(path: String, depth: Int) {
            for (entry in share.entries(path).sortedBy { it.name }) {
                if (found.size >= MAX_PHOTOS) return
                if (isPhoto(entry.name)) found += Photo(join(path, entry.name), entry.modified, entry.size)
            }
            if (depth < MAX_DEPTH) for (inside in share.folders(path)) walk(join(path, inside), depth + 1)
        }
        walk(folder.path, 0)
        found
    }

    /** [photo] as a file on this TV, and how it shows; null when it can't be had. */
    private fun take(place: String, folder: NetworkFolder?, photo: Photo): Shown? {
        val file = if (place.startsWith(SMB)) fetch(folder ?: return null, place, photo) else File(photo.path).takeIf { it.isFile }
        return file?.let { Shown(it.path, whole = !wide(it)) }
    }

    /** [photo] from its share, copied into [cache] unless it's there already; the oldest copies go. */
    private fun fetch(folder: NetworkFolder, place: String, photo: Photo): File? {
        val name = digest("$place\n${photo.path}\n${photo.size}\n${photo.taken}") + "." + photo.path.substringAfterLast('.', "jpg").lowercase()
        val file = File(cache, name)
        if (file.isFile && file.length() > 0) {
            file.setLastModified(clock())
            return file
        }
        return try {
            cache.mkdirs()
            val part = File(cache, "$name.part")
            openShare(folder).use { share -> share.read(photo.path).use { input -> part.outputStream().use { input.copyTo(it) } } }
            if (!part.renameTo(file)) return null
            prune()
            file
        } catch (failed: IOException) {
            null
        }
    }

    /** Keeps the [MAX_CACHED] copies shown last. */
    private fun prune() {
        val copies = cache.listFiles().orEmpty().filter { it.isFile }.sortedByDescending { it.lastModified() }
        for (old in copies.drop(MAX_CACHED)) old.delete()
    }

    companion object {
        /** A listing's kept this long: an hour. */
        const val LIST_FOR_MS = 60 * 60_000L

        /** How deep into the folders inside the one chosen photos are looked for. */
        const val MAX_DEPTH = 4

        /** At most this many photos, so a huge folder doesn't take long to look through. */
        const val MAX_PHOTOS = 2_000

        /** Copies of photos from the network kept on this TV at once. */
        const val MAX_CACHED = 24

        /** Photos tried in a row before Plasma's own pictures show for a while. */
        private const val MAX_MISSES = 5
        private const val RETRY_MS = 5 * 60_000L
        private const val MS_PER_MINUTE = 60_000L
        private const val SMB = "smb://"

        /** The kinds of photo shown: JPEG, PNG, WebP and HEIF. */
        private val PHOTO_TYPES = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

        /** Narrower than this (width to height) shows whole, rather than cropped to fill the screen: 4:3 and wider fill it. */
        private const val WIDE = 1.3f

        fun isPhoto(name: String): Boolean = !name.startsWith('.') && name.substringAfterLast('.', "").lowercase() in PHOTO_TYPES

        private fun join(path: String, name: String): String = if (path.isEmpty()) name else "${path.trimEnd('/')}/$name"

        private fun digest(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(DIGEST_BYTES).joinToString("") { "%02x".format(it) }

        private const val DIGEST_BYTES = 12

        /** Whether [file] fills a 16:9 screen without much cropped: as wide as 4:3, once turned as its EXIF says. */
        fun isWide(file: File): Boolean {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var width = bounds.outWidth
            var height = bounds.outHeight
            if (width <= 0 || height <= 0) return true
            val turned = try {
                ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) in TURNED
            } catch (failed: IOException) {
                false
            }
            if (turned) width = height.also { height = width }
            return width >= height * WIDE
        }

        /** EXIF orientations that turn a photo a quarter of the way round. */
        private val TURNED = setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )
    }
}
