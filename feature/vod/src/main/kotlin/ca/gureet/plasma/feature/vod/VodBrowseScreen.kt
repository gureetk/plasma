// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.ChoiceButton
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.ColorKeyHint
import ca.gureet.plasma.core.design.component.CountedList
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.PlasmaGrid
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.PosterTile
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.model.Titles
import java.time.Duration

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
    if (state.categories.isEmpty()) {
        NoCatalog(state, actions, modifier)
        return
    }
    var categoryId by rememberSaveable { mutableStateOf(state.selectedCategoryId) }
    var sort by rememberSaveable { mutableStateOf(state.sort) }
    var focusedId by rememberSaveable { mutableStateOf(state.focusedId) }
    val category = state.categories.firstOrNull { it.id == categoryId } ?: state.categories.first()
    // A locked category shows its lock instead of its titles (5.12).
    val locked = category.id in state.locked
    // Null while the category's titles load.
    val loaded = if (locked) emptyList() else state.titles[category.id]
    val titles = remember(category.id, sort, loaded) { sortTitles(loaded.orEmpty(), sort) }
    LaunchedEffect(category.id) { actions.onCategory(category.id) }

    val tabFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val categoriesFocus = remember { FocusRequester() }
    val startFocus = remember { FocusRequester() }
    val unlockFocus = remember { FocusRequester() }
    val startIndex = titles.indexOfFirst { it.id == focusedId }.coerceAtLeast(0)
    // A new category or order rebuilds the grid; when it had focus (or OK opened a category), the
    // new grid takes focus once it's there, on the same title if it's still in the list.
    var gridHasFocus by remember { mutableStateOf(false) }
    var focusNewGrid by remember { mutableStateOf(false) }
    // The poster sheet (long-press or Menu), and what it has changed so far.
    var sheetFor by remember { mutableStateOf<VodTitle?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var myList by remember(state) { mutableStateOf(emptyMap<String, Boolean>()) }
    var watched by remember(state) { mutableStateOf(emptyMap<String, Boolean>()) }
    val sheetReturn = remember { FocusRequester() }

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
            focusDown = when {
                locked -> unlockFocus
                titles.isEmpty() -> categoriesFocus
                else -> gridFocus
            },
        )

        CountedList(
            items = state.categories,
            selectedId = category.id,
            onFocusItem = { categoryId = it },
            onOpenItem = { id ->
                if (id == category.id && locked) {
                    unlockFocus.requestFocus()
                } else if (id == category.id && loaded != null) {
                    gridFocus.requestFocus()
                } else {
                    // The grid takes focus once its titles are there.
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

        if (locked) {
            LockedCategory(category.name, state.kind, onUnlock = { actions.onUnlock(category.id, category.name) }, unlockFocus, Modifier.offset(430.px, (GRID_TOP_PX + 24).px))
        } else if (loaded != null && titles.isEmpty()) {
            PlasmaText(
                stringResource(if (state.kind == VodKind.Movies) R.string.empty_movies else R.string.empty_series),
                plasmaStyle(23f, 400),
                Modifier.offset(430.px, GRID_TOP_PX.px),
                color = PlasmaColors.Text2,
            )
        }
        // A new category or order starts at the row of the focused title (the top if it isn't there),
        // once its titles have loaded.
        if (!locked) key(category.id, sort, loaded != null) {
            val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = startIndex / COLUMNS * COLUMNS)
            LaunchedEffect(Unit) {
                if (focusNewGrid && loaded != null) {
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
                        onLongClick = {
                            sheetFor = title
                            sheetOpen = true
                        },
                        onFocusChanged = { if (it) focusedId = title.id },
                        onNoImage = { actions.onPosterless(title.id) },
                        modifier = Modifier
                            .then(if (i == startIndex) Modifier.focusRequester(startFocus) else Modifier)
                            .then(if (title.id == sheetFor?.id) Modifier.focusRequester(sheetReturn) else Modifier)
                            // Up from the top row goes to the tabs, or from the last column to Sort above it.
                            .then(if (i < COLUMNS) Modifier.focusProperties { up = if (i == COLUMNS - 1) sortFocus else tabFocus } else Modifier),
                    )
                }
            }
        }

        SheetHost(visible = sheetOpen, returnFocus = sheetReturn) {
            val title = sheetFor ?: return@SheetHost
            val inList = myList[title.id] ?: title.inMyList
            val seen = watched[title.id] ?: title.watched
            OptionsSheet(
                title = title.title,
                subtitle = title.caption.text(),
                onDismiss = { sheetOpen = false },
                leading = { PlasmaImage(title.poster, Modifier.size(60.px, 90.px).clip(PlasmaShapes.SmallPlate)) },
                options = listOf(
                    SheetOption(
                        stringResource(if (inList) R.string.remove_from_my_list else R.string.add_to_my_list),
                        onClick = {
                            myList = myList + (title.id to !inList)
                            actions.onMyList(title.id, !inList)
                        },
                        icon = if (inList) PlasmaIcons.Check else PlasmaIcons.Plus,
                    ),
                    SheetOption(
                        stringResource(if (seen) R.string.mark_unwatched else R.string.mark_watched),
                        onClick = {
                            watched = watched + (title.id to !seen)
                            actions.onWatched(title.id, !seen)
                        },
                        icon = PlasmaIcons.Check,
                    ),
                ),
            )
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

internal fun sortTitles(titles: List<VodTitle>, sort: VodSort): List<VodTitle> = when (sort) {
    VodSort.Newest -> titles.sortedByDescending { it.added }
    VodSort.AZ -> titles.sortedBy { Titles.sortKey(it.title) }
    VodSort.Year -> titles.sortedWith(compareByDescending<VodTitle> { it.year }.thenByDescending { it.added })
    VodSort.Rating -> titles.sortedWith(compareByDescending<VodTitle> { it.rating }.thenBy { Titles.sortKey(it.title) })
}

/** The caption under a focused poster, and the line under a detail page's buttons. */
@Composable
internal fun VodCaption.text(): String = when (this) {
    is VodCaption.Movie -> listOfNotNull(year?.toString(), genre, minutes?.let { Formats.duration(Duration.ofMinutes(it.toLong())) }).joinToString(" · ")
    is VodCaption.ResumeEpisode ->
        stringResource(R.string.resume_episode_caption, season, episode, Formats.duration(Duration.ofMinutes(leftMinutes.toLong())))
    is VodCaption.NextEpisode -> stringResource(R.string.next_episode_caption, season, episode)
    is VodCaption.Series -> listOfNotNull(years, genre, seasons?.let { pluralStringResource(R.plurals.seasons, it, it) }).joinToString(" · ")
}

/** A category locked with the parental PIN (brief 5.12): what it is, and Enter PIN. */
@Composable
private fun LockedCategory(name: String, kind: VodKind, onUnlock: () -> Unit, buttonFocus: FocusRequester, modifier: Modifier) {
    Column(modifier.width(900.px), verticalArrangement = Arrangement.spacedBy(14.px)) {
        PlasmaIcon(PlasmaIcons.Lock, 40.px, tint = PlasmaColors.Text2)
        PlasmaText(stringResource(R.string.category_locked, name), plasmaStyle(30f, 600, trackingPx = -0.4f))
        PlasmaText(
            stringResource(if (kind == VodKind.Movies) R.string.category_locked_movies else R.string.category_locked_series),
            plasmaStyle(23f, 400, lineHeight = 1.4f),
            color = PlasmaColors.Text2,
        )
        PlasmaButton(stringResource(R.string.enter_pin), onClick = onUnlock, modifier = Modifier.padding(top = 12.px).focusRequester(buttonFocus), icon = PlasmaIcons.Lock)
    }
}

/** No source has movies (or series) yet: the tabs, and a word on where they come from. */
@Composable
private fun NoCatalog(state: VodBrowseUiState, actions: VodBrowseActions, modifier: Modifier) {
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        tabFocus.requestFocus()
    }
    Box(modifier.fillMaxSize()) {
        TopBar(
            current = if (state.kind == VodKind.Movies) TopTab.Movies else TopTab.Series,
            profileInitial = state.profileInitial,
            profileName = state.profileName,
            onTab = actions.onTab,
            onSettings = actions.onSettings,
            onProfile = actions.onProfile,
            currentTabFocusRequester = tabFocus,
        )
        PlasmaText(
            stringResource(if (state.kind == VodKind.Movies) R.string.no_movies else R.string.no_series),
            plasmaStyle(26f, 500, lineHeight = 1.4f),
            Modifier.align(Alignment.Center).width(900.px),
            color = PlasmaColors.Text2,
            align = TextAlign.Center,
        )
    }
}
