// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings › About › Reset Settings (#159). */
class ResetSettingsTest : ScreenshotTest() {
    @Test fun resetSettingsSaysWhatGoesAndWhatStaysWithCancelFirst() {
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(), initialItem = "reset") }
        focused().assert(hasText("Reset Settings"))

        press(Key.DirectionCenter)

        compose.onNodeWithText("Every setting goes back to how Plasma came", substring = true).assertExists()
        compose.onNodeWithText("Back Up Settings First").assertExists()
        focused().assert(hasText("Cancel"))
        capture("19-settings-reset")
    }

    @Test fun onlyResetSettingsHandsItToTheApp() {
        var resets = 0
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(onResetSettings = { resets++ }), initialItem = "reset") }

        // Cancel changes nothing.
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        assertEquals(0, resets)

        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionCenter)
        assertEquals(1, resets)
    }

    @Test fun backUpFirstOffersThePlacesAndBackComesBackToTheQuestion() {
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(), initialItem = "reset") }
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Back Up Settings First"))

        press(Key.DirectionCenter)
        compose.onNodeWithText("Save to USB Drive").assertExists()
        compose.onNodeWithText("Save on This TV").assertExists()
        compose.onNodeWithText("Send to Your Phone").assertExists()

        press(Key.Back)
        compose.onNodeWithText("Every setting goes back to how Plasma came", substring = true).assertExists()
    }

    @Test fun afterTheBackupItAsksAgain() {
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(), initialItem = "reset") }
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        // Save to USB Drive: Back Up Settings' own sheet, from its first step.
        press(Key.DirectionCenter)
        compose.onNodeWithText("Without Logins").assertExists()

        // Leaving it comes back to the question, Cancel first, not to where to save.
        press(Key.Back)
        compose.onNodeWithText("Every setting goes back to how Plasma came", substring = true).assertExists()
        focused().assert(hasText("Cancel"))
    }
}
