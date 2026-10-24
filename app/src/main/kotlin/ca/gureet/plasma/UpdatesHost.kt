// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.feature.settings.UpdateAction
import ca.gureet.plasma.feature.settings.UpdateFacts
import ca.gureet.plasma.feature.settings.UpdateStatus
import ca.gureet.plasma.feature.settings.UpdateTrouble
import ca.gureet.plasma.update.UpdateProblem
import java.time.ZoneId
import kotlinx.coroutines.launch

/*
 * Settings › About › Plasma Update (brief 5.18): what the updater is doing, and Android's "Install
 * unknown apps", which Plasma needs once to install its own updates.
 */

/** The TV's page for letting Plasma install apps, where it has one. */
private fun unknownAppsSettings(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null))

/** What About › Plasma Update shows, as the updater moves on; Android's answer is read again on [changes]. */
@Composable
fun rememberUpdateFacts(graph: AppGraph, changes: Int): UpdateFacts {
    val context = LocalContext.current
    val state by graph.updates.state.collectAsState()
    val zone = LocalPlasmaClock.current.zone
    var allowed by remember { mutableStateOf(true) }
    var tvSettings by remember { mutableStateOf(true) }
    LifecycleResumeEffect(changes) {
        allowed = graph.updates.allowed()
        tvSettings = unknownAppsSettings(context).resolveActivity(context.packageManager) != null
        onPauseOrDispose { }
    }
    return remember(state, allowed, tvSettings, zone) { updateFacts(state, allowed, tvSettings, zone) }
}

/** The updater's [state] as About › Plasma Update shows it. */
internal fun updateFacts(state: UpdateState, allowed: Boolean, tvSettings: Boolean, zone: ZoneId): UpdateFacts = when (state) {
    UpdateState.Off -> UpdateFacts(UpdateStatus.Off)
    is UpdateState.Current -> UpdateFacts(
        status = if (state.checkedAt == null && state.problem == null) UpdateStatus.NotChecked else UpdateStatus.UpToDate,
        checkedAt = state.checkedAt?.atZone(zone),
        problem = state.problem?.trouble(),
    )
    is UpdateState.Checking -> UpdateFacts(UpdateStatus.Checking, checkedAt = state.checkedAt?.atZone(zone))
    is UpdateState.Ready -> UpdateFacts(
        status = UpdateStatus.Ready,
        version = state.update.label,
        notes = state.update.notes,
        checkedAt = state.checkedAt?.atZone(zone),
        problem = state.problem?.trouble(),
        allowed = allowed,
        tvSettings = tvSettings,
    )
    is UpdateState.Downloading -> UpdateFacts(
        status = UpdateStatus.Downloading,
        version = state.update.label,
        percent = if (state.done < 0) -1 else (state.done * 100).toInt().coerceIn(0, 100),
    )
    is UpdateState.Installing -> UpdateFacts(UpdateStatus.Installing, version = state.update.label)
}

private fun UpdateProblem.trouble(): UpdateTrouble = when (this) {
    UpdateProblem.CantReach -> UpdateTrouble.CantReach
    UpdateProblem.NotThere -> UpdateTrouble.NotThere
    UpdateProblem.BadFile -> UpdateTrouble.BadFile
    UpdateProblem.Damaged -> UpdateTrouble.Damaged
    UpdateProblem.NotPlasma -> UpdateTrouble.NotPlasma
    UpdateProblem.NotAllowed -> UpdateTrouble.NotAllowed
    UpdateProblem.Refused -> UpdateTrouble.Refused
    UpdateProblem.NoRoom -> UpdateTrouble.NoRoom
    UpdateProblem.Recording -> UpdateTrouble.Recording
}

/** What About › Plasma Update does. [onChanged] runs once Android's answer may have changed. */
@Composable
fun rememberUpdateActions(graph: AppGraph, onChanged: () -> Unit): (UpdateAction) -> Unit {
    val context = LocalContext.current
    return remember(context, graph) {
        { action ->
            when (action) {
                UpdateAction.CheckNow -> graph.updates.checkNow()
                UpdateAction.Install -> graph.updates.install()
                UpdateAction.AllowOverWirelessDebugging -> graph.scope.launch {
                    allowOverDebugging(context, graph)
                    onChanged()
                }
                UpdateAction.OpenTvSettings -> try {
                    context.startActivity(unknownAppsSettings(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (missing: ActivityNotFoundException) {
                    // Not on this TV; the sheet doesn't offer it then.
                }
            }
        }
    }
}

/** "Install unknown apps" for Plasma, given over Plasma's pairing with the TV (brief 5.13's optional grants). */
private suspend fun allowOverDebugging(context: Context, graph: AppGraph) {
    val ran = onTvShell(context, graph.adbKey()) { adb -> adb.shell("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow") }
    val text = when {
        graph.updates.allowed() -> R.string.updates_allowed
        ran == null -> R.string.tv_unreachable
        else -> R.string.updates_refused
    }
    graph.notice(Notice(context.getString(text), PlasmaIcons.Download, if (text == R.string.updates_allowed) Notice.SHORT_MS else Notice.LONG_MS))
}
