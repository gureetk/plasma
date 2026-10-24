// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** D-pad focus on Home (brief 4.1 and 4.2). */
class HomeFocusTest : ScreenshotTest() {
    private val docked = mutableListOf<DockTarget>()
    private var played: String? = null
    private var tuned: String? = null
    private var continued: String? = null
    private val removed = mutableListOf<String>()
    private val removedContinue = mutableListOf<String>()
    private var moved: Pair<String, String?>? = null
    private var opened: String? = null

    private fun show(state: HomeUiState = HomeSamples.state) = screen {
        HomeScreen(
            state,
            HomeActions(
                onDock = { docked += it },
                onHero = { played = it.title },
                onChannel = { tuned = it },
                onContinue = { continued = it },
                onRemoveFavorite = { removed += it },
                onMoveFavorite = { id, after -> moved = id to after },
                onRemoveContinue = { removedContinue += it },
                onApp = { opened = it },
            ),
            heroAutoAdvance = false,
        )
    }

    private fun focusedOn(description: String) = focused().assert(hasContentDescription(description, substring = true))

    private fun focusedNotMoving() = focused().assert(!hasContentDescription(", moving", substring = true))

    /** Menu › Move on the focused favorite. */
    private fun startMove() {
        press(Key.Menu)
        press(Key.DirectionDown)
        focused().assert(hasText("Move"))
        press(Key.DirectionCenter)
    }

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

    @Test fun appsOpenFromTheirRow() {
        show()
        press(Key.DirectionDown, times = 4)
        focusedOn("Reelbox")
        press(Key.DirectionCenter)
        check(opened == "tv.example.reelbox") { "Opened $opened" }
    }

    @Test fun withoutAndroidAppsTheRowsEndWithNewInMovies() {
        show(HomeSamples.state.copy(showApps = false))
        press(Key.DirectionDown, times = 3)
        focusedOn("Neon Tide")
        press(Key.DirectionDown)
        focusedOn("Neon Tide")
        compose.onAllNodesWithText("All Apps").assertCountEquals(0)
    }

    @Test fun favoritesAndContinueWatchingHaveOptions() {
        show()
        press(Key.DirectionDown)
        focusedOn("Wildframe 4K")
        longPress(Key.DirectionCenter)
        focused().assert(hasText("Remove from Favorites"))
        press(Key.DirectionCenter)
        // The favorite leaves the row, the app forgets it, and focus moves on to the next one.
        focusedOn("Northline News")
        check(removed == listOf("ch180")) { "Removed $removed" }

        press(Key.DirectionDown)
        focusedOn("Atlas Station")
        press(Key.Menu)
        focused().assert(hasText("Resume"))
        press(Key.DirectionDown)
        focused().assert(hasText("Remove from Continue Watching"))
        press(Key.DirectionCenter)
        focusedOn("Kite Season")
        // The app keeps it out of the row from now on.
        check(removedContinue == listOf("series:atlas:s2e4")) { "Removed $removedContinue" }

        // Another app's item can only be hidden in Plasma.
        press(Key.Menu)
        focused().assert(hasText("Open"))
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Hide All from This App"))
        press(Key.Back)
        focusedOn("Kite Season")
    }

    @Test fun withNothingStartedContinueWatchingSaysWhatGoesThere() {
        show(HomeSamples.state.copy(continueWatching = emptyList()))
        compose.onNodeWithText("Movies and episodes you start show up here.").assertExists()
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

    @Test fun moveCarriesAFavoriteAlongTheRowAndOkDropsIt() {
        show()
        press(Key.DirectionDown)
        focusedOn("Wildframe 4K")
        startMove()
        // It lifts where it is. It's first, so Left does nothing, and Right carries it past
        // Northline News and Velocity Motor.
        focusedOn("Wildframe 4K, moving")
        press(Key.DirectionLeft)
        press(Key.DirectionRight, times = 2)
        // Nothing else acts until it's dropped.
        press(Key.DirectionUp)
        press(Key.Menu)
        focusedOn("Wildframe 4K, moving")
        press(Key.DirectionCenter)
        check(moved == ("ch180" to "ch107")) { "Moved $moved" }
        check(tuned == null) { "Tuned $tuned" }

        // It stays where it was dropped, between Velocity Motor and Pulse.
        focusedNotMoving()
        focusedOn("Wildframe 4K")
        press(Key.DirectionLeft)
        focusedOn("Velocity Motor")
        press(Key.DirectionRight, times = 2)
        focusedOn("Pulse")
    }

    @Test fun backPutsAMovingFavoriteBack() {
        show()
        press(Key.DirectionDown)
        startMove()
        press(Key.DirectionRight, times = 3)
        back()
        // It's first again, with focus, and the app keeps nothing.
        focusedNotMoving()
        focusedOn("Wildframe 4K")
        press(Key.DirectionRight)
        focusedOn("Northline News")
        check(moved == null) { "Moved $moved" }

        // Dropped where it lifted, nothing changes either.
        startMove()
        press(Key.DirectionCenter)
        focusedNotMoving()
        check(moved == null) { "Moved $moved" }

        // Back goes home, as it does from the row.
        back()
        focusedOn("Live TV")
    }

    @Test fun aLoneFavoriteHasNoMove() {
        show(HomeSamples.state.copy(favorites = HomeSamples.state.favorites.take(1)))
        press(Key.DirectionDown)
        press(Key.Menu)
        focused().assert(hasText("Remove from Favorites"))
        compose.onNodeWithText("Move").assertDoesNotExist()
    }
}
