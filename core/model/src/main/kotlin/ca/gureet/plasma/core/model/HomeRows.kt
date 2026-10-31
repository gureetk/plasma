// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * Home's rows under the dock (brief 4.1 and 4.2, the owner's #80), in their default order. Settings
 * › Launcher & Home › Home Rows puts them in another order and turns each on or off; the first sits
 * under the dock, where Favorite Channels does on board 01.
 */
enum class HomeRow(
    /** How the row is kept in the order, and in its switch's [key]: never changes. */
    val id: String,
    /** It shows until its switch is turned off. */
    val onByDefault: Boolean,
) {
    Favorites("favorites", true),
    ContinueWatching("continueWatching", true),
    NewMovies("newMovies", true),
    NewSeries("newSeries", true),
    UpcomingEvents("upcomingEvents", false),
    RecentlyRecorded("recentlyRecorded", false),
    Apps("apps", true),
    ;

    /** Its switch in Settings, kept as every switch is: 0 for On, 1 for Off. */
    val key: String get() = SettingKeys.HOME_ROW + id

    companion object {
        /** The rows that show until Settings says otherwise, in their default order. */
        val defaults: List<HomeRow> get() = entries.filter { it.onByDefault }

        /**
         * The rows in the order kept as [ids], each once. A row the order doesn't name (one added
         * since it was kept) goes after the nearest row that comes before it by default, or first;
         * ids Plasma doesn't know are skipped. Null, before any row has been moved, is the default
         * order.
         */
        fun order(ids: List<String>?): List<HomeRow> {
            if (ids == null) return entries.toList()
            val rows = ids.mapNotNull { id -> entries.firstOrNull { it.id == id } }.distinct().toMutableList()
            for (row in entries) {
                if (row in rows) continue
                val before = entries.subList(0, row.ordinal).lastOrNull { it in rows }
                rows.add(if (before == null) 0 else rows.indexOf(before) + 1, row)
            }
            return rows
        }
    }
}
