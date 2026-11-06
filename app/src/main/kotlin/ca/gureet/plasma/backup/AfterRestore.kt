// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.backup

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.AppGraph
import ca.gureet.plasma.Notice
import ca.gureet.plasma.R
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.integrations.watchnext.PlayNext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * After a restore (the owner on #141, 5 November): a setting the backup turned on that needs
 * Android's permission on this TV asks for it, once Plasma has started again; refused, the setting
 * goes off, and a notice says so. Include Other Apps, which reads other apps' Play Next items, is
 * the one there is.
 */
object AfterRestore {
    private const val MEMORY = "plasma.restore"
    private const val PENDING = "checkPermissions"

    /** A restore has put settings in place: Plasma checks them once it has started again. */
    @SuppressLint("ApplySharedPref")
    fun mark(context: Context) {
        // Written at once: Plasma's process ends just after.
        context.getSharedPreferences(MEMORY, Context.MODE_PRIVATE).edit().putBoolean(PENDING, true).commit()
    }

    /** Whether a restore is waiting to be checked, which this forgets. */
    fun take(context: Context): Boolean {
        val memory = context.getSharedPreferences(MEMORY, Context.MODE_PRIVATE)
        if (!memory.getBoolean(PENDING, false)) return false
        memory.edit().remove(PENDING).apply()
        return true
    }
}

/** Asks for what a restore's settings need, once, after Plasma starts again (#141). */
@Composable
fun AfterRestoreChecks(graph: AppGraph) {
    val context = LocalContext.current
    val turnedOff = stringResource(R.string.restore_other_apps_off)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // A TV that never shows the question answers no at once.
        if (!granted && !graph.playNext.allowed()) {
            graph.choose(SettingKeys.INCLUDE_APPS, OFF)
            graph.notice(Notice(turnedOff, PlasmaIcons.Stacked, Notice.LONG_MS))
        }
        graph.readPlayNextAgain()
    }
    LaunchedEffect(graph) {
        if (!AfterRestore.take(context)) return@LaunchedEffect
        val restored = SettingsChoices(graph.storedChoices.filterNotNull().first())
        if (restored.includeOtherApps && !graph.playNext.allowed()) ask.launch(PlayNext.READ_TV_LISTINGS)
    }
}

/** A switch's Off, as Settings stores it. */
private const val OFF = 1
