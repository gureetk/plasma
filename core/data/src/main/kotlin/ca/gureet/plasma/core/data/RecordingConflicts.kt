// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

/**
 * Conflicts (brief 5.6): recordings coming up, or being written, that overlap with more of them
 * than their source's connection limit allows at once. Each conflict is the run of overlapping
 * recordings it's in, soonest first; a source without a limit has none.
 */
object RecordingConflicts {
    /**
     * The conflicts among [recordings], given each source's connection limit by its id
     * ([limitOf]): a channel's source is the part of its id before the colon.
     */
    fun of(recordings: List<Recording>, limitOf: (sourceId: String) -> Int?): List<Conflict> {
        val live = recordings.filter { it.status == RecordingStatus.Scheduled || it.status == RecordingStatus.Recording }
        val conflicts = ArrayList<Conflict>()
        for ((sourceId, ofSource) in live.groupBy { it.channelId.substringBefore(':') }) {
            val limit = limitOf(sourceId) ?: continue
            // Runs of recordings that overlap one another, one after the other.
            var run = ArrayList<Recording>()
            var runEnd = Long.MIN_VALUE
            for (recording in ofSource.sortedBy { it.from }) {
                if (run.isNotEmpty() && recording.from.toEpochMilli() >= runEnd) {
                    if (mostAtOnce(run) > limit) conflicts += Conflict(run, limit)
                    run = ArrayList()
                }
                run += recording
                runEnd = maxOf(runEnd, recording.until.toEpochMilli())
            }
            if (run.isNotEmpty() && mostAtOnce(run) > limit) conflicts += Conflict(run, limit)
        }
        return conflicts.sortedBy { it.recordings.first().from }
    }

    /** The most of [recordings] that overlap at any one moment. */
    private fun mostAtOnce(recordings: List<Recording>): Int {
        // Starts and ends in time order; an end before a start at the same moment.
        val moments = recordings.flatMap { listOf(it.from.toEpochMilli() to 1, it.until.toEpochMilli() to -1) }
            .sortedWith(compareBy({ it.first }, { it.second }))
        var now = 0
        var most = 0
        for ((_, change) in moments) {
            now += change
            most = maxOf(most, now)
        }
        return most
    }

    /** One conflict: the [recordings] that overlap, more than [limit] at once at some moment. */
    data class Conflict(val recordings: List<Recording>, val limit: Int)
}
