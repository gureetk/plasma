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
}
