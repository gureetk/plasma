// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the profile watching can manage recordings (brief 5.11): record, cancel and delete them,
 * and set Record Series. A guest can't, so the screens leave those choices out.
 */
val LocalCanRecord = staticCompositionLocalOf { true }
