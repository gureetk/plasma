// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.launcher

/**
 * Home's Apps row and All Apps (brief 4.4 and 5.13): the row's apps in its order, every other app
 * after them, and the hidden apps, which Settings lists so they can come back.
 */
data class AppsLayout(val onHome: List<TvApp>, val others: List<TvApp>, val hidden: List<TvApp>) {
    /** [packageName], wherever it shows, or among the hidden apps. */
    fun find(packageName: String): TvApp? =
        onHome.firstOrNull { it.packageName == packageName }
            ?: others.firstOrNull { it.packageName == packageName }
            ?: hidden.firstOrNull { it.packageName == packageName }

    companion object {
        /** Until the row is arranged, it has the first apps made for TV. */
        const val FIRST_ROW = 6

        /**
         * The [installed] apps, in [TvApps.installed]'s order, laid out with the [row] as it was
         * last arranged (null if it never was) and the [hidden] apps. An app that's gone drops out
         * of the row, and one installed since goes to All Apps.
         */
        fun of(installed: List<TvApp>, row: List<String>?, hidden: Set<String>): AppsLayout {
            val shown = installed.filter { it.packageName !in hidden }
            val byPackage = shown.associateBy { it.packageName }
            val onHome = row?.distinct()?.mapNotNull { byPackage[it] } ?: shown.filter { it.forTv }.take(FIRST_ROW)
            val onRow = onHome.mapTo(HashSet()) { it.packageName }
            return AppsLayout(
                onHome = onHome,
                others = shown.filter { it.packageName !in onRow },
                hidden = installed.filter { it.packageName in hidden },
            )
        }
    }
}
