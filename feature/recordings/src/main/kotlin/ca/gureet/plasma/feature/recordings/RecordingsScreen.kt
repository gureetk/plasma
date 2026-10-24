// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ca.gureet.plasma.core.design.LocalCanRecord
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.component.ChoiceButton
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.ColorKeyHint
import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.core.design.component.CountedList
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.KeyFooter
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaGrid
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import kotlin.math.roundToInt
import ca.gureet.plasma.core.design.R as DesignR

private const val COLUMNS = 4

/** The grid starts 16 px above its first row (y 414) so a folder's layers have room. */
private const val GRID_TOP_PX = 398
private val FolderRoom = 16.px

/** Two whole rows fit above the footer; further rows scroll up into view. */
private const val GRID_BOTTOM_PX = 984

/** Changes made on this screen, until recordings are stored for real (M4). */
@Immutable
private data class RecordingEdits(
    val deleted: Set<String> = emptySet(),
    val kept: Map<String, Boolean> = emptyMap(),
    val watched: Map<String, Boolean> = emptyMap(),
    /** Scheduled recordings, reminders and rules taken away. */
    val cancelled: Set<String> = emptySet(),
) {
    fun applyTo(recordings: List<RecordingItem>): List<RecordingItem> = recordings
        .filter { it.id !in deleted }
        .map { recording ->
            var edited = recording
            kept[recording.id]?.let { edited = edited.copy(kept = it) }
            watched[recording.id]?.let { edited = edited.copy(watched = it, unwatched = !it, resume = null) }
            edited
        }
}

/** The server [recordings] have a copy on, when one has (brief 5.6). */
private fun copiedTo(recordings: List<RecordingItem>): String? = recordings.firstNotNullOfOrNull { (it.copy as? RecordingCopy.Copied)?.host }

private sealed interface RecordingsSheet {
    data class Tile(val tile: RecordedTile) : RecordingsSheet
    /** Deleting [ids]: [copiedTo] is the server one has a copy on, when one has (brief 5.6). */
    data class Delete(val ids: Set<String>, val title: String?, val stopsRecording: Boolean, val copiedTo: String? = null) : RecordingsSheet
    data class Scheduled(val item: ScheduledItem) : RecordingsSheet
    data class Reminder(val item: ReminderItem) : RecordingsSheet
    data class Rule(val rule: SeriesRuleItem) : RecordingsSheet

    /** A conflict's recordings, to choose which to skip. */
    data class Conflicting(val items: List<ScheduledItem>, val limit: Int) : RecordingsSheet
}

/**
 * Recordings (board 17, brief 4.17): filters and the storage card on the left, then Coming Up and
 * what's been recorded, newest first, in a four-column grid. A series' episodes sit in a folder; OK
 * opens it, and Back closes it. Red deletes, green keeps a recording from automatic deletion,
 * yellow changes the order and blue starts selecting several to delete. A guest only watches
 * them, and sorts them (brief 5.11).
 */
