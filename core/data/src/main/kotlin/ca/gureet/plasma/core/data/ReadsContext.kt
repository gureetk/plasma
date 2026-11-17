// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.ReadsForBenchmarks
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers

/**
 * Where a flow a screen reads is worked out, for `flowOn`: off its collector's thread, or, for the
 * benchmarks' twin ([ReadsForBenchmarks]), wherever it's collected, as before.
 */
fun readsContext(): CoroutineContext = if (ReadsForBenchmarks.asBefore) EmptyCoroutineContext else Dispatchers.Default
