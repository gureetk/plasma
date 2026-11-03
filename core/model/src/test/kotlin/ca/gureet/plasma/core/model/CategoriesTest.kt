// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A source's categories in the order Settings › Portals & Playlists › Categories says (the owner's ask, 1 November). */
class CategoriesTest {
    private data class Category(val id: String, val name: String, val custom: Boolean = false, val position: Int, val userPosition: Int? = null)

    private val news = Category("news", "News", position = 0)
    private val sports = Category("sports", "Sports", position = 1)
    private val kids = Category("kids", "kids", position = 2)
    private val mine = Category("mine", "My Picks", custom = true, position = 0)
    private val all = listOf(sports, kids, mine, news)

    private fun sorted(items: List<Category>, order: CategoryOrder) =
        CategoryOrdering.sorted(items, order) { CategoryPlace(it.name, it.custom, it.position, it.userPosition) }.map { it.id }

    @Test fun theProvidersOrderPutsTheUsersOwnFirst() {
        assertEquals(listOf("mine", "news", "sports", "kids"), sorted(all, CategoryOrder.Provider))
    }

    @Test fun aToZSortsEveryNameWhateverItsCase() {
        assertEquals(listOf("kids", "mine", "news", "sports"), sorted(all, CategoryOrder.AToZ))
    }

    @Test fun theUsersOrderKeepsItsPlacesAndPutsNewOnesLast() {
        val placed = listOf(sports.copy(userPosition = 0), news.copy(userPosition = 1), mine.copy(userPosition = 2), kids)
        // Kids came from the provider after the order was made: last, in the provider's order.
        assertEquals(listOf("sports", "news", "mine", "kids"), sorted(placed, CategoryOrder.Mine))
        // Without a place yet, it's the provider's order.
        assertEquals(sorted(all, CategoryOrder.Provider), sorted(all, CategoryOrder.Mine))
    }

    @Test fun namesCompareTheirNumbersByValue() {
        val names = listOf("Sports 10", "Sports 2", "sports 1", "Éire", "Eagles", "007 Films", "7 Days")
        assertEquals(listOf("7 Days", "007 Films", "Eagles", "Éire", "sports 1", "Sports 2", "Sports 10"), names.sortedWith(NaturalNames))
        assertTrue(NaturalNames.compare("News", "news") != 0)
        assertEquals(0, NaturalNames.compare("News", "News"))
    }

    @Test fun keptIdsComeBack() {
        CategoryKind.entries.forEach { assertEquals(it, CategoryKind.of(it.id)) }
        CategoryOrder.entries.forEach { assertEquals(it, CategoryOrder.of(it.id)) }
        assertEquals(CategoryOrder.Provider, CategoryOrder.of("shuffle"))
        assertEquals(null, CategoryKind.of("radio"))
    }
}
