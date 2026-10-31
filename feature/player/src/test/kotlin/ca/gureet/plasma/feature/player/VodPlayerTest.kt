// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** A movie or an episode in the player (brief 4.9's VOD variant), and Up Next. */
class VodPlayerTest : ScreenshotTest() {
    private val seeks = mutableListOf<Long>()
    private val pauses = mutableListOf<Boolean>()
    private var nexts = 0
    private var cancels = 0
    private var left = false

    private fun actions(next: Boolean = true) = VodPlayerActions(
        onSeek = { seeks += it },
        onPause = { pauses += it },
        onNext = if (next) ({ nexts++ }) else null,
        onCancelNext = { cancels++ },
        onBack = { left = true },
    )

    private fun settle() {
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
    }

    @Test fun theOverlayOpensOnPauseAndTheBarJumpsOnceItSettles() {
        screen { VodPlayerScreen(PlayerSamples.movie, actions(next = false), autoHide = false) }
        focused().assert(hasContentDescription("Pause"))
        // A movie has no Next Episode.
        compose.onNodeWithText("Next Episode").assertDoesNotExist()

        press(Key.DirectionUp)
        focused().assert(hasContentDescription("Timeline"))
        press(Key.DirectionRight, times = 3)
        check(seeks.isEmpty()) { "Jumped at once: $seeks" }
        settle()
        check(seeks == listOf(72 * MINUTE + 30_000)) { "Jumped to $seeks" }

        press(Key.DirectionDown)
        focused().assert(hasContentDescription("Pause"))
        press(Key.DirectionCenter)
        check(pauses == listOf(true)) { "Paused $pauses" }
    }

    @Test fun leftWithNothingShowingJumpsBackAndShowsTheBar() {
        screen { VodPlayerScreen(PlayerSamples.movie, actions(next = false)) }
        compose.mainClock.advanceTimeBy(PlasmaMotion.OVERLAY_HIDE_MS + 100)
        compose.waitForIdle()
        compose.onNodeWithText("The Last Lighthouse").assertDoesNotExist()

        press(Key.DirectionLeft)
        focused().assert(hasContentDescription("Timeline"))
        settle()
        check(seeks == listOf(72 * MINUTE - 10_000)) { "Jumped to $seeks" }
    }

    @Test fun aRingTurnsWhileTheMovieOpensAndGoesOnceItPlays() {
        var state by mutableStateOf(PlayerSamples.movie.copy(status = PlayerStatus.Opening))
        screen { VodPlayerScreen(state, actions(next = false), autoHide = false) }
        // Not at once, so a quick start never flashes it.
        compose.onNodeWithContentDescription("Loading").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Loading").assertExists()

        state = state.copy(status = PlayerStatus.Playing)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Loading").assertDoesNotExist()
    }

    @Test fun theRemotesPlayKeyPausesWhateversShowing() {
        screen { VodPlayerScreen(PlayerSamples.movie, actions(next = false), autoHide = false) }
        press(Key.MediaPlayPause)
        check(pauses == listOf(true)) { "Paused $pauses" }
    }

    @Test fun menuOpensPlayerOptionsAndBackStepsOut() {
        screen { VodPlayerScreen(PlayerSamples.movie, actions(next = false), autoHide = false) }
        press(Key.Menu)
        focused().assert(hasText("Audio and Subtitles"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("English 5.1").assertExists()
        press(Key.Back)
        focused().assert(hasText("Audio and Subtitles"))
        press(Key.Back)
        press(Key.Back)
        check(left) { "Back didn't leave the player" }
    }

    @Test fun upNextTakesFocusOnceTheOverlayHidesAndOkPlaysIt() {
        screen { VodPlayerScreen(PlayerSamples.episode, actions()) }
        // While the overlay shows, it has Next Episode instead.
        compose.onNodeWithText("Next Episode").assertExists()
        compose.mainClock.advanceTimeBy(PlasmaMotion.OVERLAY_HIDE_MS + 100)
        compose.waitForIdle()

        focused().assert(hasContentDescription("S2, E5 · The Quiet Side"))
        press(Key.DirectionCenter)
        check(nexts == 1) { "Played the next one $nexts times" }
    }

    @Test fun backPutsUpNextAwayAndCountsAsCancelled() {
        var state by mutableStateOf(PlayerSamples.episode)
        screen { VodPlayerScreen(state, actions()) }
        compose.mainClock.advanceTimeBy(PlasmaMotion.OVERLAY_HIDE_MS + 100)
        compose.waitForIdle()
        // The episode ended: the card counts down.
        state = state.copy(upNext = state.upNext?.copy(countdown = 8))
        compose.waitForIdle()
        compose.onNodeWithText("Next episode in 8 s").assertExists()

        press(Key.Back)
        check(cancels == 1) { "Cancelled $cancels times" }
        check(!left) { "Back left instead of putting Up Next away" }
        compose.onNodeWithText("Next episode in 8 s").assertDoesNotExist()
        press(Key.Back)
        check(left) { "Back didn't leave the player" }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
