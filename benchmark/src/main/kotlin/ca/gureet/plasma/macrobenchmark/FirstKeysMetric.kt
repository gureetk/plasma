// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * The first keys as Home shows at a cold start (#127): what the remote gets while Home's rows are
 * still coming in. A frame that holds the main thread holds the remote too, and Plasma's own
 * section for a key ([KEY_TO_DRAW]) starts only once the main thread takes the key up, so Plasma
 * also writes how long each key waited for that ([KEY_WAITED], named with the milliseconds since
 * the key's own time). The test presses [keys] keys, and each has its own part: the first comes
 * while Home is being composed, and each one after it moves something else.
 *
 * - `firstKeysCount`: how many keys there were.
 * - `firstKeysWaitedAverageMs` and `firstKeysWaitedMaxMs`: a key's wait for the main thread.
 * - `firstKeysFromPressAverageMs` and `firstKeysFromPressMaxMs`: from a key's press to the end of
 *   the drawing of the frame it changes: its wait, then its section taken on to the end of that
 *   frame's `DrawFrame` on the RenderThread, as [KeyToDrawMetric] takes it.
 * - `firstKeysWaited1Ms`, `firstKeysFromPress1Ms` and so on to the last: the same two for each key
 *   by its place among the keys (0 for one the trace doesn't have).
 * - `firstKeysLongestFrameMs` and `firstKeysHeldMs`: of the main thread's frames that begin once
 *   the first key has been taken up, to [AFTER_LAST_MS] after the last key, the longest, and those
 *   of [LONG_MS] and more together. The frame the first key waited for, Home's own, isn't one of
 *   them: [RowsFrameMetric] has it.
 * - `firstKeysHomeMoves`: how many times Home went somewhere else after it was first composed
 *   ([HOME_GOING_TO]): the keys that took focus to another row and moved the page. It says the
 *   keys landed as the test means them to: the same number in every iteration, none for keys
 *   along the dock.
 * - `firstKeyAfterReadyMs`: from ready ([StartupSplitMetric.READY]) to the first key's press: when
 *   the keys began, which the others are read by (0 without a ready).
 *
 * It fails the benchmark when the trace has no key, since the test's keys are what it's for.
 */
@OptIn(ExperimentalMetricApi::class)
internal class FirstKeysMetric(private val keys: Int) : TraceMetric() {
    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        val app = captureInfo.targetPackageName
        val onMain = "JOIN thread_track ON slice.track_id = thread_track.id JOIN thread USING(utid) JOIN process USING(upid) WHERE process.name = '$app' AND thread.tid = process.pid"
        // Each key's section, and the end of the first frame the RenderThread starts drawing after it.
        val pressed = traceSession.query(
            """
            SELECT key.ts AS ts, key.dur AS dur, (
                SELECT MIN(frame.ts + frame.dur) FROM slice frame
                    JOIN thread_track ON frame.track_id = thread_track.id
                    JOIN thread USING(utid)
                    JOIN process USING(upid)
                WHERE frame.name LIKE 'DrawFrame%' AND frame.dur > 0 AND thread.name = 'RenderThread'
                    AND process.name = '$app' AND frame.ts >= key.ts + key.dur
            ) AS drawn
            FROM slice key
            WHERE key.name = '$KEY_TO_DRAW' AND key.dur > 0
            ORDER BY key.ts
            """.trimIndent(),
        ).map { Triple(it.long("ts"), it.long("dur"), it.nullableLong("drawn")) }.toList()
        check(pressed.isNotEmpty()) { "No $KEY_TO_DRAW sections in the trace: the test's keys didn't reach Plasma" }
        // Each key's wait, in the order the keys came: written right before the key's own section starts.
        val waits = traceSession.query("SELECT slice.name AS name FROM slice $onMain AND slice.name GLOB '$KEY_WAITED *' ORDER BY slice.ts")
            .map { (it.nullableString("name").orEmpty().substringAfterLast(' ').toLongOrNull() ?: 0L) * NANOS_PER_MS_LONG }
            .toList()
        val waited = pressed.indices.map { waits.getOrElse(it) { 0L } }
        // A key with no frame drawn after it has no time from its press: it's left out of the
        // average, and 0 in its own place.
        val fromPress = pressed.mapIndexed { i, (ts, _, drawn) -> drawn?.let { waited[i] + it - ts } }
        val from = pressed.first().first - waited.first()
        val taken = pressed.first().first
        val until = pressed.last().first + AFTER_LAST_MS * NANOS_PER_MS_LONG
        // A frame's slice can come twice, begun in the same millisecond: the longer one is the frame.
        val frames = traceSession.query(
            """
            SELECT MIN(slice.ts) AS ts, MAX(slice.dur) AS dur FROM slice $onMain
                AND slice.dur > 0 AND slice.name GLOB 'Choreographer#doFrame [0-9]*' AND slice.ts >= $taken AND slice.ts < $until
            GROUP BY slice.ts / $NANOS_PER_MS_LONG ORDER BY ts
            """.trimIndent(),
        ).map { it.long("dur") }.toList()
        val ready = traceSession.query("SELECT MIN(slice.ts) AS t FROM slice $onMain AND slice.name = '${StartupSplitMetric.READY}'").firstOrNull()?.nullableLong("t")
        // Home says where it's going as it's first composed, and again each time that changes.
        val goings = traceSession.query("SELECT COUNT(*) AS n FROM slice $onMain AND slice.name GLOB '$HOME_GOING_TO *'").firstOrNull()?.long("n") ?: 0L
        val took = fromPress.filterNotNull()
        val each = (0 until keys).flatMap { i ->
            listOf(
                "firstKeysWaited${i + 1}Ms" to ms(waited.getOrElse(i) { 0L }.toDouble()),
                "firstKeysFromPress${i + 1}Ms" to ms(fromPress.getOrNull(i)?.toDouble() ?: 0.0),
            )
        }
        return (
            listOf(
                "firstKeysCount" to pressed.size.toDouble(),
                "firstKeysWaitedAverageMs" to ms(waited.average()),
                "firstKeysWaitedMaxMs" to ms(waited.max().toDouble()),
                "firstKeysFromPressAverageMs" to ms(if (took.isEmpty()) 0.0 else took.average()),
                "firstKeysFromPressMaxMs" to ms(took.maxOrNull()?.toDouble() ?: 0.0),
                "firstKeysLongestFrameMs" to ms(frames.maxOrNull()?.toDouble() ?: 0.0),
                "firstKeysHeldMs" to ms(frames.filter { it >= LONG_MS * NANOS_PER_MS_LONG }.sum().toDouble()),
                "firstKeysHomeMoves" to (goings - 1).coerceAtLeast(0L).toDouble(),
                "firstKeyAfterReadyMs" to ms(ready?.let { (from - it).toDouble() } ?: 0.0),
            ) + each
        ).map { (name, value) -> Metric.Measurement(name, value) }
    }

    private fun ms(nanos: Double) = nanos / NANOS_PER_MS

    private companion object {
        /** How long after the last key the frames still count: its motion, and what follows it. */
        const val AFTER_LAST_MS = 400L

        /** A frame this long is two refreshes at 60 a second: one the remote and the eye can feel. */
        const val LONG_MS = 32L

        const val NANOS_PER_MS = 1_000_000.0
        const val NANOS_PER_MS_LONG = 1_000_000L
    }
}
