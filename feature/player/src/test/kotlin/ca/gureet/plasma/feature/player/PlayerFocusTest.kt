// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** The remote in the full-screen player (brief 3.6, 4.9–4.11). */
class PlayerFocusTest : ScreenshotTest() {
    private val tuned = mutableListOf<String>()
    private var channelList = false
    private var left = false

    private fun show() = screen {
        PlayerScreen(
            PlayerSamples.state,
            PlayerActions(onTune = { tuned += it }, onChannelList = { channelList = true }, onBack = { left = true }),
            initialOverlay = PlayerOverlay.None,
            autoHide = false,
        )
    }

    @Test fun upAndDownChangeChannelAndLeftOpensTheList() {
        show()
        press(Key.DirectionUp)
        check(tuned.last() == "ch106") { "Tuned $tuned" }
        press(Key.DirectionDown, times = 2)
        check(tuned.last() == "ch104") { "Tuned $tuned" }
        press(Key.DirectionLeft)
        check(channelList) { "Left didn't open the channel list" }
    }

    @Test fun okShowsTheOverlayOnPlayPause() {
        show()
        press(Key.DirectionCenter)
        focused().assert(hasContentDescription("Pause"))
        press(Key.DirectionCenter)
        focused().assert(hasContentDescription("Play"))
        // Up reaches the timeline, and Down comes back.
        press(Key.DirectionUp)
        focused().assert(hasContentDescription("Timeline"))
        press(Key.DirectionDown)
        focused().assert(hasContentDescription("Play"))
    }

    @Test fun rightShowsTheOverlayOnTheTimeline() {
        show()
        press(Key.DirectionRight)
        focused().assert(hasContentDescription("Timeline"))
    }

    @Test fun menuOpensPlayerOptions() {
        show()
        press(Key.Menu)
        focused().assert(hasText("Watch from the Start"))
    }

    @Test fun holdingOkOpensPlayerOptions() {
        show()
        longPress(Key.DirectionCenter)
        focused().assert(hasText("Watch from the Start"))
    }

    @Test fun typingANumberTunesIt() {
        show()
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        compose.onNodeWithText("108_").assertExists()
        compose.mainClock.advanceTimeBy(PlasmaMotion.NUMBER_ENTRY_TUNE_MS + 100)
        compose.waitForIdle()
        check(tuned.last() == "ch108") { "Tuned $tuned" }
    }

    @Test fun backHidesTheOverlayThenLeaves() {
        show()
        press(Key.DirectionCenter)
        back()
        check(!left) { "Left with the overlay up" }
        compose.onNode(hasContentDescription("Pause")).assertDoesNotExist()
        back()
        check(left) { "Back didn't leave the player" }
    }
}
