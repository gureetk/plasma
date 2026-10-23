// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

/**
 * The last few megabytes of an upstream (brief 5.4.2: 8 MB, about 8 s at 8 Mb/s). One writer
 * appends; any number of [Cursor]s read behind it at their own pace. Positions count every byte
 * since the upstream opened, so a reader that falls more than the ring behind skips ahead.
 */
internal class Ring(val capacity: Int) {
    private val buffer = ByteArray(capacity)
    private val lock = Object()

    /** Bytes written since the upstream opened. */
    @Volatile var written: Long = 0
        private set

    @Volatile private var ended = false

    /** The oldest position still held. */
    val oldest: Long get() = maxOf(0, written - capacity)

    fun write(source: ByteArray, offset: Int, length: Int) = synchronized(lock) {
        var from = offset
        var remaining = length
        while (remaining > 0) {
            val at = (written % capacity).toInt()
            val count = minOf(remaining, capacity - at)
            System.arraycopy(source, from, buffer, at, count)
            written += count
            from += count
            remaining -= count
        }
        lock.notifyAll()
    }

    /** No more bytes will come: readers finish what's held and then see the end. */
    fun end() = synchronized(lock) {
        ended = true
        lock.notifyAll()
    }

    fun cursor(position: Long): Cursor = Cursor(position)

    /**
     * Where a TS packet carrying the start of a PAT (PID 0) last began, so a reader that joins
     * mid-stream starts where an engine can make sense of it (brief 5.4.2). Null when there's none.
     */
    fun latestPat(): Long? = synchronized(lock) {
        val first = oldest
        var at = written - TS_PACKET
        while (at >= first) {
            if (byteAt(at) == SYNC && isAligned(at, first)) {
                // Once in step with the packets, walk back a packet at a time.
                while (at >= first && byteAt(at) == SYNC) {
                    val flags = byteAt(at + 1)
                    val pid = ((flags and 0x1F) shl 8) or byteAt(at + 2)
                    if (pid == 0 && flags and PAYLOAD_START != 0) return at
                    at -= TS_PACKET
                }
            } else {
                at--
            }
        }
        null
    }

    private fun isAligned(at: Long, first: Long): Boolean {
        val next = at + TS_PACKET
        val previous = at - TS_PACKET
        return (next >= written || byteAt(next) == SYNC) && (previous < first || byteAt(previous) == SYNC)
    }

    private fun byteAt(position: Long): Int = buffer[(position % capacity).toInt()].toInt() and 0xFF

    inner class Cursor internal constructor(position: Long) {
        var position: Long = position
            private set

        /**
         * Copies what's there from [position] into [into], waiting up to [waitMs] for something to
         * arrive. Returns the bytes copied, 0 when nothing came in time, or -1 at the end.
         */
        fun read(into: ByteArray, waitMs: Long): Int = synchronized(lock) {
            if (position < oldest) position = oldest
            if (position >= written) {
                if (ended) return -1
                if (waitMs > 0) lock.wait(waitMs)
                if (position < oldest) position = oldest
                if (position >= written) return if (ended) -1 else 0
            }
            val available = minOf(written - position, into.size.toLong()).toInt()
            var copied = 0
            while (copied < available) {
                val at = ((position + copied) % capacity).toInt()
                val count = minOf(available - copied, capacity - at)
                System.arraycopy(buffer, at, into, copied, count)
                copied += count
            }
            position += copied
            copied
        }
    }

    companion object {
        const val TS_PACKET = 188
        const val SYNC = 0x47
        private const val PAYLOAD_START = 0x40
    }
}
