// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Settings and its pages against boards 19, 20, 22, 23, 27–29 and 31–34, and their D-pad paths. */
class SettingsScreenshotTest : ScreenshotTest() {
    private fun page(page: SettingsPage, name: String, item: String? = null) {
        val values = SettingsValues()
        screen { SettingsScreen(page, values, SettingsActions(), initialItem = item) }
        capture(name)
    }

    @Test fun settings() = page(SettingsPage.Main, "19-settings")

    @Test fun sources() = page(SettingsPage.Sources, "20-settings-portals-and-playlists")

    @Test fun launcher() = page(SettingsPage.Launcher, "22-settings-launcher-and-home", item = "home-button")

    @Test fun playback() = page(SettingsPage.Playback, "27-settings-playback")

    @Test fun audio() = page(SettingsPage.Audio, "28-settings-audio")

    @Test fun channels() = page(SettingsPage.Channels, "29-settings-channels", item = "favorites")

    @Test fun rewind() = page(SettingsPage.Rewind, "31-rewind-and-local-catch-up")

    @Test fun continueWatching() = page(SettingsPage.ContinueWatching, "32-settings-continue-watching")

    @Test fun recording() = page(SettingsPage.Recording, "34-settings-recording", item = "copy")

    @Test fun homeScreen() = page(SettingsPage.HomeScreen, "23-home-screen-setup", item = "replace")

    @Test fun phoneRemote() = page(SettingsPage.PhoneRemote, "33-phone-remote")

    @Test fun replaceGoogleTvHomeLeadsToItsSteps() {
        var went: SettingsDestination? = null
        screen { SettingsScreen(SettingsPage.HomeScreen, SettingsValues(), SettingsActions(onGo = { went = it })) }
        compose.onNodeWithContentDescription("Home Button Override, Accessibility service · works like Projectivy, On").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Turns off Google TV Home, so Home, waking the TV and power-on all open Plasma, with no flash of Google TV first.").assertExists()
        press(Key.DirectionCenter)
        check(went == SettingsDestination.ReplaceHome) { "Got $went" }
    }

    private fun homeFacts(change: HomeScreenFacts.() -> HomeScreenFacts) = SettingsSamples.facts.let { it.copy(home = it.home.change()) }

    private val overrideOff = homeFacts { copy(method = null, overrideOn = false) }

    @Test fun homeButtonOverrideExplainsItselfBeforeAccessibility() {
        val did = mutableListOf<HomeAction>()
        screen { SettingsScreen(SettingsPage.HomeScreen, SettingsValues(), SettingsActions(onHome = { did += it }), facts = overrideOff) }
        compose.onNodeWithContentDescription("Home Button Override, Accessibility service · works like Projectivy, Off").assertIsFocused()
        compose.onNodeWithText("Home button opens Google TV Home").assertExists()

        // The explainer (brief 4.35): what it sees, what it never reads, then Accessibility.
        press(Key.DirectionCenter)
        focused().assert(hasText("Open Accessibility Settings"))
        compose.onNodeWithText("Plasma only sees which app is in front, so it knows when Google TV Home comes up.").assertExists()
        compose.onNodeWithText("It never reads what’s on the screen or what you type, and nothing leaves this TV.").assertExists()
        compose.onNodeWithText("Open App Info").assertDoesNotExist()
        capture("23-home-button-override")
        press(Key.DirectionCenter)
        check(did == listOf(HomeAction.OpenAccessibility)) { "Did $did" }
        compose.onNodeWithContentDescription("Home Button Override, Accessibility service · works like Projectivy, Off").assertIsFocused()

        // Not Now closes it, and so does Back.
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Not Now"))
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        press(Key.Back)
        compose.onNodeWithText("Open Accessibility Settings").assertDoesNotExist()
        check(did == listOf(HomeAction.OpenAccessibility)) { "Did $did" }
    }

