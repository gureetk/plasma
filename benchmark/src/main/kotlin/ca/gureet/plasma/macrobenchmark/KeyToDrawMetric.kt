// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * A key to the frame it changes (brief 6.7: 100 ms): Plasma's [KEY_TO_DRAW] sections in the trace,
 * which `KeyTrace` starts for each key and ends as the next frame is drawn, as their average and
 * their longest, and how many there were. They're async sections, so they're read from the trace
 * as they are, wherever they are.
 */
@OptIn(ExperimentalMetricApi::class)
internal class KeyToDrawMetric : TraceMetric() {
    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        val ms = traceSession.query("SELECT dur FROM slice WHERE name = '$KEY_TO_DRAW' AND dur > 0")
            .map { it.long("dur") / NANOS_PER_MS }
            .toList()
        check(ms.isNotEmpty()) { "No $KEY_TO_DRAW sections in the trace: Plasma didn't time its keys" }
        return listOf(
            Metric.Measurement("keyToDrawAverageMs", ms.average()),
            Metric.Measurement("keyToDrawMaxMs", ms.max()),
            Metric.Measurement("keyToDrawCount", ms.size.toDouble()),
        )
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
    }
}
