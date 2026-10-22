// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.component.CaptionedTile
import ca.gureet.plasma.core.design.component.ImageBadge
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.PlasmaRow
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.RedDot
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
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
)

data class TitleResult(val id: String, val title: String, val poster: String?, val series: Boolean)

data class SearchResults(
    val programs: List<ProgramResult> = emptyList(),
    val titles: List<TitleResult> = emptyList(),
    val suggestions: List<String> = emptyList(),
)

class SearchActions(
    val onTab: (TopTab) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onProgram: (ProgramResult) -> Unit = {},
    val onTitle: (TitleResult) -> Unit = {},
    val onVoice: () -> Unit = {},
)

enum class SearchFocus { Keyboard, Results }

// ---- Screen --------------------------------------------------------------------------------------

/**
 * Search (board 03, brief 4.3): the query, a linear keyboard, then live, upcoming and catch-up
 * programs, movies and series, and suggestions. Results follow every key press.
 *
 * @param search finds results for a query; in M0 it searches the sample world.
 */
@Composable
fun SearchScreen(
    search: (String) -> SearchResults,
    recentSearches: List<String>,
    actions: SearchActions,
    profileName: String,
    profileInitial: String,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    initialFocus: SearchFocus = SearchFocus.Keyboard,
) {
    var query by remember { mutableStateOf(initialQuery) }
    val results = remember(query) { search(query) }
    val keyboard = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (initialFocus == SearchFocus.Results && results.programs.isNotEmpty()) firstResult.requestFocus() else keyboard.requestFocus()
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

        LinearKeyboard(
            onCharacter = { query += it },
            onSpace = { if (query.isNotEmpty() && !query.endsWith(" ")) query += " " },
            onDelete = { query = query.dropLast(1) },
            onClear = { query = "" },
            onVoice = actions.onVoice,
            modifier = Modifier
                .offset(96.px, 220.px)
                .focusRequester(keyboard)
                .focusProperties { down = firstResult },
        )

        val clock = LocalPlasmaClock.current
        val zone = clock.zone
        val now = remember(clock) { clock.now() }

        if (query.isNotBlank() && results.programs.isEmpty() && results.titles.isEmpty()) {
            PlasmaText(
                stringResource(R.string.no_results, query.trim()),
                PlasmaType.Section,
                Modifier.offset(96.px, 316.px),
            )
        }

        if (results.programs.isNotEmpty()) {
            Column(Modifier.offset(y = 316.px)) {
                SectionHeader(
                    stringResource(R.string.live_upcoming_catch_up),
                    pluralStringResource(R.plurals.program_count, results.programs.size, results.programs.size),
                    Modifier.padding(start = 96.px),
                )
                PlasmaRow(gap = 28.px, modifier = Modifier.padding(top = 22.px).focusProperties { up = keyboard }) {
                    items(results.programs, key = { it.id }) { program ->
                        ProgramCard(
                            program = program,
                            now = now,
                            zone = zone,
                            onClick = { actions.onProgram(program) },
                            modifier = if (program == results.programs.first()) Modifier.focusRequester(firstResult) else Modifier,
                        )
                    }
                }
            }
        }

        if (results.titles.isNotEmpty()) {
            Column(Modifier.offset(96.px, 724.px).width(820.px)) {
                SectionHeader(
                    stringResource(R.string.movies_and_series),
                    pluralStringResource(R.plurals.title_count, results.titles.size, results.titles.size),
                )
                Row(Modifier.padding(top = 22.px).focusRestoringGroup(), horizontalArrangement = Arrangement.spacedBy(24.px)) {
                    for (title in results.titles.take(5)) {
                        PlasmaSurface(
                            onClick = { actions.onTitle(title) },
                            modifier = Modifier.size(150.px, 225.px).semantics { contentDescription = title.title },
                            style = FocusStyle.Tile,
                            shape = PlasmaShapes.Poster,
                            color = PlasmaColors.Surface2,
                        ) {
                            PlasmaImage(title.poster, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }

        val suggestions = results.suggestions.map { it to false } + recentSearches.take(if (query.isBlank()) 6 else 1).map { it to true }
        if (suggestions.isNotEmpty()) {
            // Up under the keyboard while there's no query; beside Movies and Series once there are results.
            val top = if (query.isBlank()) 316.px else 724.px
            Column(Modifier.offset { IntOffset(952.px.roundToPx(), top.roundToPx()) }.width(872.px)) {
                PlasmaText(stringResource(R.string.suggestions), PlasmaType.Section)
                Column(
                    Modifier.padding(top = 16.px).offset(x = (-18).px).focusRestoringGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.px),
                ) {
                    for ((text, recent) in suggestions) {
                        SuggestionRow(text, query, recent, onClick = { query = text })
                    }
                }
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
    CaptionedTile(title = program.title, meta = meta, onClick = onClick, modifier = modifier) {
        if (program.art != null) {
            PlasmaImage(
                program.art,
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = program.artZoom
                    scaleY = program.artZoom
                    transformOrigin = program.zoomPivot
                },
                alignment = program.artAlignment,
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
                stringResource(R.string.catch_up),
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
                if (program.recordingScheduled) {
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(start = 16.px, bottom = 14.px),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.px),
                    ) {
                        Box(Modifier.size(10.px).background(PlasmaColors.Live, CircleShape))
                        PlasmaText(stringResource(R.string.recording_set), plasmaStyle(18f, 600), color = PlasmaColors.TextSoft3)
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
