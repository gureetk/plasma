// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Settings › TV Guide › Switch Automatically (#115; brief 5.7): the reminder showing counts down
 * [COUNTDOWN] on its toast, and at zero the toast switches to its channel, as Switch Now does. The
 * count ends as the program starts, or [COUNTDOWN] after the reminder shows when that's later, so
 * there's always time to Dismiss it. It counts only while [running]: the setting is on and Plasma
 * is in front. Coming back to Plasma starts it again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderCountdown(
    due: Flow<Reminder?>,
    running: Flow<Boolean>,
    private val clock: PlasmaClock,
    scope: CoroutineScope,
) {
    /** The count for the reminder [reminderId]: [left] seconds once it's counting, null before. */
    data class Count(val reminderId: String, val left: Int?)

    /** The count for the reminder showing; null when nothing counts. */
    val count: StateFlow<Count?> = combine(due, running) { reminder, on -> reminder.takeIf { on } }
        .distinctUntilChanged()
        .flatMapLatest { reminder -> if (reminder == null) flowOf(null) else counting(reminder) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun counting(reminder: Reminder): Flow<Count> = flow {
        val ends = maxOf(reminder.start, clock.now().plus(COUNTDOWN))
        while (true) {
            val ms = Duration.between(clock.now(), ends).toMillis()
            val left = if (ms > COUNTDOWN.toMillis()) null else ((ms + SECOND_MS - 1) / SECOND_MS).toInt().coerceAtLeast(0)
            emit(Count(reminder.id, left))
            if (left == 0) return@flow
            // To the count's start, then to its next second, in short steps, so time the TV spends
            // asleep is noticed when it wakes.
            val wait = if (left == null) ms - COUNTDOWN.toMillis() else ms - (left - 1) * SECOND_MS
            delay(wait.coerceIn(1, STEP_MS))
        }
    }

    companion object {
        /** How long the toast counts down before it switches. */
        val COUNTDOWN: Duration = Duration.ofSeconds(10)
        private const val SECOND_MS = 1_000L
        private const val STEP_MS = 30_000L
    }
}
