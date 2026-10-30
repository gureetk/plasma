// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

/**
 * Which movie and series categories the profile watching sees, and which of them show their titles
 * (brief 5.11 and 5.12): [shown] is a kids profile's kid-safe ones, null for all of them; [hidden]
 * the locked ones that aren't opened with the PIN for now.
 */
data class VodVisibility(val shown: Set<String>? = null, val hidden: Set<String> = emptySet()) {
    /** Whether the category [id] is listed. */
    fun lists(id: String): Boolean = shown == null || id in shown

    /** Whether a title of the category [categoryId] shows: it's listed, and not locked now. */
    fun shows(categoryId: String?): Boolean = (shown == null || categoryId in shown) && categoryId !in hidden

    /** Whether the category [id] is locked now. */
    fun isLocked(id: String): Boolean = id in hidden
}

/** What the PIN opens a locked movie or series category under (5.12). */
internal fun vodKey(categoryId: String): String = "vod:$categoryId"
