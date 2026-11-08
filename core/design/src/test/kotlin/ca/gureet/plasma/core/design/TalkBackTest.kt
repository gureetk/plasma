// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.testing.ScreenshotTest
import ca.gureet.plasma.core.testing.focusablesReadTwice
import ca.gureet.plasma.core.testing.unlabeledFocusables
import org.junit.Test

/** What TalkBack reads for a surface (#128), and the checks every screen's test makes of it. */
class TalkBackTest : ScreenshotTest() {
    @Test fun aSurfaceIsReadAsTheWordsInIt() {
        screen {
            PlasmaSurface(onClick = {}) {
                Column {
                    PlasmaText("Fast Zap", PlasmaType.Body)
                    PlasmaText("Off", PlasmaType.Body)
                }
            }
        }
        compose.onNodeWithText("Fast Zap").assert(hasText("Off"))
        check(compose.unlabeledFocusables().isEmpty()) { compose.unlabeledFocusables() }
        check(compose.focusablesReadTwice().isEmpty()) { compose.focusablesReadTwice() }
    }

    @Test fun aDescribedSurfaceIsReadOnceAndTestsStillFindItsWords() {
        screen {
            PlasmaSurface(onClick = {}, description = "Fast Zap, Off") {
                Column {
                    LogoImage(null, "Fast Zap", 40.px)
                    PlasmaIcon(PlasmaIcons.Lock, 20.px, contentDescription = "Locked")
                    PlasmaText("Off", PlasmaType.Body)
                }
            }
        }
        compose.onNode(hasContentDescription("Fast Zap, Off")).assert(hasText("Off")).assert(hasContentDescription("Locked"))
        compose.onNodeWithText("Fast Zap").assertExists()
        check(compose.focusablesReadTwice().isEmpty()) { compose.focusablesReadTwice() }
    }

    @Test fun aDescriptionOverWordsTalkBackStillReadsIsReadTwice() {
        screen {
            PlasmaSurface(onClick = {}, modifier = Modifier.semantics { contentDescription = "Fast Zap, Off" }) {
                PlasmaText("Fast Zap", PlasmaType.Body)
            }
        }
        check(compose.focusablesReadTwice().size == 1) { compose.focusablesReadTwice() }
    }

    @Test fun anIconAloneHasNoWords() {
        screen { PlasmaSurface(onClick = {}) { PlasmaIcon(PlasmaIcons.Search, 24.px) } }
        check(compose.unlabeledFocusables().size == 1) { compose.unlabeledFocusables() }
    }
}
