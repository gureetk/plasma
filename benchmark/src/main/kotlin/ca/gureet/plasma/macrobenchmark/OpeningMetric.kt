// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * What a screen takes to come once its key is pressed, by what reached the screen (#127). The
 * measured block opens a screen with OK for the first time since Plasma started, goes Back, and
 * opens it again, with a rest after each: its first key, its second and its last. For each of the
 * three (`opening`, `returning`, `reopening`), from what was shown in the [WITHIN_MS] after the
 * key, or up to the next key:
 *
 * - `…StandstillMs`: the longest the screen stood still, from the key to the first frame after it
 *   or from one frame to the next. While a screen is being made, nothing else is drawn.
 * - `…Ms`: from the key to the frame that ended that standstill, which is the new screen's first.
 * - `…WorkMs`: the main thread's time in the frames it made, all together.
 *
 * What a first opening takes that the second doesn't is what could be got ready ahead. A screen
 * can go on drawing once it's there (a picture arriving, a clock), so nothing here waits for its
 * frames to stop.
 */
@OptIn(ExperimentalMetricApi::class)
internal class OpeningMetric : TraceMetric() {
    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        val block = measuredBlock(traceSession)
        val keys = traceSession.query("SELECT ts FROM slice WHERE name = '$KEY_TO_DRAW' AND dur > 0 ORDER BY ts")
            .map { it.long("ts") }
            .filter { it in block }
            .toList()
        check(keys.size >= NAMES.size) { "${keys.size} keys in the measured block, where an opening, a return and a second opening are ${NAMES.size} at least" }
        val shown = shownFrames(traceSession, captureInfo.targetPackageName).filter { it.present in block }
        if (shown.isEmpty()) return emptyList()

        val measurements = mutableListOf<Metric.Measurement>()
        // The first key opens, the second goes back, and the last, once focus is on the tile again, reopens.
        for ((name, index) in NAMES.zip(listOf(0, 1, keys.lastIndex))) {
            val key = keys[index]
            val until = minOf(keys.getOrNull(index + 1) ?: Long.MAX_VALUE, key + WITHIN_MS * NANOS_PER_MS_LONG)
            var last = key
            var standstill = 0L
            var endedAt = key
            var work = 0L
            for (frame in shown) {
                if (frame.present <= key) continue
                if (frame.present > until) break
                if (frame.present - last > standstill) {
                    standstill = frame.present - last
                    endedAt = frame.present
                }
                last = frame.present
                if (frame.frameStart >= key) work += frame.frameEnd - frame.frameStart
            }
            check(last > key) { "No frame was shown after the $name key" }
            measurements += Metric.Measurement("${name}Ms", (endedAt - key) / NANOS_PER_MS)
            measurements += Metric.Measurement("${name}StandstillMs", standstill / NANOS_PER_MS)
            measurements += Metric.Measurement("${name}WorkMs", work / NANOS_PER_MS)
        }
        return measurements
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
        const val NANOS_PER_MS_LONG = 1_000_000L

        /** The block's first key, its second and its last. */
        val NAMES = listOf("opening", "returning", "reopening")

        /** How long after a key its screen is looked at: longer than one takes to come, shorter than the block's rests. */
        const val WITHIN_MS = 4_000L
    }
}
