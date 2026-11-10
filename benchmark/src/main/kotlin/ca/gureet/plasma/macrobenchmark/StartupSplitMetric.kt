// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * Where a cold start's time goes (brief 6.7: 1.5 s to an interactive Home), from Android starting
 * to launch Plasma to the end of Plasma's [READY] section, which it opens as its launch screen
 * starts to go:
 * - `startupReadyMs`, that whole time, and `startupFirstWorldMs`, the time to the end of its first
 *   [LIVE_WORLD] section, once its channels and guide were in (0 without one);
 * - how its main thread spent it: running, waiting for a processor (`Runnable`), asleep, as on a
 *   lock, another thread or another app (`Sleeping`), and waiting on storage (`Io`);
 * - the processor time all of Plasma's threads took (`startupCpuMs`), and among it its
 *   RenderThread's, its database's (Room's `arch_disk_io` threads), its coroutines' (Kotlin's
 *   `DefaultDispatcher` threads), the garbage collector's and the compiler's;
 * - how long its own sections took: the guide's window sorted by channel ([GUIDE_WINDOW]), the
 *   channel lineup worked out ([LINEUP]) and the live worlds made.
 *
 * Nothing when the trace has no launch of Plasma, or no [READY] after it.
 */
@OptIn(ExperimentalMetricApi::class)
internal class StartupSplitMetric : TraceMetric() {
    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        val app = captureInfo.targetPackageName
        val inApp = "JOIN thread_track ON slice.track_id = thread_track.id JOIN thread USING(utid) JOIN process USING(upid) WHERE process.name = '$app'"
        val start = traceSession.first("SELECT MIN(ts) AS t FROM slice WHERE name = 'launching: $app'") ?: return emptyList()
        val end = traceSession.first("SELECT MIN(slice.ts + slice.dur) AS t FROM slice $inApp AND slice.name = '$READY' AND slice.ts >= $start")
            ?: return emptyList()
        val world = traceSession.first("SELECT MIN(slice.ts + slice.dur) AS t FROM slice $inApp AND slice.name = '$LIVE_WORLD' AND slice.ts >= $start")
        // Each row's part between the two.
        fun within(table: String) = "MIN($table.ts + $table.dur, $end) - MAX($table.ts, $start)"
        val states = traceSession.durations(
            """
            SELECT thread_state.state AS name, SUM(${within("thread_state")}) AS dur
            FROM thread_state JOIN thread USING(utid) JOIN process USING(upid)
            WHERE process.name = '$app' AND thread.tid = process.pid AND thread_state.ts < $end AND thread_state.ts + thread_state.dur > $start
            GROUP BY thread_state.state
            """.trimIndent(),
        )
        val running = traceSession.durations(
            """
            SELECT thread.name AS name, SUM(${within("sched")}) AS dur
            FROM sched JOIN thread USING(utid) JOIN process USING(upid)
            WHERE process.name = '$app' AND sched.ts < $end AND sched.ts + sched.dur > $start
            GROUP BY thread.name
            """.trimIndent(),
        )
        val sections = traceSession.durations(
            """
            SELECT slice.name AS name, SUM(slice.dur) AS dur FROM slice $inApp
                AND slice.name IN ('$GUIDE_WINDOW', '$LINEUP', '$LIVE_WORLD') AND slice.ts >= $start AND slice.ts < $end
            GROUP BY slice.name
            """.trimIndent(),
        )
        fun state(match: (String) -> Boolean) = ms(states.filterKeys(match).values.sum())
        fun threads(match: (String) -> Boolean) = ms(running.filterKeys(match).values.sum())
        return listOf(
            "startupReadyMs" to ms(end - start),
            "startupFirstWorldMs" to (world?.let { ms(it - start) } ?: 0.0),
            "startupMainRunningMs" to state { it == "Running" },
            "startupMainRunnableMs" to state { it.startsWith("R") && it != "Running" },
            "startupMainSleepingMs" to state { it == "S" },
            "startupMainIoMs" to state { it.startsWith("D") },
            "startupCpuMs" to threads { true },
            "startupRenderThreadCpuMs" to threads { it == "RenderThread" },
            "startupDatabaseCpuMs" to threads { it.startsWith("arch_disk_io") },
            "startupCoroutinesCpuMs" to threads { it.startsWith("DefaultDispatch") },
            "startupGcCpuMs" to threads { it == "HeapTaskDaemon" },
            "startupJitCpuMs" to threads { it.startsWith("Jit thread pool") },
            "startupGuideWindowMs" to ms(sections[GUIDE_WINDOW] ?: 0),
            "startupLineupMs" to ms(sections[LINEUP] ?: 0),
            "startupLiveWorldMs" to ms(sections[LIVE_WORLD] ?: 0),
        ).map { (name, value) -> Metric.Measurement(name, value) }
    }

    /** The one value a query gives as `t`, or null. */
    private fun TraceProcessor.Session.first(sql: String): Long? = query(sql).firstOrNull()?.nullableLong("t")

    /**
     * Each `name` and its `dur` from a query, nothing if the trace doesn't have what it reads: a
     * metric that throws would fail the benchmark.
     */
    private fun TraceProcessor.Session.durations(sql: String): Map<String, Long> = runCatching {
        query(sql).associate { it.nullableString("name").orEmpty() to (it.nullableLong("dur") ?: 0L) }
    }.getOrDefault(emptyMap())

    private fun ms(nanos: Long) = nanos / NANOS_PER_MS

    companion object {
        /** Plasma's own sections, as `MainActivity`, `liveWorld`, `GuideRepository` and `ChannelRepository` open them. */
        const val READY = "Plasma.ready"
        const val LIVE_WORLD = "Plasma.liveWorld"
        const val GUIDE_WINDOW = "Plasma.guideWindow"
        const val LINEUP = "Plasma.lineup"

        private const val NANOS_PER_MS = 1_000_000.0
    }
}
