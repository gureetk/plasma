// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.model.TvTurnsOff
import ca.gureet.plasma.player.PlayerManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "plasma:standby")?.apply { setReferenceCounted(false) }

    private var inFront = false
    private var tvOff = false

    /** Plasma is on screen: started, and the TV is on. */
    val isOnScreen: Boolean get() = inFront && !tvOff

    /** The TV turned off and on again, so "When the TV Turns On" applies once Plasma is in front. */
    private var wokeUp = false

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
        if (!tvOff) wake()
    }

    fun onActivityStop() {
        inFront = false
        // The screen going off stops Plasma too; that's the TV turning off, with its own setting.
        if (power?.isInteractive == false) {
            turnedOff()
            return
        }
        // Another app is in front: the picture stops, and the connection goes after a minute (5.20).
        player()?.standby(if (choices().tvTurnsOff == TvTurnsOff.StopAtOnce) 0 else GRACE_MS)
    }

    private fun turnedOff() {
        if (tvOff) return
        tvOff = true
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
        wokeUp = true
        if (wakeLock?.isHeld == true) wakeLock.release()
        if (inFront) wake()
    }

    private fun wake() {
        player()?.resume()
        if (wokeUp) {
            wokeUp = false
            _turnedOn.tryEmit(Unit)
        }
    }

    /** A box sends its picture out over HDMI and has no speakers; a TV has its own. */
    private fun isBox(): Boolean =
        audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.none { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } == true

    private companion object {
        /** How long the connection outlives the picture (5.20). */
        const val GRACE_MS = 60_000L

        /** The minute, and a little over, so the release itself runs before the box sleeps. */
        const val WAKE_LOCK_MS = 70_000L
    }
}
