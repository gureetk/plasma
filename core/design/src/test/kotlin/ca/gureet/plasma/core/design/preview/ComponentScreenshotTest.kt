// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.preview

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** The interface kit against board 26, and the component galleries, so a change to a component shows up. */
class ComponentScreenshotTest : ScreenshotTest() {
    @Test fun interfaceKit() {
        screen { InterfaceKit(logo = Sample.arena1.logo) }
        capture("26-ui-kit-and-remote-map")
    }

    @Test fun bars() = gallery("components-bars") { BarsGallery() }

    @Test fun buttons() = gallery("components-buttons") { ButtonsGallery() }

    @Test fun badges() = gallery("components-badges") { BadgesGallery() }

    @Test fun tiles() = gallery("components-tiles") { TilesGallery() }

    @Test fun lists() = gallery("components-lists") { ListsGallery() }

    @Test fun optionsSheet() = gallery("components-options-sheet") { OptionsSheetGallery() }

    @Test fun settingSheet() = gallery("components-setting-sheet") { SettingSheetGallery() }

    @Test fun numbers() = gallery("components-numbers") { NumbersGallery() }

    @Test fun icons() = gallery("components-icons") { IconsGallery() }

    private fun gallery(name: String, content: @Composable () -> Unit) {
        screen { PreviewCanvas { content() } }
        capture(name)
    }
}
