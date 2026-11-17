// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.ReadsForBenchmarks
import ca.gureet.plasma.core.model.ReadsForBenchmarks.Way
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [this], a flow a screen reads, worked out off the thread that collects it, which for a screen is
 * the main thread; or, for the benchmarks' twins ([ReadsForBenchmarks]), where it's collected, as
 * before, or where it's collected only while Plasma starts: a collection that begins before the
 * first world is in stays on its collector's thread, and one that begins after is moved, which
 * coming back to a screen always is.
 */
fun <T> Flow<T>.readOffMain(): Flow<T> = when (ReadsForBenchmarks.way) {
    Way.AsBefore -> this
    Way.WhereCollectedUntilTheWorld -> {
        val upstream = this
        val moved = upstream.flowOn(Dispatchers.Default)
        flow { emitAll(if (ReadsForBenchmarks.worldIn) moved else upstream) }
    }
    else -> flowOn(Dispatchers.Default)
}
