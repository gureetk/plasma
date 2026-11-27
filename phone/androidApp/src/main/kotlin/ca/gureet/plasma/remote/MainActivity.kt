// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ca.gureet.plasma.remote.app.PlasmaRemoteApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { PlasmaRemoteApp() }
    }
}
