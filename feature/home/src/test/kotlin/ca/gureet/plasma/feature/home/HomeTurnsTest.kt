// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Home's rows below the screen's edge made in turns (#127), under keys that come faster than the
 * turns do: a key and the one frame that answers it, then the next key, from the moment Home has
 * its focus. Whatever the turns have got to, each Down finds its row made and lands where it
 * lands with everything made at once, and the way back up the rows is the same.
 */
class HomeTurnsTest : ScreenshotTest() {
    private fun show(
        belowAFrame: Int = 1,
        throughKeys: Boolean = false,
        asBefore: Boolean = false,
        onBehindUpAndDown: Boolean = false,
        onBelowMade: (() -> Unit)? = null,
    ) {
        compose.mainClock.autoAdvance = false
        screen {
            HomeScreen(
                HomeSamples.state,
                HomeActions(),
                heroAutoAdvance = false,
                belowAFrame = belowAFrame,
                belowThroughKeys = throughKeys,
                belowAsBefore = asBefore,
                belowOnBehindUpAndDown = onBehindUpAndDown,
                onBelowMade = onBelowMade,
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

    @Test fun inTurnsOfThreeTilesAFrameDownLandsOnEachRowAndUpComesBack() {
        show(belowAFrame = 3)
        downTheRowsAndBackUp()
    }

    @Test fun inTurnsThatGoOnBehindUpAndDownDownLandsOnEachRowAndUpComesBack() {
        show(onBehindUpAndDown = true)
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
        // The turns keep out of a key's way; the rows aren't made meanwhile.
        pressAndAFrame(Key.DirectionRight)
        pressAndAFrame(Key.DirectionLeft)
        focusedOn("Live TV")
        downTheRowsAndBackUp()
    }

    /** Frames until Home has said that everything below its edge is made. */
    private fun framesUntil(said: () -> Boolean) {
        var frames = 0
        while (!said()) {
            check(frames++ < MADE_WITHIN_FRAMES) { "Home hadn't said so after $MADE_WITHIN_FRAMES frames" }
            compose.mainClock.advanceTimeByFrame()
        }
    }

    @Test fun inTurnsHomeSaysThatEverythingBelowIsMadeOnceTheLastRowsTilesAreAndOnlyOnce() {
        var said = 0
        show(onBelowMade = { said++ })
        // Home has just taken its focus: the last row is still waiting for its turn.
        assertEquals(0, said)
        compose.onNodeWithContentDescription(LAST_ROWS_FIRST, substring = true).assertDoesNotExist()
        framesUntil { said > 0 }
        compose.onNodeWithContentDescription(LAST_ROWS_FIRST, substring = true).assertExists()
        repeat(AFTERWARDS_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(1, said)
    }

    @Test fun madeAllAtOnceHomeSaysSoWithinAFewFrames() {
        var said = 0
        show(belowAFrame = 0, onBelowMade = { said++ })
        compose.onNodeWithContentDescription(LAST_ROWS_FIRST, substring = true).assertExists()
        framesUntil { said > 0 }
        repeat(AFTERWARDS_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(1, said)
    }

    private companion object {
        /** The first tile of Home's last row, which is the last row to have its turn. */
        const val LAST_ROWS_FIRST = "Reelbox"

        /** More frames than the sample Home's rows and tiles below the edge are pieces. */
        const val MADE_WITHIN_FRAMES = 200

        /** Long enough afterwards for a second word to have come, if one were to. */
        const val AFTERWARDS_FRAMES = 10

        /** Home is composed, takes its focus in the frame after, and the focus shows in the next. */
        const val FRAMES_TO_FOCUS = 3

        /** Longer than Home's scroll between two of its resting places takes. */
        const val AT_REST_MS = 1_000L
    }
}
