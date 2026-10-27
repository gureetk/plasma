// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.feature.settings.OtherAppsAction
import ca.gureet.plasma.feature.settings.PlayNextApp
import ca.gureet.plasma.integrations.watchnext.PlayNext
import ca.gureet.plasma.integrations.watchnext.PlayNextItem
import kotlinx.coroutines.launch

/*
 * Settings › Launcher & Home › Home Rows › Continue Watching (brief 4.32 and 5.19): other apps'
 * Play Next items, Android's permission to read them, and which apps show.
 */

/** Every app in Play Next, by name, and whether Continue Watching shows its items. */
internal fun playNextApps(items: List<PlayNextItem>, hiddenApps: Set<String>, name: (String) -> String): List<PlayNextApp> =
    items.map { it.packageName }.distinct().map { PlayNextApp(it, name(it), shown = it !in hiddenApps) }.sortedBy { it.name.lowercase() }

/** Whether Android lets Plasma read other apps' items, read again each time Plasma comes back to the front and when [changes] goes up. */
@Composable
fun rememberPlayNextAllowed(graph: AppGraph, changes: Int): Boolean {
    var allowed by remember { mutableStateOf(graph.playNext.allowed()) }
    LifecycleResumeEffect(changes) {
        allowed = graph.playNext.allowed()
        onPauseOrDispose { }
    }
    return allowed
}

/** What Include Other Apps and Apps do. [onChanged] runs once Android's permission may have changed. */
@Composable
fun rememberOtherAppsActions(graph: AppGraph, onChanged: () -> Unit): (OtherAppsAction) -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // A TV that never shows the question answers no at once.
        if (!granted) noticeRefused(context, graph)
        graph.readPlayNextAgain()
        onChanged()
    }
    return remember(context, ask, graph) {
        { action ->
            when (action) {
                OtherAppsAction.TurnOn -> if (!graph.playNext.allowed()) ask.launch(PlayNext.READ_TV_LISTINGS)
                OtherAppsAction.AskAgain -> ask.launch(PlayNext.READ_TV_LISTINGS)
                OtherAppsAction.AllowOverWirelessDebugging -> graph.scope.launch {
                    allowOverDebugging(context, graph)
                    graph.readPlayNextAgain()
                    onChanged()
                }
                is OtherAppsAction.ShowApp -> graph.showPlayNextApp(action.packageName, action.shown)
            }
        }
    }
}

/** Android said no: there's another way, and a command for a computer. */
private fun noticeRefused(context: Context, graph: AppGraph) {
    graph.notice(Notice(context.getString(R.string.other_apps_refused), PlasmaIcons.Stacked, Notice.LONG_MS))
}

/** Android's permission given over Plasma's pairing with the TV (brief 5.13's optional grants); a notice says how it went. */
private suspend fun allowOverDebugging(context: Context, graph: AppGraph) {
    val ran = onTvShell(context, graph.adbKey()) { adb -> adb.shell("pm grant ${context.packageName} ${PlayNext.READ_TV_LISTINGS}") }
    when {
        graph.playNext.allowed() -> graph.notice(Notice(context.getString(R.string.other_apps_allowed), PlasmaIcons.Stacked))
        ran == null -> graph.notice(Notice(context.getString(R.string.tv_unreachable), PlasmaIcons.Stacked, Notice.LONG_MS))
        else -> noticeRefused(context, graph)
    }
}
