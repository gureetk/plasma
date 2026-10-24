// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.core.data.Recording
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A copy made: where it went, as a path in the folder, and the room left on the share, when it says. */
data class NetworkCopy(val path: String, val freeBytes: Long?)

/**
 * Copy to Network Folder (brief 5.6): a finished recording copied into the network folder, named
 * for Jellyfin when Settings says so. It's written as "….partial", its size checked, and renamed
 * once it's all there, with its info files and artwork beside it by then, so Jellyfin never scans
 * half a file or a file without its details. Plasma never deletes anything on the share by itself.
 */
class NetworkCopier(
    private val open: (NetworkFolder) -> ShareSession = SmbShares::open,
    /** The guide's artwork, for the copy; null when it can't be had. */
    private val fetch: suspend (url: String) -> ByteArray? = { null },
) {
    /** Copies [recording], whose file is [file], into [folder]. Throws [ShareException]. */
    suspend fun copy(recording: Recording, file: File, folder: NetworkFolder, jellyfin: Boolean, zone: ZoneId): NetworkCopy = withContext(Dispatchers.IO) {
        open(folder).use { share ->
            val base = folder.path.trim('/')
            fun inFolder(path: String) = if (base.isEmpty()) path else "$base/$path"
            val plan = JellyfinNames.plan(recording, jellyfin, zone) { season -> share.files(inFolder(season)).mapNotNull(::episodeOf) }
            val target = inFolder(plan.media)
            share.makeFolders(target.substringBeforeLast('/', ""))
            val partial = "$target$PARTIAL"
            file.inputStream().use { share.write(partial, it) }
            if (share.size(partial) != file.length()) {
                runCatching { share.delete(partial) }
                throw ShareException(ShareProblem.Refused)
            }
            for (extra in plan.extras) {
                try {
                    write(share, extra, inFolder(extra.path))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failed: Exception) {
                    // Artwork or an info file that doesn't make it leaves the recording as it is.
                }
            }
            share.rename(partial, target)
            NetworkCopy(plan.media, share.freeBytes())
        }
    }

    private suspend fun write(share: ShareSession, extra: CopyPlan.Extra, path: String) {
        when (extra) {
            is CopyPlan.Extra.Text -> {
                if (extra.onlyIfMissing && share.exists(path)) return
                share.write(path, extra.text.toByteArray(Charsets.UTF_8).inputStream())
            }
            is CopyPlan.Extra.Image -> {
                // Named for what it is: the guide's pictures aren't all JPEG.
                val name = path.substringBeforeLast('.')
                if (extra.onlyIfMissing && IMAGE_TYPES.any { share.exists("$name.$it") }) return
                val bytes = fetch(extra.url) ?: return
                share.write("$name.${imageTypeOf(bytes)}", bytes.inputStream())
            }
        }
    }

    companion object {
        internal const val PARTIAL = ".partial"

        /** "Late Night Sessions S2026E004.ts" is episode 4. */
        private val EPISODE = Regex("""S\d+E(\d+)\.ts$""")

        private val IMAGE_TYPES = listOf("jpg", "png", "webp")

        internal fun episodeOf(name: String): Int? = EPISODE.find(name)?.groupValues?.get(1)?.toIntOrNull()

        /**
         * Deletes the copy at [path] in [share] with what's beside it ([extrasOf]): "Delete
         * everywhere" (brief 5.6). A show's own info file and poster stay, for its other episodes.
         */
        fun deleteCopy(share: ShareSession, path: String) {
            share.delete(path)
            for (extra in extrasOf(path)) share.delete(extra)
        }

        /** What a copy at [path] has beside it: its info file and picture, and a film's own in its folder. */
        fun extrasOf(path: String): List<String> {
            val base = path.substringBeforeLast('.')
            val folder = path.substringBeforeLast('/', "")
            val film = folder.isNotEmpty() &&
                folder.substringAfterLast('/') == base.substringAfterLast('/') &&
                folder.substringBeforeLast('/', "").substringAfterLast('/') == JellyfinNames.MOVIES
            return buildList {
                add("$base.nfo")
                IMAGE_TYPES.forEach { add("$base-thumb.$it") }
                if (film) {
                    add("$folder/movie.nfo")
                    IMAGE_TYPES.forEach { add("$folder/poster.$it") }
                }
            }
        }

        /** "png" or "webp" by the picture's first bytes, else "jpg". */
        internal fun imageTypeOf(bytes: ByteArray): String = when {
            bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "png"
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            else -> "jpg"
        }
    }
}
