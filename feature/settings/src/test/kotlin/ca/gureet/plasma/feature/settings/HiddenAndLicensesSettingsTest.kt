// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Settings › Channels' Hidden Channels and Hidden Groups (4.29), and About's Open-Source Licenses. */
class HiddenAndLicensesSettingsTest : ScreenshotTest() {
    @Test fun hiddenChannelsShowsOneAgainAndHidesItAgain() {
        val changed = mutableListOf<Pair<String, Boolean>>()
        val facts = SettingsSamples.facts.copy(hiddenChannelList = listOf(LockedChannelInfo("107", 107, "Velocity Motor")))
        screen {
            SettingsScreen(
                SettingsPage.Channels, SettingsValues(), SettingsActions(onHideChannel = { id, hidden -> changed += id to hidden }),
                facts = facts, initialItem = "hidden-channels",
            )
        }
        press(Key.DirectionCenter)
        focused().assert(hasText("Velocity Motor"))
        press(Key.DirectionCenter)
        // Still there, unticked, to tick again.
        focused().assert(hasText("Velocity Motor"))
        press(Key.DirectionCenter)

        check(changed == listOf("107" to false, "107" to true)) { "Changed $changed" }
    }

    @Test fun hiddenGroupsHidesAGroupOrShowsItAgain() {
        val changed = mutableListOf<Pair<String, Boolean>>()
        val facts = SettingsSamples.facts.copy(
            groupsToHide = listOf(HiddenGroupInfo("sports", "Sports", hidden = false), HiddenGroupInfo("shopping", "Shopping", hidden = true)),
        )
        screen {
            SettingsScreen(
                SettingsPage.Channels, SettingsValues(), SettingsActions(onHideGroup = { id, hidden -> changed += id to hidden }),
                facts = facts, initialItem = "hidden-groups",
            )
        }
        press(Key.DirectionCenter)
        focused().assert(hasText("Sports"))
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Shopping"))
        press(Key.DirectionCenter)

        check(changed == listOf("sports" to true, "shopping" to false)) { "Changed $changed" }
    }

    @Test fun openSourceLicensesSayEachPartsLicenseAndSource() {
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(), initialItem = "licenses") }
        press(Key.DirectionCenter)
        focused().assert(hasText("Plasma"))
        press(Key.DirectionDown)
        focused().assert(hasText("libVLC"))
        press(Key.DirectionCenter)

        compose.onNodeWithText(
            "libVLC is under the GNU Lesser General Public License, version 2.1 or later. Its source is at code.videolan.org/videolan/libvlcjni.",
        ).assertExists()
    }
}
