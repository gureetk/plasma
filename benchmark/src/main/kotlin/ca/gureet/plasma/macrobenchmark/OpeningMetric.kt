// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor
import kotlin.math.max

/**
 * How long a screen takes to come once its key is pressed, by what reached the screen (#127). The
 * measured block presses three keys with a rest after each: OK to open a screen for the first time
 * since Plasma started, Back, and OK to open it again. After each key, Plasma's frames come one
 * after another until the screen is there with its pictures in, and then stop:
 *
 * - `openingMs`, `returningMs` and `reopeningMs`: from each key to the last frame shown before the
 *   frames first stop for [QUIET_MS], or before the next key.
 * - `openingStandstillMs` and `reopeningStandstillMs`: the longest the screen stood still on the
 *   way, from the key to the first frame after it or from one frame to the next.
 *
 * What a first opening takes that the second doesn't is what could be got ready ahead.
 */
@OptIn(ExperimentalMetricApi::class)
internal class OpeningMetric : TraceMetric() {
    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        val block = measuredBlock(traceSession)
        val keys = traceSession.query("SELECT ts FROM slice WHERE name = '$KEY_TO_DRAW' AND dur > 0 ORDER BY ts")
            .map { it.long("ts") }
            .filter { it in block }
            .toList()
        // Back is a key like the others to Plasma; where a build doesn't time it, the two openings are still told apart.
        val names = when (keys.size) {
            NAMES.size -> NAMES
            NAMES.size - 1 -> NAMES - RETURNING
            else -> error("${keys.size} keys in the measured block, where an opening, a return and a second opening are ${NAMES.size}")
        }
        val shown = shownFrames(traceSession, captureInfo.targetPackageName).map { it.present }.filter { it in block }
        if (shown.isEmpty()) return emptyList()

        val measurements = mutableListOf<Metric.Measurement>()
        for ((index, name) in names.withIndex()) {
            val key = keys[index]
            val until = keys.getOrNull(index + 1) ?: Long.MAX_VALUE
            var last = key
            var standstill = 0L
            for (present in shown) {
                if (present <= key) continue
                if (present >= until || (last > key && present - last >= QUIET_MS * NANOS_PER_MS)) break
                standstill = max(standstill, present - last)
                last = present
            }
            check(last > key) { "No frame was shown after the $name key" }
            measurements += Metric.Measurement("${name}Ms", (last - key) / NANOS_PER_MS)
            if (name != RETURNING) measurements += Metric.Measurement("${name}StandstillMs", standstill / NANOS_PER_MS)
        }
        return measurements
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
        const val RETURNING = "returning"

        /** The block's keys, in the order they're pressed. */
        val NAMES = listOf("opening", RETURNING, "reopening")

        /** With no new frame for this long, the screen has come. */
        const val QUIET_MS = 500L
    }
}
