// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.PlasmaIconButton
import ca.gureet.plasma.core.design.component.PlasmaRow
import ca.gureet.plasma.core.design.component.PosterTile
import ca.gureet.plasma.core.design.component.TabPill
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.Duration

/** The tabs under a movie's buttons (brief 4.14). */
private enum class MovieTab(val label: Int) {
    MoreLikeThis(R.string.tab_more_like_this),
    CastAndCrew(R.string.tab_cast_and_crew),
    AudioAndSubtitles(R.string.tab_audio_and_subtitles),
    FileInfo(R.string.tab_file_info),
}

/**
 * Movie Detail (board 14, brief 4.14): the backdrop, title art, meta, synopsis and credits, then
 * Resume (focused), From the Beginning, Trailer and + (My List) with what's left, and tabs over a
 * row of similar titles. A movie that hasn't been started offers Play instead.
 */
@Composable
fun MovieDetailScreen(state: MovieDetailUiState, actions: MovieDetailActions, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(MovieTab.MoreLikeThis) }
    var inMyList by remember(state.id) { mutableStateOf(state.inMyList) }
    val primary = remember { FocusRequester() }
    val selectedTab = remember { FocusRequester() }
    val started = state.leftMinutes != null

    LaunchedEffect(Unit) {
        withFrameNanos { }
        primary.requestFocus()
    }

    Box(modifier.fillMaxSize()) {
        DetailBackground(state.backdrop, DetailScrims.Movie)
        DetailHeader(stringResource(R.string.back_movies), actions.onBack)

        Column(Modifier.offset(96.px, 178.px).width(960.px)) {
            TitleArt(state.titleArt, state.title)
            MetaLine(
                parts = state.genres + state.year.toString() + Formats.duration(Duration.ofMinutes(state.minutes.toLong())),
                badges = state.badges,
                modifier = Modifier.padding(top = 28.px),
            )
            Synopsis(state.synopsis, maxWidthPx = 860, modifier = Modifier.padding(top = 20.px))
            val credits = listOfNotNull(
                state.cast.joinToString(", ") { it.name }.takeIf { it.isNotEmpty() },
                state.director?.let { stringResource(R.string.directed_by, it) },
            )
            if (credits.isNotEmpty()) {
                PlasmaText(
                    credits.joinToString(" · "),
                    plasmaStyle(21f, 500),
                    Modifier.padding(top = 12.px),
                    color = PlasmaColors.Text3,
                )
            }
            Row(
                Modifier.padding(top = 34.px).focusRestoringGroup(primary),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.px),
            ) {
                PrimaryPlayButton(
                    text = stringResource(if (started) R.string.resume else R.string.play),
                    onClick = { actions.onPlay(false) },
                    modifier = Modifier.padding(end = 10.px).focusRequester(primary),
                )
                if (started) {
                    PlasmaButton(
                        stringResource(R.string.from_the_beginning),
                        onClick = { actions.onPlay(true) },
                        icon = PlasmaIcons.CatchUp,
                        iconSize = 22.px,
                        iconStroke = 2f,
                    )
                }
                if (state.hasTrailer) PlasmaButton(stringResource(R.string.trailer), onClick = actions.onTrailer)
                MyListButton(inMyList) {
                    inMyList = !inMyList
                    actions.onMyList(inMyList)
                }
            }
            if (state.leftMinutes != null) {
                ProgressLine(
                    fraction = 1f - state.leftMinutes / state.minutes.toFloat(),
                    text = stringResource(R.string.left, Formats.duration(Duration.ofMinutes(state.leftMinutes.toLong()))),
                    modifier = Modifier.padding(top = 18.px),
                )
            }
        }

        Column(Modifier.offset(0.px, 784.px).fillMaxWidth()) {
            Row(
                Modifier.padding(start = 96.px).focusRestoringGroup(selectedTab),
                horizontalArrangement = Arrangement.spacedBy(6.px),
            ) {
                for (t in MovieTab.entries) {
                    TabPill(
                        stringResource(t.label),
                        selected = t == tab,
                        onClick = { tab = t },
                        onFocusChanged = { if (it) tab = t },
                        modifier = if (t == tab) Modifier.focusRequester(selectedTab) else Modifier,
                    )
                }
            }
            Box(Modifier.padding(top = 20.px).height(225.px)) {
                when (tab) {
                    MovieTab.MoreLikeThis -> PlasmaRow(gap = 22.px) {
                        items(state.moreLikeThis, key = { it.id }) { title ->
                            PosterTile(
                                image = title.poster,
                                title = title.title,
                                caption = null,
                                onClick = { actions.onTitle(title.id) },
                                width = 150.px,
                                height = 225.px,
                                shape = PlasmaShapes.PosterSmall,
                                // Grows upwards: the row sits at the bottom of the screen.
                                pivot = TransformOrigin(0.5f, 1f),
                                showCaption = false,
                            )
                        }
                    }
                    MovieTab.CastAndCrew -> {
                        val director = stringResource(R.string.director)
                        val people = state.cast + listOfNotNull(state.director?.let { Person(it, director) })
                        PlasmaRow(gap = 22.px) {
                            items(people, key = { it.name }) { person -> PersonCard(person) { actions.onPerson(person.name) } }
                        }
                    }
                    MovieTab.AudioAndSubtitles -> Row(Modifier.padding(start = 96.px), horizontalArrangement = Arrangement.spacedBy(96.px)) {
                        TrackList(stringResource(R.string.audio), state.audio)
                        TrackList(stringResource(R.string.subtitle_tracks), state.subtitles)
                    }
                    MovieTab.FileInfo -> FileInfo(state.playsWith, state.fileInfo, Modifier.padding(start = 96.px))
                }
            }
        }
    }
}

