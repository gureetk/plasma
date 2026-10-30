// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.MovieEntity
import ca.gureet.plasma.core.database.SeriesEntity

/**
 * Pictures a provider gives many of its titles: its stand-in for a poster it doesn't have, not a
 * poster (brief 4.13). The owner's server gives every movie the same placeholder.png, and Movies
 * showed a grid of its logo. A poster is a title's own, or shared by its few copies (4K, HD...);
 * one picture on [TITLES] titles in a thousand is a stand-in, and from then on it's marked as one.
 * With Replace Placeholder Posters on, a marked title shows its own poster from its details, or its
 * title set in type; off, the picture as the provider gives it (the owner's #81).
 *
 * Counted a batch of titles at a time, so a catalog is never held whole (6.4), starting from the
 * stand-ins the source's last import found ([seen]). What a batch doesn't show to be a stand-in,
 * the catalog as a whole does, once it's written (`VodDao.markStandInPosters`).
 */
internal class StandIns(seen: Collection<String> = emptyList()) {
    private val known = HashSet(seen)

    /** [batch], each marked by [mark] as a stand-in or not, by what it and those seen before show. */
    fun <T> marked(batch: List<T>, poster: (T) -> String?, mark: (T, Boolean) -> T): List<T> {
        batch.mapNotNull(poster).groupingBy { it }.eachCount().forEach { (picture, titles) -> if (titles >= TITLES) known += picture }
        return batch.map { mark(it, poster(it) in known) }
    }

    fun markedMovies(batch: List<MovieEntity>): List<MovieEntity> = marked(batch, { it.poster }, { movie, standIn -> movie.copy(standIn = standIn) })

    fun markedSeries(batch: List<SeriesEntity>): List<SeriesEntity> = marked(batch, { it.poster }, { series, standIn -> series.copy(standIn = standIn) })

    companion object {
        /** How many titles share a picture for it to be a stand-in. */
        const val TITLES = 10
    }
}
