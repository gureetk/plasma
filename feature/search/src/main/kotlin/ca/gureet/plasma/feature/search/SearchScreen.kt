// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.LocalRemoteMirror
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.R as DesignR
import ca.gureet.plasma.core.design.RemoteTypingField
import ca.gureet.plasma.core.design.component.CaptionedTile
import ca.gureet.plasma.core.design.component.ImageBadge
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.PosterTile
import ca.gureet.plasma.core.design.component.PlasmaRow
import ca.gureet.plasma.core.design.component.ProgramSheet
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.RedDot
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.delay

// ---- State ---------------------------------------------------------------------------------------

enum class ProgramResultKind { Live, CatchUp, Upcoming }

data class ProgramResult(
    val id: String,
    val title: String,
    val kind: ProgramResultKind,
    val channelId: String,
    val channelNumber: String,
    val channelName: String,
    val logo: String?,
    /** The program's art, or null for a card with just the channel's logo. */
    val art: String?,
    val start: Instant,
    val end: Instant,
    val artAlignment: Alignment = Alignment.Center,
    val artZoom: Float = 1f,
    val zoomPivot: TransformOrigin = TransformOrigin.Center,
    val recordingScheduled: Boolean = false,
    /** A reminder is set for it (brief 5.7). */
    val reminder: Boolean = false,
    /** A Record Series rule has its title (brief 5.6). */
    val seriesRule: Boolean = false,
)

data class TitleResult(val id: String, val title: String, val poster: String?, val series: Boolean)

/** A channel whose name matches, guide or not: "CP24" finds the channel itself (the owner, 28 October). */
data class ChannelResult(
    val id: String,
    val number: String,
    val name: String,
    val logo: String?,
    /** What's on it now, from the guide; null without one. */
    val nowTitle: String? = null,
)

data class SearchResults(
    val channels: List<ChannelResult> = emptyList(),
    val programs: List<ProgramResult> = emptyList(),
    val titles: List<TitleResult> = emptyList(),
    val suggestions: List<String> = emptyList(),
)

class SearchActions(
    val onTab: (TopTab) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onProfile: () -> Unit = {},
    /** OK on a live or catch-up program; one coming up opens Remind Me and Record instead. */
    val onProgram: (ProgramResult) -> Unit = {},
    val onTitle: (TitleResult) -> Unit = {},
    /** OK on a channel: it plays. */
    val onChannel: (ChannelResult) -> Unit = {},
    val onVoice: () -> Unit = {},
    /** Remind Me or Cancel Reminder on a program coming up. */
    val onRemind: (channelId: String, programId: String, on: Boolean) -> Unit = { _, _, _ -> },
    /** Record, or Cancel Recording, on a program coming up (brief 5.6). */
    val onRecord: (channelId: String, programId: String, on: Boolean) -> Unit = { _, _, _ -> },
    /** Record Series, or Cancel Series, on a program coming up: a rule for its title on its channel. */
    val onRecordSeries: (channelId: String, programId: String, on: Boolean) -> Unit = { _, _, _ -> },
    /** A result was opened, so the query joins the recent searches (brief 5.10). */
    val onSearched: (query: String) -> Unit = {},
    /** A movie found shows without a poster: the app may find its own (the owner's #81). */
    val onPosterless: (id: String) -> Unit = {},
)

enum class SearchFocus { Keyboard, Results }

// ---- Screen --------------------------------------------------------------------------------------

/**
 * Search (board 03, brief 4.3): the query; a QWERTY keyboard on the left with suggestions under
 * it; and to its right, channels, then live, upcoming and catch-up programs, then movies and
 * series. Results follow every key press. The owner chose the keyboard and the channels over board
 * 03's one-line keyboard (28 October).
 *
 * @param search finds results for a query: the sample world's, or the source's guide.
 */
