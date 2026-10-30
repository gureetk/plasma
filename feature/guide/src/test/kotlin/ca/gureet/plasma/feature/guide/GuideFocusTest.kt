// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.LocalCanRecord
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test
import java.time.Duration

/** D-pad focus on the Guide (brief 4.8): the grid, and the days above it. */
class GuideFocusTest : ScreenshotTest() {
    private val opened = mutableListOf<Pair<String, ProgramAction>>()
    private val played = mutableListOf<String>()

    private fun show(state: GuideUiState = GuideSamples.state) =
        screen { GuideScreen(state, GuideActions(onProgram = { channel, _, action -> opened += channel to action }, onPlay = { played += it })) }

    @Test fun okOpensWhatTheGridHasFocusedOn() {
        show()
        // The grid opens on what's on now on the last channel watched: OK plays it.
        press(Key.DirectionCenter)
        check(opened.last() == ("ch105" to ProgramAction.Play)) { "Opened $opened" }
        // Right moves on to what's next, where OK opens Remind and Record. Its reminder is set.
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        focused().assert(hasText("Cancel Reminder"))
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        focused().assert(hasText("Remind Me"))
        press(Key.DirectionDown)
        focused().assert(hasText("Record"))
        press(Key.Back)

        // Down keeps to the time, on the next channel.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Today, 22:00–23:30 · Velocity Motor").assertExists()
        check(opened.size == 1) { "Opened $opened" }
    }

    @Test fun remindMeAndCancelReminderReachTheApp() {
        val reminded = mutableListOf<Triple<String, String, Boolean>>()
        screen { GuideScreen(GuideSamples.state, GuideActions(onRemind = { channelId, programId, on -> reminded += Triple(channelId, programId, on) })) }
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        focused().assert(hasText("Cancel Reminder"))
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        focused().assert(hasText("Remind Me"))
        press(Key.DirectionCenter)

        check(reminded.map { it.first to it.third } == listOf("ch105" to false, "ch105" to true)) { "Reminded $reminded" }
        check(reminded.map { it.second }.distinct().size == 1) { "Reminded $reminded" }
    }

    @Test fun aPastProgramOlderThanTheArchiveIsInfoOnly() {
        val state = GuideSamples.state.let { s -> s.copy(channels = s.channels.map { it.copy(archiveFrom = Sample.now) }) }
        screen { GuideScreen(state, GuideActions(onProgram = { channel, _, action -> opened += channel to action })) }
        // Up to Northline News at 20:41, then Left to the Evening Bulletin, over at 20:30.
        press(Key.DirectionUp, times = 2)
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)

