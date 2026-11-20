// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/**
 * Home's rows below the screen's edge made in turns (#127), under keys that come faster than the
 * turns do: a key and the one frame that answers it, then the next key, from the moment Home has
 * its focus. Whatever the turns have got to, each Down finds its row made and lands where it
 * lands with everything made at once, and the way back up the rows is the same.
 */
class HomeTurnsTest : ScreenshotTest() {
    private fun show(belowAFrame: Int = 1, throughKeys: Boolean = false, asBefore: Boolean = false, waitAfterEveryKey: Boolean = false) {
        compose.mainClock.autoAdvance = false
        screen {
            HomeScreen(
                HomeSamples.state,
                HomeActions(),
                heroAutoAdvance = false,
                belowAFrame = belowAFrame,
                belowThroughKeys = throughKeys,
                belowAsBefore = asBefore,
                belowWaitAfterEveryKey = waitAfterEveryKey,
            )
        }
        // Home takes its focus a frame after it's composed.
        repeat(FRAMES_TO_FOCUS) { compose.mainClock.advanceTimeByFrame() }
        focusedOn("Live TV")
    }

    /** A key, and the one frame that answers it. */
    private fun pressAndAFrame(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun focusedOn(description: String) = focused().assert(hasContentDescription(description, substring = true))

    /**
     * Down the five rows and back up to the first, a key a frame; then, with Home come to rest,
     * up to the dock. That last step waits because it isn't the turns': while the page is still
     * on its way up, the first row's tile sits level with the top bar and the dock is far above
     * it, and Up takes the nearer of the two, however the rows were made.
     */
    private fun downTheRowsAndBackUp() {
        for (first in listOf("Wildframe 4K", "Atlas Station", "Neon Tide", "Kingsway", "Reelbox")) {
            pressAndAFrame(Key.DirectionDown)
            focusedOn(first)
        }
        for (first in listOf("Kingsway", "Neon Tide", "Atlas Station", "Wildframe 4K")) {
            pressAndAFrame(Key.DirectionUp)
            focusedOn(first)
        }
        compose.mainClock.advanceTimeBy(AT_REST_MS)
        pressAndAFrame(Key.DirectionUp)
        focusedOn("Live TV")
    }

    @Test fun madeAllAtOnceDownLandsOnEachRowAndUpComesBack() {
        show(belowAFrame = 0)
        downTheRowsAndBackUp()
    }

    @Test fun inTurnsDownLandsOnEachRowAndUpComesBack() {
        show()
        downTheRowsAndBackUp()
    }

    @Test fun inTurnsThatWaitAfterEveryKeyDownLandsOnEachRowAndUpComesBack() {
        show(waitAfterEveryKey = true)
        downTheRowsAndBackUp()
    }

    @Test fun inTurnsThatGoOnThroughKeysDownLandsOnEachRowAndUpComesBack() {
        show(throughKeys = true)
        downTheRowsAndBackUp()
    }

    @Test fun inTurnsAsTheyFirstWereDownLandsOnEachRowAndUpComesBack() {
        show(asBefore = true)
        downTheRowsAndBackUp()
    }

    @Test fun keysAlongTheDockLeaveTheRowsToTheirTurnsAndDownStillFindsThem() {
        show()
        // The turns keep out of the way of keys along a row; the rows aren't made meanwhile.
        pressAndAFrame(Key.DirectionRight)
        pressAndAFrame(Key.DirectionLeft)
        focusedOn("Live TV")
        downTheRowsAndBackUp()
    }

    private companion object {
        /** Home is composed, takes its focus in the frame after, and the focus shows in the next. */
        const val FRAMES_TO_FOCUS = 3

        /** Longer than Home's scroll between two of its resting places takes. */
        const val AT_REST_MS = 1_000L
    }
}
