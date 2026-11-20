// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The turns of a screen's pieces out of sight (#127): one piece is made at a frame, the nearest
 * first; a piece with no turns to wait for is made at once; and a key that comes between two
 * frames has the frame that answers it to itself.
 */
class TurnsTest : ScreenshotTest() {
    /** The pieces made so far, in the order they were. */
    private val made = mutableListOf<String>()

    /** How long the turns still keep out of the way, as the screen would say after a key. */
    private var quiet = 0L

    private lateinit var turns: Turns

    /**
     * [pieces] by name, each at its place or with no turns (`null`), composed in the order given.
     * A frame has room for [aFrame] tiles' worth, and a piece named "heavy…" weighs three.
     */
    private fun show(atTheFrameToo: Boolean = true, aFrame: Int = 1, pieces: () -> List<Pair<String, Int?>>) {
        turns = Turns()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LaunchedEffect(turns) { turns.give(aFrame, quietInMs = { quiet }, atTheFrameToo = atTheFrameToo) }
            for ((name, place) in pieces()) {
                key(name) {
                    InItsTurn(place?.let(turns::at), weight = if (name.startsWith("heavy")) 3 else 1) { SideEffect { if (name !in made) made += name } }
                }
            }
        }
    }

    private fun frame() = compose.mainClock.advanceTimeByFrame()

    /** Frames until [count] pieces are made, a piece a frame at most, and not more of them than [atMost]. */
    private fun framesUntilMade(count: Int, atMost: Int = 12) {
        var frames = 0
        while (made.size < count) {
            check(frames++ < atMost) { "Only $made after $atMost frames" }
            val before = made.size
            frame()
            assertTrue("More than a piece in a frame: ${made.drop(before)}", made.size - before <= 1)
        }
    }

    @Test fun aPieceAFrameTheNearestFirstAndOfTwoAsNearTheFirstToAsk() {
        show { listOf("far" to 3, "near" to 1, "inSight" to null, "nearToo" to 1, "between" to 2) }
        // What's in sight has no turn to wait for: it's there before any frame.
        compose.waitForIdle()
        assertEquals(listOf("inSight"), made)
        framesUntilMade(5)
        assertEquals(listOf("inSight", "near", "nearToo", "between", "far"), made)
        assertEquals(0, turns.waitingCount)
    }

    @Test fun aHeavyPieceHasAFrameToItselfWhereverTheFrameHasRoomForOne() {
        show { listOf("heavyShell" to 1, "a" to 1, "b" to 1) }
        // A piece a frame, as built: the heavy one is made though it's more than the frame's room.
        framesUntilMade(3)
        assertEquals(listOf("heavyShell", "a", "b"), made)
    }

    @Test fun withRoomForThreeTilesAFrameTheyGoThreeTogetherAndAHeavyPieceAlone() {
        show(aFrame = 3) { listOf("heavyShell" to 1, "a" to 1, "b" to 1, "c" to 1, "d" to 1, "heavyWide" to 2, "e" to 2, "f" to 2) }
        framesUntilMade(1)
        assertEquals(listOf("heavyShell"), made)
        frame()
        assertEquals(setOf("heavyShell", "a", "b", "c"), made.toSet())
        // One tile is left of the nearest row, and the heavy piece behind it doesn't fit beside it.
        frame()
        assertEquals(setOf("heavyShell", "a", "b", "c", "d"), made.toSet())
        frame()
        assertEquals(setOf("heavyShell", "a", "b", "c", "d", "heavyWide"), made.toSet())
        frame()
        assertEquals(8, made.size)
        assertEquals(0, turns.waitingCount)
    }

    @Test fun aNearerPieceThatAsksLaterGoesAheadOfTheOnesThatWait() {
        var late by mutableStateOf(false)
        show { listOf("third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6) + if (late) listOf("second" to 2) else emptyList() }
        framesUntilMade(1)
        assertEquals(listOf("third"), made)
        // As a row whose contents come after the others': it's the one the next key shows. The
        // frame it asks in has given its turn already; from the next one on it's ahead.
        late = true
        framesUntilMade(5)
        assertEquals(listOf("third", "fourth", "second", "fifth", "sixth"), made)
    }

    @Test fun aPieceThatComesIntoSightIsMadeRightThenAndWaitsNoMore() {
        var inSight by mutableStateOf(false)
        show { listOf("first" to 1, "second" to 2, "third" to 3, "fourth" to if (inSight) null else 4) }
        assertEquals(4, turns.waitingCount)
        framesUntilMade(1)
        inSight = true
        // The one frame gives the nearest its turn and makes the piece that came into sight.
        frame()
        assertEquals(setOf("first", "second", "fourth"), made.toSet())
        assertEquals(1, turns.waitingCount)
        frame()
        assertEquals(setOf("first", "second", "third", "fourth"), made.toSet())
        assertEquals(0, turns.waitingCount)
    }

    @Test fun theFrameThatAnswersAKeyHasNoPieceInIt() {
        show { listOf("first" to 1, "second" to 2, "third" to 3) }
        framesUntilMade(1)
        // A key comes: the next frame was asked for before it, and is the key's own.
        quiet = 300
        frame()
        frame()
        assertEquals(listOf("first"), made)
        // The wait over, the turns go on.
        quiet = 0
        compose.mainClock.advanceTimeBy(400)
        assertEquals(listOf("first", "second", "third"), made)
    }

    @Test fun asTheTurnsFirstWereTheFrameAfterAKeyStillMadeAPiece() {
        show(atTheFrameToo = false) { listOf("first" to 1, "second" to 2, "third" to 3) }
        framesUntilMade(1)
        quiet = 300
        frame()
        assertEquals(listOf("first", "second"), made)
        frame()
        frame()
        assertEquals(listOf("first", "second"), made)
    }
}
