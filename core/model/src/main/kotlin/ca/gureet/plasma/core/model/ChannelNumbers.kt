// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/** How a channel that loses a contested number gets a new one (brief 5.2). */
enum class DuplicateRule {
    /** The number plus 1000 (108 → 1108), or plus 2000 if that's taken, and so on. */
    AddThousands,

    /** The first free number after the contested one. */
    NextFree,
}

object ChannelNumbers {
    /** The number [rule] gives a channel that loses [number], skipping every number in [taken]. */
    fun moveTo(number: Int, rule: DuplicateRule, taken: Set<Int>): Int {
        val step = if (rule == DuplicateRule.AddThousands) 1_000 else 1
        var candidate = number + step
        while (candidate in taken) candidate += step
        return candidate
    }

    /**
     * New numbers for the channels that lost theirs, in order: [contested] holds each one's old
     * number, and [taken] every number in use before. A number given out earlier counts as taken
     * for the ones after it.
     */
    fun moveAll(contested: List<Int>, rule: DuplicateRule, taken: Set<Int>): List<Int> {
        val inUse = taken.toMutableSet()
        return contested.map { number -> moveTo(number, rule, inUse).also { inUse += it } }
    }
}
