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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.PlasmaRow
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
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
 * season watched, or unwatched again; long-press or Menu on an episode offers the same for it.
 * Until the episodes are there, a line says they're loading, or that they couldn't be.
 */
@Composable
fun SeriesDetailScreen(state: SeriesDetailUiState, actions: SeriesDetailActions, modifier: Modifier = Modifier) {
    var seasons by remember(state.id, state.seasons) { mutableStateOf(state.seasons) }
    val next = upNext(seasons)
    // The season picked on the tabs; until then, the one Resume plays from.
    var seasonNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    val season = seasons.firstOrNull { it.number == (seasonNumber ?: next?.season) } ?: seasons.firstOrNull()
    var inMyList by remember(state.id, state.inMyList) { mutableStateOf(state.inMyList) }
    val primary = remember { FocusRequester() }
    val selectedSeason = remember { FocusRequester() }
    val entryEpisode = remember { FocusRequester() }
    val allWatched = season != null && season.episodes.all { it.watched }
    // The episode sheet (long-press or Menu), and the card it returns focus to.
    var optionsFor by remember { mutableStateOf<EpisodeRef?>(null) }
    var optionsOpen by remember { mutableStateOf(false) }
    val optionsReturn = remember { FocusRequester() }
    var buttonsFocused by remember { mutableStateOf(false) }

    fun markWatched(ref: EpisodeRef?, seasonOf: Int, watched: Boolean) {
        seasons = seasons.map { s ->
            if (s.number != seasonOf) {
                s
            } else {
                s.copy(episodes = s.episodes.map { if (ref == null || it.number == ref.episode) it.copy(watched = watched, leftMinutes = null) else it })
            }
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        primary.requestFocus()
    }
    // The episodes came while focus waited on the buttons: Resume (or Play) takes it, as it would have.
    val hasNext = next != null
    LaunchedEffect(hasNext) {
        if (hasNext && buttonsFocused) {
            withFrameNanos { }
            primary.requestFocus()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.ProgramGreen && event.type == KeyEventType.KeyUp && season != null) {
                    val watched = !allWatched
                    markWatched(null, season.number, watched)
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
            // Specials aren't a season.
            val seasonCount = seasons.count { it.number > 0 }
            MetaLine(
                parts = state.genres + listOfNotNull(
                    state.years,
                    seasonCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.seasons, it, it) },
                ),
                badges = state.badges,
                modifier = Modifier.padding(top = 28.px),
            )
            if (state.synopsis.isNotBlank()) Synopsis(state.synopsis, maxWidthPx = 880, modifier = Modifier.padding(top = 18.px))
            Row(
                Modifier
                    .padding(top = 30.px)
                    .onFocusChanged { buttonsFocused = it.hasFocus }
                    .focusRestoringGroup(primary),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.px),
            ) {
                // The first button takes focus: Resume or Play, or, before there are episodes, the next one along.
                if (next != null) {
                    PrimaryPlayButton(
                        text = stringResource(
                            if (nextEpisode?.leftMinutes != null) R.string.resume_episode else R.string.play_episode,
                            next.season,
                            next.episode,
                        ),
                        onClick = { actions.onPlay(next, false) },
                        modifier = Modifier.padding(end = 12.px).focusRequester(primary),
                    )
                }
                if (state.hasTrailer) {
                    PlasmaButton(
                        stringResource(R.string.trailer),
                        onClick = actions.onTrailer,
                        modifier = if (next == null) Modifier.focusRequester(primary) else Modifier,
                    )
                }
                MyListButton(inMyList, if (next == null && !state.hasTrailer) Modifier.focusRequester(primary) else Modifier) {
                    inMyList = !inMyList
                    actions.onMyList(inMyList)
                }
            }
            if (nextEpisode != null) {
                val left = nextEpisode.leftMinutes
                val minutes = nextEpisode.minutes
                val length = when {
                    left != null -> stringResource(R.string.left, Formats.duration(Duration.ofMinutes(left.toLong())))
                    minutes != null -> Formats.duration(Duration.ofMinutes(minutes.toLong()))
                    else -> null
                }
                ProgressLine(
                    fraction = if (left != null && minutes != null) 1f - left / minutes.toFloat() else null,
                    text = listOfNotNull(nextEpisode.title ?: stringResource(R.string.episode_n, nextEpisode.number), length).joinToString(" · "),
                    modifier = Modifier.padding(top = 18.px),
                )
            }
        }

        if (season == null) {
            PlasmaText(
                stringResource(
                    when (state.episodes) {
                        EpisodesStatus.Loading -> R.string.episodes_loading
                        EpisodesStatus.Failed -> R.string.episodes_failed
                        EpisodesStatus.Ready -> R.string.no_episodes
                    },
                ),
                plasmaStyle(23f, 500),
                Modifier.offset(96.px, 724.px),
                color = PlasmaColors.Text2,
            )
        } else {
            Row(Modifier.offset(96.px, 716.px).width(1728.px), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.focusRestoringGroup(selectedSeason), horizontalArrangement = Arrangement.spacedBy(6.px)) {
                    for (s in seasons) {
                        TabPill(
                            seasonName(s.number),
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
                            onClick = { actions.onPlay(ref, false) },
                            onLongClick = {
                                optionsFor = ref
                                optionsOpen = true
                            },
                            modifier = Modifier
                                .then(if (i == entryIndex) Modifier.focusRequester(entryEpisode) else Modifier)
                                .then(if (ref == optionsFor) Modifier.focusRequester(optionsReturn) else Modifier),
                        )
                    }
                }
            }
        }

        SheetHost(visible = optionsOpen, returnFocus = optionsReturn) {
            val ref = optionsFor ?: return@SheetHost
            val episode = seasons.firstOrNull { it.number == ref.season }?.episodes?.firstOrNull { it.number == ref.episode } ?: return@SheetHost
            OptionsSheet(
                title = episodeName(episode),
                subtitle = seasonName(ref.season) + " · " + state.title,
                onDismiss = { optionsOpen = false },
                leading = { PlasmaImage(episode.still, Modifier.size(160.px, 90.px).clip(PlasmaShapes.SmallPlate), placeholder = PlasmaColors.VideoWell) },
                options = listOfNotNull(
                    episode.leftMinutes?.let {
                        SheetOption(stringResource(R.string.from_the_beginning), onClick = { actions.onPlay(ref, true) }, icon = PlasmaIcons.CatchUp)
                    },
                    SheetOption(
                        stringResource(if (episode.watched) R.string.mark_unwatched else R.string.mark_watched),
                        onClick = {
                            markWatched(ref, ref.season, !episode.watched)
                            actions.onEpisodeWatched(ref, !episode.watched)
                        },
                        icon = PlasmaIcons.Check,
                    ),
                ),
            )
        }
    }
}

