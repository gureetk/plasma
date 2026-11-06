// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Recordings that outlast Plasma (#161) in Settings › Recording: where they're kept, and Find Recordings. */
class FindRecordingsSettingsTest : ScreenshotTest() {
    @Test fun findRecordingsLooksInPlasmasFolders() {
        var finds = 0
        screen { SettingsScreen(SettingsPage.Recording, SettingsValues(), SettingsActions(onFindRecordings = { finds++ }), initialItem = "find-recordings") }
        focused().assert(hasText("Find Recordings"))
        compose.onNodeWithText("Adds the recordings in Download/Plasma/Recordings that Recordings doesn", substring = true).assertExists()
        capture("34-settings-find-recordings")

        press(Key.DirectionCenter)
        assertEquals(1, finds)
    }

    @Test fun recordToSaysWhereRecordingsAreKept() {
        screen { SettingsScreen(SettingsPage.Recording, SettingsValues(), SettingsActions(), initialItem = "record-to") }
        compose.onNodeWithText("kept in Download/Plasma/Recordings there, where they stay if Plasma is uninstalled", substring = true).assertExists()
    }

    @Test fun whereAndroidKeepsThemInPlasmasOwnFolderItSaysSo() {
        val own = SettingsSamples.facts.copy(recordingsLast = false)
        screen { SettingsScreen(SettingsPage.Recording, SettingsValues(), SettingsActions(), facts = own, initialItem = "record-to") }
        compose.onNodeWithText("which Android deletes if Plasma is uninstalled", substring = true).assertExists()
    }
}
