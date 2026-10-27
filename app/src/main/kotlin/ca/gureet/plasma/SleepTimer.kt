// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.feature.player.SleepChoice
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What Sleep Timer is doing (brief 4.10). */
sealed interface SleepState {
    data object Off : SleepState

    /** The channel stops at [at], as [choice] said; in its last minute, the notice shows ([warning]). */
    data class Set(val choice: SleepChoice, val at: Instant, val warning: Boolean = false) : SleepState

    /** It stopped the channel, and Plasma waits, dark, for a button. */
    data object Asleep : SleepState
}

/**
 * Player Options › Sleep Timer (brief 4.10). A minute before the time, a notice offers Keep
 * Watching; then [onSleep] stops the picture and sound and lets the provider connection go, and
 * Plasma waits, dark, without keeping the TV awake, so the TV's own power settings can turn it
 * off. A button wakes it, and [onWake] plays the channel again. Plasma can't switch the TV off
 * itself: that takes permissions only the system has.
 */
class SleepTimer(
    private val clock: PlasmaClock,
    private val scope: CoroutineScope,
    private val onSleep: () -> Unit,
    private val onWake: () -> Unit,
) {
    private val _state = MutableStateFlow<SleepState>(SleepState.Off)
    val state: StateFlow<SleepState> = _state.asStateFlow()
    private var job: Job? = null

    /** [choice], picked now: the channel stops [after] this long. Off, or no time, turns it off. */
    fun set(choice: SleepChoice, after: Duration?) {
        job?.cancel()
        job = null
        if (choice == SleepChoice.Off || after == null || after.isNegative) {
            if (_state.value is SleepState.Set) _state.value = SleepState.Off
            return
        }
        val at = clock.now().plus(after)
        _state.value = SleepState.Set(choice, at)
        job = scope.launch {
            waitUntil(at.minus(WARNING))
            _state.value = SleepState.Set(choice, at, warning = true)
            waitUntil(at)
            sleep()
        }
    }

    /** Keep Watching, on the notice: the timer is off. */
    fun keepWatching() = set(SleepChoice.Off, null)

    /** Stop Now, on the notice. */
    fun sleepNow() {
        if (_state.value !is SleepState.Set) return
        job?.cancel()
        job = null
        sleep()
    }

    /** A button while Plasma is dark: the channel plays again. */
    fun wake() {
        if (_state.value != SleepState.Asleep) return
        _state.value = SleepState.Off
        onWake()
    }

    /**
     * Plasma came back to the front, or the TV back on: it isn't dark any more, and what plays is
     * for When the TV Turns On to say (5.13 and 5.20).
     */
    fun awake() {
        if (_state.value == SleepState.Asleep) _state.value = SleepState.Off
    }

    private fun sleep() {
        _state.value = SleepState.Asleep
        onSleep()
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

    private companion object {
        /** The notice shows for the last minute. */
        val WARNING: Duration = Duration.ofMinutes(1)
        const val STEP_MS = 30_000L
    }
}
