// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.Recording
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A recording's files (brief 5.6): the stream as it came, "Channel – Title – 2026-10-21 1945.ts",
 * with a sidecar JSON of what the guide said, and a keyframe index.
 */
object RecordingFiles {
    /** A new file in [folder] for [recording], its start in [zone]; another name if that's taken. */
    fun newFile(folder: File, recording: Recording, zone: ZoneId): File {
        val base = baseNameOf(recording, zone)
        var file = File(folder, "$base.ts")
        var copy = 2
        while (file.exists() || sidecarOf(file).exists()) file = File(folder, "$base (${copy++}).ts")
        return file
    }

    /** "Channel – Title – 2026-10-21 1945.ts": [recording]'s file name, its start in [zone]. */
    fun nameOf(recording: Recording, zone: ZoneId): String = baseNameOf(recording, zone) + ".ts"

    private fun baseNameOf(recording: Recording, zone: ZoneId): String =
        clean("${recording.channelName} – ${recording.title} – ${STAMP.format(recording.start.atZone(zone))}")

    /** The JSON of what the guide said, beside [file]. */
    fun sidecarOf(file: File): File = File(file.parentFile, file.nameWithoutExtension + ".json")

    /** The keyframe index, beside [file]. */
    fun indexOf(file: File): File = File(file.parentFile, file.nameWithoutExtension + ".idx")

    /**
     * The keyframe at or before [time] (Unix ms) in [file]'s index, as chase play seeks (brief
     * 5.6): its byte in the file, and when it came. Before the first, the first; null without one.
     * A line still being written is passed over.
     */
    fun keyframeAt(file: File, time: Long): Pair<Long, Long>? {
        val index = indexOf(file)
        if (!index.isFile) return null
        val text = index.readText(Charsets.US_ASCII)
        // Only whole lines: the last may be half written.
        val lines = text.split('\n').let { if (text.endsWith('\n')) it else it.dropLast(1) }
        var first: Pair<Long, Long>? = null
        var found: Pair<Long, Long>? = null
        for (line in lines) {
            val offset = line.substringBefore(' ').toLongOrNull() ?: continue
            val at = line.substringAfter(' ', "").trim().toLongOrNull() ?: continue
            if (first == null) first = offset to at
            if (at <= time) found = offset to at
        }
        return found ?: first
    }

    /** Writes [recording]'s sidecar beside [file]. */
    fun writeSidecar(file: File, recording: Recording) {
        val json = buildJsonObject {
            put("title", recording.title)
            recording.subtitle?.let { put("subtitle", it) }
            recording.description?.let { put("description", it) }
            recording.category?.let { put("category", it) }
            recording.season?.let { put("season", it) }
            recording.episode?.let { put("episode", it) }
            put("channel", recording.channelName)
            put("channelId", recording.channelId)
            put("start", recording.start.toString())
            put("end", recording.end.toString())
            put("kind", recording.kind.key)
            recording.image?.let { put("image", it) }
        }
        sidecarOf(file).writeText(json.toString())
    }

    /** Deletes a recording's [file], its sidecar and its index. */
    fun delete(file: File) {
        file.delete()
        sidecarOf(file).delete()
        indexOf(file).delete()
    }

    /**
     * A name any drive takes: characters Windows shares refuse (`\ / : * ? " < > |`) become a
     * space or a dash, and it isn't too long.
     */
    internal fun clean(name: String): String {
        val replaced = buildString {
            for (char in name) {
                when (char) {
                    '/', '\\', '|' -> append('-')
                    // "Fight Night: Main Card" reads "Fight Night - Main Card".
                    ':' -> append(" -")
                    '*', '?', '"', '<', '>' -> append(' ')
                    else -> append(if (char.isISOControl()) ' ' else char)
                }
            }
        }
        return replaced.replace(SPACES, " ").trim().trimEnd('.').take(MAX_NAME).trim()
    }

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm", Locale.US)
    private val SPACES = Regex("\\s+")
    private const val MAX_NAME = 150
}

/**
 * A recording's keyframe index (brief 5.6): the byte offset and wall-clock time of a keyframe about
 * every [everyMs], one "offset time" line each, for chase play and seeking.
 */
class KeyframeIndex(file: File, private val everyMs: Long = EVERY_MS) : AutoCloseable {
    private val out = FileOutputStream(file, true)

    /** When a keyframe was last added; null before one is. */
    private var last: Long? = null

    /** A keyframe starts at [offset] in the file, and came at [time]. */
    fun add(offset: Long, time: Long) {
        val before = last
        if (before != null && time - before < everyMs) return
        last = time
        try {
            out.write("$offset $time\n".toByteArray(Charsets.US_ASCII))
        } catch (full: IOException) {
            // The recording itself says when the drive is full.
        }
    }

    override fun close() = out.close()

    private companion object {
        const val EVERY_MS = 2_000L
    }
}
