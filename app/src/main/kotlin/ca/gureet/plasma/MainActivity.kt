// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlasmaApp() }
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
