// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
