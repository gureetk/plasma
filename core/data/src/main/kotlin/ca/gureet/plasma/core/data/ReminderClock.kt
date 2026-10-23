// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reminders coming due (brief 5.7): a minute before a programme starts, [due] holds it, and the
 * screen showing puts up the toast from 4.11 until it's acted on (2.6) or the programme ends.
 * While Plasma isn't on screen the reminder is skipped, since TVs don't show notifications; an
 * overlay over other apps comes with "Display over other apps" (5.7).
 */
class ReminderClock(
    private val store: ReminderStore,
    private val clock: PlasmaClock,
    private val scope: CoroutineScope,
    /** Whether Plasma is on screen now. */
    private val onScreen: () -> Boolean,
) {
    /** Reminders that came due and haven't been acted on, in the order they came. */
    private val waiting = MutableStateFlow<List<Reminder>>(emptyList())

    /** The reminder the toast shows: the first that came due and hasn't been acted on. */
    val due: StateFlow<Reminder?> = waiting.map { it.firstOrNull() }.stateIn(scope, SharingStarted.Eagerly, null)

    fun start() {
        scope.launch {
            attempt { store.pruneStarted() }
            // Each change to the reminders starts over, from the soonest.
            store.reminders.collectLatest { reminders ->
                for (reminder in reminders) {
                    waitUntil(reminder.start.minus(LEAD))
                    if (clock.now().isBefore(reminder.start) && onScreen()) waiting.update { it + reminder }
                    // Shown or skipped, it's done either way.
                    attempt { store.cancel(reminder.id) }
                }
            }
        }
        // Once its programme has ended, a toast has nothing more to offer.
        scope.launch {
            waiting.collectLatest { list ->
                val soonest = list.minOfOrNull { it.end } ?: return@collectLatest
                waitUntil(soonest)
                val now = clock.now()
                waiting.update { current -> current.filter { it.end.isAfter(now) } }
            }
        }
    }

    /** Switch Now, Dismiss or Back on the toast for [id]; the next reminder waiting shows. */
    fun done(id: String) {
        waiting.update { list -> list.filterNot { it.id == id } }
    }

    /**
     * Waits for [at] in short steps, so time the TV spends asleep, which a single long delay
     * wouldn't count, is noticed when it wakes.
     */
    private suspend fun waitUntil(at: Instant) {
        while (true) {
            val left = Duration.between(clock.now(), at).toMillis()
            if (left <= 0) return
            delay(left.coerceAtMost(STEP_MS))
        }
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            // The database will be there next time.
        }
    }

    private companion object {
        /** How long before the start the toast comes up (brief 5.7). */
        val LEAD: Duration = Duration.ofMinutes(1)
        const val STEP_MS = 30_000L
    }
}
