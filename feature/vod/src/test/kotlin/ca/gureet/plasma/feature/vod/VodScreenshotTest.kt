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
}
