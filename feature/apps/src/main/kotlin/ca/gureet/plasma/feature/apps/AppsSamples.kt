// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.apps

import ca.gureet.plasma.core.fixtures.Sample

/** Apps from the sample world (appendix A): six on Home in Maya's order, seven more A to Z. */
object AppsSamples {
    private fun item(app: Sample.App) = AppItem(app.packageName, app.label, app.banner)

    val state: AppsUiState
        get() = AppsUiState(
            onHome = Sample.apps.filter { it.onHome }.map(::item),
            others = Sample.apps.filterNot { it.onHome }.sortedBy { it.label.lowercase() }.map(::item),
            profileName = Sample.maya.name,
            profileInitial = Sample.maya.initial,
        )

    /** Board 04: Sonar being moved along the Home row. */
    val moving: AppsUiState
        get() = state.copy(moving = "tv.example.sonar")
}
