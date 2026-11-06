// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.FoundRecording
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
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
    fun keyframeAt(file: File, time: Long): Pair<Long, Long>? = keyframeIn(indexOf(file), time)

    /** The keyframe at or before [time] in the keyframe [index], as [keyframeAt] finds it. */
    fun keyframeIn(index: File, time: Long): Pair<Long, Long>? {
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
            recording.rating?.let { put("rating", it) }
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
     * Moves a recording's [file], with its sidecar and index, into [folder] (#161), under its own
     * name, or another when that's taken. Renamed where the drive allows it; otherwise copied, then
     * [moved] is told where it is, and only then is the old one deleted, so a move cut short leaves
     * it where it was. Where it is now; null when it couldn't be moved, and it's still where it was.
     */
    suspend fun moveTo(file: File, folder: File, moved: suspend (File) -> Unit): File? {
        if (!folder.isDirectory && !folder.mkdirs()) return null
        val base = file.nameWithoutExtension
        // A copy a move cut short already made, as long as the file itself.
        var target = File(folder, file.name)
        var copy = 2
        while ((target.exists() && target.length() != file.length()) || (!target.exists() && sidecarOf(target).exists())) {
            target = File(folder, "$base (${copy++}).ts")
        }
        val parts = listOf(file to target, sidecarOf(file) to sidecarOf(target), indexOf(file) to indexOf(target))
        if (!target.exists() && file.renameTo(target)) {
            for ((from, to) in parts.drop(1)) {
                if (!from.exists() || from.renameTo(to)) continue
                try {
                    copyFile(from, to)
                    from.delete()
                } catch (failed: IOException) {
                    // The recording plays without them.
                }
            }
            moved(target)
            return target
        }
        try {
            for ((from, to) in parts) if (from.exists() && !(to.exists() && to.length() == from.length())) copyFile(from, to)
        } catch (failed: IOException) {
            for ((_, to) in parts) to.delete()
            return null
        }
        moved(target)
        for ((from, _) in parts) from.delete()
        return target
    }

    /** Copies [from] to [to] whole, by way of "….part", with when it was last written to. */
    private fun copyFile(from: File, to: File) {
        val part = File(to.parentFile, to.name + PART)
        FileInputStream(from).use { input ->
            FileOutputStream(part).use { output ->
                val buffer = ByteArray(COPY_BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
        if (part.length() != from.length() || !part.renameTo(to)) {
            part.delete()
            throw IOException("Couldn't copy ${from.name}")
        }
        to.setLastModified(from.lastModified())
    }

    /**
     * What a recording's [file] was (#161), for Find Recordings: as its sidecar says, when Plasma
     * may read it, or else as its name does, its start in [zone]. When it was written comes from
     * its index, when Plasma may read that, or else from its start and when the file last changed.
     * Null for a name Plasma didn't give it.
     */
    fun found(file: File, zone: ZoneId): FoundRecording? {
        val changed = Instant.ofEpochMilli(file.lastModified())
        // An index Plasma may not read, as after a reinstall, is as good as none.
        val first = readable { keyframeAt(file, 0) }?.second?.let(Instant::ofEpochMilli)
        val last = readable { keyframeAt(file, Long.MAX_VALUE) }?.second?.let(Instant::ofEpochMilli)
        readSidecar(file)?.let { said ->
            val start = said.time("start") ?: return@let
            val end = said.time("end") ?: start
            val title = said.text("title") ?: return@let
            return FoundRecording(
                file = file.path,
                sizeBytes = file.length(),
                channelId = said.text("channelId"),
                channelName = said.text("channel").orEmpty(),
                title = title,
                start = start,
                end = end,
                recordedFrom = first ?: start,
                recordedTo = last ?: changed.coerceAtLeast(first ?: start),
                subtitle = said.text("subtitle"),
                description = said.text("description"),
                category = said.text("category"),
                image = said.text("image"),
                season = said.number("season"),
                episode = said.number("episode"),
                kind = said.text("kind")?.let(RecordingKind::of),
                rating = said.text("rating"),
            )
        }
        val named = NAME.matchEntire(file.name) ?: return null
        val (channel, title, stamp) = named.destructured
        val start = runCatching { LocalDateTime.parse(stamp, STAMP).atZone(zone).toInstant() }.getOrNull() ?: return null
        val to = last ?: changed.coerceAtLeast(start)
        return FoundRecording(
            file = file.path,
            sizeBytes = file.length(),
            channelId = null,
            channelName = channel,
            title = title,
            start = start,
            end = to,
            recordedFrom = first ?: start,
            recordedTo = to,
        )
    }

    /** What [read] reads; null when Plasma may not read it. */
    private inline fun <T> readable(read: () -> T?): T? = try {
        read()
    } catch (failed: IOException) {
        null
    } catch (refused: SecurityException) {
        null
    }

    /** [file]'s sidecar, read; null when it isn't there, or Plasma may not read it. */
    private fun readSidecar(file: File): JsonObject? = try {
        Json.parseToJsonElement(sidecarOf(file).readText()) as? JsonObject
    } catch (failed: IOException) {
        null
    } catch (failed: SerializationException) {
        null
    } catch (refused: SecurityException) {
        null
    }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.number(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull
    private fun JsonObject.time(key: String): Instant? = text(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /**
     * A name any drive takes: characters Windows shares refuse (`\ / : * ? " < > |`) become a
     * space or a dash, and it isn't too long.
     */
    fun clean(name: String): String {
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

    /** "Channel – Title – 2026-10-21 1945.ts", or "… (2).ts" beside one of the same name. */
    private val NAME = Regex("^(.+?) – (.+) – (\\d{4}-\\d{2}-\\d{2} \\d{4})(?: \\(\\d+\\))?\\.ts$")

    /** What a file being copied is called till it's whole. */
    private const val PART = ".part"
    private const val COPY_BUFFER = 1024 * 1024
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
