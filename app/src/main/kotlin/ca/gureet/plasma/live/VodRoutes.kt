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
import ca.gureet.plasma.core.data.ContinueEntry
import ca.gureet.plasma.core.data.Movie
import ca.gureet.plasma.core.data.Progress
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
import kotlinx.coroutines.launch

/**
 * What the Movies or Series tab lists before a category is open: its categories with their
 * counts, Recently Added (or New Episodes), Continue Watching and My List first when they have
 * anything, and their titles.
 */
internal data class VodCatalog(
    val categories: List<CountedItem>,
    /** Recently Added's or New Episodes' titles. */
    val recent: List<VodTitle>,
    /** Continue Watching's titles, the latest played first. */
    val continuing: List<VodTitle>,
    /** My List's titles, the latest added first. */
    val myList: List<VodTitle>,
    /** Each category's name by its id, for the captions. */
    val names: Map<String, String>,
)

/** The tab's own lists' names: Recently Added (or New Episodes), Continue Watching, My List. */
internal data class SmartNames(val recent: String, val continuing: String, val myList: String)

/** The tab's own lists' titles. */
internal data class SmartLists(val recent: List<VodTitle>, val continuing: List<VodTitle>, val myList: List<VodTitle>)

/** Recently Added's id (4.13), or New Episodes' (4.15). */
internal const val RECENT = "recent"

/** Continue Watching's id (4.13 and 4.15). */
internal const val CONTINUING = "continue"

/** My List's id (4.13 and 4.15). */
internal const val MY_LIST = "my-list"

/** The ids of the tab's own lists, whose titles come with the catalog. */
private val SMART = setOf(RECENT, CONTINUING, MY_LIST)

/** What the profile did with titles (brief 4.13): which are on My List, where each was left, which are watched. */
internal data class TitleMarks(
    val listed: Set<String> = emptySet(),
    val progress: Map<String, Progress> = emptyMap(),
    val watched: Set<String> = emptySet(),
)

/** A movie's or series' poster as the profile left it: on My List, part watched, or watched. */
internal fun VodTitle.marked(marks: TitleMarks) =
    copy(inMyList = id in marks.listed, watched = id in marks.watched, progress = marks.progress[id]?.fraction)

private fun titleMarks(vod: VodRepository, kind: VodKind): Flow<TitleMarks> = when (kind) {
    VodKind.Movies -> combine(vod.myListMovieIds, vod.movieProgress) { listed, progress ->
        TitleMarks(listed, progress, progress.filterValues { it.watched }.keys)
    }
    VodKind.Series -> combine(vod.myListSeriesIds, vod.watchedSeriesIds) { listed, watched -> TitleMarks(listed, watched = watched) }
}

/**
 * The Movies or Series catalog of the sources that are turned on, kept current (brief 5.9), with
 * [continuing], Continue Watching's movies and episodes.
 */
internal fun vodCatalog(vod: VodRepository, kind: VodKind, smartNames: SmartNames, continuing: Flow<List<ContinueEntry>>): Flow<VodCatalog> = when (kind) {
    VodKind.Movies -> combine(vod.movieCategories, vod.recentMovies(), vod.myListMovies, titleMarks(vod, kind), continuing) { categories, recent, listed, marks, started ->
        catalogOf(categories, smartNames) { names ->
            SmartLists(recent.map { it.title(names).marked(marks) }, continueMovies(started).map { it.marked(marks).copy(progress = it.progress) }, listed.map { it.title(names).marked(marks) })
        }
    }
    VodKind.Series -> combine(vod.seriesCategories, vod.newEpisodes(), vod.myListSeries, titleMarks(vod, kind), continuing) { categories, recent, listed, marks, started ->
        catalogOf(categories, smartNames) { names ->
            SmartLists(recent.map { it.title(names).marked(marks) }, continueSeries(started).map { it.marked(marks).copy(progress = it.progress) }, listed.map { it.title(names).marked(marks) })
        }
    }
}.flowOn(Dispatchers.Default)

/** [smart] gives the tab's own lists' titles, by the categories' names. */
internal fun catalogOf(categories: List<VodCategory>, smartNames: SmartNames, smart: (names: Map<String, String>) -> SmartLists): VodCatalog {
    val names = categories.associate { it.id to it.name }
    val lists = smart(names)
    return VodCatalog(
        categories = listOfNotNull(
            CountedItem(RECENT, smartNames.recent, lists.recent.size, smart = true).takeIf { lists.recent.isNotEmpty() },
            CountedItem(CONTINUING, smartNames.continuing, lists.continuing.size, smart = true).takeIf { lists.continuing.isNotEmpty() },
            CountedItem(MY_LIST, smartNames.myList, lists.myList.size, smart = true).takeIf { lists.myList.isNotEmpty() },
        ) + categories.map { CountedItem(it.id, it.name, it.count) },
        recent = lists.recent,
        continuing = lists.continuing,
        myList = lists.myList,
        names = names,
    )
}

/** The titles of the category [categoryId], as posters (4.13 and 4.15). */
internal fun vodTitles(vod: VodRepository, kind: VodKind, categoryId: String, names: Map<String, String>): Flow<List<VodTitle>> = when (kind) {
    VodKind.Movies -> combine(vod.moviesIn(categoryId), titleMarks(vod, kind)) { list, marks -> list.map { it.title(names).marked(marks) } }
    VodKind.Series -> combine(vod.seriesIn(categoryId), titleMarks(vod, kind)) { list, marks -> list.map { it.title(names).marked(marks) } }
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
 * Recently Added (or New Episodes), Continue Watching and My List, then each source's categories,
 * a category's titles loading as it shows. With nothing loaded yet, the tab says so. Long-press or Menu on a
 * poster puts it on My List or marks it watched.
 */
@Composable
fun LiveVodBrowse(graph: AppGraph, kind: VodKind, profileName: String, actions: VodBrowseActions) {
    val vod = graph.data.vod
    val smartNames = SmartNames(
        recent = stringResource(if (kind == VodKind.Movies) R.string.recently_added else R.string.new_episodes),
        continuing = stringResource(R.string.continue_watching),
        myList = stringResource(R.string.my_list),
    )
    val catalog by remember(graph, kind, smartNames) {
        val choices = graph.choices
        vodCatalog(vod, kind, smartNames, vod.continueWatching(continueSince(choices, graph.clock.now()), choices.continueNextEpisodes))
    }.collectAsState(null)
    var showing by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    val current = catalog ?: return
    val category = showing?.takeIf { id -> current.categories.any { it.id == id } } ?: current.categories.firstOrNull()?.id
    val own = category == null || category in SMART
    val titles by remember(graph, kind, category, current.names) {
        if (own || category == null) flowOf(null) else vodTitles(vod, kind, category, current.names)
    }.collectAsState(null)
    // The first category's titles are there before the tab shows, so its first poster takes focus.
    if (showing == null && !own && titles == null) return
    val loaded = titles
    VodBrowseScreen(
        state = VodBrowseUiState(
            kind = kind,
            categories = current.categories,
            selectedCategoryId = category.orEmpty(),
            titles = buildMap {
                put(RECENT, current.recent)
                put(CONTINUING, current.continuing)
                put(MY_LIST, current.myList)
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
                onMyList = { id, inList ->
                    graph.scope.launch { if (kind == VodKind.Movies) vod.setMovieInMyList(id, inList) else vod.setSeriesInMyList(id, inList) }
                },
                onWatched = { id, watched ->
                    graph.scope.launch { if (kind == VodKind.Movies) vod.setMovieWatched(id, watched) else vod.setSeriesWatched(id, watched) }
                },
            )
        },
    )
}
