// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val _due = MutableStateFlow<Reminder?>(null)
    val due: StateFlow<Reminder?> = _due.asStateFlow()

    fun start() {
        scope.launch {
            attempt { store.pruneStarted() }
            // Each change to the reminders starts over, from the soonest.
            store.reminders.collectLatest { reminders ->
                for (reminder in reminders) {
                    waitUntil(reminder.start.minus(LEAD))
                    if (clock.now().isBefore(reminder.start) && onScreen()) _due.value = reminder
                    // Shown or skipped, it's done either way.
                    attempt { store.cancel(reminder.id) }
                }
            }
        }
        // Once the programme has ended, the toast has nothing more to offer.
        scope.launch {
            _due.collectLatest { shown ->
                if (shown == null) return@collectLatest
                waitUntil(shown.end)
                _due.compareAndSet(shown, null)
            }
        }
    }

    /** Switch Now, Dismiss or Back. */
    fun done() {
        _due.value = null
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
