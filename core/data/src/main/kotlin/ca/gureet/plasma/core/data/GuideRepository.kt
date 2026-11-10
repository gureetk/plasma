// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import android.os.Trace
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.ProgrammeEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The guide, for the screens (brief 5.3). */
class GuideRepository(private val db: PlasmaDatabase) {
    /** Everything on between [from] and [to], by guide channel, each in time order. */
    fun window(from: Instant, to: Instant): Flow<Map<String, List<Programme>>> =
        db.programmes().observeWindow(from.toEpochMilli(), to.toEpochMilli())
            // Writes elsewhere in the table (an import in progress) re-run the query; same rows, no change.
            .distinctUntilChanged()
            .map { list ->
                // Timed while the system traces, for the benchmarks' cold start (#127).
                Trace.beginSection(WINDOW_SECTION)
                try {
                    list.groupBy({ it.epgKey }, { it.toProgramme() })
                } finally {
                    Trace.endSection()
                }
            }

    /** When the guide runs out: the end of its last programme, or null with no guide at all. */
    val latestEnd: Flow<Instant?> = db.programmes().observeLatestEnd().map { it?.let(Instant::ofEpochMilli) }.distinctUntilChanged()

    suspend fun forChannel(epgKey: String, from: Instant, to: Instant): List<Programme> =
        db.programmes().forKey(epgKey, from.toEpochMilli(), to.toEpochMilli()).map { it.toProgramme() }

    /** What starts between [from] and [to] titled [title], whatever its case, on any channel (brief 5.6). */
    suspend fun titled(title: String, from: Instant, to: Instant): List<Programme> =
        db.programmes().titled(title, from.toEpochMilli(), to.toEpochMilli()).map { it.toProgramme() }

    companion object {
        /** The trace section for sorting the guide's window by channel, which the benchmark module looks for. */
        private const val WINDOW_SECTION = "Plasma.guideWindow"

        /** What's on at [at], and what follows it, from a channel's programmes in time order. */
        fun nowNext(programmes: List<Programme>, at: Instant): NowNext {
            val index = programmes.indexOfFirst { it.isOn(at) }
            if (index >= 0) return NowNext(programmes[index], programmes.getOrNull(index + 1))
            return NowNext(null, programmes.firstOrNull { it.start.isAfter(at) })
        }
    }
}

internal fun ProgrammeEntity.toProgramme() = Programme(
    epgKey = epgKey,
    start = Instant.ofEpochMilli(startAt),
    end = Instant.ofEpochMilli(endAt),
    title = title,
    subtitle = subtitle,
    description = description,
    category = category,
    icon = icon,
    season = season,
    episode = episode,
    isNew = isNew,
    rating = rating,
)
