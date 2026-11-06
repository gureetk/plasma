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
        // The owner's asks: the TV itself, between the drive and the phone (3 November), in a
        // folder that outlasts Plasma, asked each time (#141, 4 November).
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(), initialItem = "backup") }

        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Save on This TV"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Without Logins"))
        press(Key.DirectionCenter)

        compose.onNodeWithText("Where on this TV?", substring = true).assertExists()
        focused().assert(hasText("Downloads"))
        compose.onNodeWithText("Download/Plasma").assertExists()
        compose.onNodeWithText("Documents").assertExists()
        // A TV without a file picker that saves has no Choose a Folder.
        compose.onNodeWithText("Choose a Folder").assertDoesNotExist()
        capture("19-settings-backup-where")
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        compose.onNodeWithText("in Documents/Plasma on this TV, where it stays if Plasma is uninstalled", substring = true).assertExists()
        // Android 10 and later keep it from Plasma after that, without a file manager.
        compose.onNodeWithText("needs a file manager app", substring = true).assertExists()
        focused().assert(hasText("Done"))
    }

    @Test fun restoreFromBackupListsWhatItFoundThenWhatABackupHolds() {
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(), initialItem = "restore") }

        press(Key.DirectionCenter)
        compose.onNodeWithText("USB drive").assertExists()
        compose.onNodeWithText("Downloads").assertExists()
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
