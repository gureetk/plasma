// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingProblems
import ca.gureet.plasma.stream.TsScanner
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Writes recordings (brief 5.6): a channel's stream, as it comes from the stream hub, into the
 * recording's file from its start (less the padding) until its end (with it). When the stream
 * drops, the hub tries again; when it gives up, the recorder opens the channel again every
 * [retryMs] until the end, and what comes is added to the same file.
 */
class Recorder(
    private val host: RecordingHost,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryMs: Long = RETRY_MS,
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
        // Carrying on after Plasma stopped mid-recording: the same file.
        val file = recording.file?.let(::File)?.takeIf { it.exists() } ?: RecordingFiles.newFile(folder, recording, host.zone)
        store.started(recording.id, file.path)
        var written: Written? = null
        try {
            RecordingFiles.writeSidecar(file, recording)
            written = write(recording, file, until)
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

    /** How the writing ended: what got in the way, if anything. */
    private class Written(val problem: String?)

    private suspend fun write(recording: Recording, file: File, until: Long): Written {
        val store = host.recordings
        var written = file.length()
        var problem: String? = null
        var reported = clock()
        // The stream stopped coming after it had, and hasn't come back yet.
        var gap = false
        val buffer = ByteArray(CHUNK)
        FileOutputStream(file, true).use { out ->
            KeyframeIndex(RecordingFiles.indexOf(file)).use { index ->
                while (clock() < until) {
                    currentCoroutineContext().ensureActive()
                    when (val opened = host.openForRecording(recording.channelId)) {
                        is RecordingHost.Opened.Problem -> problem = opened.problem
                        is RecordingHost.Opened.Stream -> opened.session.use { session ->
                            problem = null
                            val reader = session.reader()
                            val scanner = TsScanner()
                            // Positions in this stream count from here in the file.
                            val base = written
                            while (clock() < until) {
                                currentCoroutineContext().ensureActive()
                                val count = reader.read(buffer, WAIT_MS)
                                if (count < 0) break
                                if (count == 0) continue
                                // Coming again after it stopped: a gap in the recording.
                                if (gap) {
                                    store.interrupted(recording.id)
                                    gap = false
                                }
                                out.write(buffer, 0, count)
                                val now = clock()
                                scanner.feed(buffer, 0, count) { keyframe -> index.add(base + keyframe, now) }
                                written += count
                                if (now - reported >= PROGRESS_MS) {
                                    reported = now
                                    store.progress(recording.id, written)
                                }
                            }
                            if (written > 0) gap = true
                        }
                    }
                    // Opened again after a moment, until the end.
                    if (clock() < until) pause(until)
                }
            }
        }
        return Written(problem)
    }

    /** Waits [retryMs], or until [until], whichever comes first. */
    private suspend fun pause(until: Long) {
        delay(minOf(retryMs, (until - clock()).coerceAtLeast(0)))
    }

    private companion object {
        const val CHUNK = 64 * 1024
        const val WAIT_MS = 500L
        const val RETRY_MS = 10_000L

        /** How often the size written is noted, for Recordings. */
        const val PROGRESS_MS = 5_000L
    }
}
