// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.MovieEntity
import ca.gureet.plasma.core.database.SeriesEntity

/**
 * Pictures a provider gives many of its titles: its stand-in for a poster it doesn't have, not a
 * poster (brief 4.13). The owner's server gives every movie the same placeholder.png, and Movies
 * showed a grid of its logo. A poster is a title's own, or shared by its few copies (4K, HD...);
 * one picture on [TITLES] titles in a thousand is a stand-in, and from then on it's left out, so
 * the poster shows as missing and its title is set in type instead.
 *
 * Counted a batch of titles at a time, so a catalog is never held whole (6.4). What a batch doesn't
 * show to be a stand-in, the catalog as a whole does, once it's written
 * (`VodDao.forgetStandInPosters`).
 */
internal class StandIns {
    private val known = HashSet<String>()

    /** A copy of [batch] with the stand-ins it shows, and those seen before, taken off by [without]. */
    fun <T> cleared(batch: List<T>, poster: (T) -> String?, without: (T) -> T): List<T> {
        batch.mapNotNull(poster).groupingBy { it }.eachCount().forEach { (picture, titles) -> if (titles >= TITLES) known += picture }
        return batch.map { if (poster(it) in known) without(it) else it }
    }

    fun clearedMovies(batch: List<MovieEntity>): List<MovieEntity> = cleared(batch, { it.poster }, { it.copy(poster = null) })

    fun clearedSeries(batch: List<SeriesEntity>): List<SeriesEntity> = cleared(batch, { it.poster }, { it.copy(poster = null) })

    companion object {
        /** How many titles share a picture for it to be a stand-in. */
        const val TITLES = 10
    }
}