@Composable
fun SearchScreen(
    search: suspend (String) -> SearchResults,
    recentSearches: List<String>,
    actions: SearchActions,
    profileName: String,
    profileInitial: String,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    initialFocus: SearchFocus = SearchFocus.Keyboard,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf(SearchResults()) }
    // The query the results are for; null until the first search is back.
    var searched by remember { mutableStateOf<String?>(null) }
    val keyboard = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    // Remind Me, Record and their undoing show at once; the app keeps them.
    var reminders by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var recordings by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var sheetId by remember { mutableStateOf<String?>(null) }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    val sheetReturn = remember { FocusRequester() }

    // A phone remote types into the query (brief 5.21), Done goes to the results, and the phone
    // shows what's found: "4 programs · 2 movies".
    val mirror = LocalRemoteMirror.current
    val fieldLabel = stringResource(DesignR.string.tab_search)
    val resources = LocalContext.current.resources
    DisposableEffect(mirror, fieldLabel, resources) {
        val field = RemoteTypingField(
            label = fieldLabel,
            text = { query },
            onText = { query = it.take(MAX_QUERY) },
            onSubmit = { if (results.hasAny) firstResult.requestFocus() },
            results = { if (searched == null || query.isBlank()) null else resultsSummary(resources, results) },
            search = true,
        )
        mirror?.open(field)
        onDispose { mirror?.close(field) }
    }

    // Results follow the keys, about 150 ms after the last one (brief 4.3); the first come at once.
    LaunchedEffect(query) {
        if (searched != null) delay(PlasmaMotion.SEARCH_DEBOUNCE_MS)
        results = search(query)
        searched = query
    }
    var placedFocus by remember { mutableStateOf(false) }
    LaunchedEffect(searched) {
        if (placedFocus || searched == null) return@LaunchedEffect
        placedFocus = true
        withFrameNanos { }
        if (initialFocus == SearchFocus.Results && results.hasAny) firstResult.requestFocus() else keyboard.requestFocus()
    }

    Box(modifier.fillMaxSize()) {
        TopBar(
            current = TopTab.Search,
            profileInitial = profileInitial,
            profileName = profileName,
            onTab = actions.onTab,
            onSettings = actions.onSettings,
            onProfile = actions.onProfile,
            focusDown = keyboard,
        )

        QueryLine(query, Modifier.offset(96.px, 132.px).width(1728.px).height(76.px))

        QwertyKeyboard(
            onCharacter = { query += it },
            onSpace = { if (query.isNotEmpty() && !query.endsWith(" ")) query += " " },
            onDelete = { query = query.dropLast(1) },
            onClear = { query = "" },
            onVoice = actions.onVoice,
            modifier = Modifier
                .offset(96.px, KEYBOARD_TOP_PX.px)
                .focusRequester(keyboard),
        )

        val suggestions = results.suggestions.map { it to false } + recentSearches.take(if (query.isBlank()) 6 else 1).map { it to true }
        if (suggestions.isNotEmpty()) {
            // Under the keyboard: completions of what's typed, and recent searches.
            Column(Modifier.offset(96.px, SUGGESTIONS_TOP_PX.px).width(KEYBOARD_WIDTH_PX.px)) {
                PlasmaText(stringResource(R.string.suggestions), PlasmaType.Section)
                Column(
                    Modifier.padding(top = 12.px).offset(x = (-18).px).focusRestoringGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.px),
                ) {
                    for ((text, recent) in suggestions.take(MAX_SUGGESTIONS)) {
                        SuggestionRow(text, query, recent, onClick = { query = text })
                    }
                }
            }
        }

        val zone = LocalPlasmaClock.current.zone
        val now = rememberNow().value.toInstant()

        // The results, to the right of the keyboard; they scroll up as focus goes down them.
        Column(
            Modifier
                .offset(RESULTS_X_PX.px, KEYBOARD_TOP_PX.px)
                .size((1920 - RESULTS_X_PX).px, (1080 - KEYBOARD_TOP_PX).px)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(36.px),
        ) {
            if (searched != null && query.isNotBlank() && !results.hasAny) {
                PlasmaText(stringResource(R.string.no_results, query.trim()), PlasmaType.Section, Modifier.padding(start = ROW_INSET_PX.px))
            }
            val firstChannel = results.channels.firstOrNull()
            val firstProgram = results.programs.firstOrNull().takeIf { firstChannel == null }
            if (results.channels.isNotEmpty()) {
                Column {
                    SectionHeader(
                        stringResource(R.string.channels),
                        pluralStringResource(R.plurals.channel_count, results.channels.size, results.channels.size),
                        Modifier.padding(start = ROW_INSET_PX.px),
                    )
                    PlasmaRow(gap = 24.px, modifier = Modifier.padding(top = 18.px), startPadding = ROW_INSET_PX.px) {
                        items(results.channels, key = { it.id }) { channel ->
                            ChannelCard(
                                channel,
                                onClick = {
                                    actions.onSearched(query.trim())
                                    actions.onChannel(channel)
                                },
                                modifier = if (channel == firstChannel) Modifier.focusRequester(firstResult) else Modifier,
                            )
                        }
                    }
                }
            }
            if (results.programs.isNotEmpty()) {
                Column {
                    SectionHeader(
                        stringResource(R.string.live_upcoming_catch_up),
                        pluralStringResource(R.plurals.program_count, results.programs.size, results.programs.size),
                        Modifier.padding(start = ROW_INSET_PX.px),
                    )
                    PlasmaRow(gap = 24.px, modifier = Modifier.padding(top = 18.px), startPadding = ROW_INSET_PX.px) {
                        items(results.programs, key = { it.id }) { program ->
                            ProgramCard(
                                program = program.copy(
                                    reminder = reminders[program.id] ?: program.reminder,
                                    recordingScheduled = recordings[program.id] ?: program.recordingScheduled,
                                ),
                                now = now,
                                zone = zone,
                                onClick = {
                                    actions.onSearched(query.trim())
                                    if (program.kind == ProgramResultKind.Upcoming) {
                                        sheetId = program.id
                                        sheetFor = program.id
                                    } else {
                                        actions.onProgram(program)
                                    }
                                },
                                modifier = Modifier
                                    .then(if (program == firstProgram) Modifier.focusRequester(firstResult) else Modifier)
                                    .then(if (program.id == sheetFor) Modifier.focusRequester(sheetReturn) else Modifier),
                            )
                        }
                    }
                }
            }
            if (results.titles.isNotEmpty()) {
                Column {
                    SectionHeader(
                        stringResource(R.string.movies_and_series),
                        pluralStringResource(R.plurals.title_count, results.titles.size, results.titles.size),
                        Modifier.padding(start = ROW_INSET_PX.px),
                    )
                    PlasmaRow(gap = 24.px, modifier = Modifier.padding(top = 18.px), startPadding = ROW_INSET_PX.px) {
                        items(results.titles, key = { it.id }) { title ->
                            // Without a poster, its name on the plate, as on Movies (the owner's #77).
                            PosterTile(
                                image = title.poster,
                                title = title.title,
                                caption = null,
                                onClick = {
                                    actions.onSearched(query.trim())
                                    actions.onTitle(title)
                                },
                                modifier = if (title == results.titles.first() && firstChannel == null && firstProgram == null) Modifier.focusRequester(firstResult) else Modifier,
                                width = 150.px,
                                height = 225.px,
                                pivot = TransformOrigin.Center,
                                showCaption = false,
                                onNoImage = { if (!title.series) actions.onPosterless(title.id) },
                            )
                        }
                    }
                }
            }
            // Room under the last row for its focused tile to grow.
            Spacer(Modifier.height(40.px))
        }

        // OK on a program coming up: Remind Me and Record (brief 4.3 and 4.35).
        val sheetProgram = results.programs.firstOrNull { it.id == sheetId }?.let {
            it.copy(reminder = reminders[it.id] ?: it.reminder, recordingScheduled = recordings[it.id] ?: it.recordingScheduled)
        }
        SheetHost(visible = sheetProgram != null, returnFocus = sheetReturn) {
            if (sheetProgram != null) {
                val start = sheetProgram.start.atZone(zone)
                ProgramSheet(
                    title = sheetProgram.title,
                    subtitle = "${dayAndTime(start, now.atZone(zone))}–${Formats.time(sheetProgram.end.atZone(zone))} · ${sheetProgram.channelName}",
                    reminder = sheetProgram.reminder,
                    recording = sheetProgram.recordingScheduled,
                    onDismiss = { sheetId = null },
                    onReminder = {
                        reminders = reminders + (sheetProgram.id to !sheetProgram.reminder)
                        actions.onRemind(sheetProgram.channelId, sheetProgram.id, !sheetProgram.reminder)
                    },
                    onRecording = {
                        recordings = recordings + (sheetProgram.id to !sheetProgram.recordingScheduled)
                        actions.onRecord(sheetProgram.channelId, sheetProgram.id, !sheetProgram.recordingScheduled)
                    },
                    series = sheetProgram.seriesRule,
                    onSeries = { actions.onRecordSeries(sheetProgram.channelId, sheetProgram.id, !sheetProgram.seriesRule) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.px)) {
        PlasmaText(title, PlasmaType.Section, Modifier.alignBy(FirstBaseline))
        PlasmaText(count, plasmaStyle(22f, 500), Modifier.alignBy(FirstBaseline), color = PlasmaColors.Text3)
    }
}

/** A channel result: its logo on a graphite card; its name, and number and what's on, under it. */
@Composable
private fun ChannelCard(channel: ChannelResult, onClick: () -> Unit, modifier: Modifier) {
    val meta = listOfNotNull(channel.number, channel.nowTitle).joinToString(" · ")
    CaptionedTile(title = channel.name, meta = meta, onClick = onClick, modifier = modifier, width = 240.px, height = 135.px) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PlasmaColors.Surface3, PlasmaColors.Plate))),
            contentAlignment = Alignment.Center,
        ) {
            LogoImage(channel.logo, channel.name, LogoSize.Card)
        }
    }
}

