// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sends a recording's [spool] to [path] on the network folder as it's written (Record To › Network
 * folder, brief 5.6). Each byte goes to the same place in the file there, so the keyframe index
 * holds for it. Once the share has a stretch, this TV lets go of it but for the last [keepBytes],
 * which a share that loses its last writes when it drops gets again. While the share can't be
 * reached, the rest waits in the spool and the share is tried again every [retryMs]; [away] hears
 * of each time it couldn't be.
 */
class ShareUpload(
    private val spool: Spool,
    private val path: String,
    private val open: () -> ShareSession,
    private val away: suspend (ShareException) -> Unit = {},
    private val keepBytes: Long = KEEP_BYTES,
    private val retryMs: Long = RETRY_MS,
    private val pollMs: Long = POLL_MS,
) {
    /** How much of it the share has. */
    @Volatile var sent: Long = 0
        private set

    /**
     * Sends what's written as it comes, until [over] says the recording's done and the share has
     * all of it: true. False when the share's still away [giveUpMs] after it's done: the rest
     * waits in the spool, for [run] again later.
     */
    suspend fun run(over: () -> Boolean, giveUpMs: Long = GIVE_UP_MS): Boolean = withContext(Dispatchers.IO) { send(over, giveUpMs) }

    private suspend fun send(over: () -> Boolean, giveUpMs: Long): Boolean {
        val buffer = ByteArray(CHUNK)
        var session: ShareSession? = null
        var file: ShareFile? = null
        var doneAt: Long? = null

        fun drop() {
            runCatching { file?.close() }
            runCatching { session?.close() }
            file = null
            session = null
        }

        /** The share couldn't be reached, or took no more: false once it's been away too long after the end. */
        suspend fun wait(failed: ShareException): Boolean {
            drop()
            away(failed)
            val since = doneAt
            if (since != null && System.nanoTime() - since >= giveUpMs * NANOS_PER_MS) return false
            delay(retryMs)
            return true
        }

        try {
            while (true) {
                val done = over()
                if (done && doneAt == null) doneAt = System.nanoTime()
                val target = file ?: try {
                    val opened = open()
                    session = opened
                    opened.openWrite(path).also { writer ->
                        file = writer
                        // What the share has: one that lost its last writes as it dropped gets them again.
                        sent = minOf(writer.size(), spool.length)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failed: Exception) {
                    if (!wait(problemOf(failed))) return false
                    continue
                }
                val length = spool.length
                if (sent >= length) {
                    if (done) return true
                    delay(pollMs)
                    continue
                }
                val count = spool.read(sent, buffer, 0, minOf(CHUNK.toLong(), length - sent).toInt())
                when {
                    // Let go of on this TV before the share had it: that stretch stays a gap there.
                    count < 0 -> sent = spool.oldest
                    count == 0 -> delay(pollMs)
                    else -> try {
                        target.write(buffer, 0, count, sent)
                        sent += count
                        spool.trimBelow(sent - keepBytes)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failed: Exception) {
                        if (!wait(problemOf(failed))) return false
                    }
                }
            }
        } finally {
            drop()
        }
    }

    /** Whatever went wrong, as the share's problem: nothing the share does stops the recording. */
    private fun problemOf(failed: Exception): ShareException = failed as? ShareException ?: ShareException(ShareProblem.Refused, failed)

    companion object {
        /** The last few minutes, kept on this TV after the share has them. */
        const val KEEP_BYTES = 256L * 1024 * 1024

        const val RETRY_MS = 30_000L

        /** After the end, how long the share is waited for before the rest waits for the copy job. */
        const val GIVE_UP_MS = 60_000L

        private const val POLL_MS = 250L
        private const val CHUNK = 1024 * 1024
        private const val NANOS_PER_MS = 1_000_000L
    }
}
