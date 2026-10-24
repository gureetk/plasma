// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Live TV against board 07 (docs/design/screens). */
class LiveTvScreenshotTest : ScreenshotTest() {
    @Test fun liveTv() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) }
        capture("07-live-tv")
    }

    /** The Volume Level bar (brief 4.35 and 5.4.6) over board 07: it has no board of its own. */
    @Test fun volumeLevel() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) }
        press(Key.Menu)
        repeat(12) { if (runCatching { focused().assert(hasText("Volume Level")) }.isFailure) press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        press(Key.DirectionRight, times = 3)
        capture("07-live-tv-volume-level")
    }

    /** The bar while the channel's sound passes through to a receiver: Decode This Channel. */
    @Test fun volumeLevelPassingThrough() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(), sound = LiveSound(passingThrough = "ch105")) }
        press(Key.Menu)
        repeat(12) { if (runCatching { focused().assert(hasText("Volume Level")) }.isFailure) press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        capture("07-live-tv-volume-level-passthrough")
    }
}
