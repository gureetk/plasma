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
            actions = SearchActions(onProgram = { opened = it.title }, onTitle = { opened = it.title }, onChannel = { opened = it.name }),
            profileName = Sample.maya.name,
            profileInitial = Sample.maya.initial,
            initialQuery = query,
        )
    }

    /** From A to the results: the keyboard's last column, then the results beside it. */
    private fun toResults() = press(Key.DirectionRight, times = 10)

    @Test fun theKeyboardTypes() {
        show()
        // Focus starts on A, and the keys go as on a QWERTY keyboard.
        focused().assert(hasText("a"))
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 5)
        focused().assert(hasText("n"))
        press(Key.DirectionCenter)
        press(Key.DirectionUp, times = 2)
        press(Key.DirectionRight, times = 2)
        focused().assert(hasText("i"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("ni").assertExists()
    }

    @Test fun rightMovesIntoTheResultsAndLeftComesBack() {
        show(query = "night")
        focused().assert(hasText("a"))
        toResults()
        focused().assert(hasContentDescription("Endurance 24: Night Stint", substring = true))
        press(Key.DirectionRight)
        focused().assert(hasContentDescription("Late Night Sessions", substring = true))
        press(Key.DirectionCenter)
        check(opened == "Late Night Sessions") { "Opened $opened" }

        // Left goes back along the row, then to the keyboard.
        press(Key.DirectionLeft, times = 2)
        focused().assert(hasText("'"))
    }

    @Test fun aChannelsNameFindsTheChannelFirst() {
        show(query = "arena")
        compose.onNodeWithText("Channels").assertExists()
        toResults()
        focused().assert(hasContentDescription("Arena Sports 1", substring = true))
        press(Key.DirectionCenter)
        check(opened == "Arena Sports 1") { "Opened $opened" }
    }

    @Test fun resultsFollowTheKeysAfterAMoment() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 5)
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
        toResults()
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
