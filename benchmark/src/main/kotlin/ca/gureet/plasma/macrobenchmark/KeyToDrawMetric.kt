// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * A key to the frame it changes, drawn (brief 6.7: 100 ms). Each of Plasma's [KEY_TO_DRAW]
 * sections, which `KeyTrace` starts as a key comes and ends as the next frame starts to draw, is
 * taken on to the end of that frame's drawing on Plasma's RenderThread: their average and their
 * longest, and how many keys there were. The display shows the frame a moment later.
 */
@OptIn(ExperimentalMetricApi::class)
internal class KeyToDrawMetric : TraceMetric() {
    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        val ms = traceSession.query(query(captureInfo.targetPackageName))
            .mapNotNull { row -> row.nullableLong("drawn")?.let { (it - row.long("ts")) / NANOS_PER_MS } }
            .toList()
        check(ms.isNotEmpty()) { "No $KEY_TO_DRAW sections with a frame after them in the trace: Plasma didn't time its keys" }
        return listOf(
            Metric.Measurement("keyToDrawAverageMs", ms.average()),
            Metric.Measurement("keyToDrawMaxMs", ms.max()),
            Metric.Measurement("keyToDrawCount", ms.size.toDouble()),
        )
    }

    /** Each key's start, and the end of the first frame the RenderThread starts drawing after its section. */
    private fun query(packageName: String) = """
        SELECT key.ts AS ts, (
            SELECT MIN(frame.ts + frame.dur) FROM slice frame
                JOIN thread_track ON frame.track_id = thread_track.id
                JOIN thread USING(utid)
                JOIN process USING(upid)
            WHERE frame.name LIKE 'DrawFrame%' AND frame.dur > 0 AND thread.name = 'RenderThread'
                AND process.name = '$packageName' AND frame.ts >= key.ts + key.dur
        ) AS drawn
        FROM slice key
        WHERE key.name = '$KEY_TO_DRAW' AND key.dur > 0
    """.trimIndent()

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
    }
}
