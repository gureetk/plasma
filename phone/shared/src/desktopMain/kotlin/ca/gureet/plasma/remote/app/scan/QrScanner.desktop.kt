// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ca.gureet.plasma.remote.app.ui.RemoteColors

/** No camera here: the screens' pictures show a plain one in its place. */
@Composable
actual fun QrScanner(onText: (String) -> Unit, onProblem: (ScanProblem) -> Unit, modifier: Modifier) = Box(modifier.background(RemoteColors.Surface3))

@Composable
actual fun rememberOpenSettings(): () -> Unit = {}