    @Test fun aRestrictedSwitchComesWithItsFix() {
        val did = mutableListOf<HomeAction>()
        val facts = homeFacts { copy(method = null, overrideOn = false, restricted = true, packageName = "ca.gureet.plasma.dev") }
        screen { SettingsScreen(SettingsPage.HomeScreen, SettingsValues(), SettingsActions(onHome = { did += it }), facts = facts) }
        press(Key.DirectionCenter)
        compose.onNodeWithText(
            "Plasma’s switch is greyed out on this TV. Allow restricted settings in Plasma’s App info, if the TV has it, " +
                "or run this from a computer: adb shell appops set ca.gureet.plasma.dev ACCESS_RESTRICTED_SETTINGS allow",
        ).assertExists()
        press(Key.DirectionDown)
        focused().assert(hasText("Open App Info"))
        press(Key.DirectionCenter)
        check(did == listOf(HomeAction.OpenAppInfo)) { "Did $did" }
    }

    @Test fun theOverrideOnOffersToTurnItOff() {
        val did = mutableListOf<HomeAction>()
        screen { SettingsScreen(SettingsPage.HomeScreen, SettingsValues(), SettingsActions(onHome = { did += it })) }
        press(Key.DirectionCenter)
        compose.onNodeWithText("On. Plasma opens when Google TV Home comes up.").assertExists()
        focused().assert(hasText("Turn Off"))
        press(Key.DirectionCenter)
        check(did == listOf(HomeAction.TurnOverrideOff)) { "Did $did" }
    }

    @Test fun withPlasmaAlreadyHomeTheOverrideIsntNeeded() {
        val facts = homeFacts { copy(method = HomeScreenMethod.DefaultApp, overrideOn = true, defaultHome = DefaultHomeOffer.Role) }
        screen { SettingsScreen(SettingsPage.HomeScreen, SettingsValues(), SettingsActions(), facts = facts) }
        compose.onNodeWithText("Home button opens Plasma, this TV’s Home app").assertExists()
        compose.onNodeWithContentDescription("Default Home App, For TVs that let you choose a Home app, On").assertExists()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Plasma is already this TV’s Home app, so the override isn’t needed.").assertExists()
        focused().assert(hasText("Turn Off"))
    }

    @Test fun aTvThatLetsAnAppBeHomeIsAskedFromDefaultHomeApp() {
        val did = mutableListOf<HomeAction>()
        val facts = homeFacts { copy(defaultHome = DefaultHomeOffer.Chooser) }
        screen { SettingsScreen(SettingsPage.HomeScreen, SettingsValues(), SettingsActions(onHome = { did += it }), facts = facts) }
        press(Key.DirectionDown, times = 2)
        compose.onNodeWithContentDescription("Default Home App, For TVs that let you choose a Home app, Set Up").assertIsFocused()
        compose.onNodeWithText("This TV asks which app the Home button opens. Press OK, choose Plasma, then Always.").assertExists()
        press(Key.DirectionCenter)
        check(did == listOf(HomeAction.SetDefaultHome)) { "Did $did" }
    }

    @Test fun aTvThatDoesntLetAnAppBeHomeSaysSo() {
        val did = mutableListOf<HomeAction>()
        screen { SettingsScreen(SettingsPage.HomeScreen, SettingsValues(), SettingsActions(onHome = { did += it })) }
        press(Key.DirectionDown, times = 2)
        compose.onNodeWithContentDescription("Default Home App, For TVs that let you choose a Home app, Not available on this TV").assertIsFocused()
        press(Key.DirectionCenter)
        check(did.isEmpty()) { "Did $did" }
    }

    @Test fun anotherAppsOverrideIsWarnedAbout() {
        val facts = homeFacts { copy(conflicts = listOf("Projectivy Launcher")) }
        screen { SettingsScreen(SettingsPage.HomeScreen, SettingsValues(), SettingsActions(), facts = facts) }
        // On This TV, and with the override's points.
        compose.onAllNodesWithText("Projectivy Launcher’s override is also on. Turn it off so only one app answers the Home button.").assertCountEquals(2)
    }