/** "Season 2", or "Specials" for season 0. */
@Composable
private fun seasonName(number: Int): String =
    if (number == 0) stringResource(R.string.specials) else stringResource(R.string.season_n, number)

/** "4 · Dead Reckoning", or "Episode 4" without a title of its own. */
@Composable
private fun episodeName(episode: EpisodeItem): String =
    episode.title?.let { stringResource(R.string.episode_title, episode.number, it) } ?: stringResource(R.string.episode_n, episode.number)

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
    val title = episodeName(episode)
    val left = episode.leftMinutes
    val minutes = episode.minutes
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
                if (left != null && minutes != null) {
                    ProgressBar(
                        1f - left / minutes.toFloat(),
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
            PlasmaText(title, plasmaStyle(23f, if (strong) 600 else 500), color = if (strong) PlasmaColors.Text else PlasmaColors.TextSoft, maxLines = 1)
            val length = when {
                left != null && minutes != null -> stringResource(R.string.minutes_of_left, left, minutes)
                left != null -> stringResource(R.string.left, Formats.duration(Duration.ofMinutes(left.toLong())))
                minutes != null -> Formats.duration(Duration.ofMinutes(minutes.toLong()))
                else -> null
            }
            if (length != null) {
                PlasmaText(length, plasmaStyle(20f, 500, tabular = true), color = if (strong) PlasmaColors.Text2 else PlasmaColors.Text3)
            }
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
