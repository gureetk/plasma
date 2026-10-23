// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Replace Google TV Home against board 24. */
class ReplaceHomeScreenshotTest : ScreenshotTest() {
    @Test fun replaceGoogleTvHome() {
        var opened = 0
        screen { ReplaceHomeScreen(ReplaceHomeSamples.state, onOpenDeveloperOptions = { opened++ }) }
        focused().assert(hasText("Open Developer Options"))
        capture("24-replace-google-tv-home")
        press(Key.DirectionCenter)
        check(opened == 1) { "Opened $opened times" }
    }
}
