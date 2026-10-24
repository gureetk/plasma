// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import java.io.File
import java.io.RandomAccessFile

/**
 * A file still being written, as a recording is (chase play, brief 5.6), read from byte [from]:
 * [tables], the stream's PAT and PMT, go first when it starts partway in, so the engine knows what
 * it reads; then what's there, and at the end it waits for more until [done] says the writing's
 * over.
 *
 * While it's written, only whole packets go, so that after [fillAfterMs] with nothing new (the
 * recording's source has dropped, and it's trying again) a null packet can: the engine keeps
 * listening instead of giving up on the connection and starting again from where it opened.
 */
class GrowingFileReader(
    file: File,
    from: Long,
    tables: ByteArray? = null,
    private val done: () -> Boolean,
    private val pollMs: Long = POLL_MS,
    private val fillAfterMs: Long = FILL_AFTER_MS,
) : LocalReader {
    private val input = RandomAccessFile(file, "r").apply { seek(from.coerceIn(0, length())) }
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
            val count = if (over) input.read(into) else readPackets(into)
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

    /** Whole packets from the file, as far as they're written; the rest waits for more. */
    private fun readPackets(into: ByteArray): Int {
        if (into.size < PACKET) {
            inStep = false
            return input.read(into)
        }
        val start = input.filePointer
        val count = input.read(into)
        if (count <= 0) return count
        val whole = wholePackets(into, count)
        input.seek(start + whole)
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

    override fun close() = input.close()

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
    val scanner = TsScanner()
    RandomAccessFile(file, "r").use { input ->
        val chunk = ByteArray(CHUNK)
        var left = scanBytes
        while (left > 0) {
            val count = input.read(chunk, 0, minOf(chunk.size, left))
            if (count <= 0) break
            scanner.feed(chunk, 0, count)
            scanner.tables()?.let { return it }
            left -= count
        }
    }
    return scanner.tables()
}

private const val TABLES_SCAN = 4 * 1024 * 1024
private const val CHUNK = 64 * 1024
