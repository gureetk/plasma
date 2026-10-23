// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.os.Bundle
import android.view.KeyEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlasmaApp() }
        // A key Plasma was taught arrives as the key it acts as (Learn a Key), before any screen sees it.
        val callback = window.callback
        window.callback = object : Window.Callback by callback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean = callback.dispatchKeyEvent(graph.keys.translate(event))
        }
    }

    override fun onStart() {
        super.onStart()
        graph.onActivityStart()
    }

    override fun onStop() {
        graph.onActivityStop()
        super.onStop()
    }
}