@Composable
fun RecordingsScreen(state: RecordingsUiState, actions: RecordingsActions, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val canRecord = LocalCanRecord.current
    var filter by rememberSaveable { mutableStateOf(RecordingFilter.All) }
    var sort by rememberSaveable { mutableStateOf(RecordingSort.Newest) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var edits by remember(state) { mutableStateOf(RecordingEdits()) }
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var sheet by remember { mutableStateOf<RecordingsSheet?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    var focusedListId by remember { mutableStateOf<String?>(null) }

    val recordings = remember(state.recordings, edits) { edits.applyTo(state.recordings) }
    val scheduled = state.scheduled.filterNot { it.id in edits.cancelled }
    val reminders = state.reminders.filterNot { it.id in edits.cancelled }
    val rules = state.rules.filterNot { it.id in edits.cancelled }
    val tiles = remember(recordings, filter, sort, folder) { recordedTiles(recordings, filter, sort, folder) }
    val openSeries = folder?.let { id -> recordings.firstNotNullOfOrNull { r -> r.series?.takeIf { it.id == id } } }
    val focusedTile = tiles.firstOrNull { it.id == focusedId }

    val filtersFocus = remember { FocusRequester() }
    val startFocus = remember { FocusRequester() }
    val sheetReturn = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    var gridHasFocus by remember { mutableStateOf(false) }
    var focusNewGrid by remember { mutableStateOf(false) }
    var focusNewList by remember { mutableStateOf(false) }
    // Bumped when the focused tile goes away (deleted), so focus moves to the one beside it.
    var refocus by remember { mutableIntStateOf(0) }
    val startIndex = tiles.indexOfFirst { it.id == focusedId }.coerceAtLeast(0)

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (tiles.isNotEmpty()) startFocus.requestFocus() else filtersFocus.requestFocus()
    }
    // OK on Scheduled or Series Rules moves focus into the list once it's there.
    LaunchedEffect(filter) {
        if (focusNewList && !filter.showsRecordings) {
            focusNewList = false
            withFrameNanos { }
            listFocus.requestFocus()
        }
    }
    LaunchedEffect(refocus) {
        if (refocus == 0) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (tiles.isNotEmpty()) startFocus.requestFocus() else filtersFocus.requestFocus()
    }

    fun openSheet(next: RecordingsSheet, forId: String) {
        sheet = next
        sheetFor = forId
        sheetOpen = true
    }

    fun delete(ids: Set<String>, everywhere: Boolean = false) {
        // When the focused tile goes, focus moves to the one after it, or the one before at the end.
        val survivors = tiles.filterNot { tile -> tile.recordings.all { it.id in ids } }
        if (survivors.none { it.id == focusedId }) {
            val index = tiles.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 }
                ?: tiles.indexOfFirst { tile -> tile.recordings.any { it.id in ids } }
            focusedId = (tiles.drop(index + 1).firstOrNull { it in survivors } ?: tiles.take(index).lastOrNull { it in survivors })?.id
        }
        edits = edits.copy(deleted = edits.deleted + ids)
        selection = selection - ids
        if (folder != null && recordings.none { it.series?.id == folder && it.id !in ids }) folder = null
        if (everywhere) actions.onDeleteEverywhere(ids) else actions.onDelete(ids)
        refocus++
    }

    fun keep(tile: RecordedTile) {
        val keepAll = !tile.recordings.all { it.kept }
        edits = edits.copy(kept = edits.kept + tile.recordings.associate { it.id to keepAll })
        tile.recordings.forEach { actions.onKeep(it.id, keepAll) }
    }

    fun markWatched(tile: RecordedTile) {
        val watched = !tile.recordings.all { it.watched }
        edits = edits.copy(watched = edits.watched + tile.recordings.associate { it.id to watched })
        tile.recordings.forEach { actions.onWatched(it.id, watched) }
    }

    fun askToDelete(tile: RecordedTile) {
        val ids = tile.recordings.map { it.id }.toSet()
        val single = (tile as? RecordedTile.Single)?.recording
        openSheet(RecordingsSheet.Delete(ids, single?.title, stopsRecording = single?.inProgress == true, copiedTo = copiedTo(tile.recordings)), tile.id)
    }

    fun openTile(tile: RecordedTile) {
        when {
            selecting -> {
                val ids = tile.recordings.map { it.id }.toSet()
                selection = if (selection.containsAll(ids)) selection - ids else selection + ids
            }
            tile is RecordedTile.Folder -> {
                folder = tile.series.id
                focusedId = null
                focusNewGrid = true
            }
            tile is RecordedTile.Single -> actions.onPlay(tile.recording)
        }
    }

    fun onKey(event: KeyEvent): Boolean {
        if (sheetOpen) return false
        val up = event.type == KeyEventType.KeyUp
        val tile = focusedTile.takeIf { gridHasFocus }
        return when (event.key) {
            Key.Back -> when {
                selecting -> {
                    if (up) {
                        selecting = false
                        selection = emptySet()
                    }
                    true
                }
                folder != null -> {
                    if (up) {
                        focusedId = "series:$folder"
                        folder = null
                        focusNewGrid = gridHasFocus
                    }
                    true
                }
                else -> false
            }
            Key.ProgramRed -> {
                // A guest can cancel its own reminders, but no recording (5.11).
                val reminder = filter == RecordingFilter.Scheduled && focusedListId?.let { id -> scheduled.none { it.id == id } } == true
                if (up && (canRecord || reminder)) {
                    when {
                        selecting && selection.isNotEmpty() ->
                            openSheet(
                                RecordingsSheet.Delete(
                                    selection,
                                    null,
                                    recordings.any { it.inProgress && it.id in selection },
                                    copiedTo(recordings.filter { it.id in selection }),
                                ),
                                focusedId.orEmpty(),
                            )
                        tile != null && !selecting -> askToDelete(tile)
                        filter == RecordingFilter.Scheduled -> focusedListId?.let { id ->
                            edits = edits.copy(cancelled = edits.cancelled + id)
                            if (scheduled.any { it.id == id }) actions.onCancelScheduled(id) else actions.onCancelReminder(id)
                        }
                        filter == RecordingFilter.SeriesRules -> focusedListId?.let { id ->
                            edits = edits.copy(cancelled = edits.cancelled + id)
                            actions.onDeleteRule(id)
                        }
                    }
                }
                true
            }
            Key.ProgramGreen -> {
                if (up && tile != null && !selecting && canRecord) keep(tile)
                true
            }
            Key.ProgramYellow -> {
                if (up && filter.showsRecordings) {
                    focusNewGrid = gridHasFocus
                    sort = sort.next()
                }
                true
            }
            Key.ProgramBlue -> {
                if (up && filter.showsRecordings && canRecord) {
                    selecting = !selecting
                    selection = emptySet()
                }
                true
            }
            else -> false
        }
    }

    Box(modifier.fillMaxSize().onPreviewKeyEvent(::onKey)) {
        SecondaryHeader(stringResource(DesignR.string.tab_home), stringResource(R.string.recordings))

        CountedList(
            items = filterItems(recordings, scheduled.size, rules.size),
            selectedId = filter.id,
            onFocusItem = { id ->
                filter = RecordingFilter.of(id)
                folder = null
                selecting = false
                selection = emptySet()
                focusedId = null
            },
            onOpenItem = { id ->
                val picked = RecordingFilter.of(id)
                if (picked != filter) {
                    filter = picked
                    folder = null
                    focusedId = null
                    focusNewGrid = picked.showsRecordings
                    focusNewList = !picked.showsRecordings
                } else if (!picked.showsRecordings) {
                    listFocus.requestFocus()
                } else if (tiles.isNotEmpty()) {
                    startFocus.requestFocus()
                }
            },
            modifier = Modifier
                .offset(96.px, 148.px)
                .size(290.px, 590.px)
                .focusRequester(filtersFocus),
        )
        state.storage?.let { StorageCard(it, Modifier.offset(96.px, 752.px).width(290.px)) }

        val status: @Composable () -> Unit = { ConnectionStatus(state.connectionsInUse, state.connectionLimit, state.conflicts) }
        when (filter) {
            RecordingFilter.Scheduled -> ScheduledList(
                scheduled = scheduled,
                reminders = reminders,
                status = status,
                now = now,
                onShow = actions.onShowInGuide,
                onScheduledOptions = { openSheet(RecordingsSheet.Scheduled(it), it.id) },
                onReminderOptions = { openSheet(RecordingsSheet.Reminder(it), it.id) },
                onFocusItem = { focusedListId = it },
                returnFocusId = sheetFor,
                returnFocus = sheetReturn,
                listFocus = listFocus,
            )
            RecordingFilter.SeriesRules -> RulesList(
                rules = rules,
                // A guest can't change a rule (5.11).
                onOptions = { if (canRecord) openSheet(RecordingsSheet.Rule(it), it.id) },
                onFocusItem = { focusedListId = it },
                returnFocusId = sheetFor,
                returnFocus = sheetReturn,
                listFocus = listFocus,
            )
            else -> {
                ComingUp(
                    items = scheduled,
                    status = status,
                    now = now,
                    // A recording in a conflict says what it overlaps, to choose what to skip.
                    onOpen = { item ->
                        val conflict = item.conflict
                        if (conflict == null || !canRecord) {
                            actions.onShowInGuide(item.channelName, item.start)
                        } else {
                            openSheet(RecordingsSheet.Conflicting(scheduled.filter { it.conflict?.group == conflict.group }, conflict.limit), item.id)
                        }
                    },
                    onOptions = { openSheet(RecordingsSheet.Scheduled(it), it.id) },
                    returnFocusId = sheetFor,
                    returnFocus = sheetReturn,
                    modifier = Modifier.offset(430.px, 148.px).width(1394.px),
                )
                RecordedHeader(
                    title = openSeries?.title ?: stringResource(R.string.recorded),
                    summary = recordedSummary(tiles, openSeries != null, now.toInstant()),
                    selected = selection.size.takeIf { selecting },
                    sortLabel = sort.label(),
                    onSort = {
                        focusNewGrid = false
                        sort = sort.next()
                    },
                    modifier = Modifier.offset(430.px, 340.px).width(1394.px),
                )
                if (tiles.isEmpty()) {
                    PlasmaText(
                        stringResource(if (recordings.isEmpty()) R.string.empty_recordings else R.string.empty_filter),
                        plasmaStyle(23f, 400),
                        Modifier.offset(430.px, 414.px),
                        color = PlasmaColors.Text2,
                    )
                }
                key(filter, sort, folder) {
                    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = startIndex / COLUMNS * COLUMNS)
                    LaunchedEffect(Unit) {
                        if (focusNewGrid) {
                            focusNewGrid = false
                            withFrameNanos { }
                            if (tiles.isNotEmpty()) startFocus.requestFocus()
                        }
                    }
                    PlasmaGrid(
                        columns = COLUMNS,
                        columnGap = 28.px,
                        rowGap = 32.px,
                        state = gridState,
                        contentPadding = PaddingValues(top = FolderRoom),
                        pivot = null,
                        modifier = Modifier
                            .offset(430.px, GRID_TOP_PX.px)
                            .size(1394.px, (GRID_BOTTOM_PX - GRID_TOP_PX).px)
                            .onFocusChanged { gridHasFocus = it.hasFocus },
                    ) {
                        itemsIndexed(tiles, key = { _, tile -> tile.id }) { i, tile ->
                            RecordingTile(
                                tile = tile,
                                now = now,
                                selecting = selecting,
                                selected = tile.recordings.all { it.id in selection },
                                onClick = { openTile(tile) },
                                onLongClick = { if (!selecting) openSheet(RecordingsSheet.Tile(tile), tile.id) },
                                onFocusChanged = { if (it) focusedId = tile.id },
                                modifier = Modifier
                                    .then(if (i == startIndex) Modifier.focusRequester(startFocus) else Modifier)
                                    .then(if (tile.id == sheetFor) Modifier.focusRequester(sheetReturn) else Modifier),
                            )
                        }
                    }
                }
            }
        }

        Footer(filter, selecting, selection.isNotEmpty(), canRecord)

        SheetHost(visible = sheetOpen, returnFocus = sheetReturn) {
            val current = sheet ?: return@SheetHost
            key(current) {
                RecordingsSheetView(
                    sheet = current,
                    now = now,
                    canRecord = canRecord,
                    onDismiss = { sheetOpen = false },
                    onPlay = actions.onPlay,
                    onOpenFolder = ::openTile,
                    onKeep = ::keep,
                    onWatched = ::markWatched,
                    onAskDelete = { tile -> askToDelete(tile) },
                    onDelete = { ids, everywhere ->
                        selecting = false
                        delete(ids, everywhere)
                    },
                    onShowInGuide = actions.onShowInGuide,
                    onCancel = { id, reminder ->
                        edits = edits.copy(cancelled = edits.cancelled + id)
                        if (reminder) actions.onCancelReminder(id) else actions.onCancelScheduled(id)
                    },
                    onDeleteRule = { id ->
                        edits = edits.copy(cancelled = edits.cancelled + id)
                        actions.onDeleteRule(id)
                    },
                    onKeepLast = actions.onKeepLast,
                )
            }
        }
    }
}

