// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import java.io.File
import java.io.RandomAccessFile

/**
 * Where a [GrowingFileReader] reads a recording from as it's written: its file, or a recording on
 * its way to the network folder, part on this TV and part on the share.
 */
interface GrowingSource : AutoCloseable {
    /** Up to [count] bytes at [position] into [into] from [offset]: how many, or 0 when nothing's there yet. */
    fun read(position: Long, into: ByteArray, offset: Int, count: Int): Int
}

/** A file on this TV, as a [GrowingSource]. */
class FileSource(file: File) : GrowingSource {
    private val input = RandomAccessFile(file, "r")

    override fun read(position: Long, into: ByteArray, offset: Int, count: Int): Int {
        input.seek(position)
        return input.read(into, offset, count).coerceAtLeast(0)
    }

    override fun close() = input.close()
}

/**
 * A recording still being written (chase play, brief 5.6), read from [source] from byte [from]:
 * [tables], the stream's PAT and PMT, go first when it starts partway in, so the engine knows what
 * it reads; then what's there, and at the end it waits for more until [done] says the writing's
 * over.
 *
 * While it's written, only whole packets go, so that after [fillAfterMs] with nothing new (the
 * recording's source has dropped, and it's trying again) a null packet can: the engine keeps
 * listening instead of giving up on the connection and starting again from where it opened.
 */
class GrowingFileReader(
    private val source: GrowingSource,
    from: Long,
    tables: ByteArray? = null,
    private val done: () -> Boolean,
    private val pollMs: Long = POLL_MS,
    private val fillAfterMs: Long = FILL_AFTER_MS,
) : LocalReader {
    /** [file] on this TV, from byte [from]. */
    constructor(
        file: File,
        from: Long,
        tables: ByteArray? = null,
        done: () -> Boolean,
        pollMs: Long = POLL_MS,
        fillAfterMs: Long = FILL_AFTER_MS,
    ) : this(FileSource(file), from.coerceIn(0, file.length()), tables, done, pollMs, fillAfterMs)

    /** Where in it the next byte comes from. */
    private var position = from
    private var tablesLeft = if (from > 0) tables else null

    /** When it last sent something. */
    private var sentAt = System.nanoTime()

    /** Whether what it has sent ends where a packet does, so a null packet can follow. */
    private var inStep = true

    override fun read(into: ByteArray, waitMs: Long): Int {
        tablesLeft?.let { first ->
            val count = minOf(first.size, into.size)
            first.copyInto(into, 0, 0, count)
            tablesLeft = first.copyOfRange(count, first.size).takeIf { it.isNotEmpty() }
            return count
        }
        val deadline = System.nanoTime() + waitMs * NANOS_PER_MS
        while (true) {
            // Asked before reading, so nothing written just before the end is missed.
            val over = done()
            val count = if (over) readAll(into) else readPackets(into)
            val now = System.nanoTime()
            if (count > 0) {
                sentAt = now
                return count
            }
            if (over) return -1
            if (inStep && into.size >= PACKET && now - sentAt >= fillAfterMs * NANOS_PER_MS) {
                sentAt = now
                NULL_PACKET.copyInto(into)
                return PACKET
            }
            if (now >= deadline) return 0
            Thread.sleep(pollMs)
        }
    }

    /** What's there, up to the end: -1 at the end. */
    private fun readAll(into: ByteArray): Int {
        val count = source.read(position, into, 0, into.size)
        if (count <= 0) return -1
        position += count
        return count
    }

    /** Whole packets, as far as they're written; the rest waits for more. */
    private fun readPackets(into: ByteArray): Int {
        val count = source.read(position, into, 0, into.size)
        if (count <= 0) return 0
        val whole = if (into.size < PACKET) {
            inStep = false
            count
        } else {
            wholePackets(into, count)
        }
        position += whole
        return whole
    }

    /**
     * How many of the [count] bytes in [buffer] go now: up to where the last packet that isn't all
     * there yet starts. A packet the stream broke off partway, as when a recording's source drops
     * and it starts again, is passed over to the next start; [inStep] says whether there is one.
     */
    private fun wholePackets(buffer: ByteArray, count: Int): Int {
        var at = 0
        var synced = true
        while (at < count) {
            if (buffer[at] != SYNC) {
                at = nextStart(buffer, at + 1, count)
                synced = at < count
                continue
            }
            // A packet that isn't all there yet starts here.
            if (at + PACKET > count) break
            at += PACKET
        }
        inStep = synced
        return at
    }

    /** The next place from [from] that a packet starts: a sync byte with another a packet on, or the end after it. */
    private fun nextStart(buffer: ByteArray, from: Int, count: Int): Int {
        var at = from
        while (at < count && !(buffer[at] == SYNC && (at + PACKET >= count || buffer[at + PACKET] == SYNC))) at++
        return at
    }

    override fun close() = source.close()

    internal companion object {
        const val POLL_MS = 100L

        /** Engines give up on a connection after about a minute without data. */
        const val FILL_AFTER_MS = 5_000L
        const val NANOS_PER_MS = 1_000_000L
        const val PACKET = 188
        const val SYNC: Byte = 0x47

        /** A packet on the null PID, 0x1FFF, that every demuxer skips: stuffing. */
        val NULL_PACKET = ByteArray(PACKET) { 0xFF.toByte() }.apply {
            this[0] = SYNC
            this[1] = 0x1F
            this[2] = 0xFF.toByte()
            this[3] = 0x10
        }
    }
}

/**
 * The PAT and PMT at the start of an MPEG-TS [file], from its first [scanBytes]: what an engine
 * needs to start partway in. Null when they're not there.
 */
fun tablesOf(file: File, scanBytes: Int = TABLES_SCAN): ByteArray? {
    if (!file.isFile) return null
    return FileSource(file).use { tablesOf(it, scanBytes) }
}

/** The PAT and PMT at the start of what [source] reads, from its first [scanBytes], as [tablesOf] a file. */
fun tablesOf(source: GrowingSource, scanBytes: Int = TABLES_SCAN): ByteArray? {
    val scanner = TsScanner()
    val chunk = ByteArray(CHUNK)
    var at = 0L
    while (at < scanBytes) {
        val count = source.read(at, chunk, 0, minOf(CHUNK.toLong(), scanBytes - at).toInt())
        if (count <= 0) break
        scanner.feed(chunk, 0, count)
        scanner.tables()?.let { return it }
        at += count
    }
    return scanner.tables()
}

private const val TABLES_SCAN = 4 * 1024 * 1024
private const val CHUNK = 64 * 1024
