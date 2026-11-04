// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

/**
 * Restart Frozen Streams' count (the owner's #91) for an engine whose statistics tell the pictures
 * it decodes from the ones it throws away, and the sound it plays from the sound it throws away:
 * VLC. Each [look] at the running totals moves the count on by the sound buffers played since the
 * last look, and by the pictures kept since then (decoded and not lost), when most of the pictures
 * decoded since then were kept.
 *
 * A picture that's decoded and then dropped, as too late to show or too far ahead to place, never
 * reaches the screen. So a stream whose clock has jumped, with every picture and sound buffer
 * thrown away, stands still however fast it's decoded: the freeze on the owner's TV (2 November),
 * where VLC kept decoding and the picture and sound had stopped. Its losses are counted a picture
 * or two after the pictures themselves, so a look's own few kept pictures don't count as moving:
 * looks are meant to be about a second apart. The totals come from the engine as ints, and may wrap.
 */
class KeptOutput {
    private var decoded = 0
    private var lost = 0
    private var played = 0
    private var looked = false

    /** The count so far: it moves only as pictures are kept and sound is played. */
    var count = 0L
        private set

    /** Looks at the totals so far, pictures [decoded] and [lost] and sound buffers [played]: the count after it. */
    fun look(decoded: Int, lost: Int, played: Int): Long {
        if (looked) {
            val newPlayed = played - this.played
            if (newPlayed > 0) count += newPlayed
            val newDecoded = decoded - this.decoded
            val kept = newDecoded.toLong() - (lost - this.lost)
            if (kept >= MIN_KEPT && kept * 2 > newDecoded) count += kept
        }
        looked = true
        this.decoded = decoded
        this.lost = lost
        this.played = played
        return count
    }

    private companion object {
        /** Fewer kept pictures than this in a look are the losses not counted yet, not the picture moving. */
        const val MIN_KEPT = 2L
    }
}
