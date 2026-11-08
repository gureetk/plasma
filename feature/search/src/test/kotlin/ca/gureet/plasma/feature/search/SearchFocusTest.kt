// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** D-pad focus on Search (brief 4.3): the keyboard, then the results. */
class SearchFocusTest : ScreenshotTest() {
    private var opened: String? = null

    private fun show(query: String = "", voice: ((onHeard: (String) -> Unit) -> Unit)? = {}) = screen {
        SearchScreen(
            search = SearchSamples::search,
            recentSearches = SearchSamples.recent,
            actions = SearchActions(onProgram = { opened = it.title }, onTitle = { opened = it.title }, onChannel = { opened = it.name }, onVoice = voice),
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

    @Test fun leftAndRightGoRoundTheKeyboardsRows() {
        // The owner's #144: from one end of a row to the other in one press, with no results to
        // go on to.
        show()
        focused().assert(hasText("a"))
        press(Key.DirectionLeft)
        focused().assert(hasText("'"))
        press(Key.DirectionRight)
        focused().assert(hasText("a"))
        // A held key stops at the end of the row, and the next press goes round.
        press(Key.DirectionRight)
        hold(Key.DirectionLeft, 1_200)
        focused().assert(hasText("a"))
        // The bottom row: Space round to the mic key.
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionLeft)
        focused().assert(hasContentDescription("Search by voice"))
    }

    @Test fun theMicKeyPutsWhatWasSaidInTheQuery() {
        // Android's recognizer hears it (#120); here, straight away.
        show(voice = { onHeard -> onHeard(" velocity ") })
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionLeft)
        focused().assert(hasContentDescription("Search by voice"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("velocity").assertExists()
        focused().assert(hasContentDescription("Search by voice"))
    }

    @Test fun aTvThatCantRecognizeSpeechHasNoMicKey() {
        show(voice = null)
        compose.onNodeWithContentDescription("Search by voice").assertDoesNotExist()
        // Clear ends the bottom row in its place, and Left from Space goes round to it.
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionLeft)
        focused().assert(hasText("Clear"))
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
        // The Arena channels, by number: Arena Sports News (103) first.
        focused().assert(hasContentDescription("Arena Sports News", substring = true))
        press(Key.DirectionRight, times = 2)
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
