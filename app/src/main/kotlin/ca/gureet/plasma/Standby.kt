// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.model.TvTurnsOff
import ca.gureet.plasma.player.PlayerManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Leaving Plasma, and the TV going to standby and coming back (brief 5.20).
 *
 * The screen turning off is the main signal. A box whose TV is switched off with the TV's own
 * remote can stay awake, so on a box (no speakers of its own) the HDMI output going away counts
 * too. The live picture stops at once either way, and the provider connection a minute later
 * unless "When the TV Turns Off" says otherwise; a partial wake lock keeps that minute running.
 */
class Standby(
    private val context: Context,
    private val choices: () -> SettingsChoices,
    /** The player, once it exists: until something has played there's nothing to stop. */
    private val player: () -> PlayerManager?,
    /** The small window is open: what plays carries on there when Plasma's screen goes (5.20). */
    private val inSmallWindow: () -> Boolean = { false },
) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "plasma:standby")?.apply { setReferenceCounted(false) }

    private var inFront = false
    private var tvOff = false

    /** Plasma is on screen: started, and the TV is on. */
    val isOnScreen: Boolean get() = inFront && !tvOff

    /** The TV's screen is on, whatever app is in front. */
    val isTvOn: Boolean get() = !tvOff

    private val tvOnNow = MutableStateFlow(true)

    /** The TV's screen is on, as it changes: local catch-up can pause while it's off (5.5). */
    val tvOn: StateFlow<Boolean> = tvOnNow.asStateFlow()

    private val front = MutableStateFlow(false)

    /** Plasma is the app in front. */
    val inFrontNow: StateFlow<Boolean> = front.asStateFlow()

    /**
     * The TV turned off and on again, so "When the TV Turns On" applies once Plasma is in front,
     * if it comes up as the TV does.
     */
    private var wokeUp = false

    /** When the TV last came on, by the boot clock. */
    private var turnedOnAt = 0L

    /**
     * The TV has just come on, and Plasma hasn't been in front since: Home pressed or Plasma
     * opened now is the TV turning on.
     */
    val waking: Boolean get() = wokeUp && SystemClock.elapsedRealtime() - turnedOnAt < WAKE_MS

    private val _turnedOn = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The TV came back on with Plasma in front: When the TV Turns On decides what shows (5.13). */
    val turnedOn: SharedFlow<Unit> = _turnedOn.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> turnedOff()
                Intent.ACTION_SCREEN_ON -> turnedOn()
                AudioManager.ACTION_HDMI_AUDIO_PLUG -> {
                    // The sticky broadcast on registering only says how things are.
                    if (isInitialStickyBroadcast || !isBox()) return
                    when (intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, -1)) {
                        0 -> turnedOff()
                        1 -> if (power?.isInteractive != false) turnedOn()
                    }
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(AudioManager.ACTION_HDMI_AUDIO_PLUG)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun onActivityStart() {
        inFront = true
        front.value = true
        if (!tvOff) wake()
    }

    fun onActivityStop() {
        inFront = false
        front.value = false
        // The screen going off stops Plasma too; that's the TV turning off, with its own setting.
        if (power?.isInteractive == false) {
            turnedOff()
            return
        }
        // What plays carries on in the small window, until it closes (5.20).
        if (inSmallWindow()) return
        leftForAnotherApp()
    }

    /** Another app is in front: the picture stops, and the connection goes after a minute (5.20). */
    fun leftForAnotherApp() {
        player()?.standby(if (choices().tvTurnsOff == TvTurnsOff.StopAtOnce) 0 else GRACE_MS)
    }

    private fun turnedOff() {
        if (tvOff) return
        tvOff = true
        tvOnNow.value = false
        when (choices().tvTurnsOff) {
            TvTurnsOff.KeepPlaying -> Unit
            TvTurnsOff.StopAtOnce -> player()?.standby(0)
            TvTurnsOff.StopAfterAMinute -> {
                // So the box going to sleep can't freeze the minute (5.20).
                wakeLock?.acquire(WAKE_LOCK_MS)
                player()?.standby(GRACE_MS)
            }
        }
    }

    private fun turnedOn() {
        if (!tvOff) return
        tvOff = false
        tvOnNow.value = true
        wokeUp = true
        turnedOnAt = SystemClock.elapsedRealtime()
        if (wakeLock?.isHeld == true) wakeLock.release()
        if (inFront) {
            wake()
        } else if (inSmallWindow()) {
            // The small window plays on; When the TV Turns On waits for Plasma's own screen.
            player()?.resume()
        }
    }

    private fun wake() {
        player()?.resume()
        // Opened long after the TV came on, from another app, Plasma just carries on.
        if (waking) _turnedOn.tryEmit(Unit)
        wokeUp = false
    }

    /** A box sends its picture out over HDMI and has no speakers; a TV has its own. */
    private fun isBox(): Boolean =
        audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.none { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } == true

    private companion object {
        /** How long the connection outlives the picture (5.20). */
        const val GRACE_MS = 60_000L

        /** The minute, and a little over, so the release itself runs before the box sleeps. */
        const val WAKE_LOCK_MS = 70_000L

        /**
         * How soon after the TV comes on Plasma coming up counts as the TV turning on: time for a
         * profile picker or the launcher to come and go first.
         */
        const val WAKE_MS = 60_000L
    }
}