    @Test fun replacedGoogleTvHomeShowsAsOn() {
        val facts = homeFacts { copy(method = HomeScreenMethod.Replace, overrideOn = false) }
        screen { SettingsScreen(SettingsPage.HomeScreen, SettingsValues(), SettingsActions(), facts = facts) }
        compose.onNodeWithText("Home button opens Plasma. Google TV Home is turned off.").assertExists()
        compose.onNodeWithContentDescription("Replace Google TV Home, Advanced · ADB from this TV or a computer, On").assertExists()
    }

    @Test fun launcherAndHomeSaysHowPlasmaIsHomeAndLeavesLongPressToTheTv() {
        val facts = homeFacts { copy(method = null, overrideOn = false, longPressHome = false) }
        screen { SettingsScreen(SettingsPage.Launcher, SettingsValues(), SettingsActions(), facts = facts) }
        compose.onNodeWithContentDescription("Use Plasma as Home Screen, Off").assertIsFocused()
        press(Key.DirectionDown, times = 2)
        compose.onNodeWithContentDescription("Long-Press Home, System default").assertIsFocused()
        compose.onNodeWithText("Holding Home is up to the TV, so it does what the TV does.").assertExists()
        // Nothing to choose.
        press(Key.DirectionCenter)
        compose.onNodeWithText("Recent Apps").assertDoesNotExist()
    }

    @Test fun settingsSaysWhenPlasmaIsntTheHomeScreenYet() {
        screen { SettingsScreen(SettingsPage.Main, SettingsValues(), SettingsActions(), facts = overrideOff, initialItem = "launcher") }
        compose.onNodeWithContentDescription("Launcher & Home, Plasma isn’t your Home screen yet").assertIsFocused()
    }

    @Test fun rowsOpenTheirPages() {
        var opened: SettingsPage? = null
        screen { SettingsScreen(SettingsPage.Main, SettingsValues(), SettingsActions(onOpen = { opened = it })) }
        compose.onNodeWithContentDescription("Portals & Playlists, 3 sources").assertIsFocused()
        press(Key.DirectionDown, times = 3)
        compose.onNodeWithContentDescription("Playback, VLC · match frame rate on").assertIsFocused()
        // The detail follows focus.
        compose.onNodeWithText("Which player plays what, how the picture and sound come out, and how far back you can rewind.").assertExists()
        press(Key.DirectionCenter)
        check(opened == SettingsPage.Playback) { "Got $opened" }

        // Down past the divider to the second half.
        press(Key.DirectionDown, times = 4)
        compose.onNodeWithContentDescription("Launcher & Home, Plasma is your Home screen").assertIsFocused()
    }

