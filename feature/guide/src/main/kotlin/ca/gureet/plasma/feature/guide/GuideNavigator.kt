// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

/** A programme's place in a channel's row: its id and its start and end in epoch milliseconds. */
data class GuideSlot(val id: String, val start: Long, val end: Long)

/**
 * Where focus is in the guide grid.
 *
 * @param row the channel row.
 * @param programId the focused programme, or null on a row with no guide data.
 * @param anchor the time vertical moves aim for (brief 4.8): the start of the focused programme,
 *   clamped to the visible window, or now on entry. Up and Down keep it; Left and Right set it.
 * @param windowStart the start of the visible 2-hour window, on a half hour.
 */
data class GuideCursor(val row: Int, val programId: String?, val anchor: Long, val windowStart: Long)

/**
 * The guide's focus logic, kept apart from the drawing so it can be tested on its own (brief 4.8).
 *
 * - Left and Right move by programme. A programme running past the edge of the window scrolls the
 *   window in 30-minute steps before focus moves on.
 * - Up and Down land on the programme that overlaps the anchor time on the new row, so focus runs
 *   straight down a column of time instead of drifting.
 */
class GuideNavigator(private val rows: List<List<GuideSlot>>, private val earliestWindowStart: Long = Long.MIN_VALUE) {

    fun rowCount(): Int = rows.size

    /** The programme on [row] at [time]; if there's a gap, the nearest one. */
    fun programAt(row: Int, time: Long): GuideSlot? {
        val list = rows.getOrNull(row) ?: return null
        return list.firstOrNull { it.start <= time && time < it.end }
            ?: list.minByOrNull { if (time < it.start) it.start - time else time - it.end + 1 }
    }

    /** Focus on entering the grid: the programme on now on [row]. */
    fun enter(row: Int, now: Long, windowStart: Long): GuideCursor =
        GuideCursor(row, programAt(row, now)?.id, now.coerceIn(windowStart, windowStart + WINDOW - 1), windowStart)

    /** Up ([delta] −1) or Down (+1). Null when there's no row there, so focus can leave the grid. */
    fun vertical(cursor: GuideCursor, delta: Int): GuideCursor? {
        val target = cursor.row + delta
        if (target !in rows.indices) return null
        val program = programAt(target, cursor.anchor)
        var windowStart = cursor.windowStart
        if (program != null) {
            // A gap can send focus to a programme outside the window; bring it into view.
            while (program.end <= windowStart && windowStart - STEP >= earliestWindowStart) windowStart -= STEP
            while (program.start >= windowStart + WINDOW) windowStart += STEP
        }
        return cursor.copy(row = target, programId = program?.id, windowStart = windowStart)
    }

    fun right(cursor: GuideCursor): GuideCursor {
        val list = rows.getOrNull(cursor.row).orEmpty()
        val index = list.indexOfFirst { it.id == cursor.programId }
        val current = list.getOrNull(index)
        val windowEnd = cursor.windowStart + WINDOW
        if (current != null && current.end > windowEnd) {
            val windowStart = cursor.windowStart + STEP
            return cursor.copy(windowStart = windowStart, anchor = maxOf(current.start, windowStart))
        }
        val next = if (current == null) list.firstOrNull { it.start >= cursor.anchor } else list.getOrNull(index + 1)
        if (next == null) {
            // Nothing more in the guide: still let the window move on, up to the next half hour.
            return cursor
        }
        var windowStart = cursor.windowStart
        while (next.start >= windowStart + WINDOW) windowStart += STEP
        return cursor.copy(programId = next.id, anchor = maxOf(next.start, windowStart), windowStart = windowStart)
    }

    fun left(cursor: GuideCursor): GuideCursor {
        val list = rows.getOrNull(cursor.row).orEmpty()
        val index = list.indexOfFirst { it.id == cursor.programId }
        val current = list.getOrNull(index)
        if (current != null && current.start < cursor.windowStart && cursor.windowStart - STEP >= earliestWindowStart) {
            val windowStart = cursor.windowStart - STEP
            return cursor.copy(windowStart = windowStart, anchor = maxOf(current.start, windowStart))
        }
        val previous = (if (current == null) list.lastOrNull { it.end <= cursor.anchor } else list.getOrNull(index - 1))
            ?: return cursor
        var windowStart = cursor.windowStart
        while (previous.end <= windowStart && windowStart - STEP >= earliestWindowStart) windowStart -= STEP
        if (previous.end <= windowStart) return cursor
        return cursor.copy(programId = previous.id, anchor = maxOf(previous.start, windowStart), windowStart = windowStart)
    }

    companion object {
        /** The visible window: 2 hours (1,412 px on the board). */
        const val WINDOW: Long = 120 * 60_000L

        /** How far the window scrolls at a time. */
        const val STEP: Long = 30 * 60_000L
    }
}
