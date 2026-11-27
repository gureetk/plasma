// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Plasma Remote for iPhone: the shared app, for the Swift side to show (iosApp/PlasmaRemote/PlasmaRemoteApp.swift). */
fun MainViewController(): UIViewController = ComposeUIViewController { PlasmaRemoteApp() }
