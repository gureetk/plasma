// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.model.QrMatrix
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Replace Google TV Home against board 24, and as its steps go (brief 4.24). */
class ReplaceHomeScreenshotTest : ScreenshotTest() {
    @Test fun replaceGoogleTvHome() {
        var opened = 0
        screen { ReplaceHomeScreen(ReplaceHomeSamples.state, ReplaceHomeActions(onOpenDeveloperOptions = { opened++ })) }
        focused().assert(hasText("Open Developer Options"))
        // The phone page asks for the code under the QR code: it's there, with the address.
        compose.onNodeWithText("Or open 192.168.1.42:8484 and enter").assertExists()
        compose.onNodeWithText("7KQ 4MZ").assertExists()
        capture("24-replace-google-tv-home")
        press(Key.DirectionCenter)
        check(opened == 1) { "Opened $opened times" }
    }

    @Test fun aWrongCodeSaysSo() {
        screen { ReplaceHomeScreen(ReplaceHomeSamples.state.copy(status = ReplaceStatus.WrongCode), ReplaceHomeActions()) }
        compose.onNodeWithText("That code didn’t work. Pair again with the new one.").assertExists()
    }

    @Test fun pairedTheButtonReplaces() {
        var replaced = 0
        screen { ReplaceHomeScreen(ReplaceHomeSamples.state.copy(step = 3), ReplaceHomeActions(onReplace = { replaced++ })) }
        focused().assert(hasText("Replace Google TV Home"))
        compose.onNodeWithText("Paired with this TV").assertExists()
        press(Key.DirectionCenter)
        check(replaced == 1) { "Replaced $replaced times" }
    }

    @Test fun aStepThatStoppedShowsWhatTheTvSaid() {
        val output = "pm disable-user --user 0 com.example.launcher\nError: Unknown package: com.example.launcher"
        val failed = ReplaceHomeSamples.state.copy(step = 3, status = ReplaceStatus.Failed(output))
        screen { ReplaceHomeScreen(failed, ReplaceHomeActions()) }
        compose.onNodeWithText("It stopped. The TV said:").assertExists()
        compose.onNodeWithText(output).assertExists()
        capture("24-replace-google-tv-home-stopped")
    }

    @Test fun doneOffersToTurnTheOverrideOff() {
        var turnedOff = 0
        val done = ReplaceHomeSamples.state.copy(step = 4, overrideOn = true)
        screen { ReplaceHomeScreen(done, ReplaceHomeActions(onTurnOverrideOff = { turnedOff++ })) }
        focused().assert(hasText("Turn Off Home Button Override"))
        compose.onNodeWithText("Done. Press Home to try it.").assertExists()
        capture("24-replace-google-tv-home-done")
        press(Key.DirectionCenter)
        check(turnedOff == 1) { "Turned off $turnedOff times" }
    }

    @Test fun withoutTheOverrideDoneJustLeaves() {
        var left = 0
        screen { ReplaceHomeScreen(ReplaceHomeSamples.state.copy(step = 4), ReplaceHomeActions(onDone = { left++ })) }
        focused().assert(hasText("Done"))
        press(Key.DirectionCenter)
        check(left == 1) { "Left $left times" }
    }

    @Test fun thePhonePagesOwnAddressAndCodeAreUnderItsQrCode() {
        val ready = ReplaceHomeSamples.state.copy(phone = PhonePairing.Ready("10.0.0.7:8484", "ABCD23", QrMatrix(21, BooleanArray(21 * 21))))
        screen { ReplaceHomeScreen(ready, ReplaceHomeActions()) }
        compose.onNodeWithText("Or open 10.0.0.7:8484 and enter").assertExists()
        compose.onNodeWithText("ABC D23").assertExists()
    }

    @Test fun withoutAPhonePageThereIsNoCodeToType() {
        screen { ReplaceHomeScreen(ReplaceHomeSamples.state.copy(phone = PhonePairing.Unavailable), ReplaceHomeActions()) }
        compose.onNodeWithText("Connect this TV to your Wi-Fi or Ethernet first, so your phone can reach it.").assertExists()
        compose.onNodeWithText("7KQ 4MZ").assertDoesNotExist()
    }

    @Test fun unknownPortsAndCodeShowAsPlaceholders() {
        screen { ReplaceHomeScreen(ReplaceHomeSamples.state.copy(step = 1, pairingPort = null, pairingCode = null, connectPort = null), ReplaceHomeActions()) }
        compose.onNodeWithText("adb pair 192.168.1.42:<port> <code>", substring = true).assertExists()
    }
}
