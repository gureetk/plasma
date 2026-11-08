// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Android TV Settings (#167): the main page's last row opens the TV's own settings. */
class TvSettingsRowTest : ScreenshotTest() {
    @Test fun theLastRowOpensTheTvsOwnSettings() {
        var opened = 0
        screen { SettingsScreen(SettingsPage.Main, SettingsValues(), SettingsActions(onTvSettings = { opened++ })) }

        // Down past About, the last of the rest.
        repeat(12) { press(Key.DirectionDown) }
        focused().assert(hasText("Android TV Settings"))
        compose.onNodeWithText("The TV’s own settings: display and sound", substring = true).assertExists()
        capture("19-settings-tv-settings")

        press(Key.DirectionCenter)
        assertEquals(1, opened)
    }
}
