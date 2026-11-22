// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * A playlist sent as a file from a phone (#122), kept on the TV: its source's address is
 * "phone:<id>/<file name>", which the channel import reads from the kept file.
 */
object PhonePlaylists {
    const val PREFIX = "phone:"

    /** Whether [address] is a playlist kept from a phone. */
    fun isKept(address: String): Boolean = address.startsWith(PREFIX)

    /** The file's own name, "My List.m3u", for a kept playlist's [address]. */
    fun nameOf(address: String): String = address.removePrefix(PREFIX).substringAfter('/')
}
