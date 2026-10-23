// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.ChannelSourceMemoryEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Why a merged channel starts on the candidate it remembers (brief 5.1). */
enum class RememberedBecause(internal val stored: String) {
    /** It took over after a failover and played for ten minutes. */
    Failover("FAILOVER"),

    /** The first candidate failed on three of its last five tunes, and this one played. */
    RepeatedFailures("REPEATED_FAILURES"),

    /** It was picked in Switch Source. */
    User("USER"),
}

/** The candidate a merged channel starts on, and since when (Switch Source shows it, brief 4.35). */
data class RememberedSource(val candidateId: String, val because: RememberedBecause, val learnedAt: Instant)

/**
 * How one tune of a merged channel goes (brief 5.1). [candidates] are in Preferred Quality's
 * order, so the [first] is the channel's own first choice; [order] is what to try this time.
 */
data class TunePlan(
    val channelId: String,
    val candidates: List<String>,
    val order: List<String>,
    val remembered: RememberedSource?,
    /** A week after it was learned, one tune tries the original order again. */
    val recheck: Boolean,
) {
    val first: String get() = candidates.first()

    /**
     * The week's check found the first candidate failing, and the channel goes straight back to
     * the remembered one: no notice for that (brief 5.1).
     */
    fun isQuietSwitch(from: String, to: String): Boolean = recheck && from == first && to == remembered?.candidateId
}

/**
 * Remember What Works per Channel (brief 5.1): a merged channel whose first candidate keeps failing
 * starts on the one that works, and a week later checks the first again. Kept per channel, not per
 * profile, as `ChannelSourceMemory` (6.3).
 */
class SourceMemory(private val db: PlasmaDatabase, private val clock: PlasmaClock) {
    private val dao get() = db.channelSourceMemory()

    /** What's remembered for the merged channel with [candidates], kept current. */
    fun observe(candidates: List<String>): Flow<RememberedSource?> =
        dao.observeAny(candidates).map { rows -> rows.firstNotNullOfOrNull { it.remembered(candidates) } }

    /**
     * How to tune [channelId], a merged channel with [candidates] in Preferred Quality's order: its
     * remembered candidate first, then the rest in order. Once a week, one tune tries the original
     * order, with the remembered candidate straight after the first.
     */
    suspend fun plan(channelId: String, candidates: List<String>): TunePlan {
        val row = row(candidates)
        val remembered = row?.remembered(candidates)
        if (row == null || remembered == null) return TunePlan(channelId, candidates, candidates, remembered = null, recheck = false)
        val first = candidates.first()
        // Preferred Quality or the primary source changed, and the remembered one comes first anyway.
        if (remembered.candidateId == first) return TunePlan(channelId, candidates, candidates, remembered, recheck = false)
        val now = clock.now()
        val due = row.recheckAfter?.let { !now.isBefore(Instant.ofEpochMilli(it)) } == true
        if (!due) return TunePlan(channelId, candidates, listOf(remembered.candidateId) + (candidates - remembered.candidateId), remembered, recheck = false)
        // This is the week's check; the next one is a week on, unless this one clears the memory.
        dao.upsert(row.copy(recheckAfter = now.plus(RECHECK_AFTER).toEpochMilli()))
        val order = listOf(first, remembered.candidateId) + (candidates - first - remembered.candidateId)
        return TunePlan(channelId, candidates, order, remembered, recheck = true)
    }

    /** The first candidate played, or failed, on a tune of [plan]'s channel: its last five are kept. */
    suspend fun firstTried(plan: TunePlan, worked: Boolean) {
        val row = row(plan.candidates) ?: empty(plan.channelId)
        dao.upsert(row.copy(recentResults = (row.recentResults + (if (worked) PLAYED else FAILED)).takeLast(RESULTS_KEPT)))
    }

    /**
     * [candidate] played on a tune whose first candidate failed. When the first has failed on
     * three of its last five tunes, [candidate] is remembered.
     */
    suspend fun playedAfterFirstFailed(plan: TunePlan, candidate: String) {
        val row = row(plan.candidates) ?: return
        if (row.recentResults.count { it == FAILED } >= FAILURES_TO_LEARN) remember(plan, candidate, RememberedBecause.RepeatedFailures)
    }