/** The search glyph and the query at 54 px; the voice hint on the right. */
@Composable
private fun QueryLine(query: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PlasmaIcon(PlasmaIcons.Search, 34.px, tint = PlasmaColors.Text3)
        PlasmaText(
            query.ifEmpty { stringResource(R.string.search_hint) },
            if (query.isEmpty()) plasmaStyle(40f, 500, trackingPx = -0.6f) else plasmaStyle(54f, 600, trackingPx = -1.2f),
            Modifier.padding(start = 20.px).weight(1f),
            color = if (query.isEmpty()) PlasmaColors.Text4 else PlasmaColors.Text,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
            PlasmaIcon(PlasmaIcons.Mic, 22.px, tint = PlasmaColors.Text3)
            PlasmaText(stringResource(R.string.voice_hint), plasmaStyle(21f, 500), color = PlasmaColors.Text3)
        }
    }
}

// ---- Results -------------------------------------------------------------------------------------

/** "Tonight, 00:30", "Yesterday, 19:30", "Monday, 22:00", "until 21:30" (board 03). */
@Composable
private fun whenText(program: ProgramResult, now: Instant, zone: ZoneId): String {
    val nowZoned = now.atZone(zone)
    val start = program.start.atZone(zone)
    return when (program.kind) {
        ProgramResultKind.Live -> stringResource(R.string.until, Formats.time(program.end.atZone(zone)))
        else -> dayAndTime(start, nowZoned)
    }
}

