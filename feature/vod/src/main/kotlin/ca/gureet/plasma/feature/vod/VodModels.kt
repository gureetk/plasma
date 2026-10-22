// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.core.design.component.TopTab

/** Movies or Series: the two share one browse layout (brief 4.13 and 4.15). */
enum class VodKind { Movies, Series }

/**
 * How a category is ordered (brief 4.13). The yellow key and the ⌄ button step through them.
 * [Newest] is "Newest first" for movies and "Latest episode first" for series.
 */
enum class VodSort { Newest, AZ, Year, Rating }

/** What a poster says under itself while it has focus. */
@Immutable
sealed interface VodCaption {
    /** "2025 · Drama · 2 h 14 min" */
    data class Movie(val year: Int, val genre: String, val minutes: Int) : VodCaption

    /** "Resume S2, E4 · 18 min left" */
    data class ResumeEpisode(val season: Int, val episode: Int, val leftMinutes: Int) : VodCaption

    /** "2024–2026 · 3 Seasons" */
    data class Series(val years: String, val seasons: Int) : VodCaption
}

/** A movie or series on a poster. */
@Immutable
data class VodTitle(
    val id: String,
    val title: String,
    val poster: String?,
    val caption: VodCaption,
    /** "NEW EPISODE", "NEW SEASON" */
    val badge: String? = null,
    /** Part watched, drawn along the poster's bottom edge. */
    val progress: Float? = null,
    val year: Int = 0,
    val rating: Float = 0f,
    /** When it arrived (for a series, its latest episode); higher is newer. */
    val added: Long = 0,
)

@Immutable
data class VodBrowseUiState(
    val kind: VodKind,
    val categories: List<CountedItem>,
    val selectedCategoryId: String,
    /** Each category's titles, newest first. */
    val titles: Map<String, List<VodTitle>>,
    val profileName: String,
    val profileInitial: String,
    val sort: VodSort = VodSort.Newest,
    /** The poster that had focus last, so coming back lands on it. */
    val focusedId: String? = null,
)

class VodBrowseActions(
    val onTab: (TopTab) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
    /** Long-press or Menu on a poster: Add to My List, Mark as Watched (brief 4.13). */
    val onOptions: (String) -> Unit = {},
)

/**
 * A detail page's backdrop: the art, how far it's shifted to keep the subject clear of the text
 * (board pixels; boards 14 and 16 move it right and up), and the dark tone from the art that the
 * scrims fade into.
 */
@Immutable
data class Backdrop(
    val image: String?,
    val color: Color,
    val shiftXPx: Int = 0,
    val shiftYPx: Int = 0,
    /** Only a poster to go on: shown enlarged, blurred and darkened, as on Home (4.2). */
    val blurred: Boolean = false,
)

/** Someone in Cast & Crew. */
@Immutable
data class Person(val name: String, val role: String)

@Immutable
data class MovieDetailUiState(
    val id: String,
    val title: String,
    /** The provider's logo art; without it, the title is set as large text. */
    val titleArt: String?,
    val backdrop: Backdrop,
    val genres: List<String>,
    val year: Int,
    val minutes: Int,
    val badges: List<String>,
    val synopsis: String,
    val cast: List<Person>,
    val director: String?,
    /** Minutes left when it's been started; null shows Play instead of Resume (4.14). */
    val leftMinutes: Int? = null,
    val inMyList: Boolean = false,
    val hasTrailer: Boolean = true,
    val moreLikeThis: List<VodTitle> = emptyList(),
    val audio: List<String> = emptyList(),
    val subtitles: List<String> = emptyList(),
    /** File Info: the engine line first ("Plays with the Android player · Dolby Vision"), then label–value pairs. */
    val playsWith: String? = null,
    val fileInfo: List<Pair<String, String>> = emptyList(),
)

class MovieDetailActions(
    val onBack: () -> Unit = {},
    /** Resume, or From the Beginning when `fromStart`. */
    val onPlay: (fromStart: Boolean) -> Unit = {},
    val onTrailer: () -> Unit = {},
    val onMyList: (inList: Boolean) -> Unit = {},
    val onTitle: (String) -> Unit = {},
    /** A name in Cast & Crew: search for it. */
    val onPerson: (String) -> Unit = {},
)

@Immutable
data class EpisodeItem(
    val number: Int,
    val title: String,
    val still: String?,
    val minutes: Int,
    val watched: Boolean = false,
    /** Minutes left when it's been started and not finished. */
    val leftMinutes: Int? = null,
)

@Immutable
data class SeasonItem(val number: Int, val episodes: List<EpisodeItem>)

/** Which episode Resume (or Play) starts. */
@Immutable
data class EpisodeRef(val season: Int, val episode: Int)

@Immutable
data class SeriesDetailUiState(
    val id: String,
    val title: String,
    val titleArt: String?,
    val backdrop: Backdrop,
    val genres: List<String>,
    /** "2024–2026" */
    val years: String,
    val badges: List<String>,
    val synopsis: String,
    val seasons: List<SeasonItem>,
    val inMyList: Boolean = false,
    val hasTrailer: Boolean = true,
)

/**
 * The episode Resume or Play starts: the one in progress, else the one after the last watched,
 * else the very first. Null when there are no episodes.
 */
internal fun upNext(seasons: List<SeasonItem>): EpisodeRef? {
    val all = seasons.flatMap { season -> season.episodes.map { EpisodeRef(season.number, it.number) to it } }
    if (all.isEmpty()) return null
    all.firstOrNull { it.second.leftMinutes != null }?.let { return it.first }
    val lastWatched = all.indexOfLast { it.second.watched }
    return all.getOrNull(lastWatched + 1)?.first ?: all.first().first
}

class SeriesDetailActions(
    val onBack: () -> Unit = {},
    val onPlay: (EpisodeRef) -> Unit = {},
    val onTrailer: () -> Unit = {},
    val onMyList: (inList: Boolean) -> Unit = {},
    /** Long-press or Menu on an episode. */
    val onEpisodeOptions: (EpisodeRef) -> Unit = {},
    /** Green: mark the whole season watched, or unwatched when it already is. */
    val onMarkSeasonWatched: (season: Int, watched: Boolean) -> Unit = { _, _ -> },
)
