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
    /** Playback › Forget Players Chosen per Channel, once confirmed (brief 5.4.3). */
    val onForgetPlayers: () -> Unit = {},
    /** Audio › Channel Volume Levels: Reset on a channel, or Reset All when it's null (5.4.6). */
    val onResetSound: (channelId: String?) -> Unit = {},
    /**
     * Playback for This Source: what's typed on a row, by its key (`source.userAgent:<id>`…) and
     * field (brief 4.20).
     */
    val onSourceText: (key: String, texts: List<String>) -> Unit = { _, _ -> },
    /** Home Screen setup: the override, App info, or the TV's own way to choose a Home app (4.23). */
    val onHome: (HomeAction) -> Unit = {},
    /** Launcher & Home › Hidden Apps: an app hidden on Apps shows again (4.4). */
    val onShowApp: (packageName: String) -> Unit = {},
    /** Launcher & Home › Use as the TV's Screensaver (5.14). */
    val onTvScreensaver: (TvScreensaverAction) -> Unit = {},
    /** Continue Watching › Include Other Apps and Apps (4.32). */
    val onOtherApps: (OtherAppsAction) -> Unit = {},
    /** Continue Watching › Jellyfin Server: signing in, over the network (4.32 and 5.19). */
    val jellyfin: JellyfinSignIn = JellyfinSignIn.Sample,
    /** Continue Watching › Jellyfin Server › Forget Server, once confirmed (6.10). */
    val onForgetJellyfin: () -> Unit = {},
    /** TV Guide › Show over Other Apps, for reminders (5.7). */
    val onOverApps: (OverAppsAction) -> Unit = {},
    /** About › Plasma Update (5.18). */
    val onUpdate: (UpdateAction) -> Unit = {},
    /** Rewind and Local Catch-up › Local Catch-up Channels: a channel ticked or unticked (5.5). */
    val onLocalCatchUp: (channelId: String, on: Boolean) -> Unit = { _, _ -> },
    /** Rewind and Local Catch-up › Clear Local Catch-up, once confirmed (4.31). */
    val onClearLocal: () -> Unit = {},
    /** Recording › Copy to Network Folder: setting the folder up, over the network (4.34 and 5.6). */
    val networkFolder: NetworkFolderSetup = NetworkFolderSetup.Sample,
    /** Recording › Copy to Network Folder › Turn Off, once confirmed: the folder is forgotten. */
    val onForgetNetworkFolder: () -> Unit = {},
    /** Parental Controls › Locked Channels: a channel unlocked, or locked again (5.12). */
    val onLockChannel: (channelId: String, locked: Boolean) -> Unit = { _, _ -> },
    /** Parental Controls › PIN › Turn Off Parental Controls, once confirmed: the PIN is gone (5.12). */
    val onTurnOffParental: () -> Unit = {},
    /** Parental Controls › Locked Groups: a group locked, or unlocked (5.12). */
    val onLockGroup: (groupId: String, locked: Boolean) -> Unit = { _, _ -> },
    /** Parental Controls › Adult Words, as typed (5.12). */
    val onAdultWords: (String) -> Unit = {},
    /** Profiles: a profile's own page opens (5.11). */
    val onProfile: (profileId: String) -> Unit = {},
    /** Profiles › Add Profile, named. */
    val onAddProfile: (name: String) -> Unit = {},
    /** A profile's Name, as typed. */
    val onProfileName: (profileId: String, name: String) -> Unit = { _, _ -> },
    /** A profile's PIN: choosing one, or a new one. */
    val onProfilePin: (profileId: String) -> Unit = {},
    /** A profile's PIN › Remove PIN, once confirmed. */
    val onRemoveProfilePin: (profileId: String) -> Unit = {},
    /** Delete Profile, once confirmed: Settings goes back to Profiles. */
    val onDeleteProfile: (profileId: String) -> Unit = {},
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
    /** The profile a profile's page is for. */
    profileId: String? = null,
) {
    val now by rememberNow()
    val spec = when (page) {
        SettingsPage.Main -> mainPage(values, facts)
        SettingsPage.SourcePlayback -> facts.sources.firstOrNull { it.id == sourceId }?.let { sourcePlaybackPage(values, it, actions.onSourceText) } ?: mainPage(values, facts)
        SettingsPage.Sources -> sourcesPage(values, facts, now, actions.onSource) { OtherPagesFooter(actions.onOpen) }
        SettingsPage.Channels -> channelsPage(values, facts)
        SettingsPage.Guide -> guidePage(values, facts, actions.onOverApps)
        SettingsPage.Playback -> playbackPage(values, facts, actions.onForgetPlayers)
        SettingsPage.Audio -> audioPage(values, facts, actions.onResetSound)
        SettingsPage.Rewind -> rewindPage(values, facts, actions.onLocalCatchUp, actions.onClearLocal)
        SettingsPage.Recording -> recordingPage(values, facts, actions.networkFolder, actions.onForgetNetworkFolder)
        SettingsPage.Parental -> parentalPage(values, facts, actions.onGo, actions.onLockChannel, actions.onTurnOffParental, actions.onLockGroup, actions.onAdultWords)
        SettingsPage.Profiles -> profilesPage(values, facts, actions)
        SettingsPage.Profile -> profilePage(values, facts, profileId, actions)
        SettingsPage.Launcher -> launcherPage(values, facts.home, facts.hiddenApps, actions.onShowApp, facts.tvScreensaver, actions.onTvScreensaver, facts.pictureInPicture)
        SettingsPage.HomeScreen -> homeScreenPage(facts.home, actions.onHome)
        SettingsPage.ContinueWatching -> continueWatchingPage(values, facts, actions.onOtherApps, actions.jellyfin, actions.onForgetJellyfin)
        SettingsPage.Remote -> remotePage(values)
        SettingsPage.PhoneRemote -> phoneRemotePage(values, facts)
        SettingsPage.Appearance -> appearancePage(values)
        SettingsPage.Network -> networkPage(facts)
        SettingsPage.About -> aboutPage(values, facts, actions.onUpdate, now)
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
