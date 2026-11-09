// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** The Guide against board 08 (docs/design/screens). */
class GuideScreenshotTest : ScreenshotTest() {
    @Test fun guide() {
        screen { GuideScreen(GuideSamples.state, GuideActions()) }
        capture("08-guide")
    }

    /** A day still loading after a moment (#158): programs' shapes in each row. */
    @Test fun aDayStillLoading() {
        val base = GuideSamples.state
        screen { GuideScreen(base.copy(loading = true, channels = base.channels.map { it.copy(programs = emptyList()) }), GuideActions()) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        capture("08-guide-loading")
    }
}
