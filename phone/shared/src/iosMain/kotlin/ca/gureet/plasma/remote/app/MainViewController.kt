// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import androidx.compose.ui.window.ComposeUIViewController
import ca.gureet.plasma.remote.app.tv.BonjourTvFinder
import ca.gureet.plasma.remote.app.tv.KeychainVault
import ca.gureet.plasma.remote.app.tv.PhoneApp
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice
import platform.UIKit.UIViewController

/** Plasma Remote for iPhone: the shared app, for the Swift side to show (iosApp/PlasmaRemote/PlasmaRemoteApp.swift). */
fun MainViewController(): UIViewController {
    val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
    // Since iOS 16 this is "iPhone" unless the app is entitled to the user's own name: it's only where "Name this phone" starts.
    val phone = Phone(PhoneApp("ios", version), UIDevice.currentDevice.name, KeychainVault(), BonjourTvFinder())
    return ComposeUIViewController { PlasmaRemoteApp(phone) }
}
