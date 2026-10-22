// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.ChoiceButton
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.ColorKeyHint
import ca.gureet.plasma.core.design.component.CountedList
import ca.gureet.plasma.core.design.component.PlasmaGrid
import ca.gureet.plasma.core.design.component.PosterTile
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.Duration
import java.util.Locale

private const val COLUMNS = 6
private const val GRID_TOP_PX = 226

/** Twelve category rows: the twelfth is cut off by the screen's edge and faded (board 13). */
private const val CATEGORIES_HEIGHT_PX = 765

/**
 * Movies and Series (boards 13 and 15, brief 4.13 and 4.15): categories on the left, then the
 * category's posters in a six-column grid. The focused poster grows and shows its caption. Yellow
 * steps through the sort orders, as does OK on the ⌄ button.
 */
@Composable
fun VodBrowseScreen(state: VodBrowseUiState, actions: VodBrowseActions, modifier: Modifier = Modifier) {
    var categoryId by rememberSaveable { mutableStateOf(state.selectedCategoryId) }
    var sort by rememberSaveable { mutableStateOf(state.sort) }
    var focusedId by rememberSaveable { mutableStateOf(state.focusedId) }
    val category = state.categories.firstOrNull { it.id == categoryId } ?: state.categories.first()
    val titles = remember(category.id, sort, state) { sortTitles(state.titles[category.id].orEmpty(), sort) }

    val tabFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val categoriesFocus = remember { FocusRequester() }
    val startFocus = remember { FocusRequester() }
    val startIndex = titles.indexOfFirst { it.id == focusedId }.coerceAtLeast(0)
    // A new category or order rebuilds the grid; when it had focus (or OK opened a category), the
    // new grid takes focus once it's there, on the same title if it's still in the list.
    var gridHasFocus by remember { mutableStateOf(false) }
    var focusNewGrid by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (titles.isNotEmpty()) startFocus.requestFocus() else categoriesFocus.requestFocus()
    }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.ProgramYellow && event.type == KeyEventType.KeyUp) {
                    focusNewGrid = gridHasFocus
                    sort = sort.next()
                    true
                } else {
                    false
                }
            },
    ) {
        TopBar(
            current = if (state.kind == VodKind.Movies) TopTab.Movies else TopTab.Series,
            profileInitial = state.profileInitial,
            profileName = state.profileName,
            onTab = actions.onTab,
            onSettings = actions.onSettings,
            onProfile = actions.onProfile,
            currentTabFocusRequester = tabFocus,
            focusDown = if (titles.isEmpty()) categoriesFocus else gridFocus,
        )

        CountedList(
            items = state.categories,
            selectedId = category.id,
            onFocusItem = { categoryId = it },
            onOpenItem = { id ->
                if (id == category.id) {
                    gridFocus.requestFocus()
                } else {
                    focusNewGrid = true
                    categoryId = id
                }
            },
            modifier = Modifier
                .offset(96.px, 148.px)
                .size(290.px, CATEGORIES_HEIGHT_PX.px)
                .focusRequester(categoriesFocus),
        )

        Row(Modifier.offset(430.px, 148.px).size(1394.px, 52.px), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.px)) {
                PlasmaText(category.name, plasmaStyle(32f, 600, trackingPx = -0.4f), Modifier.alignByBaseline())
                PlasmaText(
                    pluralStringResource(
                        if (state.kind == VodKind.Movies) R.plurals.movie_count else R.plurals.series_count,
                        category.count,
                        Formats.count(category.count),
                    ),
                    plasmaStyle(22f, 500),
                    Modifier.alignByBaseline(),
                    color = PlasmaColors.Text3,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.px)) {
                ColorKeyHint(ColorKey.Yellow, stringResource(R.string.key_sort))
                ChoiceButton(sort.label(state.kind), onClick = { sort = sort.next() }, modifier = Modifier.focusRequester(sortFocus))
            }
        }

        if (titles.isEmpty()) {
            PlasmaText(
                stringResource(if (state.kind == VodKind.Movies) R.string.empty_movies else R.string.empty_series),
                plasmaStyle(23f, 400),
                Modifier.offset(430.px, GRID_TOP_PX.px),
                color = PlasmaColors.Text2,
            )
        }
        // A new category or order starts at the row of the focused title (the top if it isn't there).
        key(category.id, sort) {
            val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = startIndex / COLUMNS * COLUMNS)
            LaunchedEffect(Unit) {
                if (focusNewGrid) {
                    focusNewGrid = false
                    withFrameNanos { }
                    if (titles.isNotEmpty()) startFocus.requestFocus()
                }
            }
            PlasmaGrid(
                columns = COLUMNS,
                columnGap = 28.px,
                rowGap = 96.px,
                state = gridState,
                // Room under the last row for its caption.
                contentPadding = PaddingValues(bottom = 130.px),
                modifier = Modifier
                    .offset(430.px, GRID_TOP_PX.px)
                    .size(1394.px, (1080 - GRID_TOP_PX).px)
                    .onFocusChanged { gridHasFocus = it.hasFocus }
                    .focusRequester(gridFocus),
            ) {
                itemsIndexed(titles, key = { _, title -> title.id }) { i, title ->
                    PosterTile(
                        image = title.poster,
                        title = title.title,
                        caption = title.caption.text(),
                        onClick = {
                            focusedId = title.id
                            actions.onOpen(title.id)
                        },
                        badge = title.badge,
                        progress = title.progress,
                        onLongClick = { actions.onOptions(title.id) },
                        onFocusChanged = { if (it) focusedId = title.id },
                        modifier = Modifier
                            .then(if (i == startIndex) Modifier.focusRequester(startFocus) else Modifier)
                            // Up from the top row goes to the tabs, or from the last column to Sort above it.
                            .then(if (i < COLUMNS) Modifier.focusProperties { up = if (i == COLUMNS - 1) sortFocus else tabFocus } else Modifier),
                    )
                }
            }
        }
    }
}

