// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ca.gureet.plasma.remote.app.Phone
import ca.gureet.plasma.remote.app.PlasmaRemoteApp
import ca.gureet.plasma.remote.app.tv.KeystoreVault
import ca.gureet.plasma.remote.app.tv.NsdTvFinder
import ca.gureet.plasma.remote.app.tv.PhoneApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        // The name set in the phone's settings, as "Name this phone" starts out.
        val deviceName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
        val phone = Phone(PhoneApp("android", version), deviceName, KeystoreVault(applicationContext), NsdTvFinder(applicationContext))
        setContent { PlasmaRemoteApp(phone) }
    }
}
