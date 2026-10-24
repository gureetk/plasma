// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Local catch-up (brief 5.5): a channel written all the time into a rolling window on storage, in
 * chunk files of about a minute ([CatchUpWriter]), and read back from any moment in it ([open]).
 *
 * Each chunk is named for when its first byte came, in Unix ms, and begins with the stream's
 * tables (PAT and PMT) and a keyframe, so it plays on its own. Beside it is an index: the tables'
 * length on the first line, then a keyframe's byte offset and the time it came, about every two
 * seconds. When the stream stops and comes back, a new chunk begins, so a gap shows between two
 * chunks.
 */
object CatchUpRing {
    /** One chunk: [file], begun at [start]. */
    class Chunk internal constructor(val file: File, val start: Long) {
        val index: File get() = indexOf(file)

        /** What its index says so far. */
        fun read(): ChunkIndex = ChunkIndex.read(index)

        /** The space it takes, index and all. */
        val size: Long get() = file.length() + index.length()
    }

    /** A chunk's index: the [tables]' length, and each keyframe's offset and time, oldest first. */
    class ChunkIndex internal constructor(val tables: Int, val offsets: LongArray, val times: LongArray) {
        /** The last moment indexed; null before any is. */
        val end: Long? get() = times.lastOrNull()

        /** The keyframe at or before [time]: its offset, or the first one's when it's earlier than all. */
        fun offsetAt(time: Long): Long? {
            if (offsets.isEmpty()) return null
            var found = 0
            for (i in times.indices) {
                if (times[i] <= time) found = i else break
            }
            return offsets[found]
        }

        /** The first keyframe after [time]: its offset, or null when none is indexed yet. */
        fun offsetAfter(time: Long): Long? {
            for (i in times.indices) if (times[i] > time) return offsets[i]
            return null
        }

        internal companion object {
            fun read(file: File): ChunkIndex {
                val lines = try {
                    file.readLines(Charsets.US_ASCII)
                } catch (missing: IOException) {
                    emptyList()
                }
                val tables = lines.firstOrNull()?.removePrefix(TABLES)?.trim()?.toIntOrNull() ?: 0
                val pairs = lines.drop(1).mapNotNull { line ->
                    val offset = line.substringBefore(' ').toLongOrNull()
                    val time = line.substringAfter(' ', "").trim().toLongOrNull()
                    if (offset != null && time != null) offset to time else null
                }
                return ChunkIndex(tables, LongArray(pairs.size) { pairs[it].first }, LongArray(pairs.size) { pairs[it].second })
            }
        }
    }

    /** [folder]'s chunks, oldest first. */
    fun chunks(folder: File): List<Chunk> = folder.listFiles { file -> file.name.endsWith(MEDIA) }
        .orEmpty()
        .mapNotNull { file -> file.name.removeSuffix(MEDIA).toLongOrNull()?.let { Chunk(file, it) } }
        .sortedBy { it.start }

    /** From when to when [folder] holds the channel, as far as its index goes; null while it's empty. */
    fun window(folder: File): LongRange? {
        val chunks = chunks(folder)
        val first = chunks.firstOrNull() ?: return null
        val last = chunks.last()
        return first.start..(last.read().end ?: last.start)
    }

    /**
     * How much of [from] to [to] wasn't written, in ms: the gaps between chunks, when the stream
     * stopped for a while (brief 5.5, "Skipped 12 min that weren't recorded").
     */
    fun missingMs(folder: File, from: Long, to: Long): Long {
        var missing = 0L
        val chunks = chunks(folder)
        for (i in 0 until chunks.size - 1) {
            val end = chunks[i].read().end ?: chunks[i].start
            val next = chunks[i + 1].start
            if (next - end <= GAP_MS) continue
            val gapFrom = maxOf(end + INDEX_MS, from)
            val gapTo = minOf(next, to)
            if (gapTo > gapFrom) missing += gapTo - gapFrom
        }
        return missing
    }

    /** The space [folder]'s chunks take. */
    fun size(folder: File): Long = chunks(folder).sumOf { it.size }

    /** Lets go of chunks wholly older than [keepMs] before [now]. */
    fun trim(folder: File, keepMs: Long, now: Long) {
        val cutoff = now - keepMs
        val chunks = chunks(folder)
        for ((i, chunk) in chunks.withIndex()) {
            val end = chunks.getOrNull(i + 1)?.start ?: chunk.read().end ?: chunk.start
            if (end < cutoff) delete(chunk) else break
        }
    }

    /**
     * Keeps [folders] together within [maxBytes] (Storage Limit, brief 5.5): the oldest chunk of any
     * of them goes first. A folder's newest chunk, which may be being written, stays.
     */
    fun trimTo(folders: List<File>, maxBytes: Long) {
        val all = folders.flatMap { folder -> chunks(folder).dropLast(1) }.sortedBy { it.start }
        var total = folders.sumOf { size(it) }
        for (chunk in all) {
            if (total <= maxBytes) break
            total -= chunk.size
            delete(chunk)
        }
    }

