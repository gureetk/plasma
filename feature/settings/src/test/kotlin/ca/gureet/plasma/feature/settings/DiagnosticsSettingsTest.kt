// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.model.QrMatrix
import ca.gureet.plasma.core.testing.ScreenshotTest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Test

/** About › Export Diagnostics (brief 5.17 and 6.11): to a USB drive, or to a phone. */
class DiagnosticsSettingsTest : ScreenshotTest() {
    @Test fun saveToUsbDriveAsksTheApp() {
        var saves = 0
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(onSaveDiagnostics = { saves++ }), initialItem = "diagnostics") }

        press(Key.DirectionCenter)
        focused().assert(hasText("Save to USB Drive"))
        press(Key.DirectionCenter)

        check(saves == 1) { "Saved $saves times" }
    }

    @Test fun sendToYourPhoneShowsTheAddressAndCodeWhileTheSheetIsOpen() {
        var running = 0
        val phone: () -> Flow<PhonePairing> = {
            flow {
                running++
                try {
                    emit(PhonePairing.Ready("192.168.1.42:8484", "7KQ4MZ", QrMatrix(21, BooleanArray(21 * 21))))
                    awaitCancellation()
                } finally {
                    running--
                }
            }
        }
        screen { SettingsScreen(SettingsPage.About, SettingsValues(), SettingsActions(diagnosticsPhone = phone), initialItem = "diagnostics") }

        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Send to Your Phone"))
        press(Key.DirectionCenter)

        compose.onNodeWithText("Send Diagnostics to Your Phone").assertExists()
        compose.onNodeWithText("Or open 192.168.1.42:8484 and enter 7KQ4MZ").assertExists()
        check(running == 1) { "The phone page runs $running times" }
        press(Key.Back)
        compose.waitForIdle()
        check(running == 0) { "The phone page still runs after Back" }
    }
}
