// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How the remote's keys work, from Settings › Remote & Buttons and Playback (brief 5.16), as the
 * screens act on them. The app provides what's chosen; a screen on its own, as in a test, has the
 * defaults.
 */
@Immutable
data class KeyChoices(
    /** Number Entry Delay: how long after its last digit a typed number tunes (4.11). */
    val numberEntryDelayMs: Long = PlasmaMotion.NUMBER_ENTRY_TUNE_MS,
    /** Long-Press OK: holding OK opens an item's options, as Menu does; off, a held OK is an OK. */
    val longPressOk: Boolean = true,
    /** Long-Press OK's Number Pad: in the full-screen player, a held OK opens a number pad instead (#105). */
    val holdOkNumberPad: Boolean = false,
    /** Channel Up Goes To: CH + goes to the next channel number, or else the previous one. */
    val channelUpToNext: Boolean = true,
    /** Up/Down in Player: they change channel, or else show the channel list, as Left does. */
    val upDownChangesChannel: Boolean = true,
) {
    /** The step CH +, and Up in the player, take through a list: +1 to the next number, −1 to the previous. */
    val channelUpStep: Int get() = if (channelUpToNext) 1 else -1
}

/** [KeyChoices] as chosen in Settings. */
val LocalKeyChoices = staticCompositionLocalOf { KeyChoices() }

/** Settings › Appearance › Show Channel Numbers: channels' numbers show beside their names; on unless turned off. */
val LocalShowChannelNumbers = staticCompositionLocalOf { true }
