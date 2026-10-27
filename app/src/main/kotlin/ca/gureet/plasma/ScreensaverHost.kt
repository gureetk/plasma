// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import ca.gureet.plasma.adb.ScreensaverResult
import ca.gureet.plasma.adb.ScreensaverSwitch
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.feature.settings.TvScreensaver
import ca.gureet.plasma.feature.settings.TvScreensaverAction
import ca.gureet.plasma.feature.settings.TvScreensaverFacts
import kotlinx.coroutines.launch

/*
 * Plasma's screensaver as the TV's own (brief 5.14): whose screensaver the TV shows, and setting it
 * over Plasma's pairing with the TV, since Google TV doesn't list other apps' screensavers.
 */

/** Plasma's screensaver, by the name the TV's settings use. */
private fun Context.plasmaDream(): ComponentName = ComponentName(this, PlasmaDream::class.java)

/** Whose screensaver the TV shows, as far as it lets Plasma read; and how the commands name Plasma's. */
fun tvScreensaverFacts(context: Context): TvScreensaverFacts {
    val ours = context.plasmaDream()
    val state = try {
        val components = Settings.Secure.getString(context.contentResolver, ScreensaverSwitch.COMPONENTS).orEmpty()
        if (components.split(',').any { ComponentName.unflattenFromString(it.trim()) == ours }) TvScreensaver.Plasma else TvScreensaver.Other
    } catch (refused: SecurityException) {
        TvScreensaver.Unknown
    }
    return TvScreensaverFacts(
        state = state,
        component = ours.flattenToString(),
        tvSettings = Intent(Settings.ACTION_DREAM_SETTINGS).resolveActivity(context.packageManager) != null,
    )
}

/**
 * Whose screensaver the TV shows, read again each time Plasma comes back to the front (from the
 * TV's settings, say) and when [changes] goes up; null until it's read.
 */
@Composable
fun rememberTvScreensaverFacts(changes: Int): TvScreensaverFacts? {
    val context = LocalContext.current
    var facts by remember { mutableStateOf<TvScreensaverFacts?>(null) }
    LifecycleResumeEffect(changes) {
        facts = tvScreensaverFacts(context)
        onPauseOrDispose { }
    }
    return facts
}

/** What Use as the TV's Screensaver does. [onChanged] runs once the TV's screensaver may have changed. */
@Composable
fun rememberTvScreensaverActions(graph: AppGraph, onChanged: () -> Unit): (TvScreensaverAction) -> Unit {
    val context = LocalContext.current
    return remember(context, graph) {
        { action ->
            when (action) {
                TvScreensaverAction.UsePlasma -> graph.scope.launch {
                    usePlasma(context, graph)
                    onChanged()
                }
                TvScreensaverAction.UseTvsOwn -> graph.scope.launch {
                    useTvsOwn(context, graph)
                    onChanged()
                }
                TvScreensaverAction.OpenTvSettings -> try {
                    context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (missing: ActivityNotFoundException) {
                    // Not on this TV; the row doesn't offer it then.
                }
            }
        }
    }
}

/** Makes Plasma's screensaver the TV's, keeping the TV's own to put back; a notice says how it went. */
private suspend fun usePlasma(context: Context, graph: AppGraph) {
    val component = context.plasmaDream().flattenToString()
    val result = onTvShell(context, graph.adbKey()) { adb -> ScreensaverSwitch(adb::shell).use(component) }
    if (result is ScreensaverResult.Done) {
        // Set twice, the first one's is the TV's own: keep that.
        result.previous?.let { graph.data.settings.setTvScreensaverBefore(it) }
        graph.notice(Notice(context.getString(R.string.tv_screensaver_set), PlasmaIcons.Moon))
    } else {
        graph.notice(Notice(context.getString(if (result == null) R.string.tv_unreachable else R.string.tv_screensaver_refused), PlasmaIcons.Moon, Notice.LONG_MS))
    }
}

/** Puts the TV's own screensaver back, or its default. */
private suspend fun useTvsOwn(context: Context, graph: AppGraph) {
    val before = graph.data.settings.tvScreensaverBefore()
    val done = onTvShell(context, graph.adbKey()) { adb -> ScreensaverSwitch(adb::shell).restore(before) }
    if (done == true) graph.data.settings.setTvScreensaverBefore(null)
    graph.notice(
        when (done) {
            true -> Notice(context.getString(R.string.tv_screensaver_restored), PlasmaIcons.Moon)
            false -> Notice(context.getString(R.string.tv_screensaver_refused), PlasmaIcons.Moon, Notice.LONG_MS)
            null -> Notice(context.getString(R.string.tv_unreachable), PlasmaIcons.Moon, Notice.LONG_MS)
        },
    )
}
