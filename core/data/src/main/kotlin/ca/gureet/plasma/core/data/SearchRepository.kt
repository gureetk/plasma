// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.SearchHistoryEntity
import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.flow.Flow

/** Search over the guide, and the searches someone made (brief 5.10). */
class SearchRepository(
    private val db: PlasmaDatabase,
    private val clock: PlasmaClock,
    private val profileId: String = DEFAULT_PROFILE,
) {
    /**
     * Programs whose title or subtitle has a word starting with each word of [query], ending after
     * [from] and starting before [to]: what's on now first, then the nearest to now.
     */
    suspend fun programmes(query: String, from: Instant, to: Instant, limit: Int = LIMIT): List<Programme> {
        val match = matchOf(query) ?: return emptyList()
        return db.search().programmes(match, from.toEpochMilli(), to.toEpochMilli(), clock.now().toEpochMilli(), limit).map { it.toProgramme() }
    }

    /** The latest searches a result was opened from, newest first. */
    val recent: Flow<List<String>> = db.searchHistory().observe(profileId, KEEP)

    /** Keeps [query] among the recent searches (the last [KEEP]). */
    suspend fun remember(query: String) {
        val text = query.trim().replace(SPACES, " ").lowercase(Locale.ROOT)
        if (text.isEmpty()) return
        db.searchHistory().add(SearchHistoryEntity(profileId, text, clock.now().toEpochMilli()), KEEP)
    }

    companion object {
        const val KEEP = 10
        const val LIMIT = 300
        private val SPACES = Regex("\\s+")
        private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

        /**
         * The FTS4 query for [query]: each word as the start of a word, so "night st" finds
         * "Endurance 24: Night Stint". Anything but letters and digits separates words, which also
         * keeps FTS's own syntax out. Null when there's nothing to look for.
         */
        fun matchOf(query: String): String? {
            val words = query.lowercase(Locale.ROOT).split(NOT_WORD).filter { it.isNotEmpty() }
            return words.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }
        }
    }
}
