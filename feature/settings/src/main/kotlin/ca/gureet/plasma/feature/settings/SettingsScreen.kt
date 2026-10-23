// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

class SettingsActions(
    /** Another page of Settings. */
    val onOpen: (SettingsPage) -> Unit = {},
    /** A screen outside Settings: Add a Source, Duplicate Numbers, Home Screen setup… */
    val onGo: (SettingsDestination) -> Unit = {},
    /** One of a source's actions in Portals & Playlists, by its id. */
    val onSource: (sourceId: String, action: SourceAction) -> Unit = { _, _ -> },
)

/**
 * Settings and its pages (boards 19, 20, 22, 27–29, 31, 32 and 34, and the rest from the settings
 * inventory, brief 5.16). [values] holds what's been chosen; keep one for the whole app so choices
 * last while it runs. [initialItem] puts focus on a row by its id, as when the player's Switch
 * Player opens Playback on Player Engine.
 */
@Composable
fun SettingsScreen(
    page: SettingsPage,
    values: SettingsValues,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    facts: SettingsFacts = SettingsSamples.facts,
    initialItem: String? = null,
    /** The source Playback for This Source is for. */
    sourceId: String? = null,
) {
    val now by rememberNow()
    val spec = when (page) {
        SettingsPage.Main -> mainPage(values, facts)
        SettingsPage.SourcePlayback -> facts.sources.firstOrNull { it.id == sourceId }?.let { sourcePlaybackPage(values, it) } ?: mainPage(values, facts)
        SettingsPage.Sources -> sourcesPage(values, facts, now, actions.onSource) { OtherPagesFooter(actions.onOpen) }
        SettingsPage.Channels -> channelsPage(values, facts)
        SettingsPage.Guide -> guidePage(values, facts)
        SettingsPage.Playback -> playbackPage(values, facts)
        SettingsPage.Audio -> audioPage(values, facts)
        SettingsPage.Rewind -> rewindPage(values, facts)
        SettingsPage.Recording -> recordingPage(values, facts)
        SettingsPage.Parental -> parentalPage(values)
        SettingsPage.Profiles -> profilesPage(values)
        SettingsPage.Launcher -> launcherPage(values)
        SettingsPage.HomeScreen -> homeScreenPage(facts)
        SettingsPage.ContinueWatching -> continueWatchingPage(values, facts)
        SettingsPage.Remote -> remotePage(values)
        SettingsPage.PhoneRemote -> phoneRemotePage(values, facts)
        SettingsPage.Appearance -> appearancePage(values)
        SettingsPage.Network -> networkPage(facts)
        SettingsPage.About -> aboutPage(values, facts)
    }
    SettingsPageView(spec, values, actions.onOpen, actions.onGo, initialItem, modifier)
}

/** Links to the other pages along the bottom of Portals & Playlists (board 20). */
@Composable
private fun OtherPagesFooter(onOpen: (SettingsPage) -> Unit) {
    val links = listOf(
        SettingsPage.Launcher to R.string.launcher,
        SettingsPage.Playback to R.string.playback,
        SettingsPage.Recording to R.string.recording,
        SettingsPage.Parental to R.string.parental,
        SettingsPage.Remote to R.string.remote,
        SettingsPage.Network to R.string.network,
        SettingsPage.About to R.string.about,
    )
    Row(
        Modifier.offset(0.px, 992.px).width(1920.px),
        horizontalArrangement = Arrangement.spacedBy(14.px, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((page, label) in links) {
            PlasmaSurface(
                onClick = { onOpen(page) },
                style = FocusStyle.Pill,
                shape = PlasmaShapes.Tab,
                contentAlignment = Alignment.Center,
            ) {
                PlasmaText(
                    stringResource(label),
                    plasmaStyle(21f, 500),
                    Modifier.padding(horizontal = 10.px, vertical = 12.px),
                    color = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.Text2,
                )
            }
        }
    }
}
