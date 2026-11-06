// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.SheetOption

/**
 * Report a Problem's sheet, as the app gives it to the error card (#114). Settings' own is out of
 * the player's reach, so this stands in for it, with its title and its Close.
 */
internal val ReportSheetStandIn: @Composable (close: () -> Unit) -> Unit = { close ->
    OptionsSheet(title = "Report a Problem", options = listOf(SheetOption("Close", onClick = close)), onDismiss = close)
}
