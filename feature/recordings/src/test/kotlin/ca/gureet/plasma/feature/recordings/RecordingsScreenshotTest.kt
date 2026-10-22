// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.recordings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Recordings against board 17 (docs/design/screens), and its D-pad paths. */
class RecordingsScreenshotTest : ScreenshotTest() {
    private fun focusedOn(description: String) = compose.onNodeWithContentDescription(description).assertIsFocused()

    @Test fun recordings() {
        screen { RecordingsScreen(RecordingsSamples.state, RecordingsActions()) }
        capture("17-recordings")
    }

    @Test fun gridComingUpAndFilters() {
        screen { RecordingsScreen(RecordingsSamples.state, RecordingsActions()) }
        focusedOn("Harbour City v Redmoor, recording now")

        // Up from the grid reaches Coming Up; Down comes back to the same tile.
        press(Key.DirectionUp)
        focusedOn("Fight Night: Main Card, Arena Sports 1, Tonight, 00:30–03:00, Single recording")
        press(Key.DirectionDown)
        focusedOn("Harbour City v Redmoor, recording now")

        // Up from the last column goes to Sort, above it.
        press(Key.DirectionRight, times = 3)
        press(Key.DirectionUp)
        focused().assert(hasText("Newest first"))

        // Left from the first column goes to the current filter.
        press(Key.DirectionDown)
        press(Key.DirectionLeft, times = 4)
        focused().assert(hasText("All Recordings"))
    }

    @Test fun folderOpensAndBackCloses() {
        screen { RecordingsScreen(RecordingsSamples.state, RecordingsActions()) }
        press(Key.DirectionDown)
        focusedOn("Kingdoms of Ice, folder with 3 episodes")
        press(Key.DirectionCenter)
        compose.onNodeWithText("3 episodes · 2 unwatched").assertExists()
        focusedOn("Kingdoms of Ice, Episode 3, unwatched")

        press(Key.Back)
        focusedOn("Kingdoms of Ice, folder with 3 episodes")
        compose.onNodeWithText("12 recordings · 31 hours").assertExists()
    }

    @Test fun redDeletesAfterAsking() {
        var deleted: Set<String>? = null
        screen { RecordingsScreen(RecordingsSamples.state, RecordingsActions(onDelete = { deleted = it })) }
        press(Key.DirectionRight)
        focusedOn("Business Wrap, unwatched")
        press(Key.ProgramRed)
        // Cancel comes first.
        focused().assert(hasText("Cancel"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(deleted == setOf("business")) { "Got $deleted" }
        // Focus moves on to the next recording.
        focusedOn("Tuesday Night Football, watched")
        compose.onNodeWithText("11 recordings · 30 hours").assertExists()
    }

    @Test fun greenKeepsARecordingFromDeletion() {
        screen { RecordingsScreen(RecordingsSamples.state, RecordingsActions()) }
        compose.onNodeWithText("Deletes in 2 days · keep last 3").assertExists()
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 3)
        focusedOn("Deep Field, Deletes in 2 days")
        press(Key.ProgramGreen)
        focusedOn("Deep Field")
        compose.onNodeWithText("Kept · Fri 16 Oct · 1 h").assertExists()
    }

    @Test fun blueSelectsSeveralToDelete() {
        var deleted: Set<String>? = null
        screen { RecordingsScreen(RecordingsSamples.state, RecordingsActions(onDelete = { deleted = it })) }
        press(Key.DirectionRight)
        press(Key.ProgramBlue)
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.onNodeWithText("2 selected").assertExists()
        press(Key.ProgramRed)
        focused().assert(hasText("Cancel"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(deleted == setOf("business", "football")) { "Got $deleted" }
        compose.onNodeWithText("2 selected").assertDoesNotExist()
    }

    @Test fun scheduledListsRecordingsAndReminders() {
        var cancelled: String? = null
        screen { RecordingsScreen(RecordingsSamples.state, RecordingsActions(onCancelScheduled = { cancelled = it })) }
        press(Key.DirectionLeft)
        focused().assert(hasText("All Recordings"))
        press(Key.DirectionDown, times = 5)
        focused().assert(hasText("Scheduled"))
        press(Key.DirectionCenter)
        focusedOn("Fight Night: Main Card, Arena Sports 1 · Tonight, 00:30–03:00")
        compose.onNodeWithText("Reminders").assertExists()

        press(Key.Menu)
        focused().assert(hasText("Show in Guide"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(cancelled == "fightnight") { "Got $cancelled" }
        compose.onNodeWithText("Fight Night: Main Card").assertDoesNotExist()
    }
}
