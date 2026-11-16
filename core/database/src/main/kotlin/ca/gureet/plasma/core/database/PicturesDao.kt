// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.Dao
import androidx.room.Query

/** A server the pictures Plasma shows are on (#228): how many of them, and the first and last by address. */
data class PictureServerRow(
    /** The server as the addresses name it, in lower case, with its port if they give one. */
    val server: String,
    val pictures: Int,
    val firstPicture: String,
    val lastPicture: String,
)

/** A title's poster, as far as looking for its own goes (#228): the catalog's, and one found in its details. */
data class PosterLook(
    val id: String,
    val sourceId: String,
    val poster: String?,
    val standIn: Boolean,
    val art: String?,
    val artStandIn: Boolean,
    val artCheckedAt: Long?,
)

/** How many titles have their own poster from their details, and how many were looked for without one (#228). */
data class FoundPosters(val moviesFound: Int, val moviesNone: Int, val seriesFound: Int, val seriesNone: Int)

/**
 * The pictures Plasma shows, taken together (#228): every poster, found poster and logo, by the
 * server it's on, for checking the servers; and the titles a category shows first, for finding
 * the posters theirs don't have.
 */
@Dao
abstract class PicturesDao {
    /**
     * The servers the catalogs' posters, the posters found in titles' details, and the channels'
     * and the guide's logos are on, those with the most pictures first, at most [most].
     */
    @Query(
        """
        SELECT server, COUNT(*) AS pictures, MIN(url) AS firstPicture, MAX(url) AS lastPicture FROM (
            SELECT lower(substr(rest, 1, instr(rest || '/', '/') - 1)) AS server, url FROM (
                SELECT substr(url, instr(url, '://') + 3) AS rest, url FROM (
                    SELECT poster AS url FROM movies WHERE poster IS NOT NULL
                    UNION ALL SELECT art FROM movies WHERE art IS NOT NULL
                    UNION ALL SELECT poster FROM series WHERE poster IS NOT NULL
                    UNION ALL SELECT art FROM series WHERE art IS NOT NULL
                    UNION ALL SELECT logo FROM channels WHERE logo IS NOT NULL
                    UNION ALL SELECT guideLogo FROM channels WHERE guideLogo IS NOT NULL
                ) WHERE instr(url, '://') > 0
            )
        ) WHERE server != '' GROUP BY server ORDER BY pictures DESC LIMIT :most
        """,
    )
    abstract suspend fun servers(most: Int): List<PictureServerRow>

    /** [categoryId]'s movies from the [offset]th, [limit] of them, in its order. */
    @Query(
        """
        SELECT m.id, m.sourceId, m.poster, m.standIn, m.art, m.artStandIn, m.artCheckedAt FROM movies m
        JOIN sources s ON s.id = m.sourceId WHERE s.enabled = 1 AND m.categoryId = :categoryId
        ORDER BY m.position LIMIT :limit OFFSET :offset
        """,
    )
    abstract suspend fun moviesIn(categoryId: String, offset: Int, limit: Int): List<PosterLook>

    /** [categoryId]'s series from the [offset]th, [limit] of them, in its order. */
    @Query(
        """
        SELECT t.id, t.sourceId, t.poster, t.standIn, t.art, t.artStandIn, t.artCheckedAt FROM series t
        JOIN sources s ON s.id = t.sourceId WHERE s.enabled = 1 AND t.categoryId = :categoryId
        ORDER BY t.position LIMIT :limit OFFSET :offset
        """,
    )
    abstract suspend fun seriesIn(categoryId: String, offset: Int, limit: Int): List<PosterLook>

    /** Recently Added's movies, added since [since], from the [offset]th, [limit] of them, the newest first. */
    @Query(
        """
        SELECT m.id, m.sourceId, m.poster, m.standIn, m.art, m.artStandIn, m.artCheckedAt FROM movies m
        JOIN sources s ON s.id = m.sourceId WHERE s.enabled = 1 AND m.addedAt >= :since
        ORDER BY m.addedAt DESC LIMIT :limit OFFSET :offset
        """,
    )
    abstract suspend fun recentMovies(since: Long, offset: Int, limit: Int): List<PosterLook>

    /** New Episodes' series, with an episode since [since], from the [offset]th, [limit] of them, the latest first. */
    @Query(
        """
        SELECT t.id, t.sourceId, t.poster, t.standIn, t.art, t.artStandIn, t.artCheckedAt FROM series t
        JOIN sources s ON s.id = t.sourceId WHERE s.enabled = 1 AND t.updatedAt >= :since
        ORDER BY t.updatedAt DESC LIMIT :limit OFFSET :offset
        """,
    )
    abstract suspend fun recentSeries(since: Long, offset: Int, limit: Int): List<PosterLook>

    /** How the lookups for titles' own posters have gone, all told, for Export Diagnostics. */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM movies WHERE art IS NOT NULL AND artStandIn = 0) AS moviesFound,
            (SELECT COUNT(*) FROM movies WHERE artCheckedAt IS NOT NULL AND (art IS NULL OR artStandIn = 1)) AS moviesNone,
            (SELECT COUNT(*) FROM series WHERE art IS NOT NULL AND artStandIn = 0) AS seriesFound,
            (SELECT COUNT(*) FROM series WHERE artCheckedAt IS NOT NULL AND (art IS NULL OR artStandIn = 1)) AS seriesNone
        """,
    )
    abstract suspend fun foundPosters(): FoundPosters
}
