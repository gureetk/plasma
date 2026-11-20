// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * What Home's first moments take of its main thread at a cold start (#127), once Plasma's launch
 * screen starts to go ([StartupSplitMetric.READY]). Home is composed in a frame right then, and a
 * little later its rows of movies, series and Continue Watching get their contents: Plasma marks
 * the composition in which each first has them ([ROW_MARKS]). A long frame then is time in which
 * Home is on screen and doesn't answer the remote. From the main thread's frames that begin in the
 * [WITHIN_MS] after ready:
 *
 * - `homeFrameMs`: the frame in which Home is composed: of those that begin within [HOME_MS] of
 *   ready, or hold it, the one with the most work.
 * - `rowsFrameMs` and `rowsFrameWorkMs`: the frame after Home's in which a row first has its
 *   contents (the longest such, where the rows come in more than one), and its work alone; and
 *   `rowsFrameStartMs`, from the launch to its start. All 0 where the rows came in Home's own
 *   frame, or didn't come.
 * - `afterHomeLongestFrameMs` and `afterHomeLongestWorkMs`: the longest frame after Home's own,
 *   whatever is in it, and the most work in one.
 * - `afterHomeHeldMs`: the frames after Home's own that took [LONG_MS] or more, together: how long
 *   Home was held, in however many pieces.
 * - `afterHomeWorkMs` and `afterHomeFrames`: the work in all the frames after Home's own,
 *   together, and how many they were: what was done, in one frame or spread over many.
 * - `rowsMadeMs`: from the launch to the end of the last frame with one of Plasma's marks in it,
 *   [ROW_MARKS] or [BELOW_MADE]: by then what Home's rows show is all made. 0 with no mark.
 *
 * And from before ready, since what the app's composition takes is time before Home shows:
 *
 * - `appFrameMs` and `appFrameWorkMs`: the frame in which the app is composed, which Plasma marks
 *   ([APP_COMPOSED]), and its work alone; and `appFrameStartMs`, from the launch to its start:
 *   less the launch's first frame (`timeToInitialDisplayMs`), how long the app's composition
 *   waited behind the launch screen. All 0 with no mark.
 * - `playerMadeMs` and `playerMakingMs`: from the launch to the player's being made, on whichever
 *   thread ([PLAYER_MADE]), and how long making it took. Both 0 where it wasn't made by then.
 * - `playerFrameStartMs` and `playerFrameMs`: the frame in which the player takes its live
 *   surface, which Plasma marks ([SURFACE_TAKEN]); without the mark, the first frame to begin
 *   once the player is made. From the launch to its start, and how long it took. Before ready
 *   it's under the launch screen; after it, it's a frame of the first screen's. Both 0 where
 *   there's none.
 * - `surfaceMadeMs`, `surfaceFrameMs` and `surfaceFrameWorkMs`: from the launch to the live
 *   surface's views being made, which Plasma marks ([SURFACE_MADE]), and the frame they're made
 *   in, where they join the window: the player's frame where they're made as it comes, the
 *   app's composition frame where they're made ahead of it. All 0 with no mark.
 * - `appToReadyMs` and `appToReadyMainRunningMs`: from the end of the app's composition frame to
 *   ready, and how long the main thread ran in that time: its effects start what the screens read
 *   there, outside any frame. Both 0 with no mark of the app's composition.
 *
 * A frame is its `Choreographer#doFrame` on the main thread, and its work is that less the main
 * thread's waits in it for the drawing thread (`postAndWait`), which where drawing is slow (on an
 * emulator above all) are most of a frame and none of its own doing.
 *
 * Nothing when the trace has no launch of Plasma, or no [StartupSplitMetric.READY] after it, as
 * [StartupSplitMetric]; otherwise every one of them, so that no iteration is left out for one.
 */
