// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
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

    @Test fun okFlipsASwitch() {
        screen { SettingsScreen(SettingsPage.Launcher, SettingsValues(), SettingsActions()) }
        compose.onNodeWithContentDescription("Use Plasma as Home Screen, Home Button Override").assertIsFocused()
        press(Key.DirectionDown, times = 8)
        compose.onNodeWithContentDescription("Show Android Apps, On").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Show Android Apps, Off").assertIsFocused()
    }
}
