// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import ca.gureet.plasma.adb.HomeSwitch
import ca.gureet.plasma.adb.SwitchResult
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.feature.settings.DefaultHomeOffer
import ca.gureet.plasma.feature.settings.HomeAction
import ca.gureet.plasma.feature.settings.HomeScreenFacts
import ca.gureet.plasma.feature.settings.HomeScreenMethod
import ca.gureet.plasma.launcher.DefaultHomeWay
import ca.gureet.plasma.launcher.HomeButton
import ca.gureet.plasma.launcher.HomeMethod
import ca.gureet.plasma.launcher.HomeSetup
import kotlinx.coroutines.launch

/**
 * Home Screen setup's facts (brief 4.23) from what was read of this TV: the override counts as on
 * while it runs, even before Android's list says so. [fallbackName] names the TV's own Home when
 * Android names none.
 */
fun homeScreenFacts(setup: HomeSetup, running: Boolean, packageName: String, fallbackName: String): HomeScreenFacts {
    val overrideOn = setup.overrideOn || running
    return HomeScreenFacts(
        device = setup.device,
        androidVersion = setup.androidVersion,
        method = when (setup.method) {
            HomeMethod.Replace -> HomeScreenMethod.Replace
            HomeMethod.DefaultApp -> HomeScreenMethod.DefaultApp
            HomeMethod.Override, null -> if (overrideOn) HomeScreenMethod.Override else null
        },
        overrideOn = overrideOn,
        launcherName = setup.launcherName ?: fallbackName,
        defaultHome = when (setup.defaultHome) {
            DefaultHomeWay.Role -> DefaultHomeOffer.Role
            DefaultHomeWay.Settings -> DefaultHomeOffer.Settings
            DefaultHomeWay.Chooser -> DefaultHomeOffer.Chooser
            DefaultHomeWay.None -> DefaultHomeOffer.None
        },
        conflicts = setup.conflicts,
        restricted = setup.restricted,
        // Holding Home goes to the TV in every method: Plasma never hears it (5.13).
        longPressHome = false,
        packageName = packageName,
    )
}

/**
 * This TV's Home button for Settings, read again each time Plasma comes back to the front (from
 * Accessibility, say), when the override starts or stops, and when [changes] goes up; null until
 * it's read.
 */
@Composable
fun rememberHomeScreenFacts(changes: Int): HomeScreenFacts? {
    val context = LocalContext.current
    val running by HomeButton.running.collectAsState()
    var setup by remember { mutableStateOf<HomeSetup?>(null) }
    LifecycleResumeEffect(running, changes) {
        setup = HomeSetup.read(context)
        onPauseOrDispose { }
    }
    val fallback = stringResource(R.string.stock_home_name)
    return setup?.let { homeScreenFacts(it, running, context.packageName, fallback) }
}

/**
 * What Home Screen setup's rows do (brief 4.23). [onChanged] runs once the TV's own question about
 * the Home app is answered, or Google TV Home is restored, so the page reads the TV again.
 */
@Composable
fun rememberHomeActions(graph: AppGraph, onChanged: () -> Unit): (HomeAction) -> Unit {
    val context = LocalContext.current
    var askedAt by remember { mutableLongStateOf(0L) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Back at once with nothing asked: this TV doesn't offer Android's Home role after all.
        if (result.resultCode != Activity.RESULT_OK && SystemClock.elapsedRealtime() - askedAt < NOT_ASKED_MS) HomeSetup.roleRefused(context)
        onChanged()
    }
    return remember(context, ask) {
        { action ->
            when (action) {
                HomeAction.OpenAccessibility -> context.openAccessibility()
                HomeAction.TurnOverrideOff -> if (!HomeButton.turnOff()) context.openAccessibility()
                HomeAction.OpenAppInfo -> context.startSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                HomeAction.SetDefaultHome -> when (HomeSetup.read(context).defaultHome) {
                    DefaultHomeWay.Role -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.getSystemService(RoleManager::class.java)?.let { roles ->
                            askedAt = SystemClock.elapsedRealtime()
                            try {
                                ask.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                            } catch (missing: ActivityNotFoundException) {
                                HomeSetup.roleRefused(context)
                                onChanged()
                            }
                        }
                    }
                    DefaultHomeWay.Settings -> context.startSettings(Intent(Settings.ACTION_HOME_SETTINGS))
                    // Home's own chooser, where Plasma and Always are picked.
                    DefaultHomeWay.Chooser -> context.startSettings(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                    DefaultHomeWay.None -> Unit
                }
                HomeAction.Restore -> graph.scope.launch {
                    restore(context, graph)
                    onChanged()
                }
            }
        }
    }
}

/**
 * Restore Google TV Home (brief 4.23) over Plasma's pairing with the TV, which needs wireless
 * debugging on: the TV's launchers back on, and Home given back to its own. A notice says how it went.
 */
private suspend fun restore(context: Context, graph: AppGraph) {
    val setup = HomeSetup.read(context)
    val home = setup.launcherHome
    val result = if (home == null) {
        null
    } else {
        onTvShell(context, graph.adbKey()) { adb -> HomeSwitch(adb::shell).restore(home, setup.launchers.map { it.packageName }) }
    }
    val done = result is SwitchResult.Done
    // Google TV Home is meant to be back: nothing to ask about after the next boot.
    if (done) graph.data.settings.setHomeReplaced(false)
    graph.notice(Notice(context.getString(if (done) R.string.restore_done else R.string.restore_failed), PlasmaIcons.House, if (done) Notice.SHORT_MS else Notice.LONG_MS))
}

/** The TV's Accessibility settings, where Plasma's switch is (its own page is the system's alone). */
private fun Context.openAccessibility() {
    startSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

/** Opens one of the TV's own pages; false when it has none. */
private fun Context.startSettings(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (missing: ActivityNotFoundException) {
    false
} catch (refused: SecurityException) {
    false
}

/** A role request answered this fast never showed the TV's question. */
private const val NOT_ASKED_MS = 700L
