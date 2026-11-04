// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** VLC's count for Restart Frozen Streams: pictures kept and sound played (#91, 2 November). */
class KeptOutputTest {
    @Test fun pictureAndSoundPlayingMoveItOn() {
        val kept = KeptOutput()
        // The first look only takes the totals in.
        assertEquals(0, kept.look(decoded = 100, lost = 3, played = 200))
        // A second of 25 pictures and 47 sound buffers, none lost.
        assertEquals(72, kept.look(decoded = 125, lost = 3, played = 247))
        // A few pictures lost along the way still leave most of them kept.
        assertEquals(72 + 20 + 47, kept.look(decoded = 150, lost = 8, played = 294))
    }

    @Test fun everyPictureDroppedAndNoSoundIsStandingStill() {
        val kept = KeptOutput()
        kept.look(decoded = 1_000, lost = 10, played = 2_000)
        val before = kept.look(decoded = 1_025, lost = 10, played = 2_047)

        // The clock jumped: every picture decoded is dropped, a picture or two late in the count,
        // and no sound plays. However fast it decodes, nothing moves.
        assertEquals(before, kept.look(decoded = 1_050, lost = 33, played = 2_047))
        assertEquals(before, kept.look(decoded = 1_075, lost = 61, played = 2_047))
        assertEquals(before, kept.look(decoded = 1_100, lost = 85, played = 2_047))
        assertEquals(before, kept.look(decoded = 1_100, lost = 87, played = 2_047))
    }

    @Test fun picturesHeldBackStandStillOnceTheDecoderStops() {
        val kept = KeptOutput()
        kept.look(decoded = 500, lost = 0, played = 900)
        // Pictures too far ahead are held: the decoder fills what it can, then waits.
        val filled = kept.look(decoded = 520, lost = 0, played = 900)
        assertEquals(filled, kept.look(decoded = 520, lost = 0, played = 900))
        assertEquals(filled, kept.look(decoded = 520, lost = 0, played = 900))
    }

    @Test fun soundAloneMovesItOn() {
        val kept = KeptOutput()
        // A radio channel: no pictures, only sound.
        kept.look(decoded = 0, lost = 0, played = 10)
        assertEquals(40, kept.look(decoded = 0, lost = 0, played = 50))
        // A picture that's mostly dropped, with the sound playing on.
        assertEquals(80, kept.look(decoded = 25, lost = 20, played = 90))
    }

    @Test fun totalsThatWrapCountOnAcrossTheWrap() {
        val kept = KeptOutput()
        kept.look(decoded = Int.MAX_VALUE - 10, lost = 0, played = Int.MAX_VALUE - 5)
        assertEquals(25 + 47, kept.look(decoded = Int.MIN_VALUE + 14, lost = 0, played = Int.MIN_VALUE + 41))
    }
}
