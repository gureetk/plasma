// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ranking and completions (brief 4.3). */
class SearchRankingTest {
    @Test fun bandsByWhereTheMatchIs() {
        assertEquals(0, matchBand("Night Stint", "night"))
        assertEquals(1, matchBand("Polar Night", "night"))
        assertEquals(2, matchBand("Midnight Run", "night"))
        assertNull(matchBand("Harbour City v Redmoor", "night"))
    }

    @Test fun exactPrefixFirstThenLiveWithinABand() {
        data class R(val title: String, val live: Boolean)
        val ranked = rankResults(
            listOf(R("Late Night Sessions", false), R("Endurance 24: Night Stint", true), R("Night Stint", false), R("Midnight", true)),
            "night",
            { it.title },
            { it.live },
        ).map { it.title }
        assertEquals(listOf("Night Stint", "Endurance 24: Night Stint", "Late Night Sessions", "Midnight"), ranked)
    }

    @Test fun completionsComeFromTheMatchingPartOfTitles() {
        val suggestions = completions(listOf("Endurance 24: Night Stint", "Fight Night: Main Card", "The Night Desk"), "night")
        assertEquals(listOf("night stint", "fight night", "the night desk"), suggestions)
    }

    @Test fun highlightsTheWordThatMatches() {
        assertEquals(6..10, matchRange("fight night", "night"))
    }
}