@OptIn(ExperimentalMetricApi::class)
internal class RowsFrameMetric : TraceMetric() {
    private class Frame(val start: Long, val end: Long, val work: Long) {
        val time get() = end - start
    }

    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        val app = captureInfo.targetPackageName
        val onMain = "JOIN thread_track ON slice.track_id = thread_track.id JOIN thread USING(utid) JOIN process USING(upid) WHERE process.name = '$app' AND thread.tid = process.pid"
        val launch = traceSession.first("SELECT MIN(ts) AS t FROM slice WHERE name = 'launching: $app'") ?: return emptyList()
        val ready = traceSession.first("SELECT MIN(slice.ts) AS t FROM slice $onMain AND slice.name = '${StartupSplitMetric.READY}' AND slice.ts >= $launch") ?: return emptyList()
        val until = ready + WITHIN_MS * NANOS_PER_MS_LONG

        val waits = traceSession.query("SELECT slice.ts AS ts, slice.dur AS dur FROM slice $onMain AND slice.name = 'postAndWait' AND slice.dur > 0 AND slice.ts >= $launch AND slice.ts < $until")
            .map { it.long("ts") to it.long("dur") }
            .toList()
        // A frame's slice can come twice, begun in the same millisecond: the longer one is the frame.
        val frames = traceSession.query(
            """
            SELECT MIN(slice.ts) AS ts, MAX(slice.dur) AS dur FROM slice $onMain
                AND slice.dur > 0 AND slice.name GLOB 'Choreographer#doFrame [0-9]*' AND slice.ts >= $launch AND slice.ts < $until
            GROUP BY slice.ts / $NANOS_PER_MS_LONG ORDER BY ts
            """.trimIndent(),
        ).map { row ->
            val start = row.long("ts")
            val end = start + row.long("dur")
            Frame(start, end, end - start - waits.sumOf { (at, took) -> if (at >= start && at + took <= end) took else 0L })
        }.toList()
        val marks = traceSession.query("SELECT slice.ts AS ts, slice.name AS name FROM slice $onMain AND slice.name IN (${(ROW_MARKS + BELOW_MADE).joinToString { "'$it'" }}) AND slice.ts >= $launch AND slice.ts < $until ORDER BY slice.ts")
            .map { it.long("ts") to it.nullableString("name").orEmpty() }
            .toList()

