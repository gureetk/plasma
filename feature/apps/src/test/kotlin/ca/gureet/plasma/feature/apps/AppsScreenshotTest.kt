// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.apps

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Apps against board 04 (docs/design/screens), moving, and its D-pad paths. */
class AppsScreenshotTest : ScreenshotTest() {
    @Test fun apps() {
        screen { AppsScreen(AppsSamples.moving, AppsActions()) }
        capture("04-apps")
    }

    @Test fun movingAndDropping() {
        var order: List<String> = emptyList()
        screen { AppsScreen(AppsSamples.moving, AppsActions(onHomeRow = { order = it })) }
        compose.onNodeWithContentDescription("Sonar, moving").assertIsFocused()

        // Left carries Sonar along the row; OK drops it there.
        press(Key.DirectionLeft, times = 2)
        compose.onNodeWithContentDescription("Sonar, moving").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Sonar").assertIsFocused()
        check(order.take(3) == listOf("tv.example.reelbox", "tv.example.sonar", "tv.example.lantern")) { "Order after the move: $order" }
    }

    @Test fun backCancelsAMove() {
        var order: List<String>? = null
        screen { AppsScreen(AppsSamples.moving, AppsActions(onHomeRow = { order = it })) }
        press(Key.DirectionRight)
        press(Key.Back)
        compose.onNodeWithContentDescription("Sonar").assertIsFocused()
        check(order == null) { "Back puts the app back without saving an order" }
        // The row is as it was: Fileport, then Sonar.
        press(Key.DirectionLeft)
        compose.onNodeWithContentDescription("Fileport").assertIsFocused()
    }

    @Test fun menuOffersMoveAndFocusComesBack() {
        screen { AppsScreen(AppsSamples.state, AppsActions()) }
        compose.onNodeWithContentDescription("Reelbox").assertIsFocused()
        press(Key.Menu)
        focused().assert(hasText("Move"))
        press(Key.Back)
        compose.onNodeWithContentDescription("Reelbox").assertIsFocused()

        // Down goes to All Apps; Up comes back to the Home row.
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Brightside Weather").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Reelbox").assertIsFocused()
    }
}
