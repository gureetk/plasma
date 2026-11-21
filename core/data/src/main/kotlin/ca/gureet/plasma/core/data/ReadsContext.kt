// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.ReadsForBenchmarks
import ca.gureet.plasma.core.model.ReadsForBenchmarks.Way
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [this], a flow a screen reads, worked out off the thread that collects it, which for a screen is
 * the main thread; or, for the benchmarks' twins ([ReadsForBenchmarks]), where it's collected, as
 * before, or where it's collected only while Plasma starts: a collection that begins before the
 * first world is in stays on its collector's thread, and one that begins after is moved, which
 * coming back to a screen always is. In the twin that changes over, a collection that began
 * before the first world is in takes its steps off its collector's thread from the moment the
 * world is in ([UntilTheWorld]).
 */
fun <T> Flow<T>.readOffMain(): Flow<T> = when (ReadsForBenchmarks.way) {
    Way.AsBefore -> this
    Way.WhereCollectedUntilTheWorld -> {
        val upstream = this
        val moved = upstream.flowOn(Dispatchers.Default)
        flow { emitAll(if (ReadsForBenchmarks.worldIn) moved else upstream) }
    }
    Way.ChangingOverAtTheWorld -> {
        val upstream = this
        val moved = upstream.flowOn(Dispatchers.Default)
        flow {
            if (ReadsForBenchmarks.worldIn) {
                emitAll(moved)
            } else {
                val here = currentCoroutineContext()[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Default
                emitAll(upstream.flowOn(UntilTheWorld(here)))
            }
        }
    }
    else -> flowOn(Dispatchers.Default)
}

/**
 * Runs what it's given where [here] would until the first world is in
 * ([ReadsForBenchmarks.worldIn]), and on [Dispatchers.Default] from then on. A read begun while
 * Plasma starts keeps its collector's thread, and that thread's pace, for as long as the world is
 * still being made, which is what leaves the processors to the world's making; once the world is
 * in, each step it still has to take runs off that thread, without the read being begun again.
 */
private class UntilTheWorld(private val here: CoroutineDispatcher) : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = ReadsForBenchmarks.worldIn || here.isDispatchNeeded(context)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (ReadsForBenchmarks.worldIn) Dispatchers.Default.dispatch(context, block) else here.dispatch(context, block)
    }
}
