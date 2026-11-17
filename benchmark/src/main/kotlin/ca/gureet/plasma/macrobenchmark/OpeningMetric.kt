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
 *   or from one frame to the next, up to the screen's first rest. While a screen is being made,
 *   nothing else is drawn.
 * - `…Ms`: from the key to the frame that ended that standstill, which is the new screen's first.
 * - `…LongestGapMs`: the longest time between two frames in all of the [WITHIN_MS], rests and all,
 *   which is what `…StandstillMs` was before it knew a rest: where the two differ, the screen came
 *   to rest and drew again later.
 * - `…WorkMs`: the main thread's time in the frames it made, all together.
 *
 * A screen at rest isn't standing still. Once a screen has come, it draws again when something
 * changes (a clock's minute, a picture arriving), and the wait before that isn't the key's: so
 * what the key brought is taken to be over at the first frame whose making began after the main
 * thread had slept for [REST_MS] or more with no frame on its way. A main thread that waits that
 * long for something else, outside any frame, reads as a rest too: `…LongestGapMs` still has it.
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
        check(keys.size >= NAMES.size) { "${keys.size} keys in the measured block, where an opening, a return and a second opening are ${NAMES.size} at least" }
        val shown = shownFrames(traceSession, captureInfo.targetPackageName).filter { it.present in block }
        if (shown.isEmpty()) return emptyList()
        val asleep = mainThreadAsleep(traceSession, captureInfo.targetPackageName)

        val measurements = mutableListOf<Metric.Measurement>()
        // The first key opens, the second goes back, and the last, once focus is on the tile again, reopens.
        for ((name, index) in NAMES.zip(listOf(0, 1, keys.lastIndex))) {
            val key = keys[index]
            val until = minOf(keys.getOrNull(index + 1) ?: Long.MAX_VALUE, key + WITHIN_MS * NANOS_PER_MS_LONG)
            var last = key
            var standstill = 0L
            var endedAt = key
            var longestGap = 0L
            var rested = false
            var work = 0L
            for (frame in shown) {
                if (frame.present <= key) continue
                if (frame.present > until) break
                val gap = frame.present - last
                if (gap > longestGap) longestGap = gap
                // Nothing was on its way to the screen and the main thread slept: the screen was at rest.
                if (!rested && frame.frameStart - last >= REST_MS * NANOS_PER_MS_LONG) {
                    rested = asleep.sumOf { (from, to) -> maxOf(0L, minOf(to, frame.frameStart) - maxOf(from, last)) } >= REST_MS * NANOS_PER_MS_LONG
                }
                if (!rested && gap > standstill) {
                    standstill = gap
                    endedAt = frame.present
                }
                last = frame.present
                if (frame.frameStart >= key) work += frame.frameEnd - frame.frameStart
            }
            check(last > key) { "No frame was shown after the $name key" }
            measurements += Metric.Measurement("${name}Ms", (endedAt - key) / NANOS_PER_MS)
            measurements += Metric.Measurement("${name}StandstillMs", standstill / NANOS_PER_MS)
            measurements += Metric.Measurement("${name}LongestGapMs", longestGap / NANOS_PER_MS)
            measurements += Metric.Measurement("${name}WorkMs", work / NANOS_PER_MS)
        }
        return measurements
    }

    /**
     * The stretches the app's main thread slept, in the trace's order: waiting for its next
     * message, or for anything else. Empty where the trace has no scheduling.
     */
    private fun mainThreadAsleep(session: TraceProcessor.Session, packageName: String): List<Pair<Long, Long>> =
        session.query(
            """
            SELECT thread_state.ts AS ts, thread_state.ts + thread_state.dur AS finish FROM thread_state
                JOIN thread USING(utid)
                JOIN process USING(upid)
            WHERE process.name = '$packageName' AND thread.tid = process.pid AND thread_state.state = 'S' AND thread_state.dur > 0
            ORDER BY thread_state.ts
            """.trimIndent(),
        ).map { it.long("ts") to it.long("finish") }.toList()

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
        const val NANOS_PER_MS_LONG = 1_000_000L

        /** The block's first key, its second and its last. */
        val NAMES = listOf("opening", "returning", "reopening")

        /** How long after a key its screen is looked at: longer than one takes to come, shorter than the block's rests. */
        const val WITHIN_MS = 4_000L

        /**
         * The main thread's sleep, with no frame on its way, that makes a rest: longer than the
         * waits within a screen's coming (a fade's frames are a refresh apart, and what arrives
         * late arrives within a few), shorter than a clock's or a ring's turns are apart.
         */
        const val REST_MS = 250L
    }
}