@Composable
private fun dayAndTime(time: ZonedDateTime, now: ZonedDateTime): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), time.toLocalDate())
    val clock = Formats.time(time)
    return when {
        // After midnight but before morning still reads as tonight, as on board 03.
        days == 0L && time.hour >= 18 || days == 1L && time.hour < 5 -> stringResource(R.string.tonight_at, clock)
        days == 0L -> stringResource(R.string.today_at, clock)
        days == 1L -> stringResource(R.string.tomorrow_at, clock)
        days == -1L -> stringResource(R.string.yesterday_at, clock)
        days in -6L..6L -> stringResource(R.string.weekday_at, time.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.US), clock)
        else -> "${Formats.date(time)}, $clock"
    }
}

/**
 * A program result: live (progress and LIVE), catch-up (↺ CATCH-UP), or upcoming ("TONIGHT 00:30",
 * and "Recording set"). Without art, the channel's logo stands in.
 */
@Composable
private fun ProgramCard(program: ProgramResult, now: Instant, zone: ZoneId, onClick: () -> Unit, modifier: Modifier) {
    val meta = "${program.channelNumber} · ${program.channelName} · ${whenText(program, now, zone)}"
    CaptionedTile(title = program.title, meta = meta, onClick = onClick, modifier = modifier, width = 320.px, height = 180.px) {
        if (program.art != null) {
            PlasmaImage(
                program.art,
                Modifier.fillMaxSize(),
                alignment = program.artAlignment,
                zoom = program.artZoom,
                zoomPivot = program.zoomPivot,
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to PlasmaColors.black(0.6f), 0.48f to Color.Transparent)))
            Box(Modifier.padding(start = 16.px).height(58.px), contentAlignment = Alignment.CenterStart) {
                LogoImage(program.logo, program.channelName, LogoSize.Card)
            }
        } else {
            LogoFallback(program)
        }
        when (program.kind) {
            ProgramResultKind.Live -> {
                ImageBadge(stringResource(R.string.live), Modifier.align(Alignment.TopEnd).padding(top = 14.px, end = 14.px)) { RedDot() }
                val fraction = (now.toEpochMilli() - program.start.toEpochMilli()).toFloat() / (program.end.toEpochMilli() - program.start.toEpochMilli())
                ProgressBar(fraction, Modifier.align(Alignment.BottomStart).fillMaxWidth(), height = 5.px, rounded = false)
            }
            ProgramResultKind.CatchUp -> ImageBadge(
                stringResource(R.string.section_catch_up),
                Modifier.align(Alignment.TopEnd).padding(top = 14.px, end = 14.px),
                trackingPx = 0.6f,
            ) {
                PlasmaIcon(PlasmaIcons.CatchUp, 15.px, stroke = 2.6f, tint = Color.White)
            }
            ProgramResultKind.Upcoming -> {
                ImageBadge(
                    dayAndTime(program.start.atZone(zone), now.atZone(zone)).replace(",", "").uppercase(Locale.US),
                    Modifier.align(Alignment.TopEnd).padding(top = 14.px, end = 14.px),
                    background = PlasmaColors.black(0.5f),
                    trackingPx = 0.6f,
                )
                if (program.recordingScheduled || program.reminder) {
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(start = 16.px, bottom = 14.px),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.px),
                    ) {
                        if (program.recordingScheduled) {
                            Box(Modifier.size(10.px).background(PlasmaColors.Live, CircleShape))
                            PlasmaText(stringResource(R.string.recording_set), plasmaStyle(18f, 600), color = PlasmaColors.TextSoft3)
                        } else {
                            PlasmaIcon(PlasmaIcons.Bell, 18.px, tint = PlasmaColors.TextSoft3)
                            PlasmaText(stringResource(R.string.reminder_set), plasmaStyle(18f, 600), color = PlasmaColors.TextSoft3)
                        }
                    }
                }
            }
        }
    }
}

