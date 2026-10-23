// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** D-pad, number keys and sheets on Live TV (brief 4.7, 4.11 and 5.2). */
class LiveTvFocusTest : ScreenshotTest() {
    private var watched: String? = null

    private fun show() = screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(onWatch = { watched = it })) }

    /** Channel rows read as "105, Arena Sports 1, Harbour City v Redmoor". */
    private fun focusedChannel(number: Int, name: String) = focused().assert(hasContentDescription("$number, $name", substring = true))

    private fun waitForTune() {
        compose.mainClock.advanceTimeBy(PlasmaMotion.NUMBER_ENTRY_TUNE_MS + 100)
        compose.waitForIdle()
    }

    @Test fun focusStartsOnTheChannelPlayingAndMovesBetweenColumns() {
        show()
        focusedChannel(105, "Arena Sports 1")
        press(Key.DirectionDown)
        focusedChannel(106, "Arena Sports 2")

        // Left goes to the groups, on the group shown.
        press(Key.DirectionLeft)
        focused().assert(hasText("Sports"))
        press(Key.DirectionRight)
        focusedChannel(106, "Arena Sports 2")

        // Right goes to what's coming up on the channel in the preview.
        press(Key.DirectionUp)
        compose.mainClock.advanceTimeBy(PlasmaMotion.PREVIEW_DWELL_MS + 100)
        press(Key.DirectionRight)
        focused().assert(hasText("Match of the Week Extra"))
        press(Key.DirectionLeft)
        focusedChannel(105, "Arena Sports 1")
    }

    @Test fun typingANumberTunesItAfterAPause() {
        show()
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        compose.onNodeWithText("108_").assertExists()
        compose.onNodeWithText("Courtside+").assertExists()
        check(watched == null) { "Tuned $watched before the pause" }

        waitForTune()
        check(watched == "ch108") { "Tuned $watched" }
        compose.onNodeWithText("108_").assertDoesNotExist()
    }

    @Test fun okTunesTheNumberAtOnceAndBackDropsIt() {
        show()
        press(Key.One)
        press(Key.Zero)
        press(Key.Seven)
        press(Key.DirectionCenter)
        check(watched == "ch107") { "Tuned $watched" }

        watched = null
        press(Key.One)
        compose.onNodeWithText("1_").assertExists()
        press(Key.Back)
        compose.onNodeWithText("1_").assertDoesNotExist()
        waitForTune()
        check(watched == null) { "Tuned $watched after Back" }
        // OK and Back went to the number, not to the list.
        focusedChannel(105, "Arena Sports 1")
    }

    @Test fun aNumberNoChannelHasSaysSo() {
        show()
        press(Key.Nine, times = 3)
        compose.onNodeWithText("999_").assertExists()
        waitForTune()
        compose.onNodeWithText("No channel 999").assertExists()
        check(watched == null) { "Tuned $watched" }
        waitForTune()
        compose.onNodeWithText("No channel 999").assertDoesNotExist()
    }

    @Test fun inFavoritesANumberIsTheListsOwn() {
        show()
        press(Key.DirectionLeft)
        press(Key.DirectionUp, times = 2)
        focused().assert(hasText("Favorites"))
        // The list shows Favorites once focus rests on it.
        compose.mainClock.advanceTimeBy(400)
        press(Key.Two)
        compose.onNodeWithText("Favorites 2 · Velocity Motor").assertExists()
        press(Key.DirectionCenter)
        check(watched == "ch107") { "Tuned $watched" }

        // A number Favorites doesn't have falls back to the full list.
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        compose.onNodeWithText("Courtside+").assertExists()
    }

    @Test fun menuOpensChannelOptions() {
        show()
        press(Key.Menu)
        focused().assert(hasText("Remove from Favorites"))
        compose.onNodeWithText("105 · Harbour City v Redmoor").assertExists()
        press(Key.DirectionCenter)

        // The sheet closes on the channel, and the favorite is gone.
        focusedChannel(105, "Arena Sports 1")
        press(Key.Menu)
        focused().assert(hasText("Add to Favorites"))
        press(Key.Back)
        focusedChannel(105, "Arena Sports 1")

        // Red toggles it back without a sheet.
        press(Key.ProgramRed)
        press(Key.Menu)
        focused().assert(hasText("Remove from Favorites"))
    }

    @Test fun hiddenChannelsLeaveTheListAndTheirNumberOffersUnhide() {
        show()
        press(Key.Menu)
        press(Key.DirectionDown)
        focused().assert(hasText("Hide Channel"))
        press(Key.DirectionCenter)

        // Focus moves on to the next channel.
        focusedChannel(106, "Arena Sports 2")
        compose.onNode(hasContentDescription("105, Arena Sports 1", substring = true)).assertDoesNotExist()

        press(Key.One)
        press(Key.Zero)
        press(Key.Five)
        press(Key.DirectionCenter)
        check(watched == null) { "Tuned hidden $watched" }
        compose.onNodeWithText("Channel 105 is hidden").assertExists()
        focused().assert(hasText("Unhide"))
        press(Key.DirectionCenter)
        check(watched == "ch105") { "Tuned $watched" }
        compose.onNode(hasContentDescription("105, Arena Sports 1", substring = true)).assertExists()
    }

    @Test fun okOnAProgramComingUpSetsAndCancelsAReminder() {
        show()
        press(Key.DirectionRight)
        focused().assert(hasText("Match of the Week Extra"))
        focused().assert(hasContentDescription("Reminder set"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Cancel Reminder"))
        press(Key.DirectionCenter)

        // Focus comes back to the program, now without its bell.
        focused().assert(hasText("Match of the Week Extra"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Remind Me"))
        press(Key.DirectionDown)
        focused().assert(hasText("Record"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Match of the Week Extra"))
        focused().assert(hasContentDescription("Recording scheduled"))
    }
}
