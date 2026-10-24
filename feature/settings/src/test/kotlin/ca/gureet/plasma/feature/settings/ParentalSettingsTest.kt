// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Settings › Parental Controls (brief 5.12): the PIN, the locked channels and Lock Settings. */
class ParentalSettingsTest : ScreenshotTest() {
    private val went = mutableListOf<SettingsDestination>()

    @Test fun withoutAPinChoosingOneComesFirst() {
        screen {
            SettingsScreen(SettingsPage.Parental, SettingsValues(), SettingsActions(onGo = { went += it }), facts = SettingsSamples.facts.copy(pinSet = false))
        }
        compose.onNodeWithText("Not set").assertExists()
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 6)
        focused().assert(hasText("Lock Settings"))
        press(Key.DirectionCenter)

        check(went == listOf(SettingsDestination.SetPin, SettingsDestination.LockSettings)) { "Went to $went" }
    }

    @Test fun settingsSaysParentalControlsAreOffWithoutAPin() {
        screen { SettingsScreen(SettingsPage.Main, SettingsValues(), SettingsActions(), facts = SettingsSamples.facts.copy(pinSet = false)) }
        compose.onNodeWithText("On · 18+ locked").assertDoesNotExist()
    }

    @Test fun thePinCanBeChangedOrParentalControlsTurnedOff() {
        var turnedOff = 0
        screen {
            SettingsScreen(SettingsPage.Parental, SettingsValues(), SettingsActions(onGo = { went += it }, onTurnOffParental = { turnedOff++ }))
        }
        press(Key.DirectionCenter)
        focused().assert(hasText("Change PIN"))
        press(Key.DirectionCenter)
        check(went == listOf(SettingsDestination.ChangePin)) { "Went to $went" }

        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Turn Off Parental Controls"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Turn Off Parental Controls?").assertExists()
        focused().assert(hasText("Turn Off"))
        press(Key.DirectionCenter)

        check(turnedOff == 1)
    }

    @Test fun lockedChannelsUnlocksOneAndLocksItAgain() {
        val changed = mutableListOf<Pair<String, Boolean>>()
        screen {
            SettingsScreen(SettingsPage.Parental, SettingsValues(), SettingsActions(onLockChannel = { id, locked -> changed += id to locked }))
        }
        press(Key.DirectionDown, times = 3)
        focused().assert(hasText("Locked Channels"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Combat Arena"))
        press(Key.DirectionCenter)
        // Still there, unticked, to tick again.
        focused().assert(hasText("Combat Arena"))
        press(Key.DirectionCenter)

        check(changed == listOf("110" to false, "110" to true)) { "Changed $changed" }
    }

    @Test fun withNoChannelsLockedTheresNothingToOpen() {
        screen {
            SettingsScreen(SettingsPage.Parental, SettingsValues(), SettingsActions(), facts = SettingsSamples.facts.copy(lockedChannels = emptyList()))
        }
        press(Key.DirectionDown, times = 3)
        press(Key.DirectionCenter)
        focused().assert(hasText("Locked Channels"))
    }
}