@Composable
private fun filterItems(recordings: List<RecordingItem>, scheduled: Int, rules: Int): List<CountedItem> {
    fun count(filter: RecordingFilter) = recordings.count { filter.matches(it) }
    return listOf(
        CountedItem(RecordingFilter.All.id, stringResource(R.string.filter_all), recordings.size, smart = true),
        CountedItem(RecordingFilter.Series.id, stringResource(R.string.filter_series), count(RecordingFilter.Series), smart = true),
        CountedItem(RecordingFilter.Sports.id, stringResource(R.string.filter_sports), count(RecordingFilter.Sports), smart = true),
        CountedItem(RecordingFilter.News.id, stringResource(R.string.filter_news), count(RecordingFilter.News), smart = true),
        CountedItem(RecordingFilter.Movies.id, stringResource(R.string.filter_movies), count(RecordingFilter.Movies), smart = true),
        CountedItem(RecordingFilter.Scheduled.id, stringResource(R.string.filter_scheduled), scheduled),
        CountedItem(RecordingFilter.SeriesRules.id, stringResource(R.string.filter_rules), rules),
    )
}

private fun RecordingSort.next(): RecordingSort = RecordingSort.entries[(ordinal + 1) % RecordingSort.entries.size]

@Composable
private fun RecordingSort.label(): String = stringResource(
    when (this) {
        RecordingSort.Newest -> R.string.sort_newest
        RecordingSort.Oldest -> R.string.sort_oldest
        RecordingSort.AZ -> R.string.sort_a_z
    },
)

