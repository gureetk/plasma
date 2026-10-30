// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/**
 * First-time setup (#85): the welcome, each step and All Set as images (the "00" boards, before
 * Home's), and the D-pad's way through them: Continue from step to step, Back to the one before,
 * Skip for Now, and each step's rows.
 */
class FirstRunTest : ScreenshotTest() {
    /** A new install: no source, Plasma not Home yet, the first profile alone and no PIN. */
    private val newInstall = SettingsSamples.facts.copy(
        sources = emptyList(),
        home = SettingsSamples.facts.home.copy(method = null, overrideOn = false),
        profiles = listOf(ProfileInfo("default", "", first = true)),
        currentProfile = "default",
        pinSet = false,
        duplicatesFixed = 0,
    )

    /** Home Portal just added: its channels are in, its guide is loading. */
    private val justAdded = newInstall.copy(sources = listOf(SettingsSamples.facts.sources.first().copy(loading = SourceLoading.Guide)))

    private fun setup(
        start: SetupScreen = SetupScreen.Welcome,
        facts: SettingsFacts = newInstall,
        values: SettingsValues = SettingsValues(),
        actions: SettingsActions = SettingsActions(),
        setup: SetupActions = SetupActions(),
        again: Boolean = false,
    ) {
        screen { FirstTimeSetup(values, { actions }, setup, facts = facts, again = again, start = start) }
    }

    /** Down until the row read as [description] has focus. */
    private fun downTo(description: String) {
        repeat(12) {
            if (runCatching { compose.onNodeWithContentDescription(description).assertIsFocused() }.isSuccess) return
            press(Key.DirectionDown)
        }
        compose.onNodeWithContentDescription(description).assertIsFocused()
    }

    // ---- The images ----------------------------------------------------------------------------------

    @Test fun welcome() {
        setup()
        focused().assert(hasText("Set Up Plasma"))
        compose.onNodeWithText("Skip for Now keeps the sample channels. Set up Plasma any time in Settings › About.").assertExists()
        capture("00-setup-welcome")
    }

    @Test fun service() {
        setup(SetupScreen.Service, justAdded)
        // Back from Add a Source with a source: Continue, with what it's still loading.
        compose.onNodeWithContentDescription("Continue").assertIsFocused()
        compose.onNodeWithText("1,284 channels").assertExists()
        compose.onNodeWithText("Guide · loading…").assertExists()
        compose.onNodeWithText("Movies and series · next").assertExists()
        compose.onNodeWithText("Next: Home Screen.").assertExists()
        capture("00-setup-service")
    }

    @Test fun homeScreen() {
        setup(SetupScreen.HomeScreen)
        // The way that suits a Google TV, marked and focused.
        compose.onNodeWithContentDescription("Home Button Override, Recommended, Accessibility service · works like Projectivy, Off").assertIsFocused()
        compose.onNodeWithText("Step 2 of 5").assertExists()
        compose.onNodeWithText("Your TV Service").assertExists()
        capture("00-setup-home-screen")
    }

    @Test fun profiles() {
        setup(SetupScreen.Profiles)
        compose.onNodeWithContentDescription("Add Profile").assertIsFocused()
        compose.onNodeWithContentDescription("Me, Watching now").assertExists()
        compose.onNodeWithContentDescription("Set PIN").assertExists()
        capture("00-setup-profiles")
    }

    @Test fun look() {
        setup(SetupScreen.Look)
        compose.onNodeWithContentDescription("White").assertIsFocused().assertIsSelected()
        capture("00-setup-look")
    }

    @Test fun lookPreviewsEachOptionBeforeItsChosen() {
        val stored = mutableListOf<Pair<String, Int>>()
        val values = SettingsValues(mapOf(SettingKeys.ACCENT to 1)) { key, index -> stored += key to index }
        setup(SetupScreen.Look, values = values)
        // Focus starts on the accent chosen.
        compose.onNodeWithContentDescription("Blue").assertIsFocused().assertIsSelected()
        downTo("Large")
        // The preview shows Large text in the blue accent, and nothing's chosen yet.
        compose.onNodeWithText("Text Size").assertExists()
        compose.onNodeWithContentDescription("Large").assertIsNotSelected()
        check(stored.isEmpty()) { "Stored $stored" }
        capture("00-setup-look-large")

        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Large").assertIsSelected()
        compose.onNodeWithContentDescription("Default").assertIsNotSelected()
        check(stored == listOf(SettingKeys.TEXT_SIZE to 1)) { "Stored $stored" }
    }

    @Test fun guide() {
        setup(SetupScreen.Guide, SettingsSamples.facts)
        // Each source's guide, then the numbers.
        compose.onNodeWithContentDescription("Home Portal, From the portal").assertIsFocused()
        compose.onNodeWithContentDescription("Backup Line, From the server").assertExists()
        compose.onNodeWithContentDescription("Channel Numbers, From your playlists").assertExists()
        compose.onNodeWithContentDescription("Duplicate Numbers, 4 channels had a number that was already taken, 4 fixed").assertExists()
        capture("00-setup-guide")
    }

    @Test fun done() {
        val facts = SettingsSamples.facts.copy(sources = SettingsSamples.facts.sources.take(1))
        setup(SetupScreen.Done, facts, values = SettingsValues(mapOf(SettingKeys.ACCENT to 1)))
        focused().assert(hasText("Go to Home"))
        // What's set up, and only that.
        compose.onNodeWithText("Home Portal, with 1,284 channels").assertExists()
        compose.onNodeWithText("Plasma is your Home screen").assertExists()
        compose.onNodeWithText("4 profiles").assertExists()
        compose.onNodeWithText("Parental controls are on").assertExists()
        compose.onNodeWithText("Blue accent").assertExists()
        compose.onNodeWithText("Large text").assertDoesNotExist()
        capture("00-setup-done")
    }

    // ---- The way through ------------------------------------------------------------------------------

    @Test fun theStepsFollowEachOtherAndBackGoesBack() {
        var finished = false
        setup(setup = SetupActions(onFinish = { finished = true }))
        press(Key.DirectionCenter)

        // Your TV Service: nothing added yet, so Add Portal or Playlist.
        compose.onNodeWithText("Your TV Service").assertExists()
        compose.onNodeWithText("Step 1 of 5").assertExists()
        compose.onNodeWithText("Welcome").assertExists()
        compose.onNodeWithContentDescription("Add Portal or Playlist").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Continue, With the sample channels").assertIsFocused()
        press(Key.DirectionCenter)

        // Home Screen, its back link naming the step before.
        compose.onNodeWithText("Step 2 of 5").assertExists()
        compose.onNodeWithText("Your TV Service").assertExists()
        compose.onNodeWithContentDescription("Home Button Override, Recommended, Accessibility service · works like Projectivy, Off").assertIsFocused()

        // Back goes to the step before, on the row that had focus.
        back()
        compose.onNodeWithText("Step 1 of 5").assertExists()
        compose.onNodeWithContentDescription("Continue, With the sample channels").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Step 2 of 5").assertExists()
        downTo("Continue")
        press(Key.DirectionCenter)

        // Profiles and Parental PIN.
        compose.onNodeWithText("Step 3 of 5").assertExists()
        compose.onNodeWithText("Home Screen").assertExists()
        compose.onNodeWithContentDescription("Add Profile").assertIsFocused()
        downTo("Continue")
        press(Key.DirectionCenter)

        // Look.
        compose.onNodeWithText("Step 4 of 5").assertExists()
        compose.onNodeWithText("Profiles and Parental PIN").assertExists()
        compose.onNodeWithContentDescription("White").assertIsFocused()
        downTo("Continue")
        press(Key.DirectionCenter)

        // Guide and Channel Numbers: the last step.
        compose.onNodeWithText("Step 5 of 5").assertExists()
        compose.onNodeWithContentDescription("TV Guide Sources").assertIsFocused()
        downTo("Continue")
        compose.onNodeWithText("That’s the last step. Next, a look at what’s set up.").assertExists()
        press(Key.DirectionCenter)

        // All Set: nothing set up, so the sample channels; Back returns to the last step.
        compose.onNodeWithText("All Set").assertExists()
        compose.onNodeWithText("You’re on the sample channels. Add your own in Settings › Portals & Playlists.").assertExists()
        focused().assert(hasText("Go to Home"))
        back()
        compose.onNodeWithText("Step 5 of 5").assertExists()
        compose.onNodeWithContentDescription("Continue").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        check(finished) { "Go to Home didn't finish setup" }
    }

    @Test fun backOnTheFirstStepGoesToTheWelcome() {
        setup()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Step 1 of 5").assertExists()
        back()
        focused().assert(hasText("Set Up Plasma"))
        compose.onNodeWithText("Step 1 of 5").assertDoesNotExist()
    }

    @Test fun skipForNowLeavesSetup() {
        var skipped = false
        setup(setup = SetupActions(onSkip = { skipped = true }))
        press(Key.DirectionRight)
        focused().assert(hasText("Skip for Now"))
        press(Key.DirectionCenter)
        check(skipped) { "Skip for Now didn't leave" }
    }

    @Test fun runAgainFromSettingsTheWayOutIsBackToSettings() {
        var left = false
        setup(setup = SetupActions(onSkip = { left = true }), again = true)
        focused().assert(hasText("Set Up Plasma"))
        compose.onNodeWithText("Skip for Now").assertDoesNotExist()
        press(Key.DirectionRight)
        focused().assert(hasText("Back to Settings"))
        press(Key.DirectionCenter)
        check(left) { "Back to Settings didn't leave" }
    }

    // ---- Each step's rows -----------------------------------------------------------------------------

    @Test fun addPortalOrPlaylistOpensAddASource() {
        var went: SettingsDestination? = null
        setup(SetupScreen.Service, actions = SettingsActions(onGo = { went = it }))
        press(Key.DirectionCenter)
        check(went == SettingsDestination.AddSource) { "Went to $went" }
    }

    @Test fun aSourceJustAddedSaysWhatItsLoading() {
        setup(SetupScreen.Service, justAdded.copy(sources = justAdded.sources.map { it.copy(loading = SourceLoading.Library) }))
        press(Key.DirectionUp, times = 2)
        compose.onNodeWithContentDescription("Home Portal, Stalker · portal.example.net, Connected").assertIsFocused()
        compose.onNodeWithText("Movies and series · loading…").assertExists()
        compose.onNodeWithText("Guide").assertExists()
    }

    @Test fun plasmaAlreadyHomeStartsOnContinue() {
        setup(SetupScreen.HomeScreen, SettingsSamples.facts)
        compose.onNodeWithContentDescription("Continue").assertIsFocused()
        compose.onNodeWithText("Recommended").assertDoesNotExist()
    }

    @Test fun aTvThatLetsAnAppBeHomeIsRecommendedThat() {
        val facts = newInstall.copy(home = newInstall.home.copy(defaultHome = DefaultHomeOffer.Chooser))
        setup(SetupScreen.HomeScreen, facts)
        compose.onNodeWithContentDescription("Default Home App, Recommended, For TVs that let you choose a Home app, Set Up").assertIsFocused()
    }

    @Test fun setPinThenChangePin() {
        var went: SettingsDestination? = null
        setup(SetupScreen.Profiles, actions = SettingsActions(onGo = { went = it }))
        downTo("Set PIN")
        press(Key.DirectionCenter)
        check(went == SettingsDestination.SetPin) { "Went to $went" }
    }

    @Test fun withAPinItsChangePin() {
        var went: SettingsDestination? = null
        setup(SetupScreen.Profiles, newInstall.copy(pinSet = true), actions = SettingsActions(onGo = { went = it }))
        downTo("Change PIN")
        press(Key.DirectionCenter)
        check(went == SettingsDestination.ChangePin) { "Went to $went" }
    }

    @Test fun addProfileNamesOne() {
        val added = mutableListOf<String>()
        setup(SetupScreen.Profiles, actions = SettingsActions(onAddProfile = { added += it }))
        press(Key.DirectionCenter)
        press(Key.M)
        press(Key.A)
        // OK moves to the keyboard, Next from the only field to Add Profile.
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight, times = 8)
        focused().assert(hasText("Next"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Add Profile"))
        press(Key.DirectionCenter)
        check(added == listOf("ma")) { "Added $added" }
    }

    @Test fun aSourcesGuideOpensItsEdit() {
        var did: Pair<String, SourceAction>? = null
        setup(SetupScreen.Guide, SettingsSamples.facts, actions = SettingsActions(onSource = { id, action -> did = id to action }))
        // A portal's guide is its own: nothing to open.
        press(Key.DirectionCenter)
        check(did == null) { "Did $did" }
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(did == "backup" to SourceAction.Edit) { "Did $did" }
    }

    @Test fun channelNumbersIsSettingsOwn() {
        val stored = mutableListOf<Pair<String, Int>>()
        var went: SettingsDestination? = null
        val values = SettingsValues { key, index -> stored += key to index }
        setup(SetupScreen.Guide, SettingsSamples.facts, values = values, actions = SettingsActions(onGo = { went = it }))
        downTo("Channel Numbers, From your playlists")
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Channel Numbers, In playlist order").assertIsFocused()
        check(stored == listOf(SettingKeys.CHANNEL_NUMBERS to 1)) { "Stored $stored" }
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(went == SettingsDestination.DuplicateNumbers) { "Went to $went" }
    }

    @Test fun withoutDuplicatesTheresNoDuplicateNumbersRow() {
        setup(SetupScreen.Guide)
        compose.onNodeWithText("Duplicate Numbers").assertDoesNotExist()
    }
}
