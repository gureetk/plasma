// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import ca.gureet.plasma.core.testing.ScreenshotTest
import ca.gureet.plasma.player.LocalVideoLayer
import ca.gureet.plasma.player.VideoFrame
import ca.gureet.plasma.player.VideoLayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Home's live picture and where focus is (#127): it plays while it shows and a row away from
 * showing, and a muted one is let go further down, to be asked for again on the way back.
 */
class HomeLivePictureTest : ScreenshotTest() {
    private var layer: VideoLayerState? = null

    /** Home on its second row, with a live picture at the top as a real channel has it. */
    private fun show(muted: Boolean) {
        screen {
            layer = LocalVideoLayer.current
            val state = remember {
                val sample = HomeSamples.state
                sample.copy(hero = listOf(sample.hero.first().copy(backdrop = HeroBackdrop.Video(VideoFrame(still = null, channelId = CHANNEL, muted = muted)))))
            }
            HomeScreen(state, HomeActions(), initialFocus = HomeFocus.Rows, heroAutoAdvance = false)
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    /** The channel Home asks the player for; null when it asks for none. */
    private val asked: String? get() = layer?.frame?.channelId

    private fun later(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    @Test fun aRowFromShowingTheLivePicturePlaysOn() {
        show(muted = true)
        later(LONG)
        assertEquals(CHANNEL, asked)
    }

    @Test fun furtherDownAMutedLivePictureIsLetGoOnceFocusHasStayed() {
        show(muted = true)
        press(Key.DirectionDown)
        later(LET_GO_AFTER - 500)
        assertEquals("Not yet: focus may be on its way back", CHANNEL, asked)
        later(1_000)
        assertNull("Focus has stayed on the third row", asked)
    }

    @Test fun itIsAskedForAgainOnTheWayBackUp() {
        show(muted = true)
        press(Key.DirectionDown)
        later(LET_GO_AFTER + 500)
        assertNull(asked)
        // Up to the second row, a move from where it shows.
        press(Key.DirectionUp)
        later(SOON)
        assertEquals(CHANNEL, asked)
    }

    @Test fun backFromFarDownAsksForItAtOnce() {
        show(muted = true)
        press(Key.DirectionDown, times = 2)
        later(LET_GO_AFTER + 500)
        assertNull(asked)
        press(Key.Back)
        later(SOON)
        assertEquals(CHANNEL, asked)
    }

    @Test fun aQuickLookFurtherDownLeavesItPlaying() {
        show(muted = true)
        press(Key.DirectionDown)
        later(LET_GO_AFTER - 1_000)
        press(Key.DirectionUp)
        later(LONG)
        assertEquals(CHANNEL, asked)
    }

    @Test fun aLivePictureWithSoundPlaysOnWhereverFocusIs() {
        show(muted = false)
        press(Key.DirectionDown, times = 2)
        later(LONG)
        assertEquals(CHANNEL, asked)
    }

    private companion object {
        const val CHANNEL = "example-one"

        /** Home lets a muted live picture go after this long far down its rows. */
        const val LET_GO_AFTER = 4_000L
        const val SOON = 100L
        const val LONG = 20_000L
    }
}