        check(opened.last() == (Sample.northline.id to ProgramAction.Info)) { "Opened $opened" }
    }

    @Test fun aChannelTheGuideKnowsNothingAboutStillTakesFocusAndOkPlaysIt() {
        // Channels without guide data: focus shows on "No information", and OK plays the channel.
        val base = GuideSamples.state
        val start = base.channels.indexOfFirst { it.id == base.currentChannelId }
        val empty = base.channels[start + 1]
        val state = base.copy(channels = base.channels.map { if (it.id == empty.id) it.copy(programs = emptyList()) else it })
        val full = mutableListOf<String>()
        screen { GuideScreen(state, GuideActions(onChannel = { full += it }, onPlay = { played += it })) }
        press(Key.DirectionDown)
        // The panel above says so, and so does the row, which has focus; the picture stays.
        compose.onAllNodesWithText("No information").assertCountEquals(2)
        capture("08-guide-no-information")
        // OK plays it in the picture, and OK again full screen.
        press(Key.DirectionCenter)
        check(played == listOf(empty.id) && full.isEmpty()) { "Played $played, full screen $full" }
        press(Key.DirectionCenter)
        check(full == listOf(empty.id)) { "Full screen $full" }
    }

    @Test fun movingThroughTheGridLeavesTheChannelOkPlaysItThenGoesFullScreen() {
        // The owner's #76: the picture waits for OK, wherever focus goes.
        show()
        press(Key.DirectionDown)
        press(Key.DirectionUp, times = 2)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        check(played.isEmpty() && opened.isEmpty()) { "Played $played, opened $opened" }
        // OK on Meridian 24's program on now plays it in the picture, then full screen.
        press(Key.DirectionCenter)
        check(played == listOf(Sample.meridian.id) && opened.isEmpty()) { "Played $played, opened $opened" }
        press(Key.DirectionCenter)
        check(opened == listOf(Sample.meridian.id to ProgramAction.Play)) { "Opened $opened" }
    }

    @Test fun aChannelThatNeedsThePinGoesStraightToFullScreen() {
        val base = GuideSamples.state
        val start = base.channels.indexOfFirst { it.id == base.currentChannelId }
        val locked = base.channels[start + 1]
        show(base.copy(channels = base.channels.map { if (it.id == locked.id) it.copy(needsPin = true) else it }))
        // Down to what's on now on it: the picture can't show it, so the PIN is asked full screen.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(played.isEmpty() && opened.lastOrNull()?.first == locked.id) { "Played $played, opened $opened" }
    }

    @Test fun thePictureStaysOnTheChannelPlayingOutsideTheGroup() {
        // The channel playing isn't in the group showing: the picture stays on it, and OK plays the group's.
        val base = GuideSamples.state
        val playing = base.channels.first { it.id == base.currentChannelId }
        val others = base.channels.filter { it.id != playing.id }
        show(base.copy(channels = others, currentChannelId = others.first().id, playing = playing))
        press(Key.DirectionCenter)
        check(played == listOf(others.first().id) && opened.isEmpty()) { "Played $played, opened $opened" }
    }

    @Test fun upFromTheTopRowGoesToTheDaysAndDownComesBack() {
        show()
        press(Key.DirectionUp, times = 3)
        focused().assert(hasText("Today"))
        // Moving onto a day shows it.
        press(Key.DirectionRight)
        focused().assert(hasText("Thu 22"))
        compose.onNodeWithText("Thu 22 Oct").assertExists()
        // Back on today, Down returns to the grid on what's on now: OK plays it in the picture, and
        // OK again goes full screen (the owner's #76).
        press(Key.DirectionLeft)
        compose.onNodeWithText("Wed 21 Oct").assertExists()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(played.size == 1 && opened.isEmpty()) { "Played $played, opened $opened" }
        press(Key.DirectionCenter)
        check(opened.singleOrNull() == (played.single() to ProgramAction.Play)) { "Opened $opened" }
        // OK on a day goes back to the grid too, where OK on the channel in the picture goes full screen.
        press(Key.DirectionUp)
        focused().assert(hasText("Today"))
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        check(opened.size == 2) { "Opened $opened" }
    }

    @Test fun aChannelNumberJumpsToItsRowAtTheSameTime() {
        show()
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        compose.onNodeWithText("108_").assertExists()
        compose.mainClock.advanceTimeBy(PlasmaMotion.NUMBER_ENTRY_TUNE_MS + 100)
        compose.waitForIdle()

        // What's on now on Courtside+ plays in the picture, then full screen.
        check(played.isEmpty()) { "Played $played" }
        press(Key.DirectionCenter)
        check(played == listOf("ch108")) { "Played $played" }
        press(Key.DirectionCenter)
        check(opened.last() == ("ch108" to ProgramAction.Play)) { "Opened $opened" }
    }

    @Test fun yellowPicksAGroup() {
        var picked: String? = null
        val state = GuideSamples.state.copy(groups = listOf(GuideGroup("all", "All Channels"), GuideGroup("sports", "Sports")), groupId = "all")
        screen { GuideScreen(state, GuideActions(onGroupPicked = { picked = it })) }

        press(Key.ProgramYellow)
        focused().assert(hasText("All Channels"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        check(picked == "sports") { "Picked $picked" }
    }

    @Test fun anOutOfDateGuideSaysSo() {
        val state = GuideSamples.state.copy(stale = GuideStale(updated = Sample.now.minus(Duration.ofDays(2))))
        screen { GuideScreen(state, GuideActions()) }

        compose.onNodeWithText("Guide updated 2 days ago").assertExists()
        compose.onNodeWithText("Refresh").assertExists()
    }

    @Test fun menuOffersRecordAndRemind() {
        show()
        // Holding OK on what's on now offers Record alone; Menu on what's next adds Remind.
        longPress(Key.DirectionCenter)
        focused().assert(hasText("Record"))
        compose.onNodeWithText("Remind Me").assertDoesNotExist()
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.Menu)
        focused().assert(hasText("Cancel Reminder"))
        press(Key.DirectionDown)
        focused().assert(hasText("Record"))
        press(Key.Back)
        check(opened.isEmpty()) { "Opened $opened" }
    }

    @Test fun aGuestIsToldItCantRecord() {
        screen {
            CompositionLocalProvider(LocalCanRecord provides false) {
                GuideScreen(GuideSamples.state, GuideActions(onProgram = { channel, _, action -> opened += channel to action }))
            }
        }
        // What's on now has nothing else to offer: Close, and why.
        longPress(Key.DirectionCenter)
        focused().assert(hasText("Close"))
        compose.onNodeWithText("Guests can’t record. Switch profiles to record or change recordings.").assertExists()
        press(Key.DirectionCenter)
        // What's next keeps Remind.
        press(Key.DirectionRight)
        press(Key.Menu)
        focused().assert(hasText("Cancel Reminder"))
        press(Key.DirectionDown)
        focused().assert(hasText("Cancel Reminder"))
        compose.onNodeWithText("Record").assertDoesNotExist()
        press(Key.Back)
        check(opened.isEmpty()) { "Opened $opened" }
    }
}
