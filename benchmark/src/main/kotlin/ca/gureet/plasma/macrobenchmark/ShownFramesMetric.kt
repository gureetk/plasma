// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * What reached the screen while Plasma was measured. A frame's own times don't say it where the
 * graphics chip is the slowest stage: the drawing thread then waits for a buffer in every frame,
 * which paces it to the display, and every frame finishes "late" by the same few refreshes,
 * whether or not one was missed. So, from SurfaceFlinger's frame timeline, within the measured
 * block:
 *
 * - `framesShown`: Plasma's frames that reached the screen.
 * - `refreshesMissed`: refreshes that went by with no new frame while Plasma was at one. A refresh
 *   counts when the main thread had less than a refresh to itself between the frame before and
 *   the one after: it was in a frame, working or waiting for the drawing thread. The pauses
 *   between keys, with nothing to draw, don't count.
 * - `refreshesWithAFramePercent`: of the refreshes Plasma had a frame to show at, those that got
 *   one. 100 is every frame on time.
 * - `displayRefreshMs`: the display's refresh, as the frame timeline expected its frames: a check
 *   on the rest.
 *
 * And from the GPU's clock, where the device's kernel traces it (`gpu_frequency`, which is 0 while
 * the GPU is powered down):
 *
 * - `gpuWorkMs`: each stretch the GPU stayed clocked, a millisecond or longer. Where it rests
 *   between frames, that's its work on one frame; where it can't keep up, a stretch runs over
 *   several.
 * - `gpuOnPerFrameMs`: all the time it was clocked, for each frame shown.
 * - `gpuBelowTopClockPercent`: how much of that time it ran below the fastest clock it showed. A
 *   GPU's clock can follow its load, and the frames after a pause then get a slower GPU.
 */
@OptIn(ExperimentalMetricApi::class)
internal class ShownFramesMetric : TraceMetric() {
    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        val block = measuredBlock(traceSession)
        val from = block.first
        val to = block.last
        val frames = shownFrames(traceSession, captureInfo.targetPackageName).filter { it.present in block }
        return timeliness(frames, refresh(traceSession, captureInfo.targetPackageName)) + gpu(traceSession, from, to, frames.size)
    }

    /**
     * The display's refresh, in nanoseconds: the time between two of the moments the frame timeline
     * expected Plasma's frames on screen, where they're one refresh apart (they're whole refreshes
     * apart). Null with fewer than two.
     */
    private fun refresh(session: TraceProcessor.Session, packageName: String): Double? {
        val expected = session.query(
            """
            SELECT DISTINCT expected_frame_timeline_slice.ts + expected_frame_timeline_slice.dur AS shown FROM expected_frame_timeline_slice
                JOIN process USING(upid)
            WHERE process.name = '$packageName' AND expected_frame_timeline_slice.dur > 0
            ORDER BY shown
            """.trimIndent(),
        ).map { it.long("shown") }.toList()
        val apart = expected.zipWithNext { a, b -> b - a }.filter { it > LEAST_REFRESH_NS }
        val least = apart.minOrNull() ?: return null
        // The middle one of those a single refresh apart: the least may be a stray one.
        return apart.filter { it < least * 3 / 2 }.sorted().let { it[it.size / 2] }.toDouble()
    }

    private fun timeliness(frames: List<Shown>, refresh: Double?): List<Metric.Measurement> {
        if (frames.size < 2 || refresh == null) return emptyList()
        var missed = 0L
        frames.zipWithNext { a, b ->
            val refreshes = max(1L, ((b.present - a.present) / refresh).roundToLong())
            val idle = max(0L, floor((b.frameStart - a.frameEnd) / refresh).toLong())
            missed += max(0L, refreshes - 1 - min(idle, refreshes - 1))
        }
        return listOf(
            Metric.Measurement("framesShown", frames.size.toDouble()),
            Metric.Measurement("refreshesMissed", missed.toDouble()),
            Metric.Measurement("refreshesWithAFramePercent", 100.0 * frames.size / (frames.size + missed)),
            Metric.Measurement("displayRefreshMs", refresh / NANOS_PER_MS),
        )
    }

    private fun gpu(session: TraceProcessor.Session, from: Long, to: Long, framesShown: Int): List<Metric.Measurement> {
        val clock = session.query(
            """
            SELECT counter.ts AS ts, counter.value AS hz FROM counter
                JOIN gpu_counter_track ON counter.track_id = gpu_counter_track.id
            WHERE gpu_counter_track.name = 'gpufreq'
            ORDER BY counter.ts
            """.trimIndent(),
        ).map { it.long("ts") to it.double("hz") }.toList()
        val top = clock.maxOfOrNull { it.second } ?: return emptyList()
        if (top <= 0.0) return emptyList()

        // Each sample holds until the next. A run of them with the clock on is one stretch at work.
        val work = mutableListOf<Double>()
        var on = 0L
        var below = 0L
        var stretch = 0L
        for (i in 0 until clock.size - 1) {
            val (ts, hz) = clock[i]
            val length = max(0L, min(clock[i + 1].first, to) - max(ts, from))
            if (hz > 0.0) {
                on += length
                if (hz < top) below += length
                stretch += length
            } else {
                if (stretch > 0) work += stretch / NANOS_PER_MS
                stretch = 0
            }
        }
        if (stretch > 0) work += stretch / NANOS_PER_MS
        if (on == 0L) return emptyList()
        return listOfNotNull(
            work.filter { it >= WORK_MS }.takeIf { it.isNotEmpty() }?.let { Metric.Measurement("gpuWorkMs", it) },
            if (framesShown > 0) Metric.Measurement("gpuOnPerFrameMs", on / NANOS_PER_MS / framesShown) else null,
            Metric.Measurement("gpuBelowTopClockPercent", 100.0 * below / on),
        )
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
        /** The GPU comes on for a moment between frames too: a stretch this long is a frame's work. */
        const val WORK_MS = 1.0

        /** No display refreshes faster than this: closer together, two expected moments are the same refresh. */
        const val LEAST_REFRESH_NS = 2_000_000L
    }
}

