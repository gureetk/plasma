// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.TreeMap

/** What's kept on this TV of a recording on its way to the network folder, to read (brief 5.6). */
interface SpoolRead {
    /** Where the oldest part kept starts: before it, only the share has it. */
    val oldest: Long

    /**
     * Up to [count] bytes from [position] into [into] at [offset], from one segment: how many, 0
     * when nothing's there yet, or -1 when it's been let go of.
     */
    fun read(position: Long, into: ByteArray, offset: Int, count: Int): Int
}

/**
 * A recording on its way to the network folder (Record To › Network folder, brief 5.6): what's
 * been written, in files of [segmentBytes] named for where they start in the recording. They stay
 * on this TV until the share has them, and the last few minutes for a while after that. Places in
 * it count from the recording's start, so the keyframe index beside them ([index]) holds for the
 * file on the share too.
 *
 * One writer adds to it; others read from it and let go of what the share has, at the same time.
 */
class Spool(val folder: File, private val segmentBytes: Long = SEGMENT_BYTES) : SpoolRead, AutoCloseable {
    /** The segments kept, by where they start. */
    private val segments = TreeMap<Long, File>()
    private var out: FileOutputStream? = null

    /** Where the segment being written starts, and how much of it there is. */
    private var lastStart = 0L
    private var lastLength = 0L

    /** How much has been written, from the start of the recording. */
    @Volatile var length: Long = 0
        private set

    init {
        folder.mkdirs()
        // Carrying on after Plasma stopped: what's there already.
        folder.listFiles()?.forEach { file ->
            if (file.name.endsWith(SEGMENT)) file.name.removeSuffix(SEGMENT).toLongOrNull()?.let { segments[it] = file }
        }
        segments.lastEntry()?.let { (start, file) ->
            lastStart = start
            lastLength = file.length()
            length = start + lastLength
        }
    }

    /** The keyframe index, its places counted as the spool's are. */
    val index: File get() = File(folder, INDEX)

    override val oldest: Long get() = synchronized(this) { segments.firstEntry()?.key ?: length }

    /** When the newest segment was last written to; 0 before there's one. */
    val writtenAt: Long get() = synchronized(this) { segments.lastEntry()?.value?.lastModified() ?: 0 }

    /** Adds [count] bytes of [bytes] from [offset]. Throws [IOException] when the drive's full or gone. */
    fun write(bytes: ByteArray, offset: Int, count: Int) {
        var at = offset
        var left = count
        while (left > 0) {
            val stream = current()
            val room = (segmentBytes - lastLength).toInt().coerceAtLeast(1)
            val part = minOf(left, room)
            stream.write(bytes, at, part)
            synchronized(this) {
                lastLength += part
                length += part
            }
            at += part
            left -= part
            if (lastLength >= segmentBytes) closeSegment()
        }
    }

    /** The segment to write to now: the last, while it has room, or a new one. */
    private fun current(): FileOutputStream {
        out?.let { return it }
        val file = synchronized(this) {
            if (segments.isEmpty() || lastLength >= segmentBytes) {
                val start = length
                segments[start] = File(folder, nameOf(start))
                lastStart = start
                lastLength = 0
            }
            segments.getValue(lastStart)
        }
        return FileOutputStream(file, true).also { out = it }
    }

    private fun closeSegment() {
        out?.close()
        out = null
    }

    override fun read(position: Long, into: ByteArray, offset: Int, count: Int): Int {
        val (start, file) = synchronized(this) {
            if (position >= length) return 0
            segments.floorEntry(position)?.toPair() ?: return -1
        }
        return readSegment(file, position - start, into, offset, count)
    }

    /** Lets go of the segments that end at or before [position]; the one being written stays. */
    fun trimBelow(position: Long) {
        val gone = synchronized(this) {
            val ends = segments.entries.filter { (start, file) -> start != lastStart && start + lengthOf(start, file) <= position }
            ends.forEach { segments.remove(it.key) }
            ends.map { it.value }
        }
        gone.forEach { it.delete() }
    }

    /** How long the segment at [start] is: the next one's start less its own, or the file's for the last. */
    private fun lengthOf(start: Long, file: File): Long = segments.higherKey(start)?.let { it - start } ?: file.length()

    /** How much this TV keeps of it now. */
    val keptBytes: Long get() = synchronized(this) { length - (segments.firstEntry()?.key ?: length) }

    override fun close() = closeSegment()

    /** Lets go of all of it, with its index: the share has it, or it's been deleted. */
    fun delete() {
        close()
        synchronized(this) { segments.clear() }
        folder.deleteRecursively()
    }

    companion object {
        /** About 4 minutes of HD. */
        const val SEGMENT_BYTES = 128L * 1024 * 1024

        internal const val SEGMENT = ".seg"
        internal const val INDEX = "index.idx"

        private fun nameOf(start: Long): String = "%016d%s".format(start, SEGMENT)

        /** Up to [count] bytes at [at] in [file] into [into] at [offset]: how many, or -1 when it's gone. */
        internal fun readSegment(file: File, at: Long, into: ByteArray, offset: Int, count: Int): Int = try {
            RandomAccessFile(file, "r").use { input ->
                input.seek(at)
                input.read(into, offset, count).coerceAtLeast(0)
            }
        } catch (gone: IOException) {
            // Let go of meanwhile.
            -1
        }
    }
}

/**
 * A [Spool] the recorder is writing, read from elsewhere in Plasma as it grows, as chase play
 * does (brief 5.6): what's in its [folder] is looked at afresh each time.
 */
class SpoolView(val folder: File) : SpoolRead {
    private fun segments(): TreeMap<Long, File> = TreeMap<Long, File>().apply {
        folder.listFiles()?.forEach { file ->
            if (file.name.endsWith(Spool.SEGMENT)) file.name.removeSuffix(Spool.SEGMENT).toLongOrNull()?.let { put(it, file) }
        }
    }

    override val oldest: Long get() = segments().firstEntry()?.key ?: Long.MAX_VALUE

    override fun read(position: Long, into: ByteArray, offset: Int, count: Int): Int {
        val (start, file) = segments().floorEntry(position) ?: return -1
        return Spool.readSegment(file, position - start, into, offset, count)
    }

    /** The keyframe index, its places counted as the spool's are. */
    val index: File get() = File(folder, Spool.INDEX)

    /** When it was last written to; 0 before it has been. */
    val writtenAt: Long get() = segments().lastEntry()?.value?.lastModified() ?: 0
}
