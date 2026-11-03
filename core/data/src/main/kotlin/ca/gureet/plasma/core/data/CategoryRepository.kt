// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.CategoryDao
import ca.gureet.plasma.core.database.CategoryMemberEntity
import ca.gureet.plasma.core.database.GroupEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.SourceEntity
import ca.gureet.plasma.core.database.VodCategoryEntity
import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.core.model.CategoryOrder
import ca.gureet.plasma.core.model.CategoryOrdering
import ca.gureet.plasma.core.model.CategoryPlace
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.SourceCategories
import ca.gureet.plasma.core.model.SourceCategory
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * A source's categories as the user arranges them, in Settings › Portals & Playlists › the source
 * › Categories (the owner's ask, 1 November): Live TV's groups, and the movie and series
 * categories, each shown or hidden, renamed, in the provider's order, A to Z or the user's own,
 * and categories of the user's own, with the channels, movies or series the user puts in them.
 * Live TV, the Guide, Movies, Series, Search and Home follow them.
 */
class CategoryRepository(private val db: PlasmaDatabase, private val clock: PlasmaClock) {
    private val dao: CategoryDao get() = db.categories()

    /** [sourceId]'s categories of [kind] in the order they show in, hidden ones too, with how many each has. */
    fun categories(sourceId: String, kind: CategoryKind): Flow<SourceCategories> {
        val counts = when (kind) {
            CategoryKind.Live -> dao.observeChannelCounts(sourceId)
            CategoryKind.Movies -> dao.observeMovieCounts(sourceId)
            CategoryKind.Series -> dao.observeSeriesCounts(sourceId)
        }
        val source = db.sources().observeAll().map { all -> all.firstOrNull { it.id == sourceId } }
        return combine(rows(sourceId, kind), counts, dao.observeMemberCounts(), source) { rows, own, picked, src ->
            val order = src?.order(kind) ?: CategoryOrder.Provider
            val count = (own + picked).associate { it.categoryId to it.count }
            SourceCategories(
                order,
                CategoryOrdering.sorted(rows, order) { it.place }.map { row ->
                    SourceCategory(row.id, row.place.name, row.providerName, row.hidden, row.place.custom, count[row.id] ?: 0)
                },
            )
        }.distinctUntilChanged()
    }

    /** Shows the category [categoryId] of [kind], or hides it: a provider's group or category with what's in it, but for what's in a shown one of the user's. */
    suspend fun setHidden(kind: CategoryKind, categoryId: String, hidden: Boolean) =
        if (kind == CategoryKind.Live) dao.setGroupHidden(categoryId, hidden) else dao.setVodHidden(categoryId, hidden)

    /** Show All or Hide All: every one of [sourceId]'s categories of [kind]. */
    suspend fun setAllHidden(sourceId: String, kind: CategoryKind, hidden: Boolean) =
        if (kind == CategoryKind.Live) dao.setGroupsHidden(sourceId, hidden) else dao.setVodAllHidden(sourceId, kind.id, hidden)

    /**
     * The category [categoryId] shows as [name] now. A provider's goes back to the provider's name
     * when [name] is blank or that name; one of the user's own keeps its name then.
     */
    suspend fun rename(kind: CategoryKind, categoryId: String, name: String) {
        val wanted = name.trim()
        val row = rows(categoryId.sourceOf(), kind).first().firstOrNull { it.id == categoryId } ?: return
        if (row.place.custom) {
            if (wanted.isEmpty()) return
            if (kind == CategoryKind.Live) dao.renameOwnGroup(categoryId, wanted) else dao.renameOwnVod(categoryId, wanted)
        } else {
            val provider = row.providerName ?: row.place.name
            val custom = wanted.takeIf { it.isNotEmpty() && it != provider }
            if (kind == CategoryKind.Live) dao.renameGroup(categoryId, custom) else dao.renameVod(categoryId, custom)
        }
    }

    /**
     * [sourceId]'s categories of [kind] show in [order] now. The user's order picks up where it was
     * left; the first time, it starts from the order they're showing in.
     */
    suspend fun setOrder(sourceId: String, kind: CategoryKind, order: CategoryOrder) {
        val places = if (order == CategoryOrder.Mine) {
            val rows = rows(sourceId, kind).first()
            if (rows.any { it.place.userPosition != null }) {
                null
            } else {
                val now = db.sources().get(sourceId)?.order(kind) ?: CategoryOrder.Provider
                CategoryOrdering.sorted(rows, now) { it.place }.map { it.id }
            }
        } else {
            null
        }
        dao.order(sourceId, kind.id, order.id, places)
    }

    /** Move: [sourceId]'s categories of [kind] are in the user's order now, [ids] as they are. */
    suspend fun move(sourceId: String, kind: CategoryKind, ids: List<String>) = dao.order(sourceId, kind.id, CategoryOrder.Mine.id, ids)

    /**
     * New Category: one of the user's own, named [name], for [sourceId]'s Live TV, movies or series.
     * It comes first among the source's categories in the user's order, as among the user's own in
     * the provider's. Returns its id.
     */
    suspend fun create(sourceId: String, kind: CategoryKind, name: String): String {
        val title = name.trim()
        val remoteId = "$CUSTOM${UUID.randomUUID()}"
        val number = ((if (kind == CategoryKind.Live) dao.lastOwnGroup(sourceId) else dao.lastOwnVod(sourceId, kind.id)) ?: -1) + 1
        val first = rows(sourceId, kind).first().mapNotNull { it.place.userPosition }.minOrNull()?.minus(1)
        return if (kind == CategoryKind.Live) {
            val id = "$sourceId:$remoteId"
            dao.insertGroup(GroupEntity(id, sourceId, remoteId, title, number, generation = 0, userPosition = first, custom = true))
            id
        } else {
            val id = "$sourceId:${kind.id}:$remoteId"
            dao.insertVod(VodCategoryEntity(id, sourceId, kind.id, remoteId, title, number, generation = 0, userPosition = first, custom = true))
            id
        }
    }

    /** Delete Category: one of the user's own goes, with what's in it; the channels and titles themselves stay. */
    suspend fun delete(kind: CategoryKind, categoryId: String) = dao.deleteOwn(categoryId, kind.id)

    /** Puts [itemId], a channel, movie or series, in [categoryId], one of the user's own. */
    suspend fun add(categoryId: String, itemId: String) =
        dao.addMember(CategoryMemberEntity(categoryId, itemId, categoryId.sourceOf(), clock.now().toEpochMilli()))

    /** Takes what [itemIds] name out of [categoryId]: a channel from several sources may be in it under any of their ids. */
    suspend fun remove(categoryId: String, itemIds: Collection<String>) = dao.removeMembers(categoryId, itemIds.toList())

    /** The ids of what's in [categoryId], one of the user's own, in the order they went in. */
    fun members(categoryId: String): Flow<List<String>> = dao.observeMembersOf(categoryId).map { list -> list.map { it.itemId } }

    /** What's in each of the user's own categories, by its id, each in the order it went in. */
    val allMembers: Flow<Map<String, List<String>>> = dao.observeMembers().map { list -> list.groupBy({ it.categoryId }, { it.itemId }) }

    /** [sourceId]'s categories of [kind] as ordering and Settings see them. */
    private fun rows(sourceId: String, kind: CategoryKind): Flow<List<Row>> = when (kind) {
        CategoryKind.Live -> dao.observeGroups(sourceId).map { list ->
            list.map { Row(it.id, it.place(), it.hidden, if (it.customName != null) it.name else null) }
        }
        else -> dao.observeVodCategories(sourceId, kind.id).map { list ->
            list.map { Row(it.id, it.place(), it.hidden, if (it.customName != null) it.name else null) }
        }
    }

    /** A category: where it goes, whether it's hidden, and the provider's name where the user gave another. */
    private class Row(val id: String, val place: CategoryPlace, val hidden: Boolean, val providerName: String?)

    companion object {
        /** How the user's own categories' provider ids start, with a UUID after it, which no provider's can be. */
        const val CUSTOM = "custom:"

        /** A category's source, from its id: every category's id starts with its source's and a colon. */
        internal fun String.sourceOf(): String = substringBefore(':')
    }
}

/** Where a movie or series category goes in its source's order. */
internal fun VodCategoryEntity.place() = CategoryPlace(customName ?: name, custom, position, userPosition)

/** The order [kind]'s categories show in. */
internal fun SourceEntity.order(kind: CategoryKind): CategoryOrder = CategoryOrder.of(
    when (kind) {
        CategoryKind.Live -> liveOrder
        CategoryKind.Movies -> movieOrder
        CategoryKind.Series -> seriesOrder
    },
)