/** The measured block's start and end in the trace, or all of it where the trace doesn't mark one. */
internal fun measuredBlock(session: TraceProcessor.Session): LongRange {
    val block = session.query("SELECT MIN(ts) AS start, MAX(ts + dur) AS finish FROM slice WHERE name = 'measureBlock' AND dur > 0").firstOrNull()
    return (block?.nullableLong("start") ?: Long.MIN_VALUE)..(block?.nullableLong("finish") ?: Long.MAX_VALUE)
}

/** One of Plasma's frames: when it was on screen, and its main thread's frame. */
internal class Shown(val present: Long, val frameStart: Long, val frameEnd: Long)

/**
 * Plasma's frames that were shown, in the order they were. A layer's frame is on screen at the end
 * of the display's frame that carries it, and its token is the vsync its main thread's frame was
 * for.
 */
internal fun shownFrames(session: TraceProcessor.Session, packageName: String): List<Shown> {
    val timed = session.query(
        "SELECT COUNT(*) AS n FROM actual_frame_timeline_slice JOIN process USING(upid) WHERE process.name = '$packageName' AND actual_frame_timeline_slice.dur > 0",
    ).first().long("n")
    if (timed == 0L) return emptyList()
    val frames = session.query(
        """
        WITH main AS (
            SELECT CAST(SUBSTR(slice.name, ${DO_FRAME.length + 1}) AS INT) AS token, slice.ts AS ts, slice.dur AS dur FROM slice
                JOIN thread_track ON slice.track_id = thread_track.id
                JOIN thread USING(utid)
                JOIN process USING(upid)
            WHERE process.name = '$packageName' AND thread.tid = process.pid AND slice.dur > 0 AND slice.name GLOB '${DO_FRAME}[0-9]*'
        )
        SELECT DISTINCT display.ts + display.dur AS present, main.ts AS frame_start, main.ts + main.dur AS frame_end
        FROM actual_frame_timeline_slice layer
            JOIN process USING(upid)
            JOIN actual_frame_timeline_slice display ON display.display_frame_token = layer.display_frame_token AND display.upid != layer.upid
            JOIN main ON main.token = layer.surface_frame_token
        WHERE process.name = '$packageName' AND layer.dur > 0 AND display.dur > 0
        ORDER BY present
        """.trimIndent(),
    ).map { Shown(it.long("present"), it.long("frame_start"), it.long("frame_end")) }.toList()
    check(frames.isNotEmpty()) { "The frame timeline has $timed of Plasma's frames, and none could be matched to the display's frames and the main thread's" }
    return frames
}

private const val DO_FRAME = "Choreographer#doFrame "
