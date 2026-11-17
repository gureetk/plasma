// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.runtime.Immutable

/**
 * How Home shows, for a Home that's kept made under the screens that open over it (#127) rather
 * than made again each time one closes.
 *
 * Under another screen ([under]) it's composed but not on screen. There it goes back to how it
 * opens: at its top, the hero on its first item, nothing left open or half done. So each [coming]
 * to the front is an opening, as it was when Home was made from nothing each time, and focus goes
 * to the Live TV tile.
 *
 * It's [inFront] while it's the screen in front or the one coming in, and not from the moment it
 * starts to leave: then its hero doesn't turn, and whoever gives it its state needn't read it.
 *
 * A Home that's simply made, as in tests and previews, is in front at its first coming.
 */
@Immutable
data class HomeShown(val inFront: Boolean = true, val under: Boolean = false, val coming: Int = 0)