    /**
     * [candidate] has played for ten minutes on a tune of [plan]'s channel, [afterFailover] when it
     * took over from a source that failed. The week's check finding the first candidate working
     * clears the memory.
     */
    suspend fun playedLong(plan: TunePlan, candidate: String, afterFailover: Boolean) {
        when {
            plan.recheck && candidate == plan.first -> forget(plan.candidates)
            afterFailover -> remember(plan, candidate, RememberedBecause.Failover)
        }
    }

    /** Switch Source: the candidate picked counts as remembered at once (brief 5.1). */
    suspend fun choose(plan: TunePlan, candidate: String) = remember(plan, candidate, RememberedBecause.User)

    /** Forget What Works: the channel goes back to its own order. */
    suspend fun forget(candidates: List<String>) = dao.delete(candidates)

    private suspend fun remember(plan: TunePlan, candidate: String, because: RememberedBecause) {
        if (candidate !in plan.candidates) return
        // The channel's own first choice is no memory at all.
        if (candidate == plan.first) {
            forget(plan.candidates)
            return
        }
        val now = clock.now()
        val row = row(plan.candidates) ?: empty(plan.channelId)
        dao.upsert(
            row.copy(
                preferredChannelId = candidate,
                reason = because.stored,
                learnedAt = now.toEpochMilli(),
                recheckAfter = now.plus(RECHECK_AFTER).toEpochMilli(),
            ),
        )
    }

    /** The channel's row: the one that remembers something, if several match. */
    private suspend fun row(candidates: List<String>): ChannelSourceMemoryEntity? =
        dao.forAny(candidates).sortedByDescending { it.preferredChannelId != null }.firstOrNull()

    private fun empty(channelId: String) = ChannelSourceMemoryEntity(
        mergedChannelId = channelId,
        preferredChannelId = null,
        reason = null,
        learnedAt = null,
        recheckAfter = null,
        recentResults = "",
    )

    private fun ChannelSourceMemoryEntity.remembered(candidates: List<String>): RememberedSource? {
        val preferred = preferredChannelId?.takeIf { it in candidates } ?: return null
        val because = RememberedBecause.entries.firstOrNull { it.stored == reason } ?: RememberedBecause.User
        return RememberedSource(preferred, because, Instant.ofEpochMilli(learnedAt ?: 0))
    }

    companion object {
        /** How long a candidate plays before it counts as working (brief 5.1). */
        val PLAYED_LONG: Duration = Duration.ofMinutes(10)

        /** When the original order is tried again. */
        val RECHECK_AFTER: Duration = Duration.ofDays(7)

        private const val RESULTS_KEPT = 5
        private const val FAILURES_TO_LEARN = 3
        private const val PLAYED = '1'
        private const val FAILED = '0'
    }
}

/**
 * One tune of a merged channel, as Remember What Works per Channel follows it (brief 5.1): the
 * player's reports, in the order they come, become what's kept. Call it from one place at a time.
 */
class TuneMemory(private val memory: SourceMemory, val plan: TunePlan) {
    private var firstFailed = false
    private val tookOver = HashSet<String>()

    /** [candidate]'s source failed, and [next] takes over (failover) if anything does. */
    suspend fun failed(candidate: String, next: String?) {
        if (next != null) tookOver += next
        if (candidate == plan.first) {
            firstFailed = true
            memory.firstTried(plan, worked = false)
        }
    }

    /** [candidate]'s picture is on screen. */
    suspend fun playing(candidate: String) {
        when {
            candidate == plan.first -> memory.firstTried(plan, worked = true)
            firstFailed -> memory.playedAfterFirstFailed(plan, candidate)
        }
    }

    /** [candidate] has played for ten minutes. */
    suspend fun playedLong(candidate: String) = memory.playedLong(plan, candidate, afterFailover = candidate in tookOver)
}
