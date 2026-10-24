// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Rational
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.StreamKeys
import kotlinx.coroutines.launch

/**
 * The small window (brief 5.20): what plays, alone in a corner, while another app is in front.
 * Android lets only an ordinary activity go small, and Plasma's own screen may be the TV's Home,
 * so the window is an activity of its own sharing Plasma's player: the picture moves to its surface
 * and back without the stream stopping. Its menu has Previous Channel, Play/Pause and Next Channel,
 * or for a movie Back 10 seconds, Play/Pause and Forward 10 seconds. Opening it goes back to
 * Plasma's full-screen player; closing it stops what plays.
 */
class PipPlayerActivity : ComponentActivity() {
    private lateinit var surface: SurfaceView

    /** What plays in the window: a channel, or a movie's key. */
    private var key: String? = null

    /** Whether it was muted before, as Home's preview can be: so it is again once the window's gone. */
    private var mutedBefore = true

    /** Done with: the picture has gone back to Plasma's screen, and the window is closing. */
    private var done = false
    private var askedSmall = false

    /** Paused from the window's menu, or a movie paused for another app's sound. */
    private var paused = false

    /** Another app took the sound: live TV is quiet, until the sound comes back or Play is pressed. */
    private var lostSound = false

    /** A movie paused because another app took the sound: it plays on when the sound comes back. */
    private var pausedForSound = false
    private var focus: AudioFocusRequest? = null

    private val actions = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            act(intent.getStringExtra(EXTRA_WHAT) ?: return)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playing = graph.playingNow()
        if (playing == null) {
            // It stopped in the meantime: the app opens on its own.
            done = true
            finishAndRemoveTask()
            graph.smallWindow.entered(small = false)
            return
        }
        key = playing
        graph.smallWindow.shown(this)
        surface = SurfaceView(this)
        setContentView(FrameLayout(this).apply { addView(surface, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) })
        val player = graph.player
        player.attach(surface, null)
        // The window plays with its sound, unless another app has it.
        mutedBefore = player.isMuted
        player.setMuted(false)
        takeFocus()
        ContextCompat.registerReceiver(this, actions, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        // The TV's own screensaver would cover the window: the screen stays on while it plays, as in Plasma.
        lifecycleScope.launch {
            player.status.collect { status ->
                surface.keepScreenOn = status is LiveStatus.Opening || status is LiveStatus.Playing || status is LiveStatus.Reconnecting
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (askedSmall || done) return
        askedSmall = true
        // Once the window is laid out, so the TV has something to shrink.
        window.decorView.post(::goSmall)
    }

    private fun goSmall() {
        if (done) return
        val small = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && try {
            enterPictureInPictureMode(params())
        } catch (refused: RuntimeException) {
            false
        }
        if (!small) {
            // The TV wouldn't: what plays stops, as it does when Plasma leaves (5.20).
            graph.standby.leftForAnotherApp()
            end()
        }
        // The app the user chose opens now, over the window.
        graph.smallWindow.entered(small)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode || done) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // Closed from its menu, and stopped already; or by the TV as it went to sleep, when
            // When the TV Turns Off says what happens.
            if (asleep()) end() else closed()
            return
        }
        // Opened from its corner: back to Plasma's full-screen player on what plays (5.20).
        val playing = key
        startActivity(
            if (playing != null && isLive(playing)) {
                ReminderOverlay.watchIntent(this, playing)
            } else {
                // A movie's screen is still there behind.
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        end()
    }

    override fun onStop() {
        super.onStop()
        // The TV turning off stops it too: When the TV Turns Off says what happens then (5.20).
        if (done || isChangingConfigurations || asleep()) return
        // Closed from its menu.
        closed()
    }

    override fun onDestroy() {
        if (::surface.isInitialized) {
            runCatching { unregisterReceiver(actions) }
            focus?.let { request -> getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request) }
            if (!done) {
                // Taken away without being closed: the sound goes back as it was, and what plays
                // stops as it does when Plasma leaves, unless Plasma's in front or the TV is off.
                done = true
                graph.player.setMuted(mutedBefore)
                if (!asleep() && !graph.standby.isOnScreen) graph.standby.leftForAnotherApp()
            }
            graph.player.detach(surface)
            graph.smallWindow.gone(this)
        }
        super.onDestroy()
    }

    /** The TV is going to sleep, or asleep. */
    private fun asleep(): Boolean = getSystemService(PowerManager::class.java)?.isInteractive == false

    /** Plasma's own screen is back: the window closes, and what played carries on there (5.20). */
    fun closeForPlasma() {
        end()
    }

    /** Closing the window from its menu stops what plays (5.20). */
    private fun closed() {
        graph.player.standby(0)
        end()
    }

    /**
     * Done with the window: the picture goes back to Plasma's screen, the sound is as it was before,
     * and the window closes.
     */
    private fun end() {
        if (done) return
        done = true
        val player = graph.player
        player.detach(surface)
        player.setMuted(mutedBefore)
        finishAndRemoveTask()
    }

    /** One of the window menu's buttons. */
    private fun act(what: String) {
        val playing = key ?: return
        if (done) return
        val player = graph.player
        when (what) {
            PREVIOUS, NEXT -> {
                val world = graph.world.value ?: return
                val next = SmallWindow.neighbourOf(world, playing, if (what == NEXT) 1 else -1) ?: return
                key = next
                paused = false
                player.show(next, muted = player.isMuted)
                graph.watchedInWindow(next)
            }
            BACK -> player.seekTo(player.position.value - SEEK_MS)
            FORWARD -> player.seekTo(player.position.value + SEEK_MS)
            PLAY_PAUSE -> {
                paused = !paused
                pausedForSound = false
                when {
                    !isLive(playing) -> player.setPaused(paused)
                    // Live TV can't pause without the rewind buffer: it stops, and plays on at the live edge.
                    paused -> player.standby(LIVE_PAUSE_MS)
                    else -> player.resume()
                }
                // Played again after another app took the sound: the window asks for it back.
                if (!paused && lostSound) takeFocus()
            }
        }
        updateMenu()
    }

    private fun updateMenu() {
        if (!done) setPictureInPictureParams(params())
    }

    private fun params(): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(WIDE_W, WIDE_H))
        .setActions(menu())
        .build()

