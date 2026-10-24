// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.CategoryCount
import ca.gureet.plasma.core.database.MovieEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.SeriesEntity
import ca.gureet.plasma.core.database.VodCategoryEntity
import ca.gureet.plasma.core.database.VodKinds
import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** A category of movies or series, with how many it holds. */
data class VodCategory(val id: String, val name: String, val count: Int)

/** A movie in a list (brief 5.9); its plot, cast and file come with its detail page. */
data class Movie(
    /** "sourceId:remoteId". */
    val id: String,
    val name: String,
    val poster: String?,
    val categoryId: String?,
    val added: Instant?,
    /** Out of 10. */
    val rating: Float?,
    val year: Int?,
)

/** A series in a list (brief 5.9); its seasons and episodes come with its detail page. */
data class Series(
    /** "sourceId:remoteId". */
    val id: String,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val categoryId: String?,
    val genre: String?,
    val year: Int?,
    /** Out of 10. */
    val rating: Float?,
    /** When its latest episode arrived. */
    val updated: Instant?,
)

/**
 * The movies and series of the sources that are turned on (brief 4.13, 4.15 and 5.9), as their
 * imports keep them. Lists come a category at a time, so a big catalog never loads whole.
 */
class VodRepository(private val db: PlasmaDatabase, private val clock: PlasmaClock) {
    /** The movie categories, in each source's order, the primary source's first; empty ones are left out. */
    val movieCategories: Flow<List<VodCategory>> = counted(db.vod().observeCategories(VodKinds.MOVIE), db.vod().observeMovieCounts())

    /** The series categories, as [movieCategories]. */
    val seriesCategories: Flow<List<VodCategory>> = counted(db.vod().observeCategories(VodKinds.SERIES), db.vod().observeSeriesCounts())

    fun moviesIn(categoryId: String): Flow<List<Movie>> = db.vod().observeMoviesIn(categoryId).map { list -> list.map { it.toMovie() } }

    fun seriesIn(categoryId: String): Flow<List<Series>> = db.vod().observeSeriesIn(categoryId).map { list -> list.map { it.toSeries() } }

    /** Recently Added (4.13): movies added in the last 30 days, newest first, at most 300. */
    fun recentMovies(): Flow<List<Movie>> =
        db.vod().observeRecentMovies(since = clock.now().minus(RECENT).toEpochMilli(), limit = RECENT_LIMIT).map { list -> list.map { it.toMovie() } }

    /** New Episodes (4.15): series with an episode added in the last 14 days, latest first, at most 300. */
    fun newEpisodes(): Flow<List<Series>> =
        db.vod().observeRecentSeries(since = clock.now().minus(NEW_EPISODES).toEpochMilli(), limit = RECENT_LIMIT).map { list -> list.map { it.toSeries() } }

    /** Movies whose name has a word starting with each word of [query] (Search, brief 5.10). */
    suspend fun searchMovies(query: String, limit: Int = SEARCH_LIMIT): List<Movie> =
        SearchRepository.matchOf(query)?.let { match -> db.vod().searchMovies(match, limit).map { it.toMovie() } }.orEmpty()

    /** Series whose name has a word starting with each word of [query]. */
    suspend fun searchSeries(query: String, limit: Int = SEARCH_LIMIT): List<Series> =
        SearchRepository.matchOf(query)?.let { match -> db.vod().searchSeries(match, limit).map { it.toSeries() } }.orEmpty()

    suspend fun movie(id: String): Movie? = db.vod().movie(id)?.toMovie()

    suspend fun series(id: String): Series? = db.vod().series(id)?.toSeries()

    private fun counted(categories: Flow<List<VodCategoryEntity>>, counts: Flow<List<CategoryCount>>): Flow<List<VodCategory>> =
        combine(categories, counts) { list, counted ->
            val byId = counted.associate { it.categoryId to it.count }
            list.mapNotNull { category -> byId[category.id]?.takeIf { it > 0 }?.let { VodCategory(category.id, category.name, it) } }
        }

    private fun MovieEntity.toMovie() = Movie(id, name, poster, categoryId, addedAt?.let(Instant::ofEpochMilli), rating, year)

    private fun SeriesEntity.toSeries() = Series(id, name, poster, backdrop, categoryId, genre, year, rating, updatedAt?.let(Instant::ofEpochMilli))

    private companion object {
        val RECENT: Duration = Duration.ofDays(30)
        val NEW_EPISODES: Duration = Duration.ofDays(14)
        const val RECENT_LIMIT = 300
        const val SEARCH_LIMIT = 100
    }
}
