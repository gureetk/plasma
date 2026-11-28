// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.feature.vod.VodCaption
import ca.gureet.plasma.feature.vod.VodTitle
import ca.gureet.plasma.remote.protocol.RemoteCategory
import ca.gureet.plasma.remote.protocol.RemoteTitle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a phone's Movies and Series tabs get from the TV (#230), on made-up titles. */
class PhoneTitlesTest {
    private fun title(id: String, name: String, year: Int = 0, rating: Float = 0f, poster: String? = null, progress: Float? = null) =
        VodTitle(id, name, poster, VodCaption.Movie(year = null, genre = null, minutes = null), progress = progress, year = year, rating = rating)

    private val lighthouse = title("home:7101", "The Lighthouse Keeper", 2025, 7.4f, "https://img.example/lighthouse.jpg", progress = 0.3f)
    private val polar = title("home:7102", "Polar Night", 2024, poster = "https://img.example/polar.jpg")
    private val orbit = title("home:7103", "Low Orbit")

    private val catalog = VodCatalog(
        categories = listOf(
            CountedItem(CONTINUING, "Continue Watching", 1, smart = true),
            CountedItem("home:movie:30", "Thrillers", 250),
            CountedItem("home:movie:31", "Late Night", 40, locked = true),
        ),
        recent = emptyList(),
        continuing = listOf(lighthouse),
        myList = emptyList(),
        names = emptyMap(),
    )

    /** Thrillers: 250 titles, the made-up three again and again. */
    private val thrillers = List(250) { listOf(lighthouse, polar, orbit)[it % 3].copy(id = "home:${8000 + it}") }

    private fun titles(categoryId: String?, from: Int = 0, asked: MutableList<String> = mutableListOf()) = runBlocking {
        phoneTitles("movies", catalog, categoryId, from) { id ->
            asked += id
            thrillers
        }
    }

    @Test fun theCategoriesAreTheTabsInItsOrderWithTheirCountsAndLocks() {
        val titles = titles(null)

        assertEquals(
            listOf(
                RemoteCategory(CONTINUING, "Continue Watching", 1),
                RemoteCategory("home:movie:30", "Thrillers", 250),
                RemoteCategory("home:movie:31", "Late Night", 40, locked = true),
            ),
            titles.categories,
        )
        // The first with titles: Continue Watching, with how far in.
        assertEquals(CONTINUING, titles.category)
        assertEquals(listOf(RemoteTitle("home:7101", "The Lighthouse Keeper", 2025, 7.4f, 0.3f, poster = true)), titles.titles)
    }

    @Test fun whatATitleDoesntSayIsLeftOut() {
        val row = phoneTitle(orbit)
        assertEquals(RemoteTitle("home:7103", "Low Orbit"), row)
    }

    @Test fun aLongCategoryComes200AtATime() {
        val first = titles("home:movie:30")
        val rest = titles("home:movie:30", from = 200)

        assertEquals(200, first.titles.size)
        assertEquals(50, rest.titles.size)
        assertEquals("home:8200", rest.titles.first().id)
        assertEquals(200, rest.from)
    }

    @Test fun aLockedCategorysTitlesArentReadOrSent() {
        val asked = mutableListOf<String>()
        val titles = titles("home:movie:31", asked = asked)

        assertEquals("home:movie:31", titles.category)
        assertTrue(titles.titles.isEmpty())
        assertTrue(asked.isEmpty())
    }

    @Test fun aCategoryThatsGoneIsTheFirstAgain() {
        assertEquals(CONTINUING, titles("home:movie:99").category)
    }
}
