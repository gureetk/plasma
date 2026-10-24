// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.launcher.HomeMethod
import ca.gureet.plasma.launcher.HomeNow
import ca.gureet.plasma.launcher.HomeSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Brief 5.13's safety rules: after a boot, check that Home still opens Plasma where Plasma replaced
 * the TV's own Home. A TV update can turn Google TV Home back on; then Plasma offers to replace it
 * again, or to use Home Button Override instead.
 */

/**
 * Whether to say the TV's Home came back: Plasma had replaced it ([replaced]), Home doesn't open
 * Plasma now, and this [boot] hasn't been asked about ([askedBoot]); with no boot count, each start.
 */
internal fun homeCameBack(replaced: Boolean, homeOpensPlasma: Boolean, askedBoot: Int?, boot: Int?): Boolean =
    replaced && !homeOpensPlasma && (boot == null || askedBoot != boot)

/** How many times this TV has started, where Android says. */
private fun bootCount(context: Context): Int? = try {
    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
} catch (unknown: Settings.SettingNotFoundException) {
    null
}

/** What the sheet leads to. */
enum class HomeBackChoice { ReplaceAgain, UseOverride }

/**
 * Once a start, checks the TV's Home, and when Google TV Home came back, asks what to do (brief
 * 5.13). Replace Google TV Home seen in place is remembered, however it was done; Restore forgets it.
 */
@Composable
fun HomeCameBackPopup(graph: AppGraph, onChoice: (HomeBackChoice) -> Unit) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(graph) {
        val setup = withContext(Dispatchers.IO) { HomeSetup.read(context) }
        val settings = graph.data.settings
        if (setup.method == HomeMethod.Replace) {
            settings.setHomeReplaced(true)
            return@LaunchedEffect
        }
        shown = homeCameBack(settings.homeReplaced(), setup.home == HomeNow.Plasma, settings.homeBackAskedBoot(), bootCount(context))
    }
    if (!shown) return

    /** Not asked again this boot; and not at all once [forget]. */
    fun close(forget: Boolean = false) {
        shown = false
        graph.scope.launch {
            if (forget) graph.data.settings.setHomeReplaced(false)
            bootCount(context)?.let { graph.data.settings.setHomeBackAskedBoot(it) }
        }
    }
    Popup(onDismissRequest = { close() }, properties = PopupProperties(focusable = true)) {
        PhoneKeysHere(graph)
        Box(Modifier.fillMaxSize()) {
            OptionsSheet(
                title = stringResource(R.string.home_came_back),
                subtitle = stringResource(R.string.home_came_back_text),
                onDismiss = { close() },
                options = listOf(
                    SheetOption(stringResource(R.string.replace_again), onClick = { onChoice(HomeBackChoice.ReplaceAgain) }),
                    SheetOption(stringResource(R.string.use_override_instead), onClick = { onChoice(HomeBackChoice.UseOverride) }),
                    SheetOption(stringResource(R.string.dont_ask_again), onClick = { close(forget = true) }),
                ),
            )
        }
    }
}
