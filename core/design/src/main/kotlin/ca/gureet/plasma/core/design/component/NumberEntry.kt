// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.R
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import kotlinx.coroutines.delay

/** Channel numbers have up to four digits (1108, after a duplicate moved). */
const val MAX_CHANNEL_DIGITS = 4

/**
 * A channel number typed with the remote's digits (brief 4.11). Digits collect until
 * [PlasmaMotion.NUMBER_ENTRY_TUNE_MS] after the last one, or until OK; then the channel with that
 * number tunes, or, when there's none, the box says "No channel 999" for as long again.
 * [NumberEntryEffect] keeps the time.
 */
@Stable
class NumberEntry {
    /** The digits typed so far, empty when no number is being typed. */
    var digits by mutableStateOf("")
        private set

    /** The number is finished and no channel has it. */
    var missed by mutableStateOf(false)
        private set

    val isActive: Boolean get() = digits.isNotEmpty()

    /** Adds a digit; after a miss, it starts a new number. */
    fun type(digit: Char) {
        digits = if (missed) digit.toString() else (digits + digit).takeLast(MAX_CHANNEL_DIGITS)
        missed = false
    }

    fun clear() {
        digits = ""
        missed = false
    }

    /**
     * Finishes the number now, as OK does: [onFound] gets the channel [find] returns for it, or the
     * box shows the miss. OK on a miss closes the box.
     */
    fun <T : Any> finish(find: (String) -> T?, onFound: (T) -> Unit) {
        if (missed) return clear()
        val found = find(digits)
        if (found == null) {
            missed = true
        } else {
            clear()
            onFound(found)
        }
    }
}

/**
 * Finishes [entry] [PlasmaMotion.NUMBER_ENTRY_TUNE_MS] after its last digit, and closes it as long
 * after a miss.
 */
@Composable
fun <T : Any> NumberEntryEffect(entry: NumberEntry, find: (String) -> T?, onFound: (T) -> Unit) {
    val currentFind by rememberUpdatedState(find)
    val currentOnFound by rememberUpdatedState(onFound)
    LaunchedEffect(entry, entry.digits, entry.missed) {
        if (!entry.isActive) return@LaunchedEffect
        delay(PlasmaMotion.NUMBER_ENTRY_TUNE_MS)
        if (entry.missed) entry.clear() else entry.finish(currentFind, currentOnFound)
    }
}

/**
 * The box typed numbers collect in (board 11): "10_" while more digits may follow, and beside it
 * [match], what the number tunes ("Velocity Motor", "Favorites 2 · Velocity Motor"), or
 * "No channel 999" once it's finished without one.
 */
@Composable
fun NumberEntryBox(entry: NumberEntry, match: String?, modifier: Modifier = Modifier) {
    val digits = entry.digits
    NumberEntryBox(
        number = if (entry.missed || digits.length == MAX_CHANNEL_DIGITS) digits else "${digits}_",
        caption = if (entry.missed) stringResource(R.string.no_channel, digits) else match,
        modifier = modifier,
    )
}

/** The box at the top left with a channel's number in it, and an optional [caption] beside it. */
@Composable
fun NumberEntryBox(number: String, caption: String?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(84.px)
            .background(PlasmaColors.NumberBox, RoundedCornerShape(16.px))
            .padding(horizontal = 26.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.px),
    ) {
        PlasmaText(number, plasmaStyle(56f, 600, trackingPx = -1f, tabular = true), color = PlasmaColors.white(1f))
        if (caption != null) PlasmaText(caption, plasmaStyle(26f, 500), color = PlasmaColors.TextSoft)
    }
}
