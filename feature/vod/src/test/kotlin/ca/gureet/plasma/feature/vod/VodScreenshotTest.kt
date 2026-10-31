// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Movies, Series and their detail pages against boards 13–16 (docs/design/screens). */
class VodScreenshotTest : ScreenshotTest() {
    @Test fun movies() {
        screen { VodBrowseScreen(VodSamples.movies, VodBrowseActions()) }
        capture("13-movies")
    }

    /**
     * A provider without posters (its own stand-in left out): each title set in type on its plate,
     * and the focused one's name, too long for a line, cut short under it. No board draws it.
     */
    @Test fun moviesWithoutPosters() {
        val base = VodSamples.movies
        val titles = base.titles.mapValues { (_, list) ->
            list.map { title ->
                title.copy(poster = null, title = if (title.id == "lighthouse") "The Last Lighthouse: The Keeper's Winter (2025) (Extended)" else title.title)
            }
        }
        screen { VodBrowseScreen(base.copy(titles = titles), VodBrowseActions()) }
        capture("13-movies-without-posters")
    }

    @Test fun movieDetail() {
        screen { MovieDetailScreen(checkNotNull(VodSamples.movieDetail("lighthouse")), MovieDetailActions()) }
        capture("14-movie-detail")
    }

    @Test fun series() {
        screen { VodBrowseScreen(VodSamples.series, VodBrowseActions()) }
        capture("15-series")
    }

    @Test fun seriesDetail() {
        screen { SeriesDetailScreen(checkNotNull(VodSamples.seriesDetail("atlas")), SeriesDetailActions()) }
        capture("16-series-detail")
    }

    /** A provider's movie before its details are read: the catalog's name, poster and year. */
    @Test fun movieDetailBeforeItsDetails() {
        val neon = checkNotNull(VodSamples.movieDetail("neon"))
        screen {
            MovieDetailScreen(
                neon.copy(genres = emptyList(), minutes = null, badges = emptyList(), synopsis = "", cast = emptyList(), director = null, leftMinutes = null, hasTrailer = false),
                MovieDetailActions(),
            )
        }
        capture("14-movie-detail-before-details")
    }

    /** A provider's series while its episodes load. */
    @Test fun seriesDetailLoading() {
        val kingsway = checkNotNull(VodSamples.seriesDetail("kingsway"))
        screen { SeriesDetailScreen(kingsway.copy(seasons = emptyList(), episodes = EpisodesStatus.Loading), SeriesDetailActions()) }
        capture("16-series-detail-loading")
    }
}
