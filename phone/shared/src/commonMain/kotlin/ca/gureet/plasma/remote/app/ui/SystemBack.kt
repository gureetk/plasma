// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.runtime.Composable

/** Android's Back, while [enabled], calls [onBack]. iPhones have no Back: each screen has its own way out. */
@Composable
expect fun SystemBack(enabled: Boolean, onBack: () -> Unit)