/** Resume or Play: the page's default action, a size up from the other buttons, with ▶. */
@Composable
internal fun PrimaryPlayButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PlasmaButton(
        text,
        onClick = onClick,
        modifier = modifier,
        icon = PlasmaIcons.Play,
        textSizePx = 25f,
        startPadding = 28.px,
        endPadding = 34.px,
        gap = 12.px,
        // It leads the row, so it grows to the right; the extra space after it keeps the gap.
        pivot = TransformOrigin(0f, 0.5f),
    )
}

/** + adds to My List; ✓ shows it's there and takes it off again. */
@Composable
internal fun MyListButton(inMyList: Boolean, onClick: () -> Unit) {
    PlasmaIconButton(
        icon = if (inMyList) PlasmaIcons.Check else PlasmaIcons.Plus,
        contentDescription = stringResource(if (inMyList) R.string.remove_from_my_list else R.string.add_to_my_list),
        onClick = onClick,
        stroke = if (inMyList) 2.6f else 2.2f,
    )
}

/** A name in Cast & Crew: initials in a circle (photos come with real metadata), the name and the role. */
@Composable
private fun PersonCard(person: Person, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(180.px), horizontalAlignment = Alignment.CenterHorizontally) {
        PlasmaSurface(
            onClick = onClick,
            modifier = Modifier.size(132.px).semantics { contentDescription = person.name },
            style = FocusStyle.Avatar,
            shape = CircleShape,
            color = PlasmaColors.Surface3,
            onFocusChanged = { focused = it },
            contentAlignment = Alignment.Center,
        ) {
            val initials = person.name.split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
            PlasmaText(initials, plasmaStyle(40f, 600, trackingPx = 1f), color = if (LocalFocused.current) PlasmaColors.Text else PlasmaColors.TextSoft)
        }
        PlasmaText(
            person.name,
            plasmaStyle(21f, if (focused) 600 else 500),
            Modifier.padding(top = 14.px),
            color = if (focused) PlasmaColors.Text else PlasmaColors.TextSoft,
            align = TextAlign.Center,
        )
        PlasmaText(
            person.role,
            plasmaStyle(19f, 500),
            Modifier.padding(top = 2.px),
            color = PlasmaColors.Text3,
            align = TextAlign.Center,
        )
    }
}

/** A heading and one line per track: "English · 5.1 · Dolby Digital Plus". */
@Composable
private fun TrackList(heading: String, tracks: List<String>) {
    Column(Modifier.width(560.px), verticalArrangement = Arrangement.spacedBy(10.px)) {
        PlasmaText(heading, plasmaStyle(21f, 600), color = PlasmaColors.Text3)
        if (tracks.isEmpty()) {
            PlasmaText(stringResource(R.string.none_listed), plasmaStyle(23f, 500), color = PlasmaColors.Text2)
        }
        for (track in tracks) PlasmaText(track, plasmaStyle(23f, 500), color = PlasmaColors.TextSoft)
    }
}

/** The player Plasma will use and why, then the file's details (brief 4.14). */
@Composable
private fun FileInfo(playsWith: String?, rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.px)) {
        if (playsWith != null) {
            PlasmaText(playsWith, plasmaStyle(23f, 600), Modifier.padding(bottom = 6.px), color = PlasmaColors.Text)
        }
        for ((label, value) in rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlasmaText(label, plasmaStyle(21f, 500), Modifier.width(180.px), color = PlasmaColors.Text3)
                PlasmaText(value, plasmaStyle(23f, 500, tabular = true), color = PlasmaColors.TextSoft)
            }
        }
    }
}
