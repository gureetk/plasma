// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.ColorKeyHint
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.PlasmaRow
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.TabPill
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.ring
import ca.gureet.plasma.core.model.Formats
import java.time.Duration

/** Cards that fit before the row runs off the right edge; the current episode stays among them. */
private const val VISIBLE_EPISODES = 4

/**
 * Series Detail (board 16, brief 4.16): the backdrop, title art, meta and synopsis, then Resume on
 * the episode in progress (focused), Trailer and + (My List), and the seasons over a row of
 * episodes. The current episode is outlined; that marks it, it isn't focus. Green marks the
 * season watched, or unwatched again.
 */
@Composable
fun SeriesDetailScreen(state: SeriesDetailUiState, actions: SeriesDetailActions, modifier: Modifier = Modifier) {
    var seasons by remember(state.id) { mutableStateOf(state.seasons) }
    val next = upNext(seasons)
    var seasonNumber by rememberSaveable { mutableIntStateOf(next?.season ?: seasons.firstOrNull()?.number ?: 1) }
    val season = seasons.firstOrNull { it.number == seasonNumber } ?: seasons.firstOrNull()
    var inMyList by remember(state.id) { mutableStateOf(state.inMyList) }
    val primary = remember { FocusRequester() }
    val selectedSeason = remember { FocusRequester() }
    val entryEpisode = remember { FocusRequester() }
    val allWatched = season != null && season.episodes.all { it.watched }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        primary.requestFocus()
    }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.ProgramGreen && event.type == KeyEventType.KeyUp && season != null) {
                    val watched = !allWatched
                    seasons = seasons.map { s ->
                        if (s.number != season.number) s else s.copy(episodes = s.episodes.map { it.copy(watched = watched, leftMinutes = null) })
                    }
                    actions.onMarkSeasonWatched(season.number, watched)
                    true
                } else {
                    false
                }
            },
    ) {
        DetailBackground(state.backdrop, DetailScrims.Series)
        DetailHeader(stringResource(R.string.back_series), actions.onBack)

        val nextEpisode = next?.let { ref -> seasons.firstOrNull { it.number == ref.season }?.episodes?.firstOrNull { it.number == ref.episode } }
        Column(Modifier.offset(96.px, 160.px).width(1000.px)) {
            TitleArt(state.titleArt, state.title)
            MetaLine(
                parts = state.genres + state.years + pluralStringResource(R.plurals.seasons, seasons.size, seasons.size),
                badges = state.badges,
                modifier = Modifier.padding(top = 28.px),
            )
            Synopsis(state.synopsis, maxWidthPx = 880, modifier = Modifier.padding(top = 18.px))
            Row(
                Modifier.padding(top = 30.px).focusRestoringGroup(primary),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.px),
            ) {
                if (next != null) {
                    PrimaryPlayButton(
                        text = stringResource(
                            if (nextEpisode?.leftMinutes != null) R.string.resume_episode else R.string.play_episode,
                            next.season,
                            next.episode,
                        ),
                        onClick = { actions.onPlay(next) },
                        modifier = Modifier.padding(end = 12.px).focusRequester(primary),
                    )
                }
                if (state.hasTrailer) PlasmaButton(stringResource(R.string.trailer), onClick = actions.onTrailer)
                MyListButton(inMyList) {
                    inMyList = !inMyList
                    actions.onMyList(inMyList)
                }
            }
            if (nextEpisode != null) {
                val left = nextEpisode.leftMinutes
                ProgressLine(
                    fraction = left?.let { 1f - it / nextEpisode.minutes.toFloat() },
                    text = nextEpisode.title + " · " + if (left != null) {
                        stringResource(R.string.left, Formats.duration(Duration.ofMinutes(left.toLong())))
                    } else {
                        Formats.duration(Duration.ofMinutes(nextEpisode.minutes.toLong()))
                    },
                    modifier = Modifier.padding(top = 18.px),
                )
            }
        }

        if (season != null) {
            Row(Modifier.offset(96.px, 716.px).width(1728.px), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.focusRestoringGroup(selectedSeason), horizontalArrangement = Arrangement.spacedBy(6.px)) {
                    for (s in seasons) {
                        TabPill(
                            stringResource(R.string.season_n, s.number),
                            selected = s.number == season.number,
                            onClick = { seasonNumber = s.number },
                            onFocusChanged = { if (it) seasonNumber = s.number },
                            modifier = if (s.number == season.number) Modifier.focusRequester(selectedSeason) else Modifier,
                        )
                    }
                }
                PlasmaText(
                    pluralStringResource(R.plurals.episode_count, season.episodes.size, season.episodes.size),
                    plasmaStyle(21f, 500),
                    Modifier.padding(start = 20.px),
                    color = PlasmaColors.Text3,
                )
                Spacer(Modifier.weight(1f))
                ColorKeyHint(
                    ColorKey.Green,
                    stringResource(if (allWatched) R.string.key_mark_season_unwatched else R.string.key_mark_season_watched),
                )
            }

            key(season.number) {
                val currentIndex = season.episodes.indexOfFirst { next?.season == season.number && it.number == next.episode }
                val entryIndex = currentIndex.coerceAtLeast(0)
                val rowState = rememberLazyListState(
                    initialFirstVisibleItemIndex = if (entryIndex >= VISIBLE_EPISODES) entryIndex - VISIBLE_EPISODES + 1 else 0,
                )
                PlasmaRow(
                    gap = 26.px,
                    state = rowState,
                    focusFallback = entryEpisode,
                    modifier = Modifier.offset(0.px, 784.px).fillMaxWidth(),
                ) {
                    itemsIndexed(season.episodes, key = { _, episode -> episode.number }) { i, episode ->
                        val ref = EpisodeRef(season.number, episode.number)
                        EpisodeCard(
                            episode = episode,
                            current = i == currentIndex,
                            onClick = { actions.onPlay(ref) },
                            onLongClick = { actions.onEpisodeOptions(ref) },
                            modifier = if (i == entryIndex) Modifier.focusRequester(entryEpisode) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/**
 * An episode: a 336 × 189 still with a check when watched, and its number, title and length (or
 * what's left) underneath. The [current] episode has a 2 px outline at 50 % white and its progress.
 */
@Composable
private fun EpisodeCard(
    episode: EpisodeItem,
    current: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val title = stringResource(R.string.episode_title, episode.number, episode.title)
    Column(Modifier.width(336.px)) {
        Box(modifier.size(336.px, 189.px).then(if (current && !focused) Modifier.ring(2.px, PlasmaColors.white(0.5f), 12.px) else Modifier)) {
            PlasmaSurface(
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = Modifier.fillMaxSize().semantics { contentDescription = title },
                style = FocusStyle.Tile,
                shape = PlasmaShapes.Poster,
                color = PlasmaColors.VideoWell,
                pivot = TransformOrigin(0.5f, 0f),
                onFocusChanged = { focused = it },
            ) {
                PlasmaImage(episode.still, Modifier.fillMaxSize(), placeholder = PlasmaColors.VideoWell)
                if (episode.watched) WatchedMark(Modifier.align(Alignment.TopEnd).padding(top = 10.px, end = 10.px))
                val left = episode.leftMinutes
                if (left != null) {
                    ProgressBar(
                        1f - left / episode.minutes.toFloat(),
                        Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                        height = 5.px,
                        track = PlasmaColors.white(0.22f),
                        rounded = false,
                    )
                }
            }
        }
        // The still grows from its top when focused, so the caption moves down out of its way.
        val shift by animateDpAsState(if (focused) 16.px else 0.px, PlasmaMotion.focus(), label = "episodeCaption")
        Column(
            Modifier.padding(top = 12.px).offset { IntOffset(0, shift.roundToPx()) },
            verticalArrangement = Arrangement.spacedBy(3.px),
        ) {
            val strong = current || focused
            PlasmaText(title, plasmaStyle(23f, if (strong) 600 else 500), color = if (strong) PlasmaColors.Text else PlasmaColors.TextSoft)
            val left = episode.leftMinutes
            PlasmaText(
                if (left != null) {
                    stringResource(R.string.minutes_of_left, left, episode.minutes)
                } else {
                    Formats.duration(Duration.ofMinutes(episode.minutes.toLong()))
                },
                plasmaStyle(20f, 500, tabular = true),
                color = if (strong) PlasmaColors.Text2 else PlasmaColors.Text3,
            )
        }
    }
}

/** A white check on a dark disc, top right of a watched episode's still. */
@Composable
private fun WatchedMark(modifier: Modifier = Modifier) {
    val watched = stringResource(R.string.watched)
    Box(
        modifier
            .size(30.px)
            .background(PlasmaColors.black(0.6f), CircleShape)
            .semantics { contentDescription = watched },
        contentAlignment = Alignment.Center,
    ) {
        PlasmaIcon(PlasmaIcons.Check, 16.px, tint = PlasmaColors.white(1f))
    }
}
