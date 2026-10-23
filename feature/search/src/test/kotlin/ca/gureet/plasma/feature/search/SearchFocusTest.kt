// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** D-pad focus on Search (brief 4.3): the keyboard, then the results. */
class SearchFocusTest : ScreenshotTest() {
    private var opened: String? = null

    private fun show(query: String = "") = screen {
        SearchScreen(
            search = SearchSamples::search,
            recentSearches = SearchSamples.recent,
            actions = SearchActions(onProgram = { opened = it.title }, onTitle = { opened = it.title }),
            profileName = Sample.maya.name,
            profileInitial = Sample.maya.initial,
            initialQuery = query,
        )
    }

    @Test fun theKeyboardTypes() {
        show()
        // Focus starts on the keyboard's first key.
        focused().assert(hasText("a"))
        press(Key.DirectionRight, times = 13)
        focused().assert(hasText("n"))
        press(Key.DirectionCenter)
        press(Key.DirectionLeft, times = 5)
        focused().assert(hasText("i"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("ni").assertExists()
    }

    @Test fun downMovesIntoTheResultsAndUpComesBack() {
        show(query = "night")
        focused().assert(hasText("a"))
        press(Key.DirectionDown)
        focused().assert(hasContentDescription("Endurance 24: Night Stint", substring = true))
        press(Key.DirectionRight)
        focused().assert(hasContentDescription("Late Night Sessions", substring = true))
        press(Key.DirectionCenter)
        check(opened == "Late Night Sessions") { "Opened $opened" }

        // Up returns to the key that had focus.
        press(Key.DirectionUp)
        focused().assert(hasText("a"))
    }
}