/** "12 recordings · 31 hours", or inside a folder "3 episodes · 2 unwatched". */
@Composable
private fun recordedSummary(tiles: List<RecordedTile>, inFolder: Boolean, now: java.time.Instant): String {
    val recordings = tiles.flatMap { it.recordings }
    if (inFolder) {
        val unwatched = recordings.count { it.unwatched }
        val episodes = pluralStringResource(R.plurals.episode_count, recordings.size, recordings.size)
        return if (unwatched > 0) "$episodes · " + pluralStringResource(R.plurals.unwatched_count, unwatched, unwatched) else episodes
    }
    val total = totalRecorded(recordings, now)
    val hours = ((total.toMinutes() + 30) / 60).toInt()
    val length = if (total.toMinutes() < 60) Formats.duration(total) else pluralStringResource(R.plurals.hour_count, hours, hours)
    return pluralStringResource(R.plurals.recording_count, recordings.size, recordings.size) + " · " + length
}

/** "Recorded · 12 recordings · 31 hours", and the sort order on the right. */
@Composable
private fun RecordedHeader(
    title: String,
    summary: String,
    selected: Int?,
    sortLabel: String,
    onSort: () -> Unit,
    modifier: Modifier,
) {
    Row(modifier.size(1394.px, 52.px), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.px)) {
            PlasmaText(title, PlasmaType.Section, Modifier.alignByBaseline())
            PlasmaText(summary, plasmaStyle(22f, 500), Modifier.alignByBaseline(), color = PlasmaColors.Text3)
        }
        Spacer(Modifier.weight(1f))
        if (selected != null) {
            PlasmaText(
                pluralStringResource(R.plurals.selected_count, selected, selected),
                plasmaStyle(22f, 600),
                Modifier.padding(end = 30.px),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.px)) {
            ColorKeyHint(ColorKey.Yellow, stringResource(DesignR.string.key_sort))
            ChoiceButton(sortLabel, onClick = onSort)
        }
    }
}

