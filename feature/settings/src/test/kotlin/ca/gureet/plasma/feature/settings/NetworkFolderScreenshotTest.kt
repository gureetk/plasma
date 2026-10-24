// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Recording › Copy to Network Folder (brief 4.34 and 4.35): setting the folder up, and turning it off. */
class NetworkFolderScreenshotTest : ScreenshotTest() {
    private val off = SettingsSamples.facts.copy(networkFolder = "", networkFolderFree = 0)

    @Test fun aFolderIsChosenOnAServerFoundOnTheNetwork() {
        screen { SettingsScreen(SettingsPage.Recording, SettingsValues(), SettingsActions(), facts = off, initialItem = "copy") }
        compose.onNodeWithContentDescription("Copy to Network Folder, Off").assertIsFocused()

        // The servers on the network, the first one found in focus, then Enter Address.
        press(Key.DirectionCenter)
        compose.onNodeWithText("Choose your network storage, or enter its address.").assertExists()
        focused().assert(hasText("NAS"))
        compose.onNodeWithText("Enter Address").assertExists()
        capture("34-network-folder-servers")

        // Its username and password, on the keyboard page; blank is a guest.
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Username").assertIsFocused()
        compose.onNodeWithText("Sign In to nas.home.lan").assertExists()
        capture("34-network-folder-sign-in")
        pressDownTo("Sign In")
        press(Key.DirectionCenter)

        // Its shares, then a share's folders, with Use This Folder first.
        compose.onNodeWithText("Choose the share to copy recordings to.").assertExists()
        focused().assert(hasText("Backups"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        focused().assert(hasText("Use This Folder"))
        pressDownTo("Recordings")
        press(Key.DirectionCenter)
        compose.onNodeWithText("nas.home.lan › Media › Recordings").assertExists()
        focused().assert(hasText("Use This Folder"))
        capture("34-network-folder-folders")

        // Back goes up a folder, then to the shares.
        press(Key.Back)
        compose.onNodeWithText("nas.home.lan › Media").assertExists()
        press(Key.Back)
        compose.onNodeWithText("Choose the share to copy recordings to.").assertExists()
    }

    @Test fun anAddressCanBeTypedInstead() {
        screen { SettingsScreen(SettingsPage.Recording, SettingsValues(), SettingsActions(), facts = off, initialItem = "copy") }
        press(Key.DirectionCenter)
        pressDownTo("Enter Address")
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Address, smb://nas.home.lan/Media").assertIsFocused()
        // The keyboard's shortcuts are a share's.
        compose.onNodeWithText("smb://").assertExists()
        compose.onNodeWithText("http://").assertDoesNotExist()
    }

    @Test fun onItCanBeChangedOrTurnedOff() {
        var forgot = 0
        screen {
            SettingsScreen(SettingsPage.Recording, SettingsValues(), SettingsActions(onForgetNetworkFolder = { forgot++ }), initialItem = "copy")
        }
        compose.onNodeWithContentDescription("Copy to Network Folder, On · nas.home.lan").assertIsFocused()

        press(Key.DirectionCenter)
        focused().assert(hasText("Change Folder"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Stop Copying to nas.home.lan?").assertExists()
        focused().assert(hasText("Turn Off"))
        press(Key.DirectionCenter)
        assertEquals(1, forgot)
        compose.onNodeWithContentDescription("Copy to Network Folder, On · nas.home.lan").assertIsFocused()

        // Change Folder: the setup.
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Choose your network storage, or enter its address.").assertExists()
    }

    private fun pressDownTo(label: String) {
        repeat(10) {
            if (runCatching { focused().assert(hasText(label)) }.isSuccess) return
            press(Key.DirectionDown)
        }
        focused().assert(hasText(label))
    }
}
