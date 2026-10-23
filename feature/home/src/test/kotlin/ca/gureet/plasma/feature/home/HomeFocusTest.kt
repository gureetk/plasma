// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** D-pad focus on Home (brief 4.1 and 4.2). */
class HomeFocusTest : ScreenshotTest() {
    private val docked = mutableListOf<DockTarget>()
    private var played: String? = null
    private var tuned: String? = null
    private var continued: String? = null

    private fun show() = screen {
        HomeScreen(
            HomeSamples.state,
            HomeActions(
                onDock = { docked += it },
                onHero = { played = it.title },
                onChannel = { tuned = it },
                onContinue = { continued = it },
            ),
            heroAutoAdvance = false,
        )
    }

    private fun focusedOn(description: String) = focused().assert(hasContentDescription(description, substring = true))

    @Test fun theDockTheHeroAndTheTopBar() {
        show()
        // The Live TV tile has focus when Home opens.
        focusedOn("Live TV")
        press(Key.DirectionRight)
        focusedOn("TV Guide")
        press(Key.DirectionCenter)
        check(docked == listOf(DockTarget.Guide)) { "Opened $docked" }

        // Up goes to the hero, where OK plays it, and Up again to the top bar's Home tab.
        press(Key.DirectionUp)
        focused().assert(hasText("Harbour City v Redmoor"))
        press(Key.DirectionCenter)
        check(played == "Harbour City v Redmoor") { "Played $played" }
        press(Key.DirectionUp)
        focused().assert(hasText("Home"))

        // Down from the top bar returns to the hero, and Down again to the Live TV tile.
        press(Key.DirectionDown)
        focused().assert(hasText("Harbour City v Redmoor"))
        press(Key.DirectionDown)
        focusedOn("Live TV")
    }

    @Test fun leftAndRightStepThroughTheHero() {
        show()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        focused().assert(hasText("Atlas Station"))
        press(Key.DirectionLeft)
        focused().assert(hasText("Harbour City v Redmoor"))
    }

    @Test fun downGoesToFavoritesThenTheRowsAndBackComesHome() {
        show()
        press(Key.DirectionDown)
        focusedOn("Wildframe 4K")
        press(Key.DirectionCenter)
        check(tuned == "ch180") { "Tuned $tuned" }

        press(Key.DirectionDown)
        focusedOn("Atlas Station")
        press(Key.DirectionCenter)
        check(continued != null) { "Nothing continued" }
        press(Key.DirectionDown)
        focusedOn("Neon Tide")
        press(Key.DirectionDown)
        focusedOn("Reelbox")

        // Back from anywhere below the dock returns to the top, on the Live TV tile.
        back()
        focusedOn("Live TV")
    }

    @Test fun favoritesAndContinueWatchingHaveOptions() {
        show()
        press(Key.DirectionDown)
        focusedOn("Wildframe 4K")
        longPress(Key.DirectionCenter)
        focused().assert(hasText("Remove from Favorites"))
        press(Key.DirectionCenter)
        // The favorite leaves the row, and focus moves on to the next one.
        focusedOn("Northline News")

        press(Key.DirectionDown)
        focusedOn("Atlas Station")
        press(Key.Menu)
        focused().assert(hasText("Resume"))
        press(Key.DirectionDown)
        focused().assert(hasText("Remove from Continue Watching"))
        press(Key.DirectionCenter)
        focusedOn("Kite Season")

        // Another app's item can only be hidden in Plasma.
        press(Key.Menu)
        focused().assert(hasText("Open"))
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Hide All from This App"))
        press(Key.Back)
        focusedOn("Kite Season")
    }

    @Test fun removingEveryFavoriteReturnsToTheDock() {
        show()
        press(Key.DirectionDown)
        repeat(HomeSamples.state.favorites.size) {
            press(Key.Menu)
            press(Key.DirectionCenter)
        }
        // With the row empty, focus goes back to the Live TV tile, and Down passes the row by.
        focusedOn("Live TV")
        press(Key.DirectionDown)
        focusedOn("Atlas Station")
    }
}