    /** Deletes everything in [folder] (Clear Local Catch-up). */
    fun clear(folder: File) {
        folder.listFiles()?.forEach { it.delete() }
    }

    /**
     * The channel from [from] to [to], from the keyframe at or before [from]: null when [folder]
     * holds nothing then. When [to] hasn't come yet, it follows the chunk being written as it
     * grows, as live TV would, until then.
     */
    fun open(folder: File, from: Long, to: Long, clock: () -> Long = System::currentTimeMillis): CatchUpReader? {
        val chunks = chunks(folder)
        if (chunks.isEmpty()) return null
        // The chunk with [from] in it, or the first one after it.
        val first = chunks.lastOrNull { it.start <= from } ?: chunks.first()
        if (first.start >= to) return null
        val index = first.read()
        val offset = if (from <= first.start) 0L else index.offsetAt(from) ?: 0L
        return CatchUpReader(folder, first, complete = first !== chunks.last(), offset, index.tables, to, clock)
    }

    /**
     * Keep as Recording (brief 5.5): copies the channel from [from] to [to] into [out], from the
     * keyframe at or before [from] to the first one after [to], the stream's tables first.
     * [onKeyframe] hears where each keyframe the chunks' indexes have lands in what's written, and
     * when it came, for the recording's own index. The bytes written: 0 when nothing's held then.
     */
    fun copy(folder: File, from: Long, to: Long, out: OutputStream, onKeyframe: (offset: Long, time: Long) -> Unit = { _, _ -> }): Long {
        val chunks = chunks(folder)
        val first = chunks.lastOrNull { it.start <= from } ?: chunks.firstOrNull() ?: return 0
        if (first.start >= to) return 0
        var written = 0L
        val buffer = ByteArray(COPY_BYTES)
        for (chunk in chunks.dropWhile { it !== first }) {
            if (chunk !== first && chunk.start >= to) break
            val index = chunk.read()
            val atFrom = if (chunk === first && from > chunk.start) index.offsetAt(from) ?: 0L else 0L
            // From the start, the chunk's own tables come first; from a keyframe further in, a copy of them.
            val begin = if (atFrom <= index.tables) 0L else atFrom
            val stop = index.offsetAfter(to)
            RandomAccessFile(chunk.file, "r").use { media ->
                val end = minOf(stop ?: media.length(), media.length())
                if (begin > 0 && index.tables > 0) {
                    val tables = ByteArray(index.tables)
                    media.seek(0)
                    media.readFully(tables)
                    out.write(tables)
                    written += tables.size
                }
                for (i in index.offsets.indices) {
                    if (index.offsets[i] in begin until end) onKeyframe(written + index.offsets[i] - begin, index.times[i])
                }
                media.seek(begin)
                var left = end - begin
                while (left > 0) {
                    val count = media.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (count < 0) break
                    out.write(buffer, 0, count)
                    written += count
                    left -= count
                }
            }
            if (stop != null) break
        }
        return written
    }

    internal fun indexOf(media: File): File = File(media.parentFile, media.name.removeSuffix(MEDIA) + INDEX)

    private fun delete(chunk: Chunk) {
        chunk.file.delete()
        chunk.index.delete()
    }

    internal const val MEDIA = ".ts"
    internal const val INDEX = ".idx"
    internal const val TABLES = "tables "

    /** About a minute a chunk. */
    const val CHUNK_MS = 60_000L

    private const val COPY_BYTES = 256 * 1024

    /** A keyframe indexed about every two seconds. */
    const val INDEX_MS = 2_000L

    /** Longer than this between chunks, and something wasn't written. */
    const val GAP_MS = 10_000L
}

/**
 * Writes a channel into its local catch-up folder (brief 5.5), as it comes. Chunks are cut at a
 * keyframe once they've run [chunkMs]; [gap] ends one when the stream stops.
 */
