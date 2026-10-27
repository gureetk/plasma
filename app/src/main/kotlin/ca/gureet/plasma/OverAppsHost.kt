// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.feature.settings.OverAppsAction
import ca.gureet.plasma.feature.settings.ReminderOverApps
import kotlinx.coroutines.launch

/*
 * Settings › TV Guide › Show over Other Apps (brief 5.7): Android's "display over other apps",
 * which reminders need to show while another app is in front.
 */

/** The TV's page for letting Plasma show over other apps, where it has one. */
private fun overlaySettings(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", context.packageName, null))

/** Whether reminders can show over other apps, read again each time Plasma comes back to the front and when [changes] goes up. */
@Composable
fun rememberRemindersOverApps(graph: AppGraph, changes: Int): ReminderOverApps {
    val context = LocalContext.current
    var facts by remember { mutableStateOf(ReminderOverApps()) }
    LifecycleResumeEffect(changes) {
        facts = ReminderOverApps(
            allowed = graph.reminderOverlay.allowed(),
            tvSettings = overlaySettings(context).resolveActivity(context.packageManager) != null,
        )
        onPauseOrDispose { }
    }
    return facts
}

/** What Show over Other Apps does. [onChanged] runs once Android's answer may have changed. */
@Composable
fun rememberOverAppsActions(graph: AppGraph, onChanged: () -> Unit): (OverAppsAction) -> Unit {
    val context = LocalContext.current
    return remember(context, graph) {
        { action ->
            when (action) {
                OverAppsAction.AllowOverWirelessDebugging -> graph.scope.launch {
                    allowOverDebugging(context, graph)
                    onChanged()
                }
                OverAppsAction.OpenTvSettings -> try {
                    context.startActivity(overlaySettings(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (missing: ActivityNotFoundException) {
                    // Not on this TV; the sheet doesn't offer it then.
                }
            }
        }
    }
}

/** "Display over other apps" given over Plasma's pairing with the TV (brief 5.13's optional grants); a notice says how it went. */
private suspend fun allowOverDebugging(context: Context, graph: AppGraph) {
    val ran = onTvShell(context, graph.adbKey()) { adb -> adb.shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow") }
    val text = when {
        graph.reminderOverlay.allowed() -> R.string.over_apps_allowed
        ran == null -> R.string.tv_unreachable
        else -> R.string.over_apps_refused
    }
    graph.notice(Notice(context.getString(text), PlasmaIcons.BellRing, if (text == R.string.over_apps_allowed) Notice.SHORT_MS else Notice.LONG_MS))
}
