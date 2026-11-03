// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.core.model.CategoryItem
import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.core.model.CategoryOrder
import ca.gureet.plasma.core.model.SourceCategories
import ca.gureet.plasma.feature.settings.CategoryEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A source's Categories pages on Plasma's own data (the owner's ask, 1 November): what they show
 * is the categories as they're kept, and each change is kept with them, off the main thread. Live
 * TV, the Guide, Movies, Series, Search and Home follow the categories as they change.
 */
internal class AppCategories(private val graph: AppGraph) : CategoryEditor {
    private val categories get() = graph.data.categories

    override fun categories(sourceId: String, kind: CategoryKind): Flow<SourceCategories> = categories.categories(sourceId, kind)

    override fun setHidden(kind: CategoryKind, categoryId: String, hidden: Boolean) = keep { categories.setHidden(kind, categoryId, hidden) }

    override fun setAllHidden(sourceId: String, kind: CategoryKind, hidden: Boolean) = keep { categories.setAllHidden(sourceId, kind, hidden) }

    override fun setOrder(sourceId: String, kind: CategoryKind, order: CategoryOrder) = keep { categories.setOrder(sourceId, kind, order) }

    override fun move(sourceId: String, kind: CategoryKind, ids: List<String>) = keep { categories.move(sourceId, kind, ids) }

    override fun rename(kind: CategoryKind, categoryId: String, name: String) = keep { categories.rename(kind, categoryId, name) }

    override fun create(sourceId: String, kind: CategoryKind, name: String) = keep { categories.create(sourceId, kind, name) }

    override fun delete(kind: CategoryKind, categoryId: String) = keep { categories.delete(kind, categoryId) }

    /**
     * A group's channels as Live TV lists them, those hidden on their own left out but for those in
     * one of the user's own, to take out; those in a hidden group are there, to put in one of the
     * user's own. Movies and series as Movies and Series list them.
     */
    override fun items(kind: CategoryKind, categoryId: String): Flow<List<CategoryItem>> = when (kind) {
        CategoryKind.Live -> graph.world.filterNotNull().map { world ->
            val own = world.groups.any { it.id == categoryId && it.custom }
            world.list(categoryId).filter { own || !it.hidden || it.hiddenWithGroup }.map { CategoryItem(it.id, it.name, it.number.toString()) }
        }
        CategoryKind.Movies -> graph.data.vod.moviesIn(categoryId).map { list -> list.map { CategoryItem(it.id, it.name) } }
        CategoryKind.Series -> graph.data.vod.seriesIn(categoryId).map { list -> list.map { CategoryItem(it.id, it.name) } }
    }

    override fun setMember(categoryId: String, itemId: String, member: Boolean) = graph.setInOwnCategory(categoryId, itemId, member)

    /** Keeps a change, off the main thread; one that fails leaves things as they were. */
    private fun keep(change: suspend () -> Unit) {
        graph.scope.launch {
            try {
                change()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                // The page shows the categories as they're kept, so a change that didn't go through doesn't show.
            }
        }
    }
}
