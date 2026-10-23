// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.preview.TvPreview
import ca.gureet.plasma.core.fixtures.SampleHost

// Movies, Series and their detail pages (boards 13–16), from the sample data.

/** Board 13. */
@TvPreview
@Composable
private fun MoviesPreview() = SampleHost { VodBrowseScreen(VodSamples.movies, VodBrowseActions()) }

/** Board 14. */
@TvPreview
@Composable
private fun MovieDetailPreview() = SampleHost { MovieDetailScreen(checkNotNull(VodSamples.movieDetail("lighthouse")), MovieDetailActions()) }

/** Board 15. */
@TvPreview
@Composable
private fun SeriesPreview() = SampleHost { VodBrowseScreen(VodSamples.series, VodBrowseActions()) }

/** Board 16. */
@TvPreview
@Composable
private fun SeriesDetailPreview() = SampleHost { SeriesDetailScreen(checkNotNull(VodSamples.seriesDetail("atlas")), SeriesDetailActions()) }
