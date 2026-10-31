// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import ca.gureet.plasma.player.StillOutput.Seen
import org.junit.Assert.assertEquals
import org.junit.Test

/** When a picture and sound have stood still long enough to be frozen (Restart Frozen Streams, #91). */
class StillOutputTest {
    @Test fun itStandsStillOnlyOnceItHasMoved() {
        val still = StillOutput(afterMs = 500)
        // Nothing has moved since it opened: never frozen, however long.
        assertEquals(Seen.Started, still.look(10, place = 0, now = 0))
        assertEquals(Seen.Waiting, still.look(10, place = 0, now = 1_000))
        assertEquals(Seen.Waiting, still.look(10, place = 0, now = 9_000))

        // It moves on, then stands still: frozen half a second after it last moved.
        assertEquals(Seen.Started, still.look(11, place = 0, now = 9_100))
        assertEquals(Seen.Waiting, still.look(11, place = 0, now = 9_500))
        assertEquals(Seen.Frozen, still.look(11, place = 0, now = 9_600))
        assertEquals(Seen.Frozen, still.look(11, place = 0, now = 9_700))
    }

    @Test fun aPauseStartsTheTimeAfresh() {
        val still = StillOutput(afterMs = 500)
        still.look(1, place = 0, now = 0)
        still.look(2, place = 0, now = 100)

        still.pause()

        // Playing again, it has half a second from now, not from when it last moved.
        assertEquals(Seen.Started, still.look(2, place = 0, now = 1_000))
        assertEquals(Seen.Waiting, still.look(2, place = 0, now = 1_400))
        assertEquals(Seen.Frozen, still.look(2, place = 0, now = 1_500))
    }

    @Test fun openedAgainItHasToMoveBeforeItCanStandStill() {
        val still = StillOutput(afterMs = 500)
        still.look(1, place = 0, now = 0)
        still.look(2, place = 0, now = 100)

        still.reopened()

        assertEquals(Seen.Started, still.look(2, place = 0, now = 200))
        assertEquals(Seen.Waiting, still.look(2, place = 0, now = 5_000))
        assertEquals(Seen.Started, still.look(3, place = 0, now = 5_100))
        assertEquals(Seen.Frozen, still.look(3, place = 0, now = 5_600))
    }

    @Test fun withoutACountItGoesByThePlace() {
        val still = StillOutput(afterMs = 500)
        assertEquals(Seen.Started, still.look(null, place = 0, now = 0))
        assertEquals(Seen.Started, still.look(null, place = 40, now = 100))
        assertEquals(Seen.Waiting, still.look(null, place = 40, now = 500))
        assertEquals(Seen.Frozen, still.look(null, place = 40, now = 600))
    }

    @Test fun aCountThatTurnsUpLateHasToMoveItself() {
        val still = StillOutput(afterMs = 500)
        // The place moves while the engine can't count yet.
        still.look(null, place = 0, now = 0)
        still.look(null, place = 40, now = 100)

        // Its count turns up, and stays put before the first picture: nothing's frozen.
        assertEquals(Seen.Started, still.look(0, place = 80, now = 200))
        assertEquals(Seen.Waiting, still.look(0, place = 120, now = 1_000))
        assertEquals(Seen.Waiting, still.look(0, place = 160, now = 9_000))
    }
}
