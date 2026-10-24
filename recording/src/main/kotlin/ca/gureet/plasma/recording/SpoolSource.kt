// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.stream.GrowingSource

/**
 * A recording on its way to the network folder, read as it's written (chase play, brief 5.6): what
 * this TV still keeps, from its [spool], and what's older from the share, opened with [openShare]
 * when it's first needed and again after it drops, no more often than every [retryMs].
 */
class SpoolSource(
    private val spool: SpoolRead,
    private val openShare: () -> ShareFile,
    private val retryMs: Long = RETRY_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) : GrowingSource {
    private var share: ShareFile? = null

    /** When the share last couldn't be opened or read. */
    private var failedAt: Long? = null

    override fun read(position: Long, into: ByteArray, offset: Int, count: Int): Int {
        if (position >= spool.oldest) {
            val read = spool.read(position, into, offset, count)
            if (read >= 0) return read
        }
        // Older than this TV keeps: from the share.
        val file = share ?: run {
            val since = failedAt
            if (since != null && clock() - since < retryMs) return 0
            try {
                openShare().also { share = it }
            } catch (failed: ShareException) {
                failedAt = clock()
                return 0
            }
        }
        return try {
            file.read(into, offset, count, position)
        } catch (failed: ShareException) {
            runCatching { file.close() }
            share = null
            failedAt = clock()
            0
        }
    }

    override fun close() {
        runCatching { share?.close() }
        share = null
    }

    private companion object {
        const val RETRY_MS = 10_000L
    }
}
