// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.stream.CatchUpRing
import ca.gureet.plasma.stream.CatchUpWriter
import ca.gureet.plasma.stream.TsScanner
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Local catch-up as Settings has it (brief 5.5): [channelIds] written all the time into [root],
 * each keeping [keepMs], all of them within [maxBytes]. None is written while [paused]: the TV is
 * off, and While the TV Is Off says to pause.
 */
data class LocalCatchUpPlan(
    val channelIds: List<String>,
    val root: File?,
    val keepMs: Long,
    val maxBytes: Long,
    val paused: Boolean = false,
) {
    /** The channels to write now. */
    val writing: List<String> get() = if (paused || root == null) emptyList() else channelIds

    companion object {
        val None = LocalCatchUpPlan(emptyList(), null, 0, 0)
    }
}

/**
 * Local catch-up (brief 5.5): a chosen channel written all the time into its rolling window,
 * through the stream hub, so watching or recording it shares the one connection. When the stream
 * stops, the channel is opened again every [retryMs], and the gap shows in the window.
 */
class LocalCatchUp(
    private val host: RecordingHost,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryMs: Long = RETRY_MS,
) {
    /** Writes [channelId] into [folder] until the coroutine is cancelled. */
    suspend fun write(channelId: String, folder: File) = withContext(Dispatchers.IO) {
        CatchUpWriter(folder, clock).use { writer ->
            val buffer = ByteArray(CHUNK)
            val keyframes = ArrayList<Int>()
            while (true) {
                currentCoroutineContext().ensureActive()
                val opened = host.openForRecording(channelId)
                if (opened is RecordingHost.Opened.Stream) {
                    opened.session.use { session ->
                        val reader = session.reader()
                        val scanner = TsScanner()
                        var fed = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = reader.read(buffer, WAIT_MS)
                            if (count < 0) break
                            if (count == 0) continue
                            // Where keyframes begin in what just came: a packet begun in the last read is left out.
                            keyframes.clear()
                            val base = fed
                            scanner.feed(buffer, 0, count) { position ->
                                val at = position - base
                                if (at in 0 until count) keyframes += at.toInt()
                            }
                            fed += count
                            writer.write(buffer, 0, count, keyframes, scanner.tables())
                        }
                    }
                }
                writer.gap()
                delay(retryMs)
            }
        }
    }

    companion object {
        /** The folder [channelId] is written to, inside local catch-up's [root]. */
        fun folderOf(root: File, channelId: String): File = File(root, URLEncoder.encode(channelId, Charsets.UTF_8.name()))

        /** The channel a folder inside local catch-up's root is for. */
        fun channelOf(folder: File): String = URLDecoder.decode(folder.name, Charsets.UTF_8.name())

        /** The channels' folders in [root]. */
        fun folders(root: File): List<File> = root.listFiles { file -> file.isDirectory }.orEmpty().toList()

        /**
         * Lets go of what local catch-up no longer keeps (brief 5.5): each channel's folder in [root]
         * keeps [keepMs], all of them within [maxBytes], the oldest going first. It never fills the
         * drive past [reserveBytes] free, so recordings, which are never deleted for it, have room.
         * A folder left empty, of a channel no longer written, goes too.
         */
        @Suppress("UsableSpace")
        fun tidy(root: File, keepMs: Long, maxBytes: Long, now: Long = System.currentTimeMillis(), reserveBytes: Long = RESERVE_BYTES) {
            val folders = folders(root)
            for (folder in folders) CatchUpRing.trim(folder, keepMs, now)
            val room = (folders.sumOf { CatchUpRing.size(it) } + root.usableSpace - reserveBytes).coerceAtLeast(0)
            CatchUpRing.trimTo(folders, minOf(maxBytes, room))
            for (folder in folders) if (folder.list().isNullOrEmpty()) folder.delete()
        }

        /** The space local catch-up takes in [root]. */
        fun size(root: File): Long = folders(root).sumOf { CatchUpRing.size(it) }

        /** The room local catch-up leaves free on the drive: two hours or so of HD recording. */
        const val RESERVE_BYTES = 5_000_000_000L

        private const val CHUNK = 64 * 1024
        private const val WAIT_MS = 500L
        private const val RETRY_MS = 15_000L
    }
}
