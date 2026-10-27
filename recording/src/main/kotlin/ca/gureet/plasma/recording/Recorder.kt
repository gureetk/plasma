// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.CopyState
import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingProblems
import ca.gureet.plasma.stream.TsScanner
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Writes recordings (brief 5.6): a channel's stream, as it comes from the stream hub, into the
 * recording's file from its start (less the padding) until its end (with it). When the stream
 * drops, the hub tries again; when it gives up, the recorder opens the channel again every
 * [retryMs] until the end, and what comes is added to the same file.
 *
 * With Record To › Network folder, the file is on the share, written as it comes: a spool on this
 * TV keeps what the share doesn't have yet, and the last few minutes ([ShareUpload]).
 */
class Recorder(
    private val host: RecordingHost,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryMs: Long = RETRY_MS,
    private val openShare: (NetworkFolder) -> ShareSession = SmbShares::open,
    private val shareRetryMs: Long = ShareUpload.RETRY_MS,
    private val shareGiveUpMs: Long = ShareUpload.GIVE_UP_MS,
) {
    /** Writes [recording] until its end, or until the coroutine is cancelled. */
    suspend fun record(recording: Recording) = withContext(Dispatchers.IO) {
        val store = host.recordings
        val until = recording.until.toEpochMilli()
        val folder = host.recordingFolder()?.takeIf { it.isDirectory || it.mkdirs() }
        if (folder == null) {
            store.finished(recording.id, failed = true, problem = RecordingProblems.NO_ROOM)
            return@withContext
        }
        // Straight to the network folder, or carrying on with one that was going there (5.6).
        if (recording.file == null) {
            val straight = if (recording.spool != null) host.copyTo() else host.recordStraightTo()
            if (straight != null && recordToShare(recording, folder, straight, until)) return@withContext
        }
        // A drive that's nearly full makes room first, or there's no recording.
        if (!hasRoom(folder, LOW_SPACE_BYTES)) {
            store.finished(recording.id, failed = true, problem = RecordingProblems.NO_ROOM)
            return@withContext
        }
        // Carrying on after Plasma stopped mid-recording: the same file.
        val file = recording.file?.let(::File)?.takeIf { it.exists() } ?: RecordingFiles.newFile(folder, recording, host.zone)
        store.started(recording.id, file.path)
        var written: Written? = null
        try {
            RecordingFiles.writeSidecar(file, recording)
            written = FileOutput(file).use { write(recording, it, file.parentFile ?: file, LOW_SPACE_BYTES, until) }
        } catch (full: IOException) {
            written = Written(RecordingProblems.NO_ROOM)
        } finally {
            withContext(NonCancellable) {
                val bytes = file.length()
                // Nothing came: no empty file is left behind.
                if (bytes == 0L) RecordingFiles.delete(file)
                store.progress(recording.id, bytes)
                // Stopped or deleted while it was written (the coroutine cancelled): that stands.
                written?.let { store.finished(recording.id, failed = bytes == 0L, problem = it.problem) }
            }
        }
    }

    /**
     * Writes [recording] straight to the network folder [to] (Record To › Network folder, 5.6): to
     * a spool in [folder] on this TV, sent on to the share as it comes ([ShareUpload]), where it
     * takes its name, with its info files and artwork, once it's all there. When the share is away
     * at the end, the rest stays in the spool for the copy job. False when the share can't be
     * reached to start one: it's written to this TV instead, and copied once the share's back.
     */
    private suspend fun recordToShare(recording: Recording, folder: File, to: RecordingHost.CopyTo, until: Long): Boolean {
        val store = host.recordings
        val copier = NetworkCopier(openShare, host::fetch)
        val carryingOn = recording.spool?.let(::File)
        val networkPath = recording.networkPath?.takeIf { carryingOn != null } ?: try {
            to.folder.locate(copier.plan(recording, to.folder, to.jellyfin, host.zone).media)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (away: Exception) {
            host.copyWaiting(to.folder)
            return false
        }
        val media = mediaOf(to.folder, networkPath) ?: return false
        if (!hasRoom(folder, SPOOL_LOW_BYTES)) {
            store.finished(recording.id, failed = true, problem = RecordingProblems.NO_ROOM)
            return true
        }
        val spool = Spool(carryingOn ?: File(folder, "$SPOOLS/${spoolName(recording.id)}"))
        store.startedOnShare(recording.id, spool.folder.path, networkPath)
        val upload = ShareUpload(
            spool,
            NetworkCopier.inFolder(to.folder, media) + NetworkCopier.PARTIAL,
            open = { openShare(to.folder) },
            away = { host.copyWaiting(to.folder) },
            retryMs = shareRetryMs,
        )
        var written: Written? = null
        var sentAll = false
        try {
            coroutineScope {
                val writing = AtomicBoolean(true)
                val sending = async { upload.run(over = { !writing.get() }, giveUpMs = shareGiveUpMs) }
                try {
                    written = write(recording, SpoolOutput(spool), folder, SPOOL_LOW_BYTES, until)
                } catch (full: IOException) {
                    written = Written(RecordingProblems.NO_ROOM)
                } finally {
                    writing.set(false)
                }
                sentAll = sending.await()
            }
        } finally {
            withContext(NonCancellable) {
                spool.close()
                val bytes = spool.length
                store.progress(recording.id, bytes)
                val now = store.get(recording.id)
                when {
                    // Deleted while it was written: none of it stays.
                    now == null -> {
                        spool.delete()
                        runCatching { copier.discard(to.folder, media) }
                    }
                    // Nothing came: nothing's left behind, here or there.
                    bytes == 0L -> {
                        spool.delete()
                        store.setSpool(recording.id, null)
                        store.setCopy(recording.id, CopyState.None)
                        runCatching { copier.discard(to.folder, media) }
                    }
                    sentAll -> finishOnShare(recording, to, media, spool, copier)
                    // The share's away, or it was stopped: the rest waits here for the copy job.
                    else -> store.setCopy(recording.id, CopyState.Waiting, networkPath)
                }
                // Stopped or deleted while it was written (the coroutine cancelled): that stands.
                written?.let { store.finished(recording.id, failed = bytes == 0L, problem = it.problem) }
            }
        }
        return true
    }

    /** All of [recording] is on the share: it takes its name there, and its spool goes. */
    private suspend fun finishOnShare(recording: Recording, to: RecordingHost.CopyTo, media: String, spool: Spool, copier: NetworkCopier) {
        val store = host.recordings
        try {
            val copy = copier.finish(recording, to.folder, to.jellyfin, host.zone, media)
            store.copied(recording.id, to.folder.locate(copy.path))
            store.setSpool(recording.id, null)
            spool.delete()
            host.copied(recording, copy.freeBytes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            // It's all there but for its name: the copy job finishes it.
            store.setCopy(recording.id, CopyState.Waiting, to.folder.locate(media))
        }
    }

    /** How the writing ended: what got in the way, if anything. */
    private class Written(val problem: String?)

    /** Where a recording's stream goes: its file, or its spool on the way to the network folder. */
    private interface Output : AutoCloseable {
        /** How much is there already. */
        val length: Long

        /** The keyframe index, its places counted as this output's. */
        val index: File

        fun write(bytes: ByteArray, offset: Int, count: Int)
    }

    private class FileOutput(private val file: File) : Output {
        private val out = FileOutputStream(file, true)
        override val length: Long get() = file.length()
        override val index: File get() = RecordingFiles.indexOf(file)

        override fun write(bytes: ByteArray, offset: Int, count: Int) = out.write(bytes, offset, count)

        override fun close() = out.close()
    }

    private class SpoolOutput(private val spool: Spool) : Output {
        override val length: Long get() = spool.length
        override val index: File get() = spool.index

        override fun write(bytes: ByteArray, offset: Int, count: Int) = spool.write(bytes, offset, count)

        // The spool stays open for the share until it has it all.
        override fun close() = Unit
    }

    /**
     * Writes the channel's stream to [output] until [until], with its keyframe index; while [room]'s
     * drive keeps [lowBytes] free, or When Space Runs Low makes it.
     */
    private suspend fun write(recording: Recording, output: Output, room: File, lowBytes: Long, until: Long): Written {
        val store = host.recordings
        var written = output.length
        var problem: String? = null
        var reported = clock()
        // The stream stopped coming after it had, and hasn't come back yet.
        var gap = false
        val buffer = ByteArray(CHUNK)
        KeyframeIndex(output.index).use { index ->
            /**
             * Copies what [read] reads to [output] until it ends or the recording does: a reason to
             * stop when the drive's full, else null.
             */
            suspend fun copy(read: (ByteArray, Long) -> Int): Written? {
                val scanner = TsScanner()
                // Positions in this stream count from here in the recording.
                val base = written
                while (clock() < until) {
                    currentCoroutineContext().ensureActive()
                    val count = read(buffer, WAIT_MS)
                    if (count < 0) break
                    if (count == 0) continue
                    // Coming again after it stopped: a gap in the recording.
                    if (gap) {
                        store.interrupted(recording.id)
                        gap = false
                    }
                    output.write(buffer, 0, count)
                    val now = clock()
                    scanner.feed(buffer, 0, count) { keyframe -> index.add(base + keyframe, now) }
                    written += count
                    if (now - reported >= PROGRESS_MS) {
                        reported = now
                        store.progress(recording.id, written)
                        // Room for the next while, or it stops here.
                        if (!hasRoom(room, lowBytes)) return Written(RecordingProblems.NO_ROOM)
                    }
                }
                if (written > 0) gap = true
                return null
            }

            while (clock() < until) {
                currentCoroutineContext().ensureActive()
                when (val opened = host.openForRecording(recording.channelId)) {
                    is RecordingHost.Opened.Problem -> problem = opened.problem
                    is RecordingHost.Opened.Stream -> opened.session.use { session ->
                        problem = null
                        copy(session.reader()::read)?.let { return it }
                    }
                    is RecordingHost.Opened.Segments -> opened.reader.use { hls ->
                        problem = null
                        copy(hls::read)?.let { return it }
                        // HLS in fMP4 and the like: no use trying again.
                        if (hls.unsupported) return Written(RecordingProblems.FORMAT)
                    }
                }
                // Opened again after a moment, until the end.
                if (clock() < until) pause(until)
            }
        }
        return Written(problem)
    }

    /** Whether [folder]'s drive has [lowBytes] free, after When Space Runs Low has had its say. */
    @Suppress("UsableSpace")
    private suspend fun hasRoom(folder: File, lowBytes: Long): Boolean = folder.usableSpace >= lowBytes || host.makeRoom(folder)

    /** Waits [retryMs], or until [until], whichever comes first. */
    private suspend fun pause(until: Long) {
        delay(minOf(retryMs, (until - clock()).coerceAtLeast(0)))
    }

    companion object {
        /** Below this much room, a drive is nearly full: about an hour of HD. */
        const val LOW_SPACE_BYTES = 2_000_000_000L

        /** A spool needs a few minutes' room, and more while the share's away. */
        const val SPOOL_LOW_BYTES = 512L * 1024 * 1024

        /** The folder in the recordings folder that spools are kept in. */
        const val SPOOLS = ".spool"

        private const val CHUNK = 64 * 1024
        private const val WAIT_MS = 500L
        private const val RETRY_MS = 10_000L

        /** How often the size written is noted, for Recordings, and the room left looked at. */
        private const val PROGRESS_MS = 5_000L

        /** A recording's id, as a folder name: "home:105@1761075660" is "home-105@1761075660". */
        fun spoolName(id: String): String = id.map { if (it.isLetterOrDigit() || it in "@-_.") it else '-' }.joinToString("")

        /** [location], a place in [folder] ([NetworkFolder.locate]), as a path in the folder; null when it's elsewhere. */
        fun mediaOf(folder: NetworkFolder, location: String): String? {
            val place = NetworkFolder.placeOf(location) ?: return null
            if (!place.host.equals(folder.host, ignoreCase = true) || !place.share.equals(folder.share, ignoreCase = true)) return null
            val base = folder.path.trim('/')
            return if (base.isEmpty()) place.path else place.path.removePrefix("$base/").takeIf { it != place.path }
        }
    }
}