/** No art: the channel's logo, large, on a graphite card. */
@Composable
private fun BoxScope.LogoFallback(program: ProgramResult) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PlasmaColors.Surface3, PlasmaColors.Plate))),
        contentAlignment = Alignment.Center,
    ) {
        LogoImage(program.logo, program.channelName, LogoSize.Fallback)
    }
}

// ---- Suggestions ---------------------------------------------------------------------------------

/** A completion with the typed part in white, or a recent search with a clock. */
@Composable
private fun SuggestionRow(text: String, query: String, recent: Boolean, onClick: () -> Unit) {
    PlasmaSurface(
        onClick = onClick,
        modifier = Modifier.height(56.px),
        style = FocusStyle.Row,
        shape = PlasmaShapes.Button,
        contentAlignment = Alignment.CenterStart,
    ) {
        val focused = LocalFocused.current
        Row(
            Modifier.padding(horizontal = 18.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.px),
        ) {
            PlasmaIcon(
                if (recent) PlasmaIcons.History else PlasmaIcons.Search,
                22.px,
                stroke = 2f,
                tint = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text3,
            )
            val range = if (recent) null else matchRange(text, query)
            PlasmaText(
                buildAnnotatedString {
                    if (range == null) {
                        append(text)
                    } else {
                        append(text.substring(0, range.first))
                        withStyle(SpanStyle(color = if (focused) PlasmaColors.OnFocus else PlasmaColors.Text, fontWeight = FontWeight.W600)) {
                            append(text.substring(range.first, range.last + 1))
                        }
                        append(text.substring(range.last + 1))
                    }
                    if (recent) {
                        withStyle(SpanStyle(color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text3)) {
                            append(" ")
                            append(stringResource(R.string.recent_suffix))
                        }
                    }
                },
                plasmaStyle(24f, 500),
                color = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.TextSoft,
            )
        }
    }
}

/** As long as a search gets: what a phone types is cut there (brief 5.21). */
private const val MAX_QUERY = 100

/** Where the keyboard starts, under the query; the results start level with it. */
private const val KEYBOARD_TOP_PX = 236

/** Suggestions under the keyboard, and as many as fit. */
private const val SUGGESTIONS_TOP_PX = KEYBOARD_TOP_PX + KEYBOARD_HEIGHT_PX + 44
private const val MAX_SUGGESTIONS = 6

/** The results' column, right of the keyboard, and their rows' inset, room for a focused tile to grow. */
private const val RESULTS_X_PX = 96 + KEYBOARD_WIDTH_PX + 36
private const val ROW_INSET_PX = 24

/** Anything to show. */
internal val SearchResults.hasAny: Boolean get() = channels.isNotEmpty() || programs.isNotEmpty() || titles.isNotEmpty()

/** What a search found, as the phone remote's Type tab says it: "4 programs · 2 movies" (brief 4.33). */
internal fun resultsSummary(resources: Resources, results: SearchResults): String {
    val movies = results.titles.count { !it.series }
    val series = results.titles.size - movies
    return buildList {
        if (results.channels.isNotEmpty()) add(resources.getQuantityString(R.plurals.channel_count, results.channels.size, results.channels.size))
        add(resources.getQuantityString(R.plurals.program_count, results.programs.size, results.programs.size))
        if (movies > 0) add(resources.getQuantityString(R.plurals.movie_count, movies, movies))
        if (series > 0) add(resources.getQuantityString(R.plurals.series_count, series, series))
    }.joinToString(" · ")
}

