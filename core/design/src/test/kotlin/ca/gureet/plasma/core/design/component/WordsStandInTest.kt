// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.FixtureImages
import ca.gureet.plasma.core.fixtures.fx
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/**
 * Words in a picture's place while it's on its way, or if it never comes (#228): a poster's title,
 * a channel's name; none for a picture that comes soon.
 */
class WordsStandInTest : ScreenshotTest() {
    private var there by mutableStateOf(false)

    /** Sample art, said to have come only once [there] says so. */
    private val coming = object : ArrivalImageResolver {
        @Composable
        override fun painter(model: String): Painter? = FixtureImages.resolver.painter(fx("poster_kite"))

        @Composable
        override fun arrived(model: String, painter: Painter): Boolean = there
    }

    private fun poster() = screen {
        CompositionLocalProvider(LocalImageResolver provides coming) {
            PosterTile(image = "https://images.home.lan/neon-tide.jpg", title = "Neon Tide", caption = null, onClick = {})
        }
    }

    private fun title() = compose.onNodeWithText("Neon Tide", useUnmergedTree = true)

    @Test fun aPosterOnItsWayHasItsTitleAfterAMomentUntilItComes() {
        compose.mainClock.autoAdvance = false
        poster()
        compose.mainClock.advanceTimeBy(WORDS_AFTER_MS - 100)
        title().assertDoesNotExist()
        compose.mainClock.advanceTimeBy(200)
        title().assertExists()

        there = true
        // A frame for the picture, and one for the tile that hears it came.
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        title().assertDoesNotExist()
    }

    @Test fun aPosterThatComesSoonNeverShowsItsTitle() {
        compose.mainClock.autoAdvance = false
        poster()
        compose.mainClock.advanceTimeBy(WORDS_AFTER_MS / 4)
        there = true
        compose.mainClock.advanceTimeBy(WORDS_AFTER_MS * 2)
        title().assertDoesNotExist()
    }

    @Test fun aLogoOnItsWayHasTheChannelsName() {
        compose.mainClock.autoAdvance = false
        screen {
            CompositionLocalProvider(LocalImageResolver provides coming) {
                LogoPlate(logo = "https://logos.home.lan/arena-sports-1.png", name = "Arena Sports 1", width = 150.px, height = 66.px)
            }
        }
        compose.mainClock.advanceTimeBy(WORDS_AFTER_MS + 100)
        compose.onNodeWithText("Arena Sports 1", useUnmergedTree = true).assertExists()
        there = true
        // A frame for the picture, and one for the tile that hears it came.
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        compose.onNodeWithText("Arena Sports 1", useUnmergedTree = true).assertDoesNotExist()
    }
}
