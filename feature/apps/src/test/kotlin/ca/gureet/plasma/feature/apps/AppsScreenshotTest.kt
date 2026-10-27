// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.apps

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import ca.gureet.plasma.core.fixtures.fx
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

    @Test fun appsWithoutBanners() {
        // An app without a banner shows its icon on graphite, with its name.
        val state = AppsSamples.state.let { s ->
            s.copy(
                onHome = s.onHome.map { if (it.label == "Fileport") it.copy(banner = null, icon = fx("logo_atlas")) else it },
                others = s.others.map { if (it.label == "Quarry") it.copy(banner = null, icon = fx("logo_orbit")) else it },
            )
        }
        screen { AppsScreen(state, AppsActions()) }
        press(Key.DirectionRight, times = 2)
        compose.onNodeWithContentDescription("Fileport").assertIsFocused()
        capture("04-apps-without-banners")
        // Its options show the icon alone beside the name.
        press(Key.Menu)
        focused().assert(hasText("Move"))
        capture("04-apps-without-banners-options")
    }

    @Test fun inputsListsTheTvsInputs() {
        var chosen: String? = null
        val state = AppsSamples.state.copy(inputs = listOf(InputItem("hdmi1", "HDMI 1"), InputItem("hdmi2", "Game Console")))
        screen { AppsScreen(state, AppsActions(onInput = { chosen = it })) }
        // All Apps' second row: Tumble, then Inputs.
        press(Key.DirectionDown, times = 2)
        compose.onNodeWithContentDescription("Tumble").assertIsFocused()
        press(Key.DirectionRight)
        focused().assert(hasText("Inputs"))
        press(Key.DirectionCenter)
        focused().assert(hasText("HDMI 1"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(chosen == "hdmi2") { "Chose $chosen" }
        focused().assert(hasText("Inputs"))
    }

    @Test fun hidingAnAppMovesFocusToTheNextOne() {
        var hidden: String? = null
        screen { AppsScreen(AppsSamples.state, AppsActions(onHide = { hidden = it })) }
        compose.onNodeWithContentDescription("Reelbox").assertIsFocused()
        press(Key.Menu)
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Hide"))
        press(Key.DirectionCenter)
        check(hidden == "tv.example.reelbox") { "Hid $hidden" }
        compose.onNodeWithContentDescription("Lantern Browser").assertIsFocused()
        compose.onAllNodesWithContentDescription("Reelbox").assertCountEquals(0)
    }

    @Test fun withoutAppsFocusStartsOnDeviceSettings() {
        screen { AppsScreen(AppsSamples.state.copy(onHome = emptyList(), others = emptyList(), systemTiles = listOf(SystemTile.DeviceSettings)), AppsActions()) }
        focused().assert(hasText("Device Settings"))
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
