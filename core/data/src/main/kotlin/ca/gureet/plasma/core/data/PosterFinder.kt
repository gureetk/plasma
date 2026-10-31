// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * Finds the posters a catalog doesn't have, as titles come on screen without one (the owner's
 * #81): each movie [want]ed has its details read for its own poster ([VodRepository.findPoster]).
 * The latest wanted go first, [WORKERS] at a time, so the titles on screen come before those
 * scrolled past, and a title is asked once while Plasma runs. Lazily, rather than the whole catalog
 * at once, as a provider may not like thousands of requests together.
 */
class PosterFinder internal constructor(private val find: suspend (String) -> Unit) {
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
        coroutineScope {
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
                        find(wanted)
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

    companion object {
        /** How many titles are looked for at once. */
        const val WORKERS = 2

        /** How many wait at most; the oldest drop off. */
        const val WAITING = 120
    }
}
