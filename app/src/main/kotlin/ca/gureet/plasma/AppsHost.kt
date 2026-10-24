// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.feature.apps.AppItem
import ca.gureet.plasma.feature.apps.AppsActions
import ca.gureet.plasma.feature.apps.AppsScreen
import ca.gureet.plasma.feature.apps.AppsUiState
import ca.gureet.plasma.feature.apps.InputItem
import ca.gureet.plasma.feature.apps.SystemTile
import ca.gureet.plasma.feature.home.HomeUiState
import ca.gureet.plasma.feature.settings.HiddenApp
import ca.gureet.plasma.launcher.AppArt
import ca.gureet.plasma.launcher.AppsLayout
import ca.gureet.plasma.launcher.TvApp
import ca.gureet.plasma.launcher.TvInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ca.gureet.plasma.feature.home.AppItem as HomeAppItem

/*
 * The TV's own apps on Apps and Home's Apps row (brief 4.4 and 5.13), in both the sample world and
 * a source's: apps don't come from a source.
 */

/** What the TV has besides its apps, for the system tiles at the end of All Apps (4.4). */
internal data class SystemFacts(val inputs: List<TvInput>, val playStore: Boolean)

/** All Apps' system tiles: Inputs on a TV with inputs, Device Settings, Get More Apps with the Play Store. */
internal fun SystemFacts.tiles(): List<SystemTile> = buildList {
    if (inputs.isNotEmpty()) add(SystemTile.Inputs)
    add(SystemTile.DeviceSettings)
    if (playStore) add(SystemTile.GetMoreApps)
}

/** An app's tile: its banner, or its icon where it has none. */
internal fun TvApp.appItem() = AppItem(packageName, label, AppArt.bannerOf(this), AppArt.iconOf(this))

internal fun TvApp.homeItem() = HomeAppItem(packageName, label, AppArt.bannerOf(this), AppArt.iconOf(this))

internal fun appsState(layout: AppsLayout, system: SystemFacts, profileName: String, profileInitial: String) = AppsUiState(
    onHome = layout.onHome.map { it.appItem() },
    others = layout.others.map { it.appItem() },
    profileName = profileName,
    profileInitial = profileInitial,
    systemTiles = system.tiles(),
    inputs = system.inputs.map { InputItem(it.id, it.label) },
    hiddenCount = layout.hidden.size,
)

/** Home with the TV's apps in its Apps row, or without the row when Show Android Apps is off (5.13). */
internal fun HomeUiState.withApps(layout: AppsLayout?, show: Boolean): HomeUiState =
    copy(apps = layout?.onHome?.map { it.homeItem() }.orEmpty(), showApps = show)

/** Launcher & Home › Hidden Apps (4.4): what's hidden, to show again. */
internal fun AppsLayout?.hiddenApps(): List<HiddenApp> = this?.hidden?.map { HiddenApp(it.packageName, it.label) }.orEmpty()

/** Where Apps' top bar leads. */
internal class AppsNav(val onTab: (TopTab) -> Unit, val onSettings: () -> Unit, val onProfile: () -> Unit)

/**
 * Apps (board 04) on the TV's apps: the Apps row in its order, All Apps, then the system tiles this
 * TV has. Moves and hides are kept in Settings, and the screen follows apps being installed and
 * removed.
 */
@Composable
internal fun AppsHost(graph: AppGraph, profileName: String, profileInitial: String, nav: AppsNav) {
    val layout by graph.apps.collectAsState()
    // Read again as apps change, for the Play Store coming or going.
    val system by produceState<SystemFacts?>(null, graph, layout) {
        value = withContext(Dispatchers.IO) { SystemFacts(graph.tvInputs.list(), graph.tvApps.hasPlayStore()) }
    }
    val apps = layout ?: return
    val facts = system ?: return
    AppsScreen(
        state = remember(apps, facts, profileName, profileInitial) { appsState(apps, facts, profileName, profileInitial) },
        actions = remember(graph, nav) {
            AppsActions(
                onTab = nav.onTab,
                onSettings = nav.onSettings,
                onProfile = nav.onProfile,
                onOpen = graph::openApp,
                onSystem = { tile ->
                    when (tile) {
                        // Inputs lists the inputs on the screen itself.
                        SystemTile.Inputs -> Unit
                        SystemTile.DeviceSettings -> graph.tvApps.openDeviceSettings()
                        SystemTile.GetMoreApps -> graph.tvApps.openPlayStore()
                    }
                },
                onInput = { graph.tvInputs.open(it) },
                onHomeRow = graph::setHomeApps,
                onHide = { graph.setAppHidden(it, hidden = true) },
                onAppInfo = { graph.tvApps.openInfo(it) },
                onUninstall = { graph.tvApps.uninstall(it) },
            )
        },
    )
}