    @Test fun choosingAnOptionChangesTheValue() {
        val values = SettingsValues()
        screen { SettingsScreen(SettingsPage.Playback, values, SettingsActions()) }
        compose.onNodeWithContentDescription("Player Engine, VLC").assertIsFocused()
        press(Key.DirectionCenter)
        // The sheet opens on the current option.
        focused().assert(hasText("VLC"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        // Focus goes back to the row, which shows the new engine; so does its detail.
        compose.onNodeWithContentDescription("Player Engine, mpv").assertIsFocused()
        compose.onNodeWithText(
            "mpv plays live TV with fine control of the picture, and can turn surround sound into Dolby Digital. " +
                "If a stream won’t start, Plasma tries the next engine by itself.",
        ).assertExists()
    }

    @Test fun aSourceOffersItsActionsAndDeleteAsksFirst() {
        var did: Pair<String, SourceAction>? = null
        screen { SettingsScreen(SettingsPage.Sources, SettingsValues(), SettingsActions(onSource = { id, action -> did = id to action })) }
        focused().assert(hasText("Home Portal"))

        // Brief 4.20's actions, in its order; the primary source isn't offered Set as Primary.
        press(Key.DirectionCenter)
        val actions = listOf("Edit", "Refresh Now", "Test Connection", "Account Info", "Playback for This Source", "Disable", "Delete Source")
        actions.forEachIndexed { i, action ->
            focused().assert(hasText(action))
            if (i < actions.lastIndex) press(Key.DirectionDown)
        }
        compose.onNodeWithText("Set as Primary").assertDoesNotExist()
        press(Key.DirectionCenter)

        // The confirmation opens on Cancel, so a second OK can't delete by accident.
        compose.onNodeWithText("Delete Home Portal?").assertExists()
        focused().assert(hasText("Cancel"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(did == "home" to SourceAction.Delete) { "Got $did" }
        focused().assert(hasText("Home Portal"))
    }

    @Test fun anotherSourceCanBeMadePrimary() {
        var did: Pair<String, SourceAction>? = null
        screen { SettingsScreen(SettingsPage.Sources, SettingsValues(), SettingsActions(onSource = { id, action -> did = id to action })) }
        press(Key.DirectionDown)
        focused().assert(hasText("Backup Line"))

        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Set as Primary"))
        press(Key.DirectionCenter)

        check(did == "backup" to SourceAction.SetPrimary) { "Got $did" }
        focused().assert(hasText("Backup Line"))
    }

    @Test fun accountInfoSaysWhatTheProviderReports() {
        screen { SettingsScreen(SettingsPage.Sources, SettingsValues(), SettingsActions()) }
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 4)
        focused().assert(hasText("Account Info"))
        press(Key.DirectionCenter)

        compose.onNodeWithText("Status").assertExists()
        compose.onNodeWithText("Active").assertExists()
        // The detail behind the sheet shows the connections too.
        compose.onAllNodesWithText("0 of 1 in use").assertCountEquals(2)
        compose.onNodeWithText("HLS, MPEG-TS").assertExists()
        compose.onNodeWithText("Europe/London").assertExists()
    }

    @Test fun aSourceThatsOffIsDimmedAndOffersEnable() {
        var did: Pair<String, SourceAction>? = null
        val facts = SettingsSamples.facts.let { facts ->
            facts.copy(sources = facts.sources.map { if (it.id == "extras") it.copy(enabled = false, health = SourceHealth.Off) else it })
        }
        screen { SettingsScreen(SettingsPage.Sources, SettingsValues(), SettingsActions(onSource = { id, action -> did = id to action }), facts = facts) }
        press(Key.DirectionDown, times = 2)
        compose.onNodeWithContentDescription("Sports Extras, M3U playlist · 212 channels, Off").assertIsFocused()

        press(Key.DirectionCenter)
        compose.onNodeWithText("Disable").assertDoesNotExist()
        press(Key.DirectionDown, times = 6)
        focused().assert(hasText("Enable"))
        press(Key.DirectionCenter)
        check(did == "extras" to SourceAction.Enable) { "Got $did" }
    }

    @Test fun aFailedTestSaysWhy() {
        val facts = SettingsSamples.facts.let { facts ->
            facts.copy(sources = facts.sources.map { if (it.id == "home") it.copy(health = SourceHealth.TestFailed(ConnectProblem.CantReach)) else it })
        }
        screen { SettingsScreen(SettingsPage.Sources, SettingsValues(), SettingsActions(), facts = facts) }

        compose.onNodeWithContentDescription("Home Portal, Stalker · portal.example.net, Can’t connect").assertIsFocused()
        compose.onNodeWithText("Can’t reach the server. Check the address and port.").assertExists()
    }

    @Test fun playbackForThisSourceKeepsItsChoicesWithTheSource() {
        val stored = mutableListOf<Pair<String, Int>>()
        val values = SettingsValues { key, index -> stored += key to index }
        screen { SettingsScreen(SettingsPage.SourcePlayback, values, SettingsActions(), sourceId = "backup") }
        compose.onNodeWithText("Playback for Backup Line").assertExists()
        compose.onNodeWithContentDescription("Player Engine, Same as Playback settings").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Stream Format, MPEG-TS").assertIsFocused()

        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Buffer Size, Same as Playback settings").assertIsFocused()
        press(Key.DirectionCenter)
        focused().assert(hasText("Same as Playback settings"))
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionCenter)

        compose.onNodeWithContentDescription("Buffer Size, Large · 6 s").assertIsFocused()
        check(stored == listOf("source.buffer:backup" to 4)) { "Stored $stored" }
        check(SourceSettings.sourceOf(stored.single().first) == "backup")
    }

    @Test fun aPlaylistsPlaybackOffersACatchUpTimeOffsetAndNoStreamFormat() {
        screen { SettingsScreen(SettingsPage.SourcePlayback, SettingsValues(), SettingsActions(), sourceId = "extras") }

        compose.onNodeWithContentDescription("Player Engine, Same as Playback settings").assertIsFocused()
        compose.onNodeWithContentDescription("Buffer Size, Same as Playback settings").assertExists()
        compose.onNodeWithText("Stream Format").assertDoesNotExist()
        compose.onNodeWithContentDescription("Catch-up Time Offset, 0 h").assertExists()
        compose.onNodeWithContentDescription("Use Stream Hub, Same as Playback settings").assertExists()
        compose.onNodeWithContentDescription("udpxy Proxy, None").assertExists()
    }

    /** Down the page until [description] has focus. */
    private fun pressDownToRow(description: String) {
        repeat(12) {
            if (runCatching { compose.onNodeWithContentDescription(description).assertIsFocused() }.isSuccess) return
            press(Key.DirectionDown)
        }
        compose.onNodeWithContentDescription(description).assertIsFocused()
    }

    @Test fun aSourcesUserAgentIsTypedOnAPageOfItsOwn() {
        val typed = mutableListOf<Pair<String, List<String>>>()
        screen { SettingsScreen(SettingsPage.SourcePlayback, SettingsValues(), SettingsActions(onSourceText = { key, texts -> typed += key to texts }), sourceId = "extras") }
        pressDownToRow("User Agent, VLC")
        press(Key.DirectionCenter)

        // The field has focus; typing goes into it.
        compose.onNodeWithContentDescription("User agent, VLC").assertIsFocused()
        press(Key.P)
        press(Key.One)
        compose.onNodeWithContentDescription("User agent, p1").assertExists()
        // OK moves to the keyboard, Next from the only field to Save.
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight, times = 8)
        focused().assert(hasText("Next"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Save"))
        press(Key.DirectionCenter)

        check(typed == listOf("source.userAgent:extras" to listOf("p1"))) { "Typed $typed" }
        compose.onNodeWithContentDescription("User Agent, VLC").assertIsFocused()

        // Back leaves without saving.
        press(Key.DirectionCenter)
        press(Key.Q)
        press(Key.Back)
        compose.onNodeWithContentDescription("User Agent, VLC").assertIsFocused()
        check(typed.size == 1) { "Typed $typed" }
    }

    @Test fun referrerAndExtraHeadersAreTwoFields() {
        val typed = mutableListOf<Pair<String, List<String>>>()
        screen { SettingsScreen(SettingsPage.SourcePlayback, SettingsValues(), SettingsActions(onSourceText = { key, texts -> typed += key to texts }), sourceId = "backup") }
        pressDownToRow("Referrer and Extra Headers, None")
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Referrer, None").assertIsFocused()
        compose.onNodeWithContentDescription("Extra headers, Name: value; Name: value").assertExists()
        capture("20-settings-referrer-and-extra-headers")

        press(Key.R)
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Extra headers, Name: value; Name: value").assertIsFocused()
        press(Key.X)
        pressDownTo("Save")
        press(Key.DirectionCenter)
        check(typed == listOf("source.headers:backup" to listOf("r", "x"))) { "Typed $typed" }
    }

    /** Down until [label] has focus, through the keyboard. */
    private fun pressDownTo(label: String) {
        repeat(10) {
            if (runCatching { focused().assert(hasText(label)) }.isSuccess) return
            press(Key.DirectionDown)
        }
        focused().assert(hasText(label))
    }

    @Test fun eachSourcesGuideHasItsOwnTimeOffsetAndTheListScrolls() {
        val stored = mutableListOf<Pair<String, Int>>()
        val values = SettingsValues { key, index -> stored += key to index }
        screen { SettingsScreen(SettingsPage.Guide, values, SettingsActions()) }
        press(Key.DirectionDown, times = 2)
        compose.onNodeWithContentDescription("Time Offset, Backup Line, 0 h").assertIsFocused()

        // Twenty-five offsets: the sheet opens on the one in use and scrolls to those past it.
        press(Key.DirectionCenter)
        focused().assert(hasText("0 h"))
        press(Key.DirectionDown, times = 9)
        focused().assert(hasText("+9 h")).assertIsDisplayed()
        press(Key.DirectionCenter)

        compose.onNodeWithContentDescription("Time Offset, Backup Line, +9 h").assertIsFocused()
        check(stored == listOf("source.guideOffset:backup" to 21)) { "Stored $stored" }
    }

    @Test fun choicesStartFromWhatsStoredAndGoBackToBeStored() {
        val stored = mutableListOf<Pair<String, Int>>()
        val values = SettingsValues(mapOf("engine" to 1)) { key, index -> stored += key to index }
        screen { SettingsScreen(SettingsPage.Playback, values, SettingsActions()) }
        compose.onNodeWithContentDescription("Player Engine, mpv").assertIsFocused()

        press(Key.DirectionCenter)
        focused().assert(hasText("mpv"))
        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        compose.onNodeWithContentDescription("Player Engine, VLC").assertIsFocused()
        check(stored == listOf("engine" to 0)) { "Stored $stored" }
    }

    @Test fun channelVolumeLevelsListsTheChannelsEachWithReset() {
        val reset = mutableListOf<String?>()
        screen { SettingsScreen(SettingsPage.Audio, SettingsValues(), SettingsActions(onResetSound = { reset += it }), initialItem = "levels") }
        compose.onNodeWithContentDescription("$LEVELS, 3 channels").assertIsFocused()

        press(Key.DirectionCenter)
        focused().assert(hasText("Northline News"))
        compose.onNodeWithText("+3 dB").assertExists()
        compose.onNodeWithText("−2 dB").assertExists()
        press(Key.DirectionCenter)
        focused().assert(hasText("Reset"))
        press(Key.DirectionCenter)
        check(reset == listOf("home:101")) { "Reset $reset" }

        // Reset All, after the channels, asks first.
        compose.onNodeWithContentDescription("$LEVELS, 3 channels").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 3)
        focused().assert(hasText("Reset All"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Every channel goes back to 0 dB, and to its source’s Audio Output.").assertExists()
        press(Key.DirectionCenter)
        check(reset == listOf("home:101", null)) { "Reset $reset" }
    }

    @Test fun withNoLevelsTheresNothingToOpen() {
        screen { SettingsScreen(SettingsPage.Audio, SettingsValues(), SettingsActions(), facts = SettingsSamples.facts.copy(channelSounds = emptyList()), initialItem = "levels") }
        compose.onNodeWithContentDescription("$LEVELS, None").assertIsFocused()
    }

    @Test fun okFlipsASwitch() {
        screen { SettingsScreen(SettingsPage.Launcher, SettingsValues(), SettingsActions()) }
        compose.onNodeWithContentDescription("Use Plasma as Home Screen, Home Button Override").assertIsFocused()
        press(Key.DirectionDown, times = 8)
        compose.onNodeWithContentDescription("Show Android Apps, On").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Show Android Apps, Off").assertIsFocused()
    }

    private companion object {
        const val LEVELS = "Channel Volume Levels, Set from a channel’s options · decoded audio only"
    }
}
