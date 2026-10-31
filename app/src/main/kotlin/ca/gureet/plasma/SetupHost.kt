// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.feature.settings.FirstTimeSetup
import ca.gureet.plasma.feature.settings.HomeAction
import ca.gureet.plasma.feature.settings.SettingsActions
import ca.gureet.plasma.feature.settings.SettingsDestination
import ca.gureet.plasma.feature.settings.SettingsPage
import ca.gureet.plasma.feature.settings.SettingsValues
import ca.gureet.plasma.feature.settings.SetupActions
import ca.gureet.plasma.feature.settings.SetupScreen
import ca.gureet.plasma.feature.settings.title
import ca.gureet.plasma.live.ChannelSummary
import ca.gureet.plasma.live.settingsFacts
import ca.gureet.plasma.nav.AddSourceRoute
import ca.gureet.plasma.nav.ChoosePinRoute
import ca.gureet.plasma.nav.DuplicateNumbersRoute
import ca.gureet.plasma.nav.HomeRoute
import ca.gureet.plasma.nav.ReplaceHomeRoute
import ca.gureet.plasma.nav.SettingsRoute
import ca.gureet.plasma.nav.SetupRoute
import kotlinx.coroutines.launch
import ca.gureet.plasma.feature.settings.SourceType as AddSourceType

/**
 * First-time setup (#85) on this TV's own facts, whichever world Plasma runs in: its sources (none
 * on a new install, where Settings shows the sample world's), what each new one is still loading,
 * its Home button, its profiles and its PIN. A row that opens another screen opens it over setup,
 * its back link naming the step, and Back comes back to the step. Skip for Now and Go to Home mark
 * setup done and open Home; run [again] from Settings, Skip for Now reads Back to Settings and
 * goes back there.
 */
@Composable
fun SetupHost(graph: AppGraph, settings: SettingsValues, nav: NavHostController, again: Boolean) {
    val context = LocalContext.current
    val sources by graph.data.sources.sources.collectAsState(null)
    val counts by graph.data.channels.countsBySource.collectAsState(emptyMap())
    val tests by graph.tests.collectAsState()
    val summary by graph.channelSummary.collectAsState(ChannelSummary())
    val loading by graph.loading.collectAsState()
    val profiles by graph.data.profiles.all.collectAsState(null)
    val current by graph.data.profiles.current.collectAsState(null)
    val pinSet by graph.parental.pinSet.collectAsState()
    var homeChanges by remember { mutableIntStateOf(0) }
    val home = rememberHomeScreenFacts(homeChanges)
    val onHome = rememberHomeActions(graph) { homeChanges++ }
    val zone = LocalPlasmaClock.current.zone
    // The steps' names, which the screens they open name as where Back goes.
    val titles = SetupScreen.entries.associateWith { it.title() }
    // Nothing until it's known what this TV has, so no step shows the sample world first.
    val added = sources ?: return
    val people = profiles ?: return
    val tv = home ?: return
    val facts = remember(added, counts, tests, summary, loading, people, current, pinSet, tv, zone) {
        val base = settingsFacts(added, counts, tests, summary, zone, audio = null, home = tv, loading = loading)
        base.copy(
            sources = if (added.isEmpty()) emptyList() else base.sources,
            duplicatesFixed = if (added.isEmpty()) 0 else base.duplicatesFixed,
            profiles = profileInfos(people),
            currentProfile = current?.id ?: people.first().id,
            pinSet = pinSet == true,
        )
    }
    val actions = remember(nav, context, graph, onHome, titles) {
        SetupScreen.entries.associateWith { step -> setupActions(nav, context, graph, onHome, back = titles.getValue(step)) }
    }
    FirstTimeSetup(
        values = settings,
        actions = actions::getValue,
        setup = remember(nav, graph, again) {
            SetupActions(
                onSkip = {
                    if (again) {
                        nav.popBackStack()
                    } else {
                        graph.finishSetup()
                        nav.homeAfterSetup()
                    }
                },
                onFinish = {
                    graph.finishSetup()
                    nav.homeAfterSetup()
                },
            )
        },
        facts = facts,
        again = again,
    )
}

/**
 * What a step's rows do: Settings' own actions, with the screens they open naming the step,
 * [back], as where Back goes. Add a Source opens on the Stalker tab, as brief 3.4's first run has
 * it; Change PIN asks for the PIN there is first, since setup isn't behind it.
 */
private fun setupActions(nav: NavHostController, context: Context, graph: AppGraph, onHome: (HomeAction) -> Unit, back: String) = SettingsActions(
    onOpen = { page -> nav.navigate(SettingsRoute(page.name, back = back)) },
    onGo = { destination ->
        when (destination) {
            SettingsDestination.AddSource -> nav.navigate(AddSourceRoute(back = back, kind = AddSourceType.Stalker.name))
            SettingsDestination.DuplicateNumbers -> nav.navigate(DuplicateNumbersRoute(back = back))
            SettingsDestination.ReplaceHome -> nav.navigate(ReplaceHomeRoute)
            SettingsDestination.SetPin -> nav.navigate(ChoosePinRoute(back = back))
            SettingsDestination.ChangePin -> nav.navigate(ChoosePinRoute(change = true, verify = true, back = back))
            // Not on setup's steps.
            else -> settingsDestination(nav, context, destination)
        }
    },
    onSource = sourceAction(nav, graph, back),
    onHome = onHome,
    onProfile = { profileId -> nav.navigate(SettingsRoute(SettingsPage.Profile.name, profile = profileId, back = back)) },
    onAddProfile = { name -> graph.scope.launch { graph.addProfile(name) } },
)

/**
 * Home after first-time setup, with nothing behind it: setup's screens are gone, and Home is the
 * root again. Without a last channel, it plays the first, muted (brief 3.4, A6).
 */
private fun NavHostController.homeAfterSetup() {
    navigate(HomeRoute) { popUpTo(graph.id) { inclusive = true } }
}

/** First-time setup is on screen, or a screen opened from it: setup is on the back stack (#85). */
fun NavHostController.inSetup(): Boolean = runCatching { getBackStackEntry<SetupRoute>() }.isSuccess
