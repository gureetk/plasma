// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.profiles

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Who's Watching against board 05 (docs/design/screens), and its D-pad paths. */
class ProfilesScreenshotTest : ScreenshotTest() {
    @Test fun profiles() {
        screen { ProfilesScreen(ProfilesSamples.state, ProfilesActions()) }
        capture("05-profiles")
    }

    @Test fun focus() {
        var picked: String? = null
        screen { ProfilesScreen(ProfilesSamples.state, ProfilesActions(onPick = { picked = it.id })) }
        compose.onNodeWithContentDescription("Maya").assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Daniel, PIN protected").assertIsFocused()
        press(Key.DirectionDown)
        focused().assert(hasText("Manage Profiles"))
        // Back up to the profile that had focus.
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Daniel, PIN protected").assertIsFocused()

        press(Key.DirectionRight, times = 3)
        compose.onNodeWithContentDescription("Add Profile").assertIsFocused()

        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        check(picked == "guest") { "OK picks the focused profile, got $picked" }
    }
}