/** The storage card (board 17): the drive, how full it is, and about how many hours of HD fit. */
@Composable
private fun StorageCard(storage: StorageInfo, modifier: Modifier) {
    Column(
        modifier
            .background(PlasmaColors.Surface1, RoundedCornerShape(14.px))
            .padding(start = 22.px, end = 22.px, top = 22.px, bottom = 24.px),
    ) {
        PlasmaText(stringResource(R.string.storage), plasmaStyle(20f, 600), color = PlasmaColors.Text3)
        Row(Modifier.padding(top = 10.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
            PlasmaIcon(PlasmaIcons.Drive, 24.px, tint = PlasmaColors.Text)
            PlasmaText(storage.name, plasmaStyle(24f, 600))
        }
        val percent = (storage.usedFraction * 100).roundToInt()
        val used = pluralStringResource(R.plurals.storage_used, percent, percent)
        ProgressBar(
            storage.usedFraction,
            Modifier.padding(top = 16.px).fillMaxWidth().semantics { contentDescription = used },
            height = 8.px,
            color = PlasmaColors.Text,
            track = PlasmaColors.white(0.12f),
        )
        val lines = plasmaStyle(21f, 500, lineHeight = 1.35f)
        PlasmaText(
            stringResource(R.string.storage_free, Formats.size(storage.freeBytes), Formats.size(storage.totalBytes)),
            lines,
            Modifier.padding(top = 14.px),
            color = PlasmaColors.Text2,
        )
        PlasmaText(pluralStringResource(R.plurals.storage_hours, storage.hoursInHd, storage.hoursInHd), lines, color = PlasmaColors.Text3)
    }
}

@Composable
private fun Footer(filter: RecordingFilter, selecting: Boolean, anySelected: Boolean, canRecord: Boolean) {
    val options = FooterKey(PlasmaIcons.Menu, stringResource(DesignR.string.key_options))
    when {
        filter == RecordingFilter.Scheduled -> KeyFooter(
            colorKeys = if (canRecord) listOf(ColorKey.Red to stringResource(R.string.key_cancel_recording)) else emptyList(),
            keys = listOf(FooterKey("OK", stringResource(R.string.option_show_in_guide)), options),
        )
        // A guest only sees the rules (5.11).
        filter == RecordingFilter.SeriesRules -> if (canRecord) {
            KeyFooter(
                colorKeys = listOf(ColorKey.Red to stringResource(R.string.key_delete_rule)),
                keys = listOf(FooterKey("OK", stringResource(DesignR.string.key_options))),
            )
        }
        selecting -> KeyFooter(
            colorKeys = buildList {
                if (anySelected) add(ColorKey.Red to stringResource(R.string.key_delete))
                add(ColorKey.Blue to stringResource(R.string.key_done))
            },
            keys = listOf(FooterKey("OK", stringResource(R.string.key_select))),
        )
        else -> KeyFooter(
            colorKeys = if (canRecord) {
                listOf(
                    ColorKey.Red to stringResource(R.string.key_delete),
                    ColorKey.Green to stringResource(R.string.key_keep),
                    ColorKey.Yellow to stringResource(DesignR.string.key_sort),
                    ColorKey.Blue to stringResource(R.string.key_select),
                )
            } else {
                listOf(ColorKey.Yellow to stringResource(DesignR.string.key_sort))
            },
            keys = listOf(FooterKey("OK", stringResource(DesignR.string.key_play)), options),
        )
    }
}

/** The options for a tile, a scheduled recording, a reminder or a rule, and the delete confirmation. */
@Composable
private fun RecordingsSheetView(
    sheet: RecordingsSheet,
    now: java.time.ZonedDateTime,
    canRecord: Boolean,
    onDismiss: () -> Unit,
    onPlay: (RecordingItem) -> Unit,
    onOpenFolder: (RecordedTile) -> Unit,
    onKeep: (RecordedTile) -> Unit,
    onWatched: (RecordedTile) -> Unit,
    onAskDelete: (RecordedTile) -> Unit,
    onDelete: (ids: Set<String>, everywhere: Boolean) -> Unit,
    onShowInGuide: (String, java.time.ZonedDateTime) -> Unit,
    onCancel: (id: String, reminder: Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
    onKeepLast: (ruleId: String, keepLast: Int?) -> Unit = { _, _ -> },
) {
    when (sheet) {
        is RecordingsSheet.Tile -> {
            val tile = sheet.tile
            val kept = tile.recordings.all { it.kept }
            val watched = tile.recordings.all { it.watched }
            val single = (tile as? RecordedTile.Single)?.recording
            OptionsSheet(
                title = tile.title,
                subtitle = tile.meta(now).text,
                onDismiss = onDismiss,
                options = buildList {
                    when {
                        single == null -> add(SheetOption(stringResource(R.string.option_open_folder), { onOpenFolder(tile) }, icon = PlasmaIcons.Series))
                        single.inProgress -> add(SheetOption(stringResource(R.string.option_watch_live_edge), { onPlay(single) }, icon = PlasmaIcons.StartOver))
                        else -> add(SheetOption(stringResource(R.string.option_play), { onPlay(single) }, icon = PlasmaIcons.Play))
                    }
                    // A guest only watches (5.11).
                    if (!canRecord) return@buildList
                    add(SheetOption(stringResource(if (kept) R.string.option_stop_keeping else R.string.option_keep), { onKeep(tile) }, icon = PlasmaIcons.Lock))
                    if (single?.inProgress != true) {
                        add(
                            SheetOption(
                                stringResource(if (watched) R.string.option_mark_unwatched else R.string.option_mark_watched),
                                { onWatched(tile) },
                                icon = PlasmaIcons.Check,
                            ),
                        )
                    }
                    add(SheetOption.Divider)
                    val delete = when {
                        single == null -> R.string.option_delete_all
                        single.inProgress -> R.string.option_stop_and_delete
                        else -> R.string.option_delete
                    }
                    add(SheetOption(stringResource(delete), { onAskDelete(tile) }))
                },
            )
        }
        is RecordingsSheet.Delete -> OptionsSheet(
            title = if (sheet.title != null) {
                stringResource(R.string.confirm_delete, sheet.title)
            } else {
                pluralStringResource(R.plurals.confirm_delete_many, sheet.ids.size, sheet.ids.size)
            },
            subtitle = when {
                sheet.stopsRecording -> stringResource(R.string.confirm_stop_detail)
                sheet.copiedTo != null -> stringResource(R.string.confirm_delete_copied, sheet.copiedTo)
                else -> stringResource(R.string.confirm_delete_detail)
            },
            onDismiss = onDismiss,
            // Cancel comes first, so a second OK press can't delete by accident.
            options = if (sheet.copiedTo != null && !sheet.stopsRecording) {
                // With a copy on the network folder: from this TV, or everywhere (brief 5.6).
                listOf(
                    SheetOption(stringResource(R.string.option_cancel), {}),
                    SheetOption(stringResource(R.string.option_delete_here), { onDelete(sheet.ids, false) }),
                    SheetOption(
                        stringResource(R.string.option_delete_everywhere),
                        { onDelete(sheet.ids, true) },
                        note = stringResource(R.string.delete_everywhere_note, sheet.copiedTo),
                    ),
                )
            } else {
                listOf(
                    SheetOption(stringResource(R.string.option_cancel), {}),
                    SheetOption(stringResource(if (sheet.stopsRecording) R.string.option_stop_and_delete else R.string.option_delete), { onDelete(sheet.ids, false) }),
                )
            },
        )
        is RecordingsSheet.Scheduled -> OptionsSheet(
            title = sheet.item.title,
            subtitle = "${sheet.item.channelName} · ${comingUpWhen(sheet.item, now)}",
            onDismiss = onDismiss,
            options = listOfNotNull(
                SheetOption(stringResource(R.string.option_show_in_guide), { onShowInGuide(sheet.item.channelName, sheet.item.start) }, icon = PlasmaIcons.Guide),
                SheetOption(stringResource(R.string.option_cancel_recording), { onCancel(sheet.item.id, false) }, icon = PlasmaIcons.StopSquare).takeIf { canRecord },
            ),
        )
        is RecordingsSheet.Reminder -> OptionsSheet(
            title = sheet.item.title,
            subtitle = "${sheet.item.channelName} · ${scheduleWhen(sheet.item.start, null, now)}",
            onDismiss = onDismiss,
            options = listOf(
                SheetOption(stringResource(R.string.option_show_in_guide), { onShowInGuide(sheet.item.channelName, sheet.item.start) }, icon = PlasmaIcons.Guide),
                SheetOption(stringResource(R.string.option_cancel_reminder), { onCancel(sheet.item.id, true) }, icon = PlasmaIcons.Bell),
            ),
        )
        is RecordingsSheet.Rule -> {
            // OK on Keep moves to the next choice: all, then the newest 3, 5 or 10.
            val keep = sheet.rule.keepLast
            val nextKeep = KEEP_LAST_CHOICES[(KEEP_LAST_CHOICES.indexOf(keep) + 1) % KEEP_LAST_CHOICES.size]
            OptionsSheet(
                title = sheet.rule.title,
                subtitle = sheet.rule.channelName ?: stringResource(R.string.any_channel),
                onDismiss = onDismiss,
                options = listOf(
                    SheetOption(
                        stringResource(R.string.option_keep_now, keep?.let { pluralStringResource(R.plurals.keep_last_rule, it, it) } ?: stringResource(R.string.keep_all)),
                        { onKeepLast(sheet.rule.id, nextKeep) },
                        icon = PlasmaIcons.History,
                    ),
                    SheetOption(stringResource(R.string.option_delete_rule), { onDeleteRule(sheet.rule.id) }),
                ),
            )
        }
        // "You can record 2 things at once. Choose what to skip." (brief 4.35)
        is RecordingsSheet.Conflicting -> OptionsSheet(
            title = pluralStringResource(R.plurals.conflict_choose, sheet.limit, sheet.limit),
            subtitle = null,
            onDismiss = onDismiss,
            options = sheet.items.map { item ->
                SheetOption(stringResource(R.string.option_skip, item.title, comingUpWhen(item, now)), { onCancel(item.id, false) }, icon = PlasmaIcons.StopSquare)
            },
        )
    }
}