class CatchUpWriter(
    private val folder: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val chunkMs: Long = CatchUpRing.CHUNK_MS,
    private val indexMs: Long = CatchUpRing.INDEX_MS,
) : AutoCloseable {
    private var media: FileOutputStream? = null
    private var index: FileOutputStream? = null
    private var chunkStart = 0L
    private var written = 0L

    /** When a keyframe was last indexed in this chunk; null before one is. */
    private var indexed: Long? = null

    /** Whether a chunk is being written. */
    val writing: Boolean get() = media != null

    /**
     * Adds [length] bytes from [offset] in [bytes], which just came. [keyframes] are indexes into
     * [bytes] where a keyframe's packet begins; [tables] the stream's PAT and PMT once they're
     * known. Nothing is kept until there's a keyframe and the tables, so each chunk plays on its own.
     */
    fun write(bytes: ByteArray, offset: Int, length: Int, keyframes: List<Int>, tables: ByteArray?) {
        val now = clock()
        val end = offset + length
        var from = offset
        try {
            for (keyframe in keyframes) {
                if (keyframe < from || keyframe >= end) continue
                when {
                    media == null -> {
                        if (tables == null) continue
                        begin(now, tables)
                    }
                    now - chunkStart >= chunkMs && tables != null -> {
                        append(bytes, from, keyframe - from)
                        finish()
                        begin(now, tables)
                    }
                    else -> append(bytes, from, keyframe - from)
                }
                from = keyframe
                mark(now)
            }
            if (media != null) append(bytes, from, end - from)
        } catch (failed: IOException) {
            // The drive's full, or gone: this chunk ends, and the next keyframe tries a new one.
            finish()
        }
    }

    /** The stream stopped: the chunk ends here, and the next one begins when it's back. */
    fun gap() = finish()

    override fun close() = finish()

    private fun begin(now: Long, tables: ByteArray) {
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("No room for local catch-up")
        var start = now
        while (File(folder, "$start${CatchUpRing.MEDIA}").exists()) start++
        val file = File(folder, "$start${CatchUpRing.MEDIA}")
        media = FileOutputStream(file)
        index = FileOutputStream(CatchUpRing.indexOf(file)).also { it.write("${CatchUpRing.TABLES}${tables.size}\n".toByteArray(Charsets.US_ASCII)) }
        chunkStart = start
        written = 0
        indexed = null
        append(tables, 0, tables.size)
    }

    private fun append(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        val out = media ?: return
        out.write(bytes, offset, length)
        written += length
    }

    /** A keyframe starts where the chunk has got to: indexed, when the last one was a while ago. */
    private fun mark(now: Long) {
        val last = indexed
        if (last != null && now - last < indexMs) return
        indexed = now
        index?.write("$written $now\n".toByteArray(Charsets.US_ASCII))
    }

    private fun finish() {
        runCatching { media?.close() }
        runCatching { index?.close() }
        media = null
        index = null
    }
}

/**
 * A stretch of local catch-up being read ([CatchUpRing.open]): the stream's tables, then from a
 * keyframe on, chunk after chunk, until [to].
 */
class CatchUpReader internal constructor(
    private val folder: File,
    first: CatchUpRing.Chunk,
    complete: Boolean,
    startOffset: Long,
    tables: Int,
    private val to: Long,
    private val clock: () -> Long,
) : LocalReader {
    private var chunk = first
    private var file: RandomAccessFile? = RandomAccessFile(first.file, "r")

    // From the start, the chunk's own tables come first; from a keyframe further in, a copy of them.
    private var position = if (startOffset <= tables) 0L else startOffset
    private val prefix: ByteArray = if (position > 0 && tables > 0) readTables(first.file, tables) else ByteArray(0)
    private var prefixAt = 0

    /** Where this chunk passes [to], once its index says. */
    private var limit: Long? = first.read().offsetAfter(to)

    /** Whether this chunk was done when it was opened, its index with it; otherwise it's being written. */
    private var complete = complete
    @Volatile private var closed = false

    override fun read(into: ByteArray, waitMs: Long): Int {
        if (closed) return -1
        if (prefixAt < prefix.size) {
            val count = minOf(prefix.size - prefixAt, into.size)
            System.arraycopy(prefix, prefixAt, into, 0, count)
            prefixAt += count
            return count
        }
        val deadline = clock() + waitMs
        while (!closed) {
            val raf = file ?: return -1
            // The chunk being written, past [to]: its index says where to stop, once it's got there.
            if (limit == null && !complete && clock() >= to) limit = chunk.read().offsetAfter(to)
            val stop = limit
            val available = raf.length().let { if (stop != null) minOf(it, stop) else it }
            if (position < available) {
                raf.seek(position)
                val count = raf.read(into, 0, minOf(into.size.toLong(), available - position).toInt())
                if (count > 0) {
                    position += count
                    return count
                }
            }
            if (stop != null && position >= stop) return -1
            // At the end of this chunk: on to the next one, if it's there and before [to].
            val later = CatchUpRing.chunks(folder).filter { it.start > chunk.start }
            val next = later.firstOrNull()
            if (next != null) {
                if (next.start >= to) return -1
                runCatching { raf.close() }
                chunk = next
                file = RandomAccessFile(next.file, "r")
                position = 0
                limit = next.read().offsetAfter(to)
                complete = later.size > 1
                continue
            }
            // The chunk being written: more comes until [to], and its index says when that's passed.
            limit = chunk.read().offsetAfter(to)
            if (limit != null) continue
            if (clock() >= to + END_MARGIN_MS) return -1
            if (clock() >= deadline) return 0
            Thread.sleep(POLL_MS)
        }
        return -1
    }

    override fun close() {
        closed = true
        runCatching { file?.close() }
        file = null
    }

    private fun readTables(media: File, length: Int): ByteArray = try {
        RandomAccessFile(media, "r").use { raf ->
            ByteArray(length).also { raf.readFully(it) }
        }
    } catch (failed: IOException) {
        ByteArray(0)
    }

    private companion object {
        const val POLL_MS = 100L

        /** A programme still on is followed a little past its end, as the index catches up. */
        const val END_MARGIN_MS = 5_000L
    }
}
