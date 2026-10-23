// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.preview.TvPreview
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.fixtures.SampleHost

// Search (board 03), from the sample data.

/** Board 03: "night" typed, the first result focused. */
@TvPreview
@Composable
private fun SearchPreview() = SampleHost {
    SearchScreen(
        search = SearchSamples::search,
        recentSearches = SearchSamples.recent,
        actions = SearchActions(),
        profileName = Sample.maya.name,
        profileInitial = Sample.maya.initial,
        initialQuery = "night",
        initialFocus = SearchFocus.Results,
    )
}
