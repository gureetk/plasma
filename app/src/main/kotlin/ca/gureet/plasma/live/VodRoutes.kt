// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.AppGraph
import ca.gureet.plasma.R
import ca.gureet.plasma.core.data.Movie
import ca.gureet.plasma.core.data.Series
import ca.gureet.plasma.core.data.VodCategory
import ca.gureet.plasma.core.data.VodRepository
import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.feature.search.TitleResult
import ca.gureet.plasma.feature.vod.VodBrowseActions
import ca.gureet.plasma.feature.vod.VodBrowseScreen
import ca.gureet.plasma.feature.vod.VodBrowseUiState
import ca.gureet.plasma.feature.vod.VodCaption
import ca.gureet.plasma.feature.vod.VodKind
import ca.gureet.plasma.feature.vod.VodTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * What the Movies or Series tab lists before a category is open: its categories with their
 * counts, Recently Added (or New Episodes) first when there are any, and their titles.
 */
internal data class VodCatalog(
    val categories: List<CountedItem>,
    /** Recently Added's or New Episodes' titles. */
    val recent: List<VodTitle>,
    /** Each category's name by its id, for the captions. */
    val names: Map<String, String>,
)

/** Recently Added's id (4.13), or New Episodes' (4.15). */
internal const val RECENT = "recent"

/** The Movies or Series catalog of the sources that are turned on, kept current (brief 5.9). */
internal fun vodCatalog(vod: VodRepository, kind: VodKind, recentName: String): Flow<VodCatalog> = when (kind) {
    VodKind.Movies -> combine(vod.movieCategories, vod.recentMovies()) { categories, recent ->
        catalogOf(categories, recentName) { names -> recent.map { it.title(names) } }
    }
    VodKind.Series -> combine(vod.seriesCategories, vod.newEpisodes()) { categories, recent ->
        catalogOf(categories, recentName) { names -> recent.map { it.title(names) } }
    }
}.flowOn(Dispatchers.Default)

private fun catalogOf(categories: List<VodCategory>, recentName: String, recent: (names: Map<String, String>) -> List<VodTitle>): VodCatalog {
    val names = categories.associate { it.id to it.name }
    val newest = recent(names)
    return VodCatalog(
        categories = listOfNotNull(CountedItem(RECENT, recentName, newest.size, smart = true).takeIf { newest.isNotEmpty() }) +
            categories.map { CountedItem(it.id, it.name, it.count) },
        recent = newest,
        names = names,
    )
}

/** The titles of the category [categoryId], as posters (4.13 and 4.15). */
internal fun vodTitles(vod: VodRepository, kind: VodKind, categoryId: String, names: Map<String, String>): Flow<List<VodTitle>> = when (kind) {
    VodKind.Movies -> vod.moviesIn(categoryId).map { list -> list.map { it.title(names) } }
    VodKind.Series -> vod.seriesIn(categoryId).map { list -> list.map { it.title(names) } }
}.flowOn(Dispatchers.Default)

/**
 * A movie's poster: "2026 · Thrillers" under it when it has focus, with its category standing in
 * for the genre until its details load (4.13).
 */
internal fun Movie.title(names: Map<String, String>) = VodTitle(
    id = id,
    title = name,
    poster = poster,
    caption = VodCaption.Movie(year = year, genre = categoryId?.let(names::get), minutes = null),
    year = year ?: 0,
    rating = rating ?: 0f,
    added = added?.toEpochMilli() ?: 0,
)

/** A series' poster: "2024 · Drama", its own genres or else its category's. */
internal fun Series.title(names: Map<String, String>) = VodTitle(
    id = id,
    title = name,
    poster = poster,
    caption = VodCaption.Series(years = year?.toString(), seasons = null, genre = genre ?: categoryId?.let(names::get)),
    year = year ?: 0,
    rating = rating ?: 0f,
    added = updated?.toEpochMilli() ?: 0,
)

/** A movie among Search's results: `movie:` and its id, as the samples' are (brief 5.10). */
internal fun Movie.searchResult() = TitleResult("movie:$id", name, poster, series = false)

/** A series among Search's results: `series:` and its id. */
internal fun Series.searchResult() = TitleResult("series:$id", name, poster, series = true)

/**
 * Movies or Series (boards 13 and 15) on the sources' own catalogs (brief 4.13, 4.15 and 5.9):
 * Recently Added (or New Episodes), then each source's categories, a category's titles loading as
 * it shows. With nothing loaded yet, the tab says so.
 */
@Composable
fun LiveVodBrowse(graph: AppGraph, kind: VodKind, profileName: String, actions: VodBrowseActions) {
    val recentName = stringResource(if (kind == VodKind.Movies) R.string.recently_added else R.string.new_episodes)
    val catalog by remember(graph, kind, recentName) { vodCatalog(graph.data.vod, kind, recentName) }.collectAsState(null)
    var showing by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    val current = catalog ?: return
    val category = showing?.takeIf { id -> current.categories.any { it.id == id } } ?: current.categories.firstOrNull()?.id
    val titles by remember(graph, kind, category, current.names) {
        if (category == null || category == RECENT) flowOf(null) else vodTitles(graph.data.vod, kind, category, current.names)
    }.collectAsState(null)
    // The first category's titles are there before the tab shows, so its first poster takes focus.
    if (showing == null && category != null && category != RECENT && titles == null) return
    val loaded = titles
    VodBrowseScreen(
        state = VodBrowseUiState(
            kind = kind,
            categories = current.categories,
            selectedCategoryId = category.orEmpty(),
            titles = buildMap {
                put(RECENT, current.recent)
                if (category != null && loaded != null) put(category, loaded)
            },
            profileName = profileName,
            profileInitial = profileName.take(1).uppercase(),
        ),
        actions = remember(actions) {
            VodBrowseActions(
                onTab = actions.onTab,
                onSettings = actions.onSettings,
                onProfile = actions.onProfile,
                onOpen = actions.onOpen,
                onCategory = { showing = it },
                onMyList = actions.onMyList,
                onWatched = actions.onWatched,
            )
        },
    )
}
