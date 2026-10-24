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

/** Continue Watching › Jellyfin Server (brief 4.32 and 4.35): signing in, and Forget Server. */
class JellyfinSignInScreenshotTest : ScreenshotTest() {
    private val signedOut = SettingsSamples.facts.copy(jellyfinConnected = false)

    @Test fun signsInWithQuickConnectOrAPassword() {
        screen { SettingsScreen(SettingsPage.ContinueWatching, SettingsValues(mapOf(KEY_INCLUDE_APPS to 0)), SettingsActions(), facts = signedOut) }
        press(Key.DirectionDown, times = 2)
        compose.onNodeWithContentDescription("Jellyfin Server, Not Connected").assertIsFocused()

        // The servers on the network, the first one found in focus, then Enter Address.
        press(Key.DirectionCenter)
        compose.onNodeWithText("Choose your server, or enter its address.").assertExists()
        focused().assert(hasText("Media"))
        compose.onNodeWithText("Enter Address").assertExists()
        capture("32-jellyfin-sign-in")

        // Quick Connect: the code to approve in another Jellyfin app.
        press(Key.DirectionCenter)
        compose.onNodeWithText("Sign In to Media").assertExists()
        compose.onNodeWithText("736 204").assertExists()
        focused().assert(hasText("Use Password Instead"))
        capture("32-jellyfin-quick-connect")

        // The password instead, on the keyboard page.
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Username").assertIsFocused()
        compose.onNodeWithText("Sign in with your Jellyfin username and password.").assertExists()
        compose.onNodeWithText("Sign In").assertExists()
        capture("32-jellyfin-password")

        // Back: Quick Connect again; Back again closes, on the row.
        press(Key.Back)
        focused().assert(hasText("Use Password Instead"))
        press(Key.Back)
        compose.onNodeWithContentDescription("Jellyfin Server, Not Connected").assertIsFocused()
    }

    @Test fun anAddressCanBeTypedInstead() {
        screen { SettingsScreen(SettingsPage.ContinueWatching, SettingsValues(mapOf(KEY_INCLUDE_APPS to 0)), SettingsActions(), facts = signedOut) }
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Enter Address"))

        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Server Address, 192.168.1.20:8096").assertIsFocused()
        press(Key.One)
        press(Key.DirectionCenter)
        pressDownTo("Connect")
        press(Key.DirectionCenter)

        // The server there, then its code.
        compose.onNodeWithText("736 204").assertExists()
    }

    @Test fun signedInTheServerCanBeForgotten() {
        var forgot = 0
        screen { SettingsScreen(SettingsPage.ContinueWatching, SettingsValues(mapOf(KEY_INCLUDE_APPS to 0)), SettingsActions(onForgetJellyfin = { forgot++ })) }
        press(Key.DirectionDown, times = 2)
        compose.onNodeWithContentDescription("Jellyfin Server, media.home.lan · signed in as Maya, Connected").assertIsFocused()

        press(Key.DirectionCenter)
        focused().assert(hasText("Forget Server"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Forget media.home.lan?").assertExists()
        compose.onNodeWithText("Plasma signs out of it, and its items leave Continue Watching.").assertExists()
        focused().assert(hasText("Forget Server"))
        press(Key.DirectionCenter)

        check(forgot == 1) { "Forgot $forgot times" }
    }

    /** Down until [label] has focus, through the keyboard. */
    private fun pressDownTo(label: String) {
        repeat(10) {
            if (runCatching { focused().assert(hasText(label)) }.isSuccess) return
            press(Key.DirectionDown)
        }
        focused().assert(hasText(label))
    }
}
