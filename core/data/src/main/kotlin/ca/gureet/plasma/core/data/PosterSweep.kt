// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Looks posters up while the screensaver is up (#228), so a title whose catalog poster is a
 * placeholder, missing or doesn't load has its own before it's on screen. Each list is taken a
 * screen at a time ([VodRepository.SWEEP_PAGE]), a round of every list before the next of any, so
 * the top of each category comes first: Recently Added and the movie categories, then New Episodes
 * and the series categories ([VodRepository.sweepLists]).
 *
 * Gently, as each lookup is a request to the provider, and a provider can turn away an address
 * that sends too many, live TV with it: one title at a time, [PAUSE_MS] after each whose details
 * were read. A lookup that fails stops the sweep for [REST_MS], and its title isn't looked for
 * again for a week ([VodRepository.lookedFor]). It stops at once when the screensaver goes, and
 * carries on from the round it reached the next time; a sweep that reached the end of every list
 * starts again from the top after [AGAIN_AFTER_MS], for titles new since.
 */
class PosterSweep internal constructor(
    private val lists: suspend () -> List<VodRepository.SweepList>,
    private val page: suspend (VodRepository.SweepList, Int) -> List<Title>?,
    /** Looks a title up and keeps what's found: true when its details were read; throws when that fails. */
    private val find: suspend (Title) -> Boolean,
    private val lookedFor: suspend (Title) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** A movie, or a series, that looks for its own poster. */
    data class Title(val id: String, val series: Boolean)

    /** How the sweeps have gone since Plasma started, for Export Diagnostics. */
    data class Tally(
        /** Titles whose details were read. */
        val read: Int,
        /** When the last one was, in Unix ms. */
        val lastReadAt: Long?,
        /** The kind of failure that stopped the last sweep, if one did. */
        val stoppedBy: String?,
        /** When a sweep last reached the end of every list, in Unix ms. */
        val finishedAt: Long?,
    )

    @Volatile private var read = 0
    @Volatile private var lastReadAt: Long? = null
    @Volatile private var stoppedBy: String? = null
    @Volatile private var finishedAt: Long? = null
    @Volatile private var restUntil = 0L
    @Volatile private var round = 0

    val tally: Tally get() = Tally(read, lastReadAt, stoppedBy, finishedAt)

    /** Sweeps each time [screensaver] says it's up, [START_AFTER_MS] after, until it's down again. Until cancelled. */
    suspend fun run(screensaver: Flow<Boolean>) {
        screensaver.distinctUntilChanged().collectLatest { up ->
            if (!up) return@collectLatest
            delay(START_AFTER_MS)
            sweep()
        }
    }

    /** One sweep, from the round the last one reached, until every list has run out or a lookup fails. */
    internal suspend fun sweep() {
        val start = now()
        if (start < restUntil) return
        val finished = finishedAt
        if (round == 0 && finished != null && start - finished < AGAIN_AFTER_MS) return
        var going = lists()
        // None while Replace Placeholder Posters is off: nothing's looked for, nor finished.
        if (going.isEmpty()) return
        while (going.isNotEmpty()) {
            val left = ArrayList<VodRepository.SweepList>(going.size)
            for (list in going) {
                val titles = page(list, round) ?: continue
                left += list
                for (title in titles) {
                    val wasRead = try {
                        find(title)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failed: Exception) {
                        try {
                            lookedFor(title)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (alsoFailed: Exception) {
                            // It's looked for again next time, then.
                        }
                        stoppedBy = failed.javaClass.simpleName.ifEmpty { "Exception" }
                        restUntil = now() + REST_MS
                        return
                    }
                    if (wasRead) {
                        read++
                        lastReadAt = now()
                        delay(PAUSE_MS)
                    }
                }
            }
            going = left
            round++
        }
        round = 0
        finishedAt = now()
        stoppedBy = null
    }

    internal companion object {
        /** How long the screensaver is up before the sweep starts: a pause in watching often isn't long. */
        const val START_AFTER_MS = 60_000L

        /** The pause after each title whose details were read. */
        const val PAUSE_MS = 2_000L

        /** How long a sweep that a failed lookup stopped waits before the next. */
        const val REST_MS = 60L * 60 * 1000

        /** How long after reaching the end of every list a sweep starts again from the top. */
        const val AGAIN_AFTER_MS = 6L * 60 * 60 * 1000
    }
}
