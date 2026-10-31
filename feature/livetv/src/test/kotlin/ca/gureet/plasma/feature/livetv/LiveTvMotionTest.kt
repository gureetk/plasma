// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live TV with its motion under way (brief 2.6). The clock stops and moves a frame at a time, so
 * each key here comes while a sheet fades in or out or the rows slide, and it acts at once:
 * nothing waits for an animation to end.
 */
class LiveTvMotionTest : ScreenshotTest() {
    private var stoppedAt = 0L

    private fun focusedChannel(number: Int, name: String) = focused().assert(hasContentDescription("$number, $name", substring = true))

    private fun isFocused(text: String) = runCatching { focused().assert(hasText(text)) }.isSuccess

    private fun isFocusedChannel(number: Int, name: String) = runCatching { focusedChannel(number, name) }.isSuccess

    /** Stops the clock: from here, it moves only with the frames and presses below. */
    private fun stopTheClock() {
        compose.mainClock.autoAdvance = false
        stoppedAt = compose.mainClock.currentTime
    }

    /** How long since the clock stopped, in ms. */
    private fun elapsed() = compose.mainClock.currentTime - stoppedAt

    /** A quick press: down, and up a frame later. */
    @OptIn(ExperimentalTestApi::class)
    private fun tap(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key, pressDurationMillis = FRAME_MS) }
        compose.waitForIdle()
    }

    /** A frame at a time until [done], for four frames at most. */
    private fun framesUntil(done: () -> Boolean) {
        repeat(MAX_FRAMES) {
            if (done()) return
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        check(done()) { "Not done after $MAX_FRAMES frames" }
    }

    @Test fun okWhileASheetFadesInActsAtOnce() {
        val favorites = mutableListOf<Pair<String, Boolean>>()
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(onFavorite = { id, on -> favorites += id to on })) }
        focusedChannel(105, "Arena Sports 1")
        stopTheClock()

        tap(Key.Menu)
        framesUntil { isFocused("Remove from Favorites") }
        tap(Key.DirectionCenter)

        // The sheet's 200 ms aren't up, and OK has done what it said.
        assertTrue("${elapsed()} ms", elapsed() < 200)
        assertEquals(listOf("ch105" to false), favorites)
        framesUntil { isFocusedChannel(105, "Arena Sports 1") }
    }

    @Test fun keysGoToTheListWhileASheetFadesOut() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) }
        press(Key.Menu)
        focused().assert(hasText("Remove from Favorites"))
        stopTheClock()

        tap(Key.Back)
        framesUntil { isFocusedChannel(105, "Arena Sports 1") }
        tap(Key.DirectionDown)
        framesUntil { isFocusedChannel(106, "Arena Sports 2") }

        // Within the sheet's 150 ms: it's still there, fading out, and the list has the keys.
        assertTrue("${elapsed()} ms", elapsed() < 150)
        compose.onNodeWithText("Remove from Favorites").assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Remove from Favorites").assertDoesNotExist()
        focusedChannel(106, "Arena Sports 2")
    }

    @Test fun aFavoriteCarriedWhileTheRowsSlideKeepsFocus() {
        val moved = mutableListOf<Pair<String, String?>>()
        screen { LiveTvScreen(LiveTvSamples.state.copy(selectedGroupId = "favorites"), LiveTvActions(onMoveFavorite = { id, after -> moved += id to after })) }
        focusedChannel(1, "Arena Sports 1")
        press(Key.Menu)
        pressDownTo("Move")
        press(Key.DirectionCenter)
        compose.onNodeWithText("Moving Arena Sports 1").assertExists()
        stopTheClock()

        // Down twice, a frame apart: the second comes while the rows still slide from the first.
        tap(Key.DirectionDown)
        compose.mainClock.advanceTimeByFrame()
        tap(Key.DirectionDown)
        framesUntil { isFocusedChannel(3, "Arena Sports 1") }
        tap(Key.DirectionCenter)

        // Dropped before the rows have settled, where it was carried, and with focus.
        assertTrue("${elapsed()} ms", elapsed() < 200)
        assertEquals(listOf("ch105" to "ch101"), moved)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Moving Arena Sports 1").assertDoesNotExist()
        focusedChannel(3, "Arena Sports 1")
        compose.onNode(hasContentDescription("2, Northline News", substring = true)).assertExists()
    }

    /** Down through a sheet until [label] has focus. */
    private fun pressDownTo(label: String) {
        repeat(12) {
            if (isFocused(label)) return
            press(Key.DirectionDown)
        }
        focused().assert(hasText(label))
    }

    private companion object {
        const val FRAME_MS = 16L
        const val MAX_FRAMES = 4
    }
}