    /** The window menu: channels for live TV, jumps for a movie, and Play/Pause alone for the archive (5.20). */
    private fun menu(): List<RemoteAction> {
        val playPause = if (paused) {
            button(PLAY_PAUSE, R.drawable.window_play, R.string.window_play)
        } else {
            button(PLAY_PAUSE, R.drawable.window_pause, R.string.window_pause)
        }
        val playing = key ?: return listOf(playPause)
        return when {
            StreamKeys.isVod(playing) -> listOf(button(BACK, R.drawable.window_back, R.string.window_back), playPause, button(FORWARD, R.drawable.window_forward, R.string.window_forward))
            isLive(playing) -> listOf(button(PREVIOUS, R.drawable.window_previous, R.string.window_previous), playPause, button(NEXT, R.drawable.window_next, R.string.window_next))
            else -> listOf(playPause)
        }
    }

    private fun button(what: String, @DrawableRes icon: Int, @StringRes title: Int): RemoteAction {
        val intent = PendingIntent.getBroadcast(
            this,
            BUTTONS.indexOf(what),
            Intent(ACTION).setPackage(packageName).putExtra(EXTRA_WHAT, what),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = getString(title)
        return RemoteAction(Icon.createWithResource(this, icon), label, label, intent)
    }

    /**
     * The window has the sound until another app takes it: then live TV goes quiet rather than
     * stopping, and a movie pauses, until the sound comes back (5.20).
     */
    private fun takeFocus() {
        val manager = getSystemService(AudioManager::class.java) ?: return
        val request = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            .setOnAudioFocusChangeListener(::focusChanged)
            .build()
            .also { focus = it }
        if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) soundBack() else soundGone()
    }

    private fun focusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> soundGone()
            AudioManager.AUDIOFOCUS_GAIN -> soundBack()
            // Ducked: it carries on, and the TV turns it down.
        }
    }

    /** Another app has the sound: live TV goes quiet, and a movie pauses. */
    private fun soundGone() {
        val playing = key ?: return
        if (done) return
        lostSound = true
        if (isLive(playing)) {
            graph.player.setMuted(true)
        } else if (!paused) {
            paused = true
            pausedForSound = true
            graph.player.setPaused(true)
            updateMenu()
        }
    }

    /** The sound is back: live TV is heard again, and a movie paused for it plays on. */
    private fun soundBack() {
        val playing = key ?: return
        if (done) return
        lostSound = false
        if (isLive(playing)) {
            graph.player.setMuted(false)
        } else if (pausedForSound) {
            paused = false
            pausedForSound = false
            graph.player.setPaused(false)
            updateMenu()
        }
    }

    private companion object {
        val ACTION = "${PipPlayerActivity::class.java.name}.BUTTON"
        const val EXTRA_WHAT = "what"
        const val PREVIOUS = "previous"
        const val NEXT = "next"
        const val PLAY_PAUSE = "playPause"
        const val BACK = "back"
        const val FORWARD = "forward"
        val BUTTONS = listOf(PREVIOUS, NEXT, PLAY_PAUSE, BACK, FORWARD)

        /** The window's shape: 16:9. */
        const val WIDE_W = 16
        const val WIDE_H = 9

        /** Back and Forward jump 10 s, as the player's buttons do. */
        const val SEEK_MS = 10_000L

        /** Live TV paused in the window keeps its connection a minute, as when Plasma leaves (5.20). */
        const val LIVE_PAUSE_MS = 60_000L

        /** Live TV: not a movie or an episode, and not a program from the archive. */
        fun isLive(key: String): Boolean = !StreamKeys.isVod(key) && StreamKeys.archiveStart(key) == null
    }
}
