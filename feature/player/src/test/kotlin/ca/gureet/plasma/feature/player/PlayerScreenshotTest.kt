// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** The player against boards 09, 10 and 11 (docs/design/screens). */
class PlayerScreenshotTest : ScreenshotTest() {
    @Test fun overlay() {
        screen { PlayerScreen(PlayerSamples.state, PlayerActions(), initialOverlay = PlayerOverlay.Controls, autoHide = false) }
        capture("09-player")
    }

    @Test fun options() {
        screen { PlayerScreen(PlayerSamples.state, PlayerActions(), initialOverlay = PlayerOverlay.Options, autoHide = false) }
        capture("10-player-options")
    }

    @Test fun zapBannerAndReminder() {
        screen(Sample.zapClock) { PlayerScreen(PlayerSamples.zapState, PlayerActions(), initialOverlay = PlayerOverlay.Zap, autoHide = false) }
        capture("11-zap-banner-and-reminder")
    }
}
