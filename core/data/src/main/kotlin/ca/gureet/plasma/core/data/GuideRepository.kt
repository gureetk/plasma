// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

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
            .map { list -> list.groupBy({ it.epgKey }, { it.toProgramme() }) }

    suspend fun forChannel(epgKey: String, from: Instant, to: Instant): List<Programme> =
        db.programmes().forKey(epgKey, from.toEpochMilli(), to.toEpochMilli()).map { it.toProgramme() }

    companion object {
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
)
