// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import ca.gureet.plasma.live.LiveWorld
import ca.gureet.plasma.player.StreamKeys
import java.lang.ref.WeakReference

/**
 * Keep Watching in a Small Window (brief 5.20): opening another app from Plasma while a channel
 * or a movie plays moves it to a small window in a corner, on TVs with picture-in-picture, rather
 * than stopping it. The window is [PipPlayerActivity], which shares Plasma's player.
 */
class SmallWindow(private val context: Context, private val graph: AppGraph) {
    /** The TV has picture-in-picture: only some Android TVs do. */
    val supported: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** The window is open, or opening: Plasma's own screen going away doesn't stop what plays. */
    @Volatile
    var open: Boolean = false
        private set

    private var window: WeakReference<PipPlayerActivity>? = null

    /** The app the user chose, which opens once the window is small. */
    private var then: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    /** The window never came up: the app opens all the same. */
    private val late = Runnable {
        if (window?.get() == null) open = false
        val next = then ?: return@Runnable
        then = null
        next()
    }

    /**
     * Opens another app with [openApp], first moving what plays to the small window where the TV
     * and Settings allow it. Otherwise the app just opens, and what plays stops as before.
     */
    fun thenOpen(openApp: () -> Unit) {
        // With the window already up, it stays as it is.
        if (open || !goesSmall(supported, graph.choices.keepWatchingSmall, graph.playingNow())) {
            openApp()
            return
        }
        then = openApp
        open = true
        try {
            context.startActivity(Intent(context, PipPlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            main.postDelayed(late, LATE_MS)
        } catch (refused: RuntimeException) {
            open = false
            then = null
            openApp()
        }
    }

    /** The window [activity] is up: Plasma closes it when it comes back. */
    internal fun shown(activity: PipPlayerActivity) {
        window = WeakReference(activity)
    }

    /** The window went small, or couldn't ([small] false): either way, the app opens now. */
    internal fun entered(small: Boolean) {
        main.removeCallbacks(late)
        if (!small) open = false
        val next = then
        then = null
        next?.invoke()
    }

    /** The window has gone. */
    internal fun gone(activity: PipPlayerActivity) {
        if (window?.get() !== activity) return
        window = null
        open = false
    }

    /**
     * Plasma's own screen is back, by Home or by opening Plasma: the window closes, and what played
     * in it carries on there (5.20).
     */
    fun closeForPlasma() {
        window?.get()?.closeForPlasma()
    }

    internal companion object {
        /** How long the window has to come up before the app opens without it. */
        const val LATE_MS = 3_000L

        /** Whether opening another app now goes small: on a TV that can, with the setting on, while something plays. */
        fun goesSmall(supported: Boolean, wanted: Boolean, playing: String?): Boolean = supported && wanted && playing != null

        /**
         * The channel [delta] places from [channelId], past hidden ones and round the ends: the
         * small window's Previous and Next Channel. They go through the list last watched, as the
         * player's channel buttons do, or else all channels in number order. Null for a movie or a
         * program from the archive, and for a channel that isn't there.
         */
        fun neighbourOf(world: LiveWorld, channelId: String, delta: Int): String? {
            if (StreamKeys.isVod(channelId) || StreamKeys.archiveStart(channelId) != null) return null
            val channel = world.byId[channelId] ?: return null
            val last = world.lastWatched?.listId?.let(world::list)?.filter { !it.hidden }?.distinctBy { it.id }
            val shown = last?.takeIf { list -> list.any { it.id == channel.id } } ?: world.channels.filter { !it.hidden }
            val at = shown.indexOfFirst { it.id == channel.id }
            if (at < 0 || shown.size < 2) return null
            return shown[(at + delta).mod(shown.size)].id
        }
    }
}
