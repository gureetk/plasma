// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

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
        if (savedInstanceState == null) graph.opened(HomeButton.reasonOf(intent))
        addOnNewIntentListener { intent -> graph.opened(HomeButton.reasonOf(intent)) }
    }

    override fun onStart() {
        super.onStart()
        HomeButton.shown()
        graph.onActivityStart()
    }

    override fun onStop() {
        graph.onActivityStop()
        super.onStop()
    }

    override fun onDestroy() {
        graph.screenDestroyed(this)
        super.onDestroy()
    }
}
