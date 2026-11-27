// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.feature.vod.VodTitle
import ca.gureet.plasma.remote.protocol.RemoteCategory
import ca.gureet.plasma.remote.protocol.RemoteTitle
import ca.gureet.plasma.remote.protocol.RemoteTitles

/**
 * Movies' or Series' categories and titles for a phone's tab (#230): the categories as the TV's
 * tab lists them to the profile watching, in its order and with its counts (Recently Added or New
 * Episodes, Continue Watching and My List first when they have titles, then the sources' and the
 * user's own, without hidden ones), and [from] onward of one's titles, 200 at a time. [categoryId]
 * null or gone: the first category with titles that isn't locked. A locked category's titles
 * aren't read or sent ([titlesOf] gives a source category's).
 */
internal suspend fun phoneTitles(
    kind: String,
    catalog: VodCatalog,
    categoryId: String?,
    from: Int,
    titlesOf: suspend (categoryId: String) -> List<VodTitle>,
): RemoteTitles {
    val categories = catalog.categories.map { RemoteCategory(it.id, it.name, it.count, it.locked) }
    val id = categoryId?.takeIf { wanted -> categories.any { it.id == wanted } }
        ?: categories.firstOrNull { !it.locked && it.count > 0 }?.id
        ?: categories.firstOrNull()?.id.orEmpty()
    val category = categories.firstOrNull { it.id == id }
    val titles = when {
        category == null || category.locked -> emptyList()
        id == RECENT -> catalog.recent
        id == CONTINUING -> catalog.continuing
        id == MY_LIST -> catalog.myList
        else -> titlesOf(id)
    }
    return RemoteTitles(kind, categories, id, from, titles.drop(from.coerceAtLeast(0)).take(PHONE_PAGE).map(::phoneTitle))
}

/** A title as a phone's row shows it: its name, year and rating as its poster does, how far in, and whether it has a poster. */
internal fun phoneTitle(title: VodTitle) = RemoteTitle(
    id = title.id,
    name = title.title,
    year = title.year.takeIf { it > 0 },
    rating = title.rating.takeIf { it > 0f },
    progress = title.progress,
    poster = !title.poster.isNullOrBlank(),
)

/** Titles a phone gets at a time, as it gets channels (5.21). */
private const val PHONE_PAGE = 200
