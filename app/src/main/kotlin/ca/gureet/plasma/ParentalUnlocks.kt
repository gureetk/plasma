// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.gureet.plasma.core.data.SettingsStore
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.feature.profiles.PinTries
import ca.gureet.plasma.feature.profiles.UnlockFor
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Which locked channels the PIN has opened, and until when (brief 4.25). */
interface ChannelUnlocks {
    fun isUnlocked(channelId: String): Boolean

    /** [channelId] opened with the PIN, for the program ending at [programEnd], an hour, or until the TV sleeps. */
    fun unlock(channelId: String, programEnd: Instant?, unlockFor: UnlockFor)
}

/** Opened until when, as [unlockFor] says, from [now]. */
private fun until(now: Instant, programEnd: Instant?, unlockFor: UnlockFor): Instant {
    val hour = now.plus(Duration.ofHours(1))
    return when (unlockFor) {
        UnlockFor.Program -> programEnd ?: hour
        UnlockFor.Hour -> hour
        UnlockFor.UntilSleep -> Instant.MAX
    }
}

/** Where the parental PIN is kept, as a salted hash (5.12). */
interface PinStore {
    /** Whether a PIN is set. */
    val pinSet: Flow<Boolean>

    /** Sets the PIN; null takes it away. */
    suspend fun setPin(pin: String?)

    /** Whether [pin] is the PIN; never when none is set. */
    suspend fun isPin(pin: String): Boolean
}

/** The PIN as Settings keeps it. */
fun SettingsStore.pins(): PinStore = object : PinStore {
    override val pinSet: Flow<Boolean> get() = this@pins.pinSet

    override suspend fun setPin(pin: String?) = this@pins.setPin(pin)

    override suspend fun isPin(pin: String): Boolean = this@pins.isPin(pin)
}

/** The sample world's locked channel, opened with the sample PIN (brief appendix A). */
@Stable
class ParentalUnlocks(private val clock: PlasmaClock) : ChannelUnlocks {
    private val until = mutableStateMapOf<String, Instant>()

    override fun isUnlocked(channelId: String): Boolean = until[channelId]?.isAfter(clock.now()) == true

    override fun unlock(channelId: String, programEnd: Instant?, unlockFor: UnlockFor) {
        until[channelId] = until(clock.now(), programEnd, unlockFor)
    }
}

/**
 * Parental controls (brief 5.12 and 4.25): the PIN, kept as a salted hash, and what it's opened
 * for how long. They're on once a PIN is set; until then nothing is locked. Five wrong tries,
 * wherever the PIN's asked for, stop entry for a minute ([tries]). Settings opened with the PIN
 * (Lock Settings) stays open until Home, or the TV sleeps.
 */
@Stable
class ParentalControls(private val pins: PinStore, private val clock: PlasmaClock, scope: CoroutineScope) : ChannelUnlocks {
    /** Whether a PIN is set; null until it's been read, when everything counts as locked. */
    val pinSet: StateFlow<Boolean?> = pins.pinSet.stateIn(scope, SharingStarted.Eagerly, null)

    /** Whether parental controls are on: a PIN is set, or it isn't known yet that there's none. */
    val on: Boolean get() = pinSet.value != false

    val tries = PinTries()
    private val until = mutableStateMapOf<String, Instant>()

    /** Settings has been opened with the PIN (Lock Settings, 5.12), until Home or the TV sleeps. */
    var settingsOpen by mutableStateOf(false)

    /** How many times the TV has slept: what the PIN opened for a while counts from the last time. */
    var sleeps by mutableIntStateOf(0)
        private set

    override fun isUnlocked(channelId: String): Boolean = !on || until[channelId]?.isAfter(clock.now()) == true

    override fun unlock(channelId: String, programEnd: Instant?, unlockFor: UnlockFor) {
        until[channelId] = until(clock.now(), programEnd, unlockFor)
    }

    /** Whether [pin] is the PIN. Never when none is set. */
    suspend fun check(pin: String): Boolean = withContext(Dispatchers.Default) { pins.isPin(pin) }

    /** Sets the PIN, or with null takes it away: parental controls are off until there's one again. */
    suspend fun setPin(pin: String?) {
        pins.setPin(pin)
        if (pin == null) {
            until.clear()
            settingsOpen = false
        }
    }

    /** The TV went to sleep: what was opened until then is locked again, and so is Settings. */
    fun slept() {
        until.filterValues { it == Instant.MAX }.keys.forEach(until::remove)
        settingsOpen = false
        sleeps++
    }
}
