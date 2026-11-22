// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import kotlin.math.ceil

/**
 * How long a picture took to come up (brief 6.7's budgets, #127), timed by the player itself so a
 * TV reports its own numbers: from a tune to the channel's first frame ([Kind.Zap]), from an
 * engine's fault to the next engine's first frame ([Kind.Fallback]), and from Switch Player to
 * the picture in the engine picked ([Kind.SwitchPlayer]).
 */
data class TuneTiming(val kind: Kind, val channelId: String, val engine: EngineId, val millis: Long) {
    enum class Kind {
        /** Budget: 1 s, at the 95th percentile, with the Small buffer. */
        Zap,

        /** Budget: 3 s, with the hub keeping the provider's connection open. */
        Fallback,

        /** Budget: 2 s. */
        SwitchPlayer,
    }
}

/** The last timing of each kind, and the last [KEPT] zaps, whose 95th percentile 6.7 tracks. */
data class TuneTimings(
    val last: Map<TuneTiming.Kind, TuneTiming> = emptyMap(),
    val zaps: List<Long> = emptyList(),
) {
    /** The 95th percentile of [zaps], by nearest rank; null before the first. */
    val zapP95: Long? get() = zaps.sorted().let { if (it.isEmpty()) null else it[ceil(it.size * P95).toInt() - 1] }

    fun with(timing: TuneTiming): TuneTimings = copy(
        last = last + (timing.kind to timing),
        zaps = if (timing.kind == TuneTiming.Kind.Zap) (zaps + timing.millis).takeLast(KEPT) else zaps,
    )

    companion object {
        const val KEPT = 100
        private const val P95 = 0.95
    }
}
