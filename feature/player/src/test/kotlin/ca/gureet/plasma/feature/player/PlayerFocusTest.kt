// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** The remote in the full-screen player (brief 3.6, 4.9–4.11). */
class PlayerFocusTest : ScreenshotTest() {
    private val tuned = mutableListOf<String>()
    private var channelList = false
    private var left = false

    private fun show() = screen {
        PlayerScreen(
            PlayerSamples.state,
            PlayerActions(onTune = { tuned += it }, onChannelList = { channelList = true }, onBack = { left = true }),
            initialOverlay = PlayerOverlay.None,
            autoHide = false,
        )
    }

    @Test fun upAndDownChangeChannelAndLeftOpensTheList() {
        show()
        press(Key.DirectionUp)
        check(tuned.last() == "ch106") { "Tuned $tuned" }
        press(Key.DirectionDown, times = 2)
        check(tuned.last() == "ch104") { "Tuned $tuned" }
        press(Key.DirectionLeft)
        check(channelList) { "Left didn't open the channel list" }
    }

    @Test fun okShowsTheOverlayOnPlayPause() {
        show()
        press(Key.DirectionCenter)
        focused().assert(hasContentDescription("Pause"))
        press(Key.DirectionCenter)
        focused().assert(hasContentDescription("Play"))
        // Up reaches the timeline, and Down comes back.
        press(Key.DirectionUp)
        focused().assert(hasContentDescription("Timeline"))
        press(Key.DirectionDown)
        focused().assert(hasContentDescription("Play"))
    }

    @Test fun rightShowsTheOverlayOnTheTimeline() {
        show()
        press(Key.DirectionRight)
        focused().assert(hasContentDescription("Timeline"))
    }

    @Test fun menuOpensPlayerOptions() {
        show()
        press(Key.Menu)
        focused().assert(hasText("Watch from the Start"))
    }

    @Test fun holdingOkOpensPlayerOptions() {
        show()
        longPress(Key.DirectionCenter)
        focused().assert(hasText("Watch from the Start"))
    }

    @Test fun typingANumberTunesIt() {
        show()
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        compose.onNodeWithText("108_").assertExists()
        compose.mainClock.advanceTimeBy(PlasmaMotion.NUMBER_ENTRY_TUNE_MS + 100)
        compose.waitForIdle()
        check(tuned.last() == "ch108") { "Tuned $tuned" }
    }

    @Test fun aReminderTakesFocusAndSwitchNowTunesItsChannel() {
        var done = 0
        screen(Sample.zapClock) {
            PlayerScreen(
                PlayerSamples.zapState,
                PlayerActions(onTune = { tuned += it }, onReminderDone = { done++ }),
                initialOverlay = PlayerOverlay.Zap,
                autoHide = false,
            )
        }
        focused().assert(hasText("Switch Now"))
        // Channel keys still reach the player underneath (brief 4.11).
        press(Key.ChannelUp)
        check(tuned.size == 1) { "Tuned $tuned" }
        focused().assert(hasText("Switch Now"))

        press(Key.DirectionCenter)
        check(tuned.last() == Sample.arena1.id) { "Tuned $tuned" }
        check(done == 1) { "Done $done times" }
        compose.onNodeWithText("Switch Now").assertDoesNotExist()
    }

    @Test fun backDismissesAReminderBeforeLeaving() {
        var done = 0
        screen(Sample.zapClock) {
            PlayerScreen(PlayerSamples.zapState, PlayerActions(onBack = { left = true }, onReminderDone = { done++ }), initialOverlay = PlayerOverlay.None, autoHide = false)
        }
        focused().assert(hasText("Switch Now"))
        back()
        check(!left && done == 1) { "Left $left, done $done times" }
        compose.onNodeWithText("Switch Now").assertDoesNotExist()
        back()
        check(left) { "Back didn't leave the player" }
    }

    @Test fun theReminderKeepsLeftAndRightAndOkTunesATypedNumber() {
        screen(Sample.zapClock) {
            PlayerScreen(
                PlayerSamples.zapState,
                PlayerActions(onTune = { tuned += it }, onChannelList = { channelList = true }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
        focused().assert(hasText("Switch Now"))
        press(Key.DirectionRight)
        focused().assert(hasText("Dismiss"))
        press(Key.DirectionLeft)
        focused().assert(hasText("Switch Now"))
        check(!channelList) { "Left opened the channel list" }

        // A number typed under the reminder tunes on OK, and the reminder stays.
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        press(Key.DirectionCenter)
        check(tuned.last() == "ch108") { "Tuned $tuned" }
        compose.onNodeWithText("Switch Now").assertExists()
    }

    @Test fun aReminderComingDueForAChannelOutsideTheListOpensIt() {
        val opened = mutableListOf<String>()
        var state by mutableStateOf(PlayerSamples.state)
        screen { PlayerScreen(state, PlayerActions(onTune = { tuned += it }, onOpenChannel = { opened += it }), initialOverlay = PlayerOverlay.None, autoHide = false) }

        state = state.copy(reminder = PlayerSamples.zapState.reminder!!.copy(channelId = "elsewhere"))
        compose.waitForIdle()
        focused().assert(hasText("Switch Now"))
        press(Key.DirectionCenter)

        check(opened == listOf("elsewhere")) { "Opened $opened" }
        check(tuned.isEmpty()) { "Tuned $tuned" }
    }

    @Test fun tuningInLateOffersWatchFromTheStartOnce() {
        val started = mutableListOf<Pair<String, java.time.Instant>>()
        screen {
            PlayerScreen(
                PlayerSamples.state,
                PlayerActions(onTune = { tuned += it }, onStartOver = { id, start -> started += id to start }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
        // Grand Slam Tennis on Arena Sports 2 started at 19:00; it's 20:41.
        press(Key.DirectionUp)
        compose.onNodeWithText("Started 1 h 41 min ago · OK to watch from the start").assertExists()
        // OK opens the overlay on Start Over, and a second OK starts it.
        press(Key.DirectionCenter)
        focused().assert(hasContentDescription("Start over"))
        press(Key.DirectionCenter)
        check(started == listOf(Sample.arena2.id to Sample.schedule.getValue(Sample.arena2).first().start.toInstant())) { "Started $started" }

        // Back on the same program later, it isn't offered again.
        back()
        press(Key.DirectionDown)
        press(Key.DirectionUp)
        compose.onNodeWithText("Started 1 h 41 min ago · OK to watch from the start").assertDoesNotExist()
    }

    @Test fun greenStartsOverOnAChannelWithAnArchiveAndGoLiveComesBack() {
        val started = mutableListOf<String>()
        var live = 0
        screen {
            PlayerScreen(
                PlayerSamples.state,
                PlayerActions(onStartOver = { id, _ -> started += id }, onGoLive = { live++ }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
        press(Key.ProgramGreen)
        check(started == listOf(Sample.arena1.id)) { "Started $started" }

        press(Key.DirectionCenter)
        press(Key.DirectionRight, times = 2)
        focused().assert(hasText("Go Live"))
        press(Key.DirectionCenter)
        check(live == 1) { "Went live $live times" }

        // Arena Sports News keeps no archive: nothing to start over.
        back()
        press(Key.DirectionDown, times = 2)
        press(Key.ProgramGreen)
        check(started == listOf(Sample.arena1.id)) { "Started $started" }
    }

    @Test fun backHidesTheOverlayThenLeaves() {
        show()
        press(Key.DirectionCenter)
        back()
        check(!left) { "Left with the overlay up" }
        compose.onNode(hasContentDescription("Pause")).assertDoesNotExist()
        back()
        check(left) { "Back didn't leave the player" }
    }
}
