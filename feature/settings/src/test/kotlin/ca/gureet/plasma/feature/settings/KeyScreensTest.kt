// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.model.RemoteKeys
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Remote Key Test and Learn a Key (Settings › Remote & Buttons; the owner's decision of 22 October 2026). */
class KeyScreensTest : ScreenshotTest() {
    @Test fun theKeyTestShowsEachKeyAndWhetherPlasmaUsesIt() {
        var left = false
        val learned = mapOf(RemoteKeys.idOf(KeyEvent.KEYCODE_F1, 0) to KeyEvent.KEYCODE_PROG_RED)
        screen { KeyTestScreen(learned, backLabel = "Remote & Buttons", onBack = { left = true }) }
        compose.onNodeWithText("Nothing pressed yet.").assertExists()

        press(Key.ChannelUp)
        compose.onNodeWithText("Channel Up").assertExists()
        compose.onNodeWithText("Plasma uses it").assertExists()
        press(Key.F1)
        compose.onNodeWithText("Acts as Red").assertExists()
        press(Key.TvInput)
        compose.onNodeWithText("Plasma doesn’t use it").assertExists()
        // Every key stops here, so the D-pad shows too; Back leaves.
        press(Key.DirectionUp)
        check(!left) { "Left on Up" }
        back()
        check(left) { "Back didn't leave" }
    }

    @Test fun learnAKeyTeachesAKeyAndForgetsIt() {
        val learned = mutableStateMapOf<String, Int>()
        screen {
            LearnKeyScreen(
                learned = learned.toMap(),
                backLabel = "Remote & Buttons",
                onLearn = { id, code -> learned[id] = code },
                onForget = { learned.remove(it) },
            )
        }
        compose.onNodeWithText("No keys learned yet.").assertExists()

        // F1 becomes Red.
        press(Key.F1)
        compose.onNodeWithText("What should", substring = true).assertExists()
        focused().assert(hasText("Channel Up"))
        press(Key.DirectionDown, times = 4)
        focused().assert(hasText("Red"))
        press(Key.DirectionCenter)
        check(learned.toMap() == mapOf("k${KeyEvent.KEYCODE_F1}" to KeyEvent.KEYCODE_PROG_RED)) { "Learned $learned" }
        compose.onNodeWithText("now acts as Red.", substring = true).assertExists()

        // F2 becomes the number 5.
        press(Key.F2)
        press(Key.DirectionDown, times = 8)
        focused().assert(hasText("A Number…"))
        press(Key.DirectionCenter)
        focused().assert(hasText("0"))
        press(Key.DirectionDown, times = 5)
        press(Key.DirectionCenter)
        check(learned["k${KeyEvent.KEYCODE_F2}"] == KeyEvent.KEYCODE_5) { "Learned $learned" }

        // Focus is on the learned keys now; OK on one offers Forget.
        focused().assert(hasText("Acts as Red"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Forget"))
        press(Key.DirectionCenter)
        check(learned.keys.toSet() == setOf("k${KeyEvent.KEYCODE_F2}")) { "Learned $learned" }
    }
}