private fun VodSort.next(): VodSort = VodSort.entries[(ordinal + 1) % VodSort.entries.size]

@Composable
private fun VodSort.label(kind: VodKind): String = stringResource(
    when (this) {
        VodSort.Newest -> if (kind == VodKind.Movies) R.string.sort_newest else R.string.sort_latest_episode
        VodSort.AZ -> R.string.sort_a_z
        VodSort.Year -> R.string.sort_year
        VodSort.Rating -> R.string.sort_rating
    },
)

/** Titles sort by name without a leading article, as libraries do: The Last Lighthouse under L. */
private fun sortKey(title: String): String {
    val lower = title.lowercase(Locale.US)
    val article = listOf("the ", "a ", "an ").firstOrNull { lower.startsWith(it) }
    return if (article != null) lower.removePrefix(article) else lower
}

internal fun sortTitles(titles: List<VodTitle>, sort: VodSort): List<VodTitle> = when (sort) {
    VodSort.Newest -> titles.sortedByDescending { it.added }
    VodSort.AZ -> titles.sortedBy { sortKey(it.title) }
    VodSort.Year -> titles.sortedWith(compareByDescending<VodTitle> { it.year }.thenByDescending { it.added })
    VodSort.Rating -> titles.sortedWith(compareByDescending<VodTitle> { it.rating }.thenBy { sortKey(it.title) })
}

/** The caption under a focused poster, and the line under a detail page's buttons. */
@Composable
internal fun VodCaption.text(): String = when (this) {
    is VodCaption.Movie -> listOf(year.toString(), genre, Formats.duration(Duration.ofMinutes(minutes.toLong()))).joinToString(" · ")
    is VodCaption.ResumeEpisode ->
        stringResource(R.string.resume_episode_caption, season, episode, Formats.duration(Duration.ofMinutes(leftMinutes.toLong())))
    is VodCaption.Series -> "$years · " + pluralStringResource(R.plurals.seasons, seasons, seasons)
}
