// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

/**
 * Restart Frozen Streams (the owner's #91): whether what an engine puts out has stood still for
 * [afterMs], from looks at it while the stream plays.
 *
 * It goes by the engine's own count of pictures shown and sound played ([PlayerEngine.output]),
 * or by the place in the stream when the engine can't count. Nothing counts as frozen until what
 * it goes by has moved since the stream opened, so an engine whose count never moves (one that
 * can't tell, say) never looks frozen. A look by the other measure starts afresh, so a count
 * that only becomes known after the place moved has to move itself first. With [movesNeeded],
 * it has to have moved at that many looks first: one picture that comes and stays isn't playing.
 */
internal class StillOutput(private val afterMs: Long, private val movesNeeded: Int = 1) {
    /** What a look found. */
    enum class Seen {
        /** It moved, or looking started afresh: the time it stands still is counted from now. */
        Started,

        /** It hasn't moved since, or never has: not frozen, or not yet. */
        Waiting,

        /** It moved, and has stood still for [afterMs] since. */
        Frozen,
    }

    /** What the last look saw, and whether it was the engine's count; null after a pause. */
    private var last: Long? = null
    private var counted = false

    /** When it last moved, or when looking started afresh. */
    private var since = 0L

    /** Whether it has moved since the stream opened, by the measure it goes by now, at [movesNeeded] looks. */
    private var moved = false
    private var moves = 0

    /** Looks at the engine's [count] (null when it can't count) and its [place], at [now] in ms. */
    fun look(count: Long?, place: Long, now: Long): Seen {
        val value = count ?: place
        val before = last
        val sameMeasure = counted == (count != null)
        last = value
        counted = count != null
        if (before == null || !sameMeasure) {
            if (!sameMeasure) {
                moved = false
                moves = 0
            }
            since = now
            return Seen.Started
        }
        if (value != before) {
            moves++
            if (moves >= movesNeeded) moved = true
            since = now
            return Seen.Started
        }
        return if (moved && now - since >= afterMs) Seen.Frozen else Seen.Waiting
    }

    /** Whether what it goes by has moved since the stream opened, so it can stand still. */
    val hasMoved: Boolean get() = moved

    /** Whether the last look went by the engine's count, rather than its place in the stream. */
    val byCount: Boolean get() = counted

    /** How long it has stood still at [now], in ms: 0 until it has moved, and while it's paused. */
    fun stillFor(now: Long): Long = if (moved && last != null) (now - since).coerceAtLeast(0) else 0

    /** Paused, buffering, opening or behind live: the next look starts the time afresh. */
    fun pause() {
        last = null
    }

    /** Opened again, after a seek or Go Live: it has to move again before it can stand still. */
    fun reopened() {
        last = null
        moved = false
        moves = 0
    }
}
