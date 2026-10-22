// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Multiview against board 12 (docs/design/screens), and its D-pad paths. */
class MultiviewTest : ScreenshotTest() {
    @Test fun multiview() {
        screen { MultiviewScreen(PlayerSamples.multiview, MultiviewActions()) }
        capture("12-multiview")
    }

    @Test fun soundFollowsFocus() {
        var fullScreen: String? = null
        screen { MultiviewScreen(PlayerSamples.multiview, MultiviewActions(onFullScreen = { fullScreen = it })) }
        compose.onNodeWithContentDescription("Arena Sports 1: Harbour City v Redmoor").assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Velocity Motor: Endurance 24: Night Stint").assertIsFocused()
        compose.onNode(hasText("Sound")).assertExists()
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Wildframe 4K: Kingdoms of Ice, Episode 3").assertIsFocused()

        // Down again reaches the toolbar, on the current layout.
        press(Key.DirectionDown)
        focused().assert(hasText("Grid"))
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        // Focus + 3: the toolbar keeps focus, now on the new layout.
        focused().assert(hasText("Focus + 3"))

        press(Key.DirectionRight, times = 3)
        focused().assert(hasText("Full Screen"))
        press(Key.DirectionCenter)
        check(fullScreen == "ch180") { "Full Screen opens the tile with the sound, got $fullScreen" }
    }

    @Test fun menuChangesTheChannel() {
        screen { MultiviewScreen(PlayerSamples.multiview, MultiviewActions()) }
        press(Key.Menu)
        focused().assert(hasText("Change Channel"))
        press(Key.DirectionCenter)
        // The sound tile now shows the next favorite that isn't on screen, and has focus again.
        focused().assert(hasContentDescription("Meridian 24", substring = true))
    }
}
