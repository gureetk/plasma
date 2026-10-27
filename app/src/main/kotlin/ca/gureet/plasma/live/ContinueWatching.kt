// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import android.content.res.Resources
import ca.gureet.plasma.R
import ca.gureet.plasma.core.data.ContinueEntry
import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.feature.home.ContinueItem
import ca.gureet.plasma.feature.home.ContinueKind
import ca.gureet.plasma.feature.vod.VodCaption
import ca.gureet.plasma.feature.vod.VodTitle
import ca.gureet.plasma.player.StreamKeys
import java.time.Duration
import java.time.Instant

/** The words Continue Watching's lines are written in, from string resources. */
internal class ContinueWords(
    /** "1 h 02 min left" */
    val left: (duration: String) -> String,
    /** "S2, E4" */
    val episode: (season: Int, number: Int) -> String,
    /** "Up next", for an episode after one that was watched. */
    val upNext: String,
    /** Another app's episode by the numbers it gives: "S2, E4", "E4", or null without a number. */
    val episodeText: (season: String?, number: String?) -> String? = { _, _ -> null },
    /** "Jellyfin", for a Jellyfin movie's line and its tag (brief 4.1). */
    val jellyfin: String = "Jellyfin",
    /** "Jellyfin is offline", on Jellyfin's items when the server didn't answer (5.19). */
    val jellyfinOffline: String = "Jellyfin is offline",
) {
    companion object {
        fun of(resources: Resources) = ContinueWords(
            left = { resources.getString(R.string.continue_left, it) },
            episode = { season, number -> resources.getString(R.string.continue_episode, season, number) },
            upNext = resources.getString(R.string.continue_up_next),
            episodeText = { season, number ->
                when {
                    number == null -> null
                    season == null -> resources.getString(R.string.continue_episode_only, number)
                    else -> resources.getString(R.string.continue_episode_text, season, number)
                }
            },
            jellyfin = resources.getString(R.string.jellyfin),
            jellyfinOffline = resources.getString(R.string.jellyfin_offline),
        )
    }
}

/** How far back Continue Watching goes, as Hide Items Older Than says (brief 4.32); null for ever. */
internal fun continueSince(choices: SettingsChoices, now: Instant): Instant? =
    choices.hideOlderDays?.let { now.minus(Duration.ofDays(it.toLong())) }

/**
 * Home's Continue Watching (board 02, brief 5.9): Plasma's own movies and episodes, the latest
 * first, with what's left, or the episode up next. 16:9 art where there is some, or else the
 * poster, which Home shows sharp over a blurred enlargement. Each opens as its player key.
 */
internal fun continueItems(entries: List<ContinueEntry>, words: ContinueWords): List<ContinueItem> = entries.map { entry ->
    val playable = entry.playable
    val left = entry.progress?.left?.let { words.left(Formats.duration(it)) }
    val meta = when (playable.kind) {
        Playable.Kind.Movie -> left.orEmpty()
        Playable.Kind.Episode -> listOf(words.episode(playable.season ?: 1, playable.number ?: 1), left ?: words.upNext).joinToString(" · ")
    }
    ContinueItem(
        id = vodKey(playable.kind, playable.id),
        title = playable.title,
        meta = meta,
        image = entry.wide ?: entry.poster,
        kind = if (entry.wide != null) ContinueKind.Art else ContinueKind.Poster,
        progress = entry.progress?.fraction ?: 0f,
    )
}

/** Movies' Continue Watching (4.13): the movies started, as posters with what's been watched. */
internal fun continueMovies(entries: List<ContinueEntry>): List<VodTitle> = entries.filter { it.playable.kind == Playable.Kind.Movie }.map { entry ->
    val movie = entry.playable
    VodTitle(
        id = movie.id,
        title = movie.title,
        poster = entry.poster,
        caption = VodCaption.Movie(year = movie.year, genre = movie.genres.firstOrNull(), minutes = movie.duration?.toMinutes()?.toInt()?.takeIf { it > 0 }),
        progress = entry.progress?.fraction,
        year = movie.year ?: 0,
        added = entry.updated.toEpochMilli(),
    )
}

/**
 * Series' Continue Watching (4.15): each series once, by its episode in progress ("Resume S2, E4 ·
 * 18 min left") or the one up next.
 */
internal fun continueSeries(entries: List<ContinueEntry>): List<VodTitle> = entries.mapNotNull { entry ->
    val episode = entry.playable.takeIf { it.kind == Playable.Kind.Episode } ?: return@mapNotNull null
    val season = episode.season ?: 1
    val number = episode.number ?: 1
    val left = entry.progress?.left
    VodTitle(
        id = episode.seriesId ?: return@mapNotNull null,
        title = episode.title,
        poster = entry.poster,
        caption = if (left != null) VodCaption.ResumeEpisode(season, number, minutesLeft(left)) else VodCaption.NextEpisode(season, number),
        progress = entry.progress?.fraction,
        added = entry.updated.toEpochMilli(),
    )
}

/** What a Continue Watching item on Home plays from a source's catalog: its kind and id, from its key. */
internal fun continuePlayable(id: String): Pair<Playable.Kind, String>? = StreamKeys.vodOf(id)?.let { (kind, vodId) ->
    when (kind) {
        StreamKeys.KIND_MOVIE -> Playable.Kind.Movie to vodId
        StreamKeys.KIND_EPISODE -> Playable.Kind.Episode to vodId
        else -> null
    }
}
