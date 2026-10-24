// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.catchup

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Catch-up against board 18 (docs/design/screens), and its D-pad paths. */
class CatchUpScreenshotTest : ScreenshotTest() {
    private fun focusedOn(description: String) = compose.onNodeWithContentDescription(description).assertIsFocused()

    @Test fun catchUp() {
        screen { CatchUpScreen(CatchUpSamples.state, CatchUpActions()) }
        capture("18-catch-up")
    }

    @Test fun opensOnWhatWasOnAtThisTimeYesterday() {
        var played: String? = null
        screen { CatchUpScreen(CatchUpSamples.state, CatchUpActions(onPlay = { _, program -> played = program.title })) }
        focusedOn("Tuesday Night Football, 19:30–21:40, 2 h 10 min")
        press(Key.DirectionCenter)
        check(played == "Tuesday Night Football") { "Got $played" }

        // Up through the day's earlier programmes, then to the days.
        press(Key.DirectionUp, times = 2)
        focusedOn("City League Preview, 17:00–18:30, 1 h 30 min")
        press(Key.DirectionDown, times = 4)
        focusedOn("Overseas Cup: Live, 22:30–23:30, unavailable")
        press(Key.DirectionCenter)
        check(played == "Tuesday Night Football") { "An unavailable programme doesn't play, got $played" }
    }

    @Test fun localCatchUpCanBeKeptAsARecording() {
        val kept = mutableListOf<String>()
        screen { CatchUpScreen(CatchUpSamples.state, CatchUpActions(onKeep = { channelId, program -> kept += "$channelId ${program.title}" })) }
        // Courtside+, last in the list, is recorded locally (5.5).
        press(Key.DirectionLeft)
        press(Key.DirectionDown, times = 10)
        focusedOn("Courtside+, Local · 24 h")
        press(Key.DirectionCenter)
        press(Key.Menu)
        compose.onNodeWithText("Keep as Recording").assertExists()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(kept.size == 1 && kept.single().startsWith("${Sample.courtside.id} ")) { "Kept $kept" }

        // A provider's archive has nothing to keep.
        press(Key.DirectionLeft)
        press(Key.DirectionUp, times = 10)
        focusedOn("Arena Sports 1, 7 days")
        press(Key.DirectionCenter)
        press(Key.Menu)
        compose.onNodeWithText("Keep as Recording").assertDoesNotExist()
    }

    @Test fun daysAndChannels() {
        screen { CatchUpScreen(CatchUpSamples.state, CatchUpActions()) }
        // CH− goes back a day, CH+ forward again.
        press(Key.ChannelDown)
        focusedOn("Live: City League, 19:30–21:40, 2 h 10 min")
        compose.onNodeWithText("Redmoor v Ashford Town · City League").assertDoesNotExist()
        press(Key.ChannelUp)
        focusedOn("Tuesday Night Football, 19:30–21:40, 2 h 10 min")

        // Left goes to the current channel; resting on another shows it.
        press(Key.DirectionLeft)
        focusedOn("Arena Sports 1, 7 days")
        press(Key.DirectionDown, times = 3)
        focusedOn("Velocity Motor, 3 days")
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithText("107 · 3-day archive").assertExists()
        // OK opens its programmes, on the same day.
        press(Key.DirectionCenter)
        focusedOn("Endurance 24: Night Stint, 20:00–21:30, 1 h 30 min")
    }
}
