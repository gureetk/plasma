// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** About › Back Up Settings and Restore from Backup (brief 5.17, #108). */
class BackupSettingsTest : ScreenshotTest() {
    @Test fun backUpSettingsAsksAboutLogins() {
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(), initialItem = "backup") }

        press(Key.DirectionCenter)
        focused().assert(hasText("Save to USB Drive"))
        press(Key.DirectionCenter)

        focused().assert(hasText("Without Logins"))
        compose.onNodeWithText("Put your sources’ logins in it too?", substring = true).assertExists()
        capture("19-settings-backup")
    }

    @Test fun backUpSettingsCanSaveOnThisTv() {
        // The owner's ask (3 November): the TV itself, between the drive and the phone.
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(), initialItem = "backup") }

        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Save on This TV"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Without Logins"))
        press(Key.DirectionCenter)

        compose.onNodeWithText("Saved on this TV", substring = true).assertExists()
        compose.onNodeWithText("use a USB drive or your phone", substring = true).assertExists()
        focused().assert(hasText("Done"))
    }

    @Test fun restoreFromBackupListsWhatItFoundThenWhatABackupHolds() {
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(), initialItem = "restore") }

        press(Key.DirectionCenter)
        compose.onNodeWithText("USB drive").assertExists()
        compose.onNodeWithText("This TV").assertExists()
        compose.onNodeWithText("Before an update").assertExists()
        compose.onNodeWithText("From Your Phone").assertExists()
        press(Key.DirectionCenter)

        compose.onNodeWithText("Restore This Backup?").assertExists()
        compose.onNodeWithText("Sources: Home Server, Arena Playlist").assertExists()
        compose.onNodeWithText("3 profiles · 24 favorites · 5 reminders and recordings").assertExists()
        focused().assert(hasText("Restore with Logins"))
        capture("19-settings-restore")
    }
}
