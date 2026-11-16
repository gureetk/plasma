// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Finds the posters a catalog doesn't have, as titles come on screen without one (the owner's
 * #81), or with one that doesn't load (#228): each title [want]ed has its details read for its own
 * poster ([lookUp]). The latest wanted go first, [WORKERS] at a time, so the titles on screen come
 * before those scrolled past, and a title is asked once while Plasma runs. Lazily, rather than the
 * whole catalog at once, as a provider may not like thousands of requests together. Movies' and
 * series' finders share their [slots], so they're [WORKERS] at a time together; [PosterSweep]
 * looks titles up through them too ([findNow]).
 *
 * What's found is kept ([keep]) as it comes, but at most once each [KEEP_EVERY_MS], all that came
 * in the meantime together, so a long list showing the titles changes once for them all rather
 * than once for each.
 */
class PosterFinder<T : Any> internal constructor(
    private val lookUp: suspend (String) -> T?,
    private val keep: suspend (List<T>) -> Unit,
    private val slots: Semaphore = Semaphore(WORKERS),
) {
    private val lock = Any()
    private val waiting = ArrayDeque<String>()
    private val asked = HashSet<String>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val read = AtomicInteger()
    private val failures = ConcurrentHashMap<String, Int>()

    /** How the lookups have gone since Plasma started, for Export Diagnostics. */
    class Tally(
        /** Titles whose details were read. */
        val read: Int,
        /** Lookups that failed, by the kind of failure. */
        val failed: Map<String, Int>,
    )

    val tally: Tally get() = Tally(read.get(), HashMap(failures))

    /** The title [id] showed without a poster: its own is looked for, if it hasn't been. */
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
        val found = Channel<T>(Channel.UNLIMITED)
        coroutineScope {
            launch { keepAsFound(found) }
            while (true) {
                // A slot first, then the latest wanted; none is held while nothing's wanted, as
                // the other finder and the sweep share them.
                slots.acquire()
                val wanted = next()
                if (wanted == null) {
                    slots.release()
                    wake.receive()
                    continue
                }
                launch {
                    try {
                        lookedUp(wanted)?.let { found.send(it) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failed: Exception) {
                        // Counted: the title keeps its name on the plate, and is looked for again
                        // the next time Plasma starts.
                    } finally {
                        slots.release()
                    }
                }
            }
        }
    }

    /**
     * Looks [id] up at once, as one of the [WORKERS], and keeps what's found: for [PosterSweep],
     * a title at a time. True when its details were read, false when it wasn't asked; a lookup that
     * fails throws, as the provider is out of reach or turned it down.
     */
    internal suspend fun findNow(id: String): Boolean {
        val details = slots.withPermit { lookedUp(id) } ?: return false
        keep(listOf(details))
        return true
    }

    /** [lookUp], counted. */
    private suspend fun lookedUp(id: String): T? = try {
        lookUp(id)?.also { read.incrementAndGet() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        failures.merge(failed.javaClass.simpleName.ifEmpty { "Exception" }, 1, Int::plus)
        throw failed
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
