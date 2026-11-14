// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * Finds the posters a catalog doesn't have, as titles come on screen without one (the owner's
 * #81), or with one that doesn't load (#228): each movie [want]ed has its details read for its own
 * poster ([lookUp]). The latest wanted go first, [WORKERS] at a time, so the titles on screen come
 * before those scrolled past, and a title is asked once while Plasma runs. Lazily, rather than the
 * whole catalog at once, as a provider may not like thousands of requests together.
 *
 * What's found is kept ([keep]) as it comes, but at most once each [KEEP_EVERY_MS], all that came
 * in the meantime together, so a long list showing the titles changes once for them all rather
 * than once for each.
 */
class PosterFinder<T : Any> internal constructor(
    private val lookUp: suspend (String) -> T?,
    private val keep: suspend (List<T>) -> Unit,
) {
    private val lock = Any()
    private val waiting = ArrayDeque<String>()
    private val asked = HashSet<String>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** The movie [id] showed without a poster: its own is looked for, if it hasn't been. */
    fun want(id: String) {
        synchronized(lock) {
            if (!asked.add(id)) return
            waiting.addFirst(id)
            // Titles scrolled past long ago drop off, to be looked for again if they show again.
            while (waiting.size > WAITING) asked.remove(waiting.removeLast())
        }
        wake.trySend(Unit)
    }

    private fun next(): String? = synchronized(lock) { waiting.removeFirstOrNull() }

    /** Finds posters as they're wanted, until cancelled. */
    suspend fun run() {
        val slots = Semaphore(WORKERS)
        val found = Channel<T>(Channel.UNLIMITED)
        coroutineScope {
            launch { keepAsFound(found) }
            while (true) {
                slots.acquire()
                var id = next()
                while (id == null) {
                    wake.receive()
                    id = next()
                }
                val wanted: String = id
                launch {
                    try {
                        lookUp(wanted)?.let { found.send(it) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failed: Exception) {
                        // Quietly: the title keeps its name on the plate, and is looked for again
                        // the next time Plasma starts.
                    } finally {
                        slots.release()
                    }
                }
            }
        }
    }

    /** Keeps what's [found]: the first at once, then all that came in each [KEEP_EVERY_MS] together. */
    private suspend fun keepAsFound(found: ReceiveChannel<T>) {
        while (true) {
            val batch = mutableListOf(found.receive())
            while (true) batch += found.tryReceive().getOrNull() ?: break
            try {
                keep(batch)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                // Quietly, as a lookup that fails: they're looked for again the next time Plasma starts.
            }
            delay(KEEP_EVERY_MS)
        }
    }

    companion object {
        /** How many titles are looked for at once. */
        const val WORKERS = 4

        /** How many wait at most; the oldest drop off. */
        const val WAITING = 120

        /** How often what's found is kept, at most. */
        const val KEEP_EVERY_MS = 500L
    }
}
