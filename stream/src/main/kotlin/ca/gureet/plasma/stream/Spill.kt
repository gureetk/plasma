// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * The rewind buffer on storage (brief 5.5): an upstream's bytes kept in segment files of about two
 * seconds, cut at keyframes where the stream has them, each with the wall-clock time it began. A
 * paused engine carries on from the byte it stopped at, and a seek lands on a keyframe. The oldest
 * segments go once it holds more than [keepMs] or [maxBytes].
 *
 * Positions count every byte since the upstream opened, as the memory [Ring]'s do. One writer
 * appends; readers read from any thread.
 */
internal class Spill(
    private val folder: File,
    private val keepMs: Long,
    private val maxBytes: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    /** One file: bytes [start] to [end], begun at [startedAt], from a keyframe when [keyframe]. */
    class Segment(val number: Long, val start: Long, val startedAt: Long, val keyframe: Boolean) {
        @Volatile var end: Long = start
            internal set
    }

    private val lock = Any()
    private val segments = ArrayDeque<Segment>()
    private var output: FileOutputStream? = null
    private var next = 0L
    private var held = 0L
    @Volatile private var closed = false

    /** The oldest byte held, or −1 before anything is. */
    val oldest: Long get() = synchronized(lock) { segments.firstOrNull()?.start ?: -1 }

    /** One past the newest byte held. */
    val written: Long get() = synchronized(lock) { segments.lastOrNull()?.end ?: -1 }

    /** When the oldest byte held came in, or null before anything is. */
    val oldestTime: Long? get() = synchronized(lock) { segments.firstOrNull()?.startedAt }

    /** The space it takes now. */
    val size: Long get() = synchronized(lock) { held }

    /** Whether it has started letting its oldest moments go: a reader close to them may lose them. */
    @Volatile var full: Boolean = false
        private set

    init {
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("No room for the rewind buffer")
        folder.listFiles()?.forEach { it.delete() }
    }

    /**
     * Appends [length] bytes from [offset], which begin at stream position [position] and came in at
     * [time]. [keyframes] are positions in them where a keyframe's packet starts: a new segment
     * begins at the first one after the current segment has run [SEGMENT_MS], or at a packet once
     * it has run twice that.
     */
    fun write(
        position: Long,
        bytes: ByteArray,
        offset: Int,
        length: Int,
        keyframes: List<Long> = emptyList(),
        time: Long = clock(),
    ): Unit = synchronized(lock) {
        if (closed || length <= 0) return
        try {
            keep(position, bytes, offset, length, keyframes, time)
        } catch (failed: IOException) {
            // Storage ran out, or went (a USB drive taken out): what's held stays, and nothing more is kept.
            closed = true
            runCatching { output?.close() }
            output = null
        }
        trim()
    }

    private fun keep(position: Long, bytes: ByteArray, offset: Int, length: Int, keyframes: List<Long>, time: Long) {
        val end = position + length
        // A gap, as when the memory ring ran over before this began: start afresh.
        if (segments.isNotEmpty() && segments.last().end != position) clear()
        if (segments.isEmpty()) startSegment(position, keyframe = position in keyframes, time)
        var from = offset
        var at = position
        for (cut in keyframes.filter { it >= position && it < end }.sorted()) {
            val current = segments.last()
            if (cut > current.start && time - current.startedAt >= SEGMENT_MS) {
                append(bytes, from, (cut - at).toInt())
                from += (cut - at).toInt()
                at = cut
                startSegment(cut, keyframe = true, time)
            }
        }
        // A stream without keyframes (radio, or one Plasma can't read) still gets segments, at a packet.
        val current = segments.last()
        if (time - current.startedAt >= SEGMENT_MS * 2) {
            val cut = alignedCut(current, at, end)
            if (cut != null) {
                append(bytes, from, (cut - at).toInt())
                from += (cut - at).toInt()
                at = cut
                startSegment(cut, keyframe = false, time)
            }
        }
        append(bytes, from, (end - at).toInt())
    }

    /** Reads bytes from [position] into [into]; the count, or −1 when they aren't held. */
    fun read(position: Long, into: ByteArray, offset: Int = 0, length: Int = into.size - offset): Int {
        val segment = synchronized(lock) { segments.firstOrNull { position >= it.start && position < it.end } } ?: return -1
        val count = minOf(length.toLong(), segment.end - position).toInt()
        return try {
            RandomAccessFile(fileOf(segment.number), "r").use { file ->
                file.seek(position - segment.start)
                file.read(into, offset, count)
            }
        } catch (gone: IOException) {
            // Let go of while it was read: the reader moves on to what's still held.
            -1
        }
    }

    /**
     * Where to start for [time]: the latest keyframe at or before it, or the oldest keyframe held
     * when it's earlier. A stream without keyframes starts at the segment it's in.
     */
    fun positionAt(time: Long): Long? = synchronized(lock) {
        val starts = segments.filter { it.keyframe }.ifEmpty { segments.toList() }
        (starts.lastOrNull { it.startedAt <= time } ?: starts.firstOrNull())?.start
    }

    /** When the byte at [position] came in, from its segment's start and the next one's; null when it isn't held. */
    fun timeAt(position: Long): Long? = synchronized(lock) {
        val index = segments.indexOfFirst { position >= it.start && position < it.end }
        if (index < 0) return null
        val segment = segments[index]
        val following = segments.getOrNull(index + 1)?.startedAt ?: clock()
        val size = (segment.end - segment.start).coerceAtLeast(1)
        segment.startedAt + (following - segment.startedAt) * (position - segment.start) / size
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            clear()
            folder.delete()
        }
    }

    /** The first packet boundary after [from], before [end], in [segment]'s alignment. */
    private fun alignedCut(segment: Segment, from: Long, end: Long): Long? {
        val offset = (from - segment.start) % TsScanner.PACKET
        val cut = if (offset == 0L) from else from + (TsScanner.PACKET - offset)
        return cut.takeIf { it > segment.start && it < end }
    }

    private fun startSegment(start: Long, keyframe: Boolean, time: Long) {
        output?.close()
        output = null
        val segment = Segment(next++, start, time, keyframe)
        output = FileOutputStream(fileOf(segment.number))
        segments.addLast(segment)
    }

    private fun append(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        val segment = segments.last()
        output?.write(bytes, offset, length) ?: return
        segment.end += length
        held += length
    }

    /** Lets go of the oldest segments beyond the length and the space allowed, keeping the one being written. */
    private fun trim() {
        val now = clock()
        while (segments.size > 1) {
            val oldest = segments.first()
            val second = segments[1]
            if (held <= maxBytes && now - second.startedAt < keepMs) break
            full = true
            segments.removeFirst()
            held -= oldest.end - oldest.start
            fileOf(oldest.number).delete()
        }
    }

    private fun clear() {
        output?.close()
        output = null
        for (segment in segments) fileOf(segment.number).delete()
        segments.clear()
        held = 0
    }

    private fun fileOf(number: Long) = File(folder, "$number.ts")

    companion object {
        /** About how long a segment runs (brief 5.5). */
        const val SEGMENT_MS = 2_000L
    }
}
