// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import java.security.SecureRandom

/**
 * A PIN's recovery code (the owner's #112): twelve characters, shown in three groups of four, from
 * letters and digits that can't be mixed up (no 0 and O, no 1, I and L), such as 7KQX-M4TR-9WCH.
 * It's shown once, to write down; only its salted hash is kept, as the PIN's is.
 */
object RecoveryCode {
    /** The characters a code is made of, in the order the keys show them. */
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    /** How many characters a code has. */
    const val LENGTH = 12

    /** How many characters each group has. */
    const val GROUP = 4

    /** A new code, without its dashes. */
    fun new(random: SecureRandom = SecureRandom()): String = buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** [code] as it's shown: its groups with dashes between them. */
    fun shown(code: String): String = code.chunked(GROUP).joinToString("-")

    /** What [text] says as a code: capitals, without dashes, spaces or what no code has. */
    fun typed(text: String): String = text.uppercase().filter { it in ALPHABET }
}
