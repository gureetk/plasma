// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Search against board 03 (docs/design/screens): "night" typed, the first result focused. */
class SearchScreenshotTest : ScreenshotTest() {
    @Test fun search() {
        screen {
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
        capture("03-search")
    }
}
