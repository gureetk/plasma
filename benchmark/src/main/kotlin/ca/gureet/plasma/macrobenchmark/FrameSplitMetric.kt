// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * Where a frame's time goes in Plasma, one sample a frame, for the frames FrameTimingMetric finds
 * slow: on the main thread, the whole frame, its animations and recomposing (Choreographer's
 * "animation") and its measuring, layout and recording of what to draw ("traversal"); on the
 * RenderThread, the drawing itself, and within it, the wait for a buffer to draw into (long when
 * the graphics chip is behind), sending the drawing to the chip ("flush commands") and handing the
 * frame over (`eglSwapBuffers`); and the graphics chip's own time to finish each frame, as HWUI's
 * GPU completion thread waits for it. Each only where the device's trace has it.
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
            "frameDequeueMs" to ms("thread.name = 'RenderThread' AND slice.name = 'dequeueBuffer'"),
            "frameFlushMs" to ms("thread.name = 'RenderThread' AND slice.name = 'flush commands'"),
            "frameSwapMs" to ms("thread.name = 'RenderThread' AND slice.name LIKE 'eglSwapBuffers%'"),
            "frameGpuMs" to ms("slice.name LIKE 'waiting for GPU completion%'"),
        ).filter { (_, samples) -> samples.isNotEmpty() }
            .map { (name, samples) -> Metric.Measurement(name, samples) }
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
    }
}
