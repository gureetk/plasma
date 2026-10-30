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
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Settings and its pages against boards 19, 20, 22, 23, 27–29 and 31–34, and their D-pad paths. */
class SettingsScreenshotTest : ScreenshotTest() {
    private fun page(page: SettingsPage, name: String, item: String? = null, values: SettingsValues = SettingsValues()) {
        screen { SettingsScreen(page, values, SettingsActions(), initialItem = item) }
        capture(name)
    }

    @Test fun settings() = page(SettingsPage.Main, "19-settings")

    @Test fun sources() = page(SettingsPage.Sources, "20-settings-portals-and-playlists")

    @Test fun launcher() = page(SettingsPage.Launcher, "22-settings-launcher-and-home", item = "home-button")

    // The boards have a user who chose Always · 2 hours; When I pause is the default (brief 5.5).
    private val boardRewind = SettingsValues(mapOf(KEY_REWIND to 4))

    @Test fun playback() = page(SettingsPage.Playback, "27-settings-playback", values = boardRewind)

    @Test fun audio() = page(SettingsPage.Audio, "28-settings-audio")

    @Test fun channels() = page(SettingsPage.Channels, "29-settings-channels", item = "favorites")

    @Test fun rewind() = page(SettingsPage.Rewind, "31-rewind-and-local-catch-up", values = boardRewind)

    @Test fun localCatchUpChannelsTicksWhatTheConnectionsAllow() {
        val toggled = mutableListOf<Pair<String, Boolean>>()
        val choices = listOf(
            LocalChannelChoice("home:103", Sample.arenaNews.number, Sample.arenaNews.name, "Home Portal", limit = 2, on = false),
            LocalChannelChoice("home:108", Sample.courtside.number, Sample.courtside.name, "Home Portal", limit = 2, on = true),
            LocalChannelChoice("home:109", Sample.fairway.number, Sample.fairway.name, "Home Portal", limit = 2, on = false),
            LocalChannelChoice("backup:140", Sample.cinema.number, Sample.cinema.name, "Backup Line", limit = 1, on = false),
            LocalChannelChoice("extras:110", Sample.combat.number, Sample.combat.name, "Sports Extras", limit = null, on = false),
        )
        screen {
            SettingsScreen(
                SettingsPage.Rewind, boardRewind, SettingsActions(onLocalCatchUp = { id, on -> toggled += id to on }),
                facts = SettingsSamples.facts.copy(localChoices = choices), initialItem = "local-channels",
            )
        }
        press(Key.DirectionCenter)
        // Home Portal has two connections, and Courtside+ holds one: another would leave none to watch with.
        compose.onNodeWithText("Home Portal has no connection to spare: one has to stay free for watching.").assertExists()
        capture("31-local-catch-up-channels")
        press(Key.DirectionCenter)
        check(toggled.isEmpty()) { "Ticked $toggled" }

        press(Key.DirectionDown)
        compose.onNodeWithText("Uses 1 of your 2 Home Portal connections all the time.").assertExists()
        press(Key.DirectionCenter)
        // Untick Courtside+, and Arena Sports News can have its connection.
        press(Key.DirectionUp)
        compose.onNodeWithText("Uses 1 of your 2 Home Portal connections all the time.").assertExists()
        press(Key.DirectionCenter)
        check(toggled == listOf("home:108" to false, "home:103" to true)) { "Ticked $toggled" }
        press(Key.DirectionDown, times = 4)
        compose.onNodeWithText("Holds one of your Sports Extras connections all the time.").assertExists()
    }

    // The board has other apps turned on (they're off until the user turns them on).
    @Test fun continueWatching() = page(SettingsPage.ContinueWatching, "32-settings-continue-watching", values = SettingsValues(mapOf(KEY_INCLUDE_APPS to 0)))

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
        // Plasma isn't Home yet, so the way that suits this TV is marked (brief 3.4, A5).
        compose.onNodeWithContentDescription(OVERRIDE_RECOMMENDED).assertIsFocused()
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
        compose.onNodeWithContentDescription(OVERRIDE_RECOMMENDED).assertIsFocused()

        // Turn On over Wireless Debugging is next, for a TV that holds the switch back.
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Turn On over Wireless Debugging"))
        press(Key.DirectionCenter)
        check(did == listOf(HomeAction.OpenAccessibility, HomeAction.TurnOnOverDebugging)) { "Did $did" }

