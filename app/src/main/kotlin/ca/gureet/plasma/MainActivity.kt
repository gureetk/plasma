// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ca.gureet.plasma.launcher.HomeButton

/**
 * Plasma's one screen, which is also the TV's Home when Plasma is (brief 5.13, as `.HomeActivity`).
 * Started by Home, by power-on or by the Home Button Override, it says why, and the app opens what
 * Settings › Launcher & Home chooses.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Started as the TV's Home and as an app, Plasma would be two screens: the newer one stays.
        graph.screenCreated(this)
        setContent { PlasmaApp() }
        // A key Plasma was taught arrives as the key it acts as (Learn a Key), before any screen sees it.
        val callback = window.callback
        window.callback = object : Window.Callback by callback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean = callback.dispatchKeyEvent(graph.keys.translate(event))
        }
        // Brought back from Recents after the process went, it isn't opened again.
        if (savedInstanceState == null) opened(intent)
        addOnNewIntentListener(::opened)
    }

    /** Opened by Home or at power-on, or by a reminder's Switch Now over another app (brief 5.7). */
    private fun opened(intent: Intent) {
        graph.opened(HomeButton.reasonOf(intent))
        ReminderOverlay.channelOf(intent)?.let(graph::watchFromReminder)
    }

    /** Plasma's own screen is back, by Home or by opening Plasma: the small window closes (5.20). */
    private val closeSmallWindow = Runnable { graph.smallWindow.closeForPlasma() }

    override fun onStart() {
        super.onStart()
        HomeButton.shown()
        graph.onActivityStart()
    }

    override fun onResume() {
        super.onResume()
        // Not at once: as the window goes small, Plasma can come forward for a moment before the app does.
        window.decorView.postDelayed(closeSmallWindow, BACK_FOR_GOOD_MS)
    }

    override fun onPause() {
        window.decorView.removeCallbacks(closeSmallWindow)
        super.onPause()
    }

    override fun onStop() {
        graph.onActivityStop()
        super.onStop()
    }

    override fun onDestroy() {
        graph.screenDestroyed(this)
        super.onDestroy()
    }

    private companion object {
        /** How long Plasma stays in front before it counts as back. */
        const val BACK_FOR_GOOD_MS = 600L
    }
}
