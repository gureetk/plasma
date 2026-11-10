// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * Where a frame's time goes in Plasma, one sample a frame, for the frames FrameTimingMetric finds
 * slow: on the main thread, the whole frame, its animations and recomposing (Choreographer's
 * "animation") and its measuring, layout and recording of what to draw ("traversal"); and on the
 * RenderThread, the drawing itself.
 */
@OptIn(ExperimentalMetricApi::class)
internal class FrameSplitMetric : TraceMetric() {
    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        fun ms(where: String) = traceSession.query(
            """
            SELECT slice.dur AS dur FROM slice
                JOIN thread_track ON slice.track_id = thread_track.id
                JOIN thread USING(utid)
                JOIN process USING(upid)
            WHERE process.name = '${captureInfo.targetPackageName}' AND slice.dur > 0 AND $where
            """.trimIndent(),
        ).map { it.long("dur") / NANOS_PER_MS }.toList()
        val main = "thread.tid = process.pid"
        return listOf(
            "frameMainThreadMs" to ms("$main AND slice.name LIKE 'Choreographer#doFrame%'"),
            "frameAnimationMs" to ms("$main AND slice.name = 'animation'"),
            "frameTraversalMs" to ms("$main AND slice.name = 'traversal'"),
            "frameRenderThreadMs" to ms("thread.name = 'RenderThread' AND slice.name LIKE 'DrawFrame%'"),
        ).filter { (_, samples) -> samples.isNotEmpty() }
            .map { (name, samples) -> Metric.Measurement(name, samples) }
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
    }
}