        val home = frames.filter { it.end > ready && it.start < ready + HOME_MS * NANOS_PER_MS_LONG }.maxByOrNull { it.work }
        val after = if (home == null) emptyList() else frames.filter { it.start >= home.end }
        val composedAt = traceSession.first("SELECT MIN(slice.ts) AS t FROM slice $onMain AND slice.name = '$APP_COMPOSED' AND slice.ts >= $launch AND slice.ts < $until")
        val composed = composedAt?.let { at -> frames.firstOrNull { at >= it.start && at < it.end } }
        val player = traceSession.query(
            """
            SELECT slice.ts AS ts, slice.dur AS dur FROM slice
                JOIN thread_track ON slice.track_id = thread_track.id JOIN thread USING(utid) JOIN process USING(upid)
            WHERE process.name = '$app' AND slice.name = '$PLAYER_MADE' AND slice.dur > 0 AND slice.ts >= $launch AND slice.ts < $until
            ORDER BY slice.ts LIMIT 1
            """.trimIndent(),
        ).map { it.long("ts") to it.long("dur") }.firstOrNull()
        val surfaceMade = traceSession.first("SELECT MIN(slice.ts) AS t FROM slice $onMain AND slice.name = '$SURFACE_MADE' AND slice.ts >= $launch AND slice.ts < $until")
        val surfaceFrame = surfaceMade?.let { at -> frames.firstOrNull { at >= it.start && at < it.end } }
        val surfaceTaken = traceSession.first("SELECT MIN(slice.ts) AS t FROM slice $onMain AND slice.name = '$SURFACE_TAKEN' AND slice.ts >= $launch AND slice.ts < $until")
        val playerFrame = surfaceTaken?.let { at -> frames.firstOrNull { at >= it.start && at < it.end } }
            ?: player?.let { (at, took) -> frames.firstOrNull { it.start >= at + took } }
        // The main thread's running time between the app's composition and ready, each state's part in between.
        val ranToReady = composed?.takeIf { it.end < ready }?.let { frame ->
            traceSession.first(
                """
                SELECT SUM(MIN(thread_state.ts + thread_state.dur, $ready) - MAX(thread_state.ts, ${frame.end})) AS t
                FROM thread_state JOIN thread USING(utid) JOIN process USING(upid)
                WHERE process.name = '$app' AND thread.tid = process.pid AND thread_state.state = 'Running'
                    AND thread_state.ts < $ready AND thread_state.ts + thread_state.dur > ${frame.end}
                """.trimIndent(),
            )
        }
        fun holds(frame: Frame, names: List<String>) = marks.any { (at, name) -> name in names && at >= frame.start && at < frame.end }
        val rows = after.filter { holds(it, ROW_MARKS) }.maxByOrNull { it.time }
        val made = frames.lastOrNull { holds(it, ROW_MARKS + BELOW_MADE) }
        return listOf(
            "homeFrameMs" to ms(home?.time ?: 0),
            "rowsFrameMs" to ms(rows?.time ?: 0),
            "rowsFrameWorkMs" to ms(rows?.work ?: 0),
            "rowsFrameStartMs" to ms(rows?.let { it.start - launch } ?: 0),
            "afterHomeLongestFrameMs" to ms(after.maxOfOrNull { it.time } ?: 0),
            "afterHomeLongestWorkMs" to ms(after.maxOfOrNull { it.work } ?: 0),
            "afterHomeHeldMs" to ms(after.filter { it.time >= LONG_MS * NANOS_PER_MS_LONG }.sumOf { it.time }),
            "afterHomeWorkMs" to ms(after.sumOf { it.work }),
            "afterHomeFrames" to after.size.toDouble(),
            "rowsMadeMs" to ms(made?.let { it.end - launch } ?: 0),
            "appFrameMs" to ms(composed?.time ?: 0),
            "appFrameWorkMs" to ms(composed?.work ?: 0),
            "appFrameStartMs" to ms(composed?.let { it.start - launch } ?: 0),
            "playerMadeMs" to ms(player?.let { (at, took) -> at + took - launch } ?: 0),
            "playerMakingMs" to ms(player?.second ?: 0),
            "playerFrameStartMs" to ms(playerFrame?.let { it.start - launch } ?: 0),
            "playerFrameMs" to ms(playerFrame?.time ?: 0),
            "surfaceMadeMs" to ms(surfaceMade?.let { it - launch } ?: 0),
            "surfaceFrameMs" to ms(surfaceFrame?.time ?: 0),
            "surfaceFrameWorkMs" to ms(surfaceFrame?.work ?: 0),
            "appToReadyMs" to ms(composed?.takeIf { it.end < ready }?.let { ready - it.end } ?: 0),
            "appToReadyMainRunningMs" to ms(ranToReady ?: 0),
        ).map { (name, value) -> Metric.Measurement(name, value) }
    }

    /** The one value a query gives as `t`, or null. */
    private fun TraceProcessor.Session.first(sql: String): Long? = query(sql).firstOrNull()?.nullableLong("t")

    private fun ms(nanos: Long) = nanos / NANOS_PER_MS

    private companion object {
        /** The compositions in which Home's rows first have their contents, as `LiveHome` marks them. */
        val ROW_MARKS = listOf("Plasma.homeContinuing", "Plasma.homeNewMovies", "Plasma.homeNewSeries")

        /** The composition in which the last of what's below Home's screen edge is made, where it's made piece by piece. */
        const val BELOW_MADE = "Plasma.homeBelowMade"

        /** The app's first composition, as `PlasmaApp` marks it. */
        const val APP_COMPOSED = "Plasma.appComposed"

        /** The player being made, as `AppGraph` marks it. */
        const val PLAYER_MADE = "Plasma.playerMade"

        /** The live surface's views being made, and the player taking them, as `VideoLayer` marks them. */
        const val SURFACE_MADE = "Plasma.liveSurfaceMade"
        const val SURFACE_TAKEN = "Plasma.liveSurfaceTaken"

        /** How long after ready Home's frames are looked at: its rows come within half a second, and a start's trace runs on for three. */
        const val WITHIN_MS = 2_500L

        /** Home is composed in a frame that begins this soon after ready, at the latest. */
        const val HOME_MS = 100L

        /** A frame this long is two refreshes at 60 a second: one the remote and the eye can feel. */
        const val LONG_MS = 32L

        const val NANOS_PER_MS = 1_000_000.0
        const val NANOS_PER_MS_LONG = 1_000_000L
    }
}
