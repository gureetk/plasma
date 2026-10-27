// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * Keys a remote sends, as Plasma remembers them for Settings › Remote & Buttons › Learn a Key
 * (the owner's decision of 22 October 2026).
 */
object RemoteKeys {
    /** Android's key code for a key it doesn't name. */
    const val UNKNOWN = 0

    /** A key by its key code; a key Android doesn't name, by its scan code. */
    fun idOf(keyCode: Int, scanCode: Int): String = if (keyCode != UNKNOWN) "k$keyCode" else "s$scanCode"

    /** The key code [keyCode] and [scanCode] act as, from [learned] (key id to key code); null when it isn't learned. */
    fun actsAs(learned: Map<String, Int>, keyCode: Int, scanCode: Int): Int? = learned[idOf(keyCode, scanCode)]
}
