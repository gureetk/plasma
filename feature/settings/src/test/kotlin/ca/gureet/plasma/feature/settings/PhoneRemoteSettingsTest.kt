// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Settings › Remote & Buttons › Phone Remote (brief 4.33 and 5.21): the phones paired, renamed and forgotten. */
class PhoneRemoteSettingsTest : ScreenshotTest() {
    @Test fun forgetAsksFirst() {
        val forgotten = mutableListOf<String>()
        screen { SettingsScreen(SettingsPage.PhoneRemote, SettingsValues(), SettingsActions(onForgetPhone = { forgotten += it })) }

        // Phone Remote, then the paired phones.
        press(Key.DirectionDown)
        focused().assert(hasText("Maya’s iPhone"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Rename"))
        press(Key.DirectionDown)
        focused().assert(hasText("Forget"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Forget Maya’s iPhone?").assertExists()
        press(Key.DirectionCenter)

        check(forgotten == listOf("Maya’s iPhone")) { "Forgot $forgotten" }
    }

    @Test fun renameTypesANewName() {
        val renamed = mutableListOf<Pair<String, String>>()
        screen { SettingsScreen(SettingsPage.PhoneRemote, SettingsValues(), SettingsActions(onRenamePhone = { id, name -> renamed += id to name })) }

        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Daniel’s Pixel"))
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Daniel’s Pixel").assertExists()
        press(Key.Two)
        // OK moves to the keyboard, Next from the only field to Save.
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight, times = 8)
        focused().assert(hasText("Next"))
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)

        check(renamed == listOf("Daniel’s Pixel" to "Daniel’s Pixel2")) { "Renamed $renamed" }
    }

    @Test fun nothingShowsWhileThePhoneRemoteStarts() {
        screen { SettingsScreen(SettingsPage.PhoneRemote, SettingsValues(), SettingsActions(), facts = SettingsSamples.facts.copy(phoneStarting = true)) }

        compose.onNodeWithText("7KQ 4MZ").assertDoesNotExist()
        compose.onNodeWithText("Connect this TV to your Wi-Fi or Ethernet first, so your phone can reach it.").assertDoesNotExist()
    }

    @Test fun offTheNetworkItSaysSo() {
        screen { SettingsScreen(SettingsPage.PhoneRemote, SettingsValues(), SettingsActions(), facts = SettingsSamples.facts.copy(phoneAvailable = false)) }

        compose.onNodeWithText("Connect this TV to your Wi-Fi or Ethernet first, so your phone can reach it.").assertExists()
        compose.onNodeWithText("7KQ 4MZ").assertDoesNotExist()
    }
}
