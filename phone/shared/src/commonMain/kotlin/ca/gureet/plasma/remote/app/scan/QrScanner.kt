// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Why the camera can't scan: the phone said no to it, or there's no camera to use. */
enum class ScanProblem { Denied, Unavailable }

/**
 * The back camera's picture, filling [modifier], with every QR code it reads given to [onText]
 * (the same one again and again while it's in view). It asks for the camera the first time.
 * **Android:** CameraX, read by ZXing. **iPhone:** AVFoundation's QR codes.
 */
@Composable
expect fun QrScanner(onText: (String) -> Unit, onProblem: (ScanProblem) -> Unit, modifier: Modifier = Modifier)

/** Opens this app's page in the phone's settings, where the camera can be allowed. */
@Composable
expect fun rememberOpenSettings(): () -> Unit