        // Not Now closes it, and so does Back.
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Not Now"))
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        press(Key.Back)
        compose.onNodeWithText("Open Accessibility Settings").assertDoesNotExist()
        check(did == listOf(HomeAction.OpenAccessibility, HomeAction.TurnOnOverDebugging)) { "Did $did" }
    }

    @Test fun aRestrictedSwitchComesWithItsFix() {
        val did = mutableListOf<HomeAction>()
        val facts = homeFacts { copy(method = null, overrideOn = false, restricted = true, packageName = "ca.gureet.plasma.dev") }
        screen { SettingsScreen(SettingsPage.HomeScreen, SettingsValues(), SettingsActions(onHome = { did += it }), facts = facts) }
        press(Key.DirectionCenter)
        compose.onNodeWithText(
            "Plasma’s switch may be grayed out on this TV. Allow restricted settings in Plasma’s App info, if the TV has it, " +
                "or run this from a computer: adb shell appops set ca.gureet.plasma.dev ACCESS_RESTRICTED_SETTINGS allow",
        ).assertExists()
        press(Key.DirectionDown, times = 2)
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

    @Test fun hiddenAppsShowAgainFromLauncherAndHome() {
        var shown: String? = null
        val facts = SettingsSamples.facts.copy(hiddenApps = listOf(HiddenApp("tv.example.cloudnest", "Cloudnest"), HiddenApp("tv.example.quarry", "Quarry")))
        screen { SettingsScreen(SettingsPage.Launcher, SettingsValues(), SettingsActions(onShowApp = { shown = it }), facts = facts) }
        // The last row, after Clock.
        press(Key.DirectionDown, times = 13)
        compose.onNodeWithContentDescription("Hidden Apps, 2 apps").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Choose an app to show it again.").assertExists()
        press(Key.DirectionDown)
        focused().assert(hasText("Quarry"))
        press(Key.DirectionCenter)
        check(shown == "tv.example.quarry") { "Showed $shown" }
    }

    @Test fun withNoHiddenAppsTheresNothingToOpen() {
        screen { SettingsScreen(SettingsPage.Launcher, SettingsValues(), SettingsActions()) }
        press(Key.DirectionDown, times = 13)
        compose.onNodeWithContentDescription("Hidden Apps, None").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Hidden Apps, None").assertIsFocused()
    }

    @Test fun keepWatchingInASmallWindowIsOnlyOnTvsWithPictureInPicture() {
        screen { SettingsScreen(SettingsPage.Launcher, SettingsValues(), SettingsActions()) }
        press(Key.DirectionDown, times = 5)
        compose.onNodeWithContentDescription("Keep Watching in a Small Window, When opening another app").assertIsFocused()
    }

    @Test fun withoutPictureInPictureTheresNoSmallWindowRow() {
        val facts = SettingsSamples.facts.copy(pictureInPicture = false)
        screen { SettingsScreen(SettingsPage.Launcher, SettingsValues(), SettingsActions(), facts = facts) }
        press(Key.DirectionDown, times = 4)
        compose.onNodeWithContentDescription("When the TV Turns Off, Stop after 1 minute").assertIsFocused()
        // Live Preview on Home comes straight after.
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Live Preview on Home, Muted").assertIsFocused()
        compose.onNodeWithText("Keep Watching in a Small Window").assertDoesNotExist()
    }

    @Test fun useAsTheTvsScreensaverExplainsThenSets() {
        val chosen = mutableListOf<TvScreensaverAction>()
        screen { SettingsScreen(SettingsPage.Launcher, SettingsValues(), SettingsActions(onTvScreensaver = { chosen += it })) }
        // After Screensaver.
        press(Key.DirectionDown, times = 11)
        compose.onNodeWithContentDescription("Use as the TV’s Screensaver, Off").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("From a computer: adb shell settings put secure screensaver_components ca.gureet.plasma/ca.gureet.plasma.PlasmaDream").assertExists()
        focused().assert(hasText("Set It Now"))
        press(Key.DirectionCenter)
        check(chosen == listOf(TvScreensaverAction.UsePlasma)) { "Chose $chosen" }
    }

    @Test fun theTvsOwnScreensaverComesBack() {
        val chosen = mutableListOf<TvScreensaverAction>()
        val facts = SettingsSamples.facts.copy(tvScreensaver = TvScreensaverFacts(state = TvScreensaver.Plasma))
        screen { SettingsScreen(SettingsPage.Launcher, SettingsValues(), SettingsActions(onTvScreensaver = { chosen += it }), facts = facts) }
        press(Key.DirectionDown, times = 11)
        compose.onNodeWithContentDescription("Use as the TV’s Screensaver, On").assertIsFocused()
        press(Key.DirectionCenter)
        focused().assert(hasText("Use the TV’s Own Screensaver"))
        press(Key.DirectionCenter)
        check(chosen == listOf(TvScreensaverAction.UseTvsOwn)) { "Chose $chosen" }
    }

    @Test fun includeOtherAppsExplainsThenAsksAndroid() {
        val chosen = mutableListOf<OtherAppsAction>()
        val values = SettingsValues()
        screen { SettingsScreen(SettingsPage.ContinueWatching, values, SettingsActions(onOtherApps = { chosen += it })) }
        compose.onNodeWithContentDescription("Include Other Apps, Off").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Plasma will read the Play Next row that apps on this TV share. It stays on this TV.").assertExists()
        focused().assert(hasText("Turn On"))
        press(Key.DirectionCenter)
        check(chosen == listOf(OtherAppsAction.TurnOn)) { "Chose $chosen" }
        compose.onNodeWithContentDescription("Include Other Apps, On").assertIsFocused()
    }

    @Test fun notAllowedYetOffersAnotherWay() {
        val chosen = mutableListOf<OtherAppsAction>()
        val facts = SettingsSamples.facts.copy(playNextAllowed = false)
        screen { SettingsScreen(SettingsPage.ContinueWatching, SettingsValues(mapOf(KEY_INCLUDE_APPS to 0)), SettingsActions(onOtherApps = { chosen += it }), facts = facts) }
        compose.onNodeWithText("Not allowed yet").assertExists()
        press(Key.DirectionCenter)
        focused().assert(hasText("Ask Again"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(chosen == listOf(OtherAppsAction.AllowOverWirelessDebugging)) { "Chose $chosen" }
    }

    @Test fun eachAppCanBeHidden() {
        val chosen = mutableListOf<OtherAppsAction>()
        screen { SettingsScreen(SettingsPage.ContinueWatching, SettingsValues(mapOf(KEY_INCLUDE_APPS to 0)), SettingsActions(onOtherApps = { chosen += it })) }
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Apps, Reelbox and Driftwood Podcasts, 2 apps").assertIsFocused()
        press(Key.DirectionCenter)
        focused().assert(hasText("Reelbox"))
        press(Key.DirectionCenter)
        check(chosen == listOf(OtherAppsAction.ShowApp("tv.example.reelbox", shown = false))) { "Chose $chosen" }
    }

    @Test fun remindersOverOtherAppsExplainsThenAllows() {
        val chosen = mutableListOf<OverAppsAction>()
        screen { SettingsScreen(SettingsPage.Guide, SettingsValues(), SettingsActions(onOverApps = { chosen += it })) }
        pressDownTo("Show over Other Apps")
        compose.onNodeWithContentDescription("Show over Other Apps, Not allowed yet").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("From a computer: adb shell appops set ca.gureet.plasma SYSTEM_ALERT_WINDOW allow").assertExists()
        focused().assert(hasText("Allow over Wireless Debugging"))
        press(Key.DirectionCenter)
        check(chosen == listOf(OverAppsAction.AllowOverWirelessDebugging)) { "Chose $chosen" }
    }

    @Test fun liveTvDetailsChoosesWhoseScheduleShows() {
        // The owner's choice of 30 October: the focused channel's, unless Playing Channel is chosen.
        val stored = mutableListOf<Pair<String, Int>>()
        val values = SettingsValues { key, index -> stored += key to index }
        screen { SettingsScreen(SettingsPage.Guide, values, SettingsActions()) }
        pressDownToRow("Live TV Details, Focused Channel")
        press(Key.DirectionCenter)
        focused().assert(hasText("Focused Channel"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(stored == listOf(KEY_LIVE_DETAILS to 1)) { "Stored $stored" }
    }

    @Test fun replacePlaceholderPostersIsASwitch() {
        // The owner's #81: on unless turned off.
        val stored = mutableListOf<Pair<String, Int>>()
        val values = SettingsValues { key, index -> stored += key to index }
        screen { SettingsScreen(SettingsPage.Sources, values, SettingsActions()) }
        pressDownToRow("Replace Placeholder Posters, On")
        press(Key.DirectionCenter)
        check(stored == listOf(KEY_PLACEHOLDER_POSTERS to 1)) { "Stored $stored" }
    }

    @Test fun allowedThereIsNothingToDo() {
        val facts = SettingsSamples.facts.copy(remindersOverApps = ReminderOverApps(allowed = true))
        screen { SettingsScreen(SettingsPage.Guide, SettingsValues(), SettingsActions(), facts = facts) }
        pressDownTo("Show over Other Apps")
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Show over Other Apps, Allowed").assertIsFocused()
    }

    // ---- About › Plasma Update (brief 5.18) ----------------------------------------------------------

    private val ready = UpdateFacts(
        status = UpdateStatus.Ready,
        version = "0.2.0 (512)",
        notes = "Picture-in-picture when you open another app, and Plasma updates itself.",
        checkedAt = Sample.at(18, 2),
    )

    private fun about(update: UpdateFacts, did: MutableList<UpdateAction> = mutableListOf()) {
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(onUpdate = { did += it }), facts = SettingsSamples.facts.copy(update = update)) }
        press(Key.DirectionDown)
    }

    @Test fun aboutWithAnUpdate() {
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(), facts = SettingsSamples.facts.copy(update = ready), initialItem = "update") }
        compose.onNodeWithText("Last checked · Today, 18:02").assertExists()
        capture("19-settings-about")
    }

    @Test fun anUpdateInstallsAfterItsNotes() {
        val did = mutableListOf<UpdateAction>()
        about(ready, did)
        compose.onNodeWithContentDescription("Plasma Update, 0.2.0 (512) is ready").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Plasma 0.2.0 (512)").assertExists()
        compose.onNodeWithText("Your settings, sources and favorites stay as they are.").assertExists()
        focused().assert(hasText("Install Now"))
        press(Key.DirectionCenter)
        check(did == listOf(UpdateAction.Install)) { "Did $did" }
    }

    @Test fun installingAsksForAndroidsOkFirst() {
        val did = mutableListOf<UpdateAction>()
        about(ready.copy(allowed = false), did)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Let Plasma Install Its Updates").assertExists()
        compose.onNodeWithText("From a computer: adb shell appops set ca.gureet.plasma REQUEST_INSTALL_PACKAGES allow").assertExists()
        focused().assert(hasText("Allow over Wireless Debugging"))
        press(Key.DirectionDown)
        focused().assert(hasText("Open the TV’s Settings"))
        press(Key.DirectionCenter)
        check(did == listOf(UpdateAction.OpenTvSettings)) { "Did $did" }
    }

    @Test fun upToDateLooksAgainOnOk() {
        val did = mutableListOf<UpdateAction>()
        about(UpdateFacts(UpdateStatus.UpToDate, checkedAt = Sample.at(9, 12)), did)
        compose.onNodeWithContentDescription("Plasma Update, Up to date").assertIsFocused()
        compose.onNodeWithText("Last checked · Today, 09:12").assertExists()
        press(Key.DirectionCenter)
        check(did == listOf(UpdateAction.CheckNow)) { "Did $did" }
    }

    @Test fun aCheckThatDidntWorkSaysWhy() {
        val did = mutableListOf<UpdateAction>()
        about(UpdateFacts(UpdateStatus.UpToDate, checkedAt = Sample.at(9, 12), problem = UpdateTrouble.CantReach), did)
        compose.onNodeWithContentDescription("Plasma Update, Couldn’t check").assertIsFocused()
        compose.onNodeWithText("Plasma couldn’t reach the update address. Check the TV’s internet connection. Press OK to try again.").assertExists()
        press(Key.DirectionCenter)
        check(did == listOf(UpdateAction.CheckNow)) { "Did $did" }
    }

    @Test fun aDownloadShowsHowFarItsGot() {
        val did = mutableListOf<UpdateAction>()
        about(ready.copy(status = UpdateStatus.Downloading, percent = 42), did)
        compose.onNodeWithContentDescription("Plasma Update, Downloading · 42%").assertIsFocused()
        // Nothing to do meanwhile.
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Plasma Update, Downloading · 42%").assertIsFocused()
        check(did.isEmpty()) { "Did $did" }
    }

    @Test fun whileTheTvInstallsOkAsksAgain() {
        val did = mutableListOf<UpdateAction>()
        about(ready.copy(status = UpdateStatus.Installing), did)
        compose.onNodeWithContentDescription("Plasma Update, Installing…").assertIsFocused()
        press(Key.DirectionCenter)
        check(did == listOf(UpdateAction.Install)) { "Did $did" }
    }

    @Test fun aBuildThatDoesntUpdateSaysSo() {
        about(UpdateFacts(UpdateStatus.Off))
        compose.onNodeWithContentDescription("Plasma Update, Not in this build").assertIsFocused()
        compose.onNodeWithText("This build of Plasma doesn’t look for updates.").assertExists()
    }

    @Test fun settingsSaysAnUpdateIsReady() {
        screen { SettingsScreen(SettingsPage.Main, SettingsValues(), SettingsActions(), facts = SettingsSamples.facts.copy(update = ready), initialItem = "about") }
        compose.onNodeWithContentDescription("About, Update ready").assertIsFocused()
    }

    private companion object {
        const val LEVELS = "Channel Volume Levels, Set from a channel’s options · decoded audio only"
        const val OVERRIDE_RECOMMENDED = "Home Button Override, Recommended, Accessibility service · works like Projectivy, Off"
    }
}
