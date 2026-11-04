// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

/**
 * Which movie and series categories the profile watching sees, and which of them show their titles
 * (brief 5.11 and 5.12): [shown] is a kids profile's kid-safe ones, null for all of them; [hidden]
 * the locked ones that aren't opened with the PIN for now. [out] are those the user hid on their
 * source's Categories page, and [kept] the titles in a category of the user's own that shows,
 * which show though their own category is hidden (the owner's ask, 1 November).
 */
data class VodVisibility(
    val shown: Set<String>? = null,
    val hidden: Set<String> = emptySet(),
    val out: Set<String> = emptySet(),
    val kept: Set<String> = emptySet(),
) {
    /** Whether the category [id] is listed. */
    fun lists(id: String): Boolean = (shown == null || id in shown) && id !in out

    /**
     * Whether the title [titleId], of the category [categoryId], shows: its category is listed, or
     * it's in a category of the user's own that shows, and its category isn't locked now.
     */
    fun shows(categoryId: String?, titleId: String? = null): Boolean =
        (shown == null || categoryId in shown) && categoryId !in hidden && (categoryId !in out || (titleId != null && titleId in kept))

    /** Whether the category [id] is locked now. */
    fun isLocked(id: String): Boolean = id in hidden
}

/** What the PIN opens a locked movie or series category under (5.12). */
internal fun vodKey(categoryId: String): String = "vod:$categoryId"
