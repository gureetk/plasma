// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * Whether a cold start's full display could be measured, for when StartupTimingMetric has none:
 * whether the device's log said Plasma was fully drawn (`waitForFullyDrawn`), 1 or 0, and how many
 * `reportFullyDrawn` sections the trace has on Plasma's main thread.
 */
@OptIn(ExperimentalMetricApi::class)
internal class FullyDrawnCheckMetric : TraceMetric() {
    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        val inTrace = traceSession.query(
            """
            SELECT COUNT(*) AS n FROM slice
                JOIN thread_track ON slice.track_id = thread_track.id
                JOIN thread USING(utid)
                JOIN process USING(upid)
            WHERE process.name = '${captureInfo.targetPackageName}' AND thread.tid = process.pid
                AND slice.name LIKE 'reportFullyDrawn() for %'
            """.trimIndent(),
        ).first().long("n")
        return listOf(
            Metric.Measurement("fullyDrawnInLog", if (seenInLog) 1.0 else 0.0),
            Metric.Measurement("fullyDrawnInTrace", inTrace.toDouble()),
        )
    }

    companion object {
        /** Whether the last wait saw the log say so. */
        @Volatile
        var seenInLog = false
    }
}
