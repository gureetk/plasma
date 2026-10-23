// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.feature.profiles.UnlockFor
import java.time.Duration
import java.time.Instant

/**
 * The locked channels opened with the PIN, and until when (brief 4.25): the end of the program,
 * an hour, or until the TV sleeps (while the app runs, until standby is handled in M5).
 */
@Stable
class ParentalUnlocks(private val clock: PlasmaClock) {
    private val until = mutableStateMapOf<String, Instant>()

    fun isUnlocked(channelId: String): Boolean = until[channelId]?.isAfter(clock.now()) == true

    fun unlock(channelId: String, programEnd: Instant?, unlockFor: UnlockFor) {
        val hour = clock.now().plus(Duration.ofHours(1))
        until[channelId] = when (unlockFor) {
            UnlockFor.Program -> programEnd ?: hour
            UnlockFor.Hour -> hour
            UnlockFor.UntilSleep -> Instant.MAX
        }
    }
}
