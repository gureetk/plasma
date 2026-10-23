// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.PlasmaMotion
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

    @Test fun resultsFollowTheKeysAfterAMoment() {
        show()
        press(Key.DirectionRight, times = 13)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Live, Upcoming and Catch-up").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(PlasmaMotion.SEARCH_DEBOUNCE_MS + 50)
        compose.waitForIdle()
        compose.onNodeWithText("Live, Upcoming and Catch-up").assertExists()
    }

    @Test fun okOnAProgramComingUpOffersRemindMe() {
        val reminded = mutableListOf<Pair<String, Boolean>>()
        val searched = mutableListOf<String>()
        screen {
            SearchScreen(
                search = SearchSamples::search,
                recentSearches = SearchSamples.recent,
                actions = SearchActions(
                    onProgram = { opened = it.title },
                    onRemind = { channelId, _, on -> reminded += channelId to on },
                    onSearched = { searched += it },
                ),
                profileName = Sample.maya.name,
                profileInitial = Sample.maya.initial,
                initialQuery = "night",
            )
        }
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 2)
        focused().assert(hasContentDescription("Fight Night: Main Card", substring = true))
        press(Key.DirectionCenter)
        focused().assert(hasText("Remind Me"))
        press(Key.DirectionCenter)

        check(reminded == listOf(Sample.arena1.id to true)) { "Reminded $reminded" }
        check(searched == listOf("night")) { "Searched $searched" }
        check(opened == null) { "Opened $opened" }
        // Focus is back on the program, whose reminder is now set.
        focused().assert(hasContentDescription("Fight Night: Main Card", substring = true))
        press(Key.DirectionCenter)
        focused().assert(hasText("Cancel Reminder"))
    }
}
