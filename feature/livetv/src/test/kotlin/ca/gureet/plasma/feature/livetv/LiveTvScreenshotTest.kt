// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Live TV against board 07 (docs/design/screens). */
class LiveTvScreenshotTest : ScreenshotTest() {
    @Test fun liveTv() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) }
        capture("07-live-tv")
    }
}
