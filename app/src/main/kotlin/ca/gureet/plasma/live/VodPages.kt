// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import ca.gureet.plasma.AppGraph
import ca.gureet.plasma.R
import ca.gureet.plasma.core.data.FileAudio
import ca.gureet.plasma.core.data.FileVideo
import ca.gureet.plasma.core.data.MoviePage
import ca.gureet.plasma.core.data.SeriesPage
import ca.gureet.plasma.core.data.VodRepository
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.feature.player.TrackNames
import ca.gureet.plasma.feature.vod.Backdrop
import ca.gureet.plasma.feature.vod.EpisodeItem
import ca.gureet.plasma.feature.vod.EpisodeRef
import ca.gureet.plasma.feature.vod.EpisodesStatus
import ca.gureet.plasma.feature.vod.MovieDetailActions
import ca.gureet.plasma.feature.vod.MovieDetailScreen
import ca.gureet.plasma.feature.vod.MovieDetailUiState
import ca.gureet.plasma.feature.vod.Person
import ca.gureet.plasma.feature.vod.SeasonItem
import ca.gureet.plasma.feature.vod.SeriesDetailActions
import ca.gureet.plasma.feature.vod.SeriesDetailScreen
import ca.gureet.plasma.feature.vod.SeriesDetailUiState
import ca.gureet.plasma.player.EngineId
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The words a detail page writes its facts in, from string resources, so the pages can be put
 * together in tests without them. Format names ("HEVC", "E-AC-3") are the same in every language,
 * as in the stream info.
 */
internal class VodWords(
    val container: String,
    val video: String,
    val hdr: String,
    val audio: String,
    val badge4k: String,
    val badgeHd: String,
    val badgeHdr: String,
    /** "3840 × 2160" */
    val resolution: (width: Int, height: Int) -> String,
    /** "23.976 fps" */
    val frameRate: (rate: String) -> String,
    /** "640 kb/s" */
    val kilobits: (kbps: Int) -> String,
    /** "2024–2026" */
    val years: (first: Int, last: Int) -> String,
    /** "5.1", "Stereo": as Audio and Subtitles names a track's channels. */
    val layout: (channels: Int) -> String,
    /** "Dolby Digital Plus" for "eac3"; null for a format Plasma doesn't name. */
    val audioFormat: (codec: String) -> String?,
    /** "English" for "eng"; null when it isn't known. */
    val language: (code: String) -> String?,
    /** "Plays with the Android player": File Info's first line (4.14). */
    val playsWith: (engine: EngineId) -> String,
) {
    companion object {
        fun of(resources: Resources) = VodWords(
            container = resources.getString(R.string.file_container),
            video = resources.getString(R.string.file_video),
            hdr = resources.getString(R.string.file_hdr),
            audio = resources.getString(R.string.file_audio),
            badge4k = resources.getString(R.string.badge_4k),
            badgeHd = resources.getString(R.string.badge_hd),
            badgeHdr = resources.getString(R.string.badge_hdr),
            resolution = { width, height -> resources.getString(R.string.file_resolution, width, height) },
            frameRate = { resources.getString(R.string.stream_frame_rate, it) },
            kilobits = { resources.getString(R.string.bitrate_kbps, it.toString()) },
            years = { first, last -> resources.getString(R.string.years_range, first, last) },
            layout = { TrackNames.layout(resources, it) },
            audioFormat = { TrackNames.audioFormat(resources, it) },
            language = { TrackNames.languageName(it) },
            playsWith = { engine ->
                resources.getString(
                    when (engine) {
                        EngineId.Vlc -> R.string.plays_with_vlc
                        EngineId.Mpv -> R.string.plays_with_mpv
                        EngineId.Media3 -> R.string.plays_with_android_player
                    },
                )
            },
        )
    }
}

@Composable
internal fun rememberVodWords(): VodWords {
    val resources = LocalResources.current
    return remember(resources) { VodWords.of(resources) }
}

/**
 * A movie's page (board 14, brief 4.14) from what the catalog has, then what its provider says once
 * that's read. Without a backdrop the poster stands in, blurred (5.9); without title art, the title
 * is set in type. [trailers]: the TV can play the provider's trailers. [engine] plays it (5.4.3),
 * as File Info says.
 */
internal fun moviePageState(page: MoviePage, words: VodWords, trailers: Boolean, engine: EngineId): MovieDetailUiState {
    val details = page.details
    val progress = page.progress
    return MovieDetailUiState(
        id = page.movie.id,
        title = page.movie.name,
        titleArt = null,
        backdrop = backdropOf(details?.backdrop, details?.poster ?: page.movie.poster),
        genres = details?.genres.orEmpty(),
        year = details?.year ?: page.movie.year,
        minutes = (details?.duration ?: progress?.duration?.takeIf { !it.isZero })?.let(::wholeMinutes),
        badges = badges(details?.age, details?.video, details?.audio, words),
        synopsis = details?.plot.orEmpty(),
        cast = details?.cast.orEmpty().map { Person(it, role = "") },
        director = details?.director,
        leftMinutes = progress?.left?.let(::minutesLeft),
        inMyList = page.inMyList,
        hasTrailer = trailers && details?.trailer != null,
        moreLikeThis = page.moreLikeThis.map { it.title(emptyMap()) },
        audio = listOfNotNull(details?.audio?.let { trackLine(it, words) }),
        playsWith = listOfNotNull(words.playsWith(engine), details?.video?.hdr).joinToString(" · "),
        fileInfo = fileInfo(page.extension, details?.video, details?.audio, words),
    )
}

/** A series' page, and which episode each card is. */
internal class SeriesScreen(val state: SeriesDetailUiState, val episodeIds: Map<EpisodeRef, String>)

/**
 * A series' page (board 16, brief 4.16): its seasons, the specials last, and each episode with
 * where it was left. Within a season whose episodes don't each have their own number, they're
 * counted in order, so every one has its own place. [failed]: its episodes couldn't be read.
 */
internal fun seriesPageState(page: SeriesPage, words: VodWords, trailers: Boolean, failed: Boolean): SeriesScreen {
    val details = page.details
    val ids = HashMap<EpisodeRef, String>()
    val seasons = page.episodes.groupBy { it.season }.map { (season, episodes) ->
        val numbers = episodes.map { it.number }
        val counted = numbers.any { it <= 0 } || numbers.toSet().size != numbers.size
        SeasonItem(
            season,
            episodes.mapIndexed { i, episode ->
                val number = if (counted) i + 1 else episode.number
                ids[EpisodeRef(season, number)] = episode.id
                val progress = page.progress[episode.id]
                EpisodeItem(
                    number = number,
                    title = episode.title,
                    still = episode.still,
                    minutes = (episode.duration ?: progress?.duration?.takeIf { !it.isZero })?.let(::wholeMinutes),
                    watched = progress?.watched == true,
                    leftMinutes = progress?.left?.let(::minutesLeft),
                )
            },
        )
    }
    // Badges from the first episode the provider probed.
    val probed = page.episodes.firstOrNull { it.video != null || it.audio != null }
    val first = listOfNotNull(details?.year ?: page.series.year, page.episodes.mapNotNull { it.year }.minOrNull()).minOrNull()
    val last = page.episodes.mapNotNull { it.year }.maxOrNull()
    val state = SeriesDetailUiState(
        id = page.series.id,
        title = page.series.name,
        titleArt = null,
        backdrop = backdropOf(details?.backdrop ?: page.series.backdrop, page.series.poster),
        genres = details?.genres?.takeIf { it.isNotEmpty() } ?: VodRepository.genres(page.series.genre),
        years = when {
            first == null -> null
            last == null || last <= first -> first.toString()
            else -> words.years(first, last)
        },
        badges = badges(details?.age, probed?.video, probed?.audio, words),
        synopsis = page.plot.orEmpty(),
        seasons = seasons,
        inMyList = page.inMyList,
        hasTrailer = trailers && page.trailer != null,
        episodes = when {
            seasons.isNotEmpty() || details != null -> EpisodesStatus.Ready
            failed -> EpisodesStatus.Failed
            else -> EpisodesStatus.Loading
        },
    )
    return SeriesScreen(state, ids)
}

/** The art behind a page: its backdrop, or else its poster, enlarged and blurred (5.9). */
private fun backdropOf(backdrop: String?, poster: String?): Backdrop =
    if (backdrop != null) Backdrop(backdrop, PlasmaColors.Bg) else Backdrop(poster, PlasmaColors.Bg, blurred = true)

/** "13+", "4K", "HDR", "5.1": the age rating and what the provider's probe of the file found. */
internal fun badges(age: String?, video: FileVideo?, audio: FileAudio?, words: VodWords): List<String> {
    val width = video?.width ?: 0
    val height = video?.height ?: 0
    return listOfNotNull(
        age,
        when {
            width >= UHD_WIDTH || height >= UHD_HEIGHT -> words.badge4k
            width >= HD_WIDTH || height >= HD_HEIGHT -> words.badgeHd
            else -> null
        },
        words.badgeHdr.takeIf { video?.hdr != null },
        audio?.channels?.takeIf { it >= SURROUND }?.let(words.layout),
    )
}

/** "English · 5.1 · Dolby Digital Plus": the main sound, as Audio & Subtitles lists it (4.14). */
private fun trackLine(audio: FileAudio, words: VodWords): String? =
    listOfNotNull(
        audio.language?.let(words.language),
        audio.channels?.let(words.layout),
        audio.codec?.let { words.audioFormat(it) ?: audioCodecName(it) },
    ).joinToString(" · ").takeIf { it.isNotEmpty() }

/**
 * File Info (4.14): the container, the picture ("HEVC Main 10 · 3840 × 2160 · 23.976 fps"), its HDR
 * and the sound ("E-AC-3 5.1 · 640 kb/s"), as far as the provider probed the file.
 */
internal fun fileInfo(extension: String?, video: FileVideo?, audio: FileAudio?, words: VodWords): List<Pair<String, String>> {
    val picture = video?.let {
        val width = it.width
        val height = it.height
        listOfNotNull(
            listOfNotNull(videoCodecName(it.codec), it.profile).joinToString(" ").takeIf(String::isNotEmpty),
            if (width != null && height != null) words.resolution(width, height) else null,
            it.frameRate?.let { rate -> words.frameRate(frameRateText(rate)) },
        ).joinToString(" · ").takeIf(String::isNotEmpty)
    }
    val sound = audio?.let {
        listOfNotNull(
            listOfNotNull(audioCodecName(it.codec), it.channels?.let(::channelLayout)).joinToString(" ").takeIf(String::isNotEmpty),
            it.bitRate?.let { bits -> words.kilobits(bits / KILO) },
        ).joinToString(" · ").takeIf(String::isNotEmpty)
    }
    return listOfNotNull(
        extension?.let { words.container to containerName(it) },
        picture?.let { words.video to it },
        video?.hdr?.let { words.hdr to it },
        sound?.let { words.audio to it },
    )
}

/** A file's container by its usual name: "Matroska (MKV)", "MP4". */
private fun containerName(extension: String): String = when (val name = extension.lowercase()) {
    "mkv" -> "Matroska (MKV)"
    "mp4", "m4v" -> "MP4"
    "ts", "m2ts", "mts" -> "MPEG-TS"
    "avi" -> "AVI"
    "mov" -> "QuickTime (MOV)"
    "webm" -> "WebM"
    "m3u8" -> "HLS"
    else -> name.uppercase()
}

/** 24 → "24", 23.976 → "23.976", 29.97 → "29.97". */
private fun frameRateText(rate: Float): String = String.format(Locale.US, "%.3f", rate).trimEnd('0').trimEnd('.')

/** A running time to the nearest minute; none under half a minute. */
private fun wholeMinutes(duration: Duration): Int? = ((duration.seconds + HALF_MINUTE) / MINUTE).toInt().takeIf { it > 0 }

/** What's left, in minutes, rounded up: a minute left is never "0 min". */
internal fun minutesLeft(left: Duration): Int = ((left.seconds + MINUTE - 1) / MINUTE).toInt().coerceAtLeast(1)

private const val MINUTE = 60L
private const val HALF_MINUTE = 30L
private const val KILO = 1_000
private const val SURROUND = 6
private const val UHD_WIDTH = 3_200
private const val UHD_HEIGHT = 2_000
private const val HD_WIDTH = 1_200
private const val HD_HEIGHT = 700

/** Trailers play in the YouTube app, from the provider's video id (brief 5.9); without the app, there's no Trailer button. */
internal object Trailers {
    private fun intent(videoId: String) = Intent(Intent.ACTION_VIEW, "vnd.youtube:$videoId".toUri())

    fun available(context: Context): Boolean = context.packageManager.queryIntentActivities(intent(PROBE), 0).isNotEmpty()

    fun open(context: Context, videoId: String) {
        try {
            context.startActivity(intent(videoId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // YouTube went since the page opened.
        }
    }

    /** Any id does to ask whether YouTube is there. */
    private const val PROBE = "00000000000"
}

/** A page's flow, or a read, once it has said whether the title is there. */
internal class Read<T>(val page: T)

/**
 * A movie's page on a source (board 14): what the catalog has at once, then the provider's
 * details, read as it opens (once a week). My List and the trailer are handled here; the rest of
 * [actions] go where the app says. [gone] shows when the movie isn't in the catalog any more.
 */
@Composable
fun LiveMovieDetail(graph: AppGraph, id: String, actions: MovieDetailActions, gone: @Composable () -> Unit) {
    val context = LocalContext.current
    val words = rememberVodWords()
    val trailers = remember(context) { Trailers.available(context) }
    val read by remember(graph, id) { graph.data.vod.moviePage(id).map { Read(it) } }.collectAsState(null)
    LaunchedEffect(graph, id) { graph.data.vod.loadMovie(id) }
    // The engine it plays with, by its HDR format and the settings (5.4.3).
    val hdr = read?.page?.details?.video?.hdr
    val chosen by graph.storedChoices.collectAsState()
    val engine by produceState(EngineId.Vlc, graph, id, hdr, chosen) { value = graph.vodEngineFor(id, hdr) }
    val page = read?.page
    if (read == null) return
    if (page == null) return gone()
    val state = remember(page, words, trailers, engine) { moviePageState(page, words, trailers, engine) }
    val trailer = page.details?.trailer
    MovieDetailScreen(
        state = state,
        actions = remember(actions, trailer) {
            MovieDetailActions(
                onBack = actions.onBack,
                onPlay = actions.onPlay,
                onTrailer = { trailer?.let { Trailers.open(context, it) } },
                onMyList = { inList -> graph.scope.launch { graph.data.vod.setMovieInMyList(id, inList) } },
                onTitle = actions.onTitle,
                onPerson = actions.onPerson,
                onPosterless = graph.data.posters::want,
            )
        },
    )
}

/**
 * A series' page on a source (board 16): the catalog's at once, then its episodes, read as it
 * opens (after a day, or when the catalog sees it change). My List, what's watched and the
 * trailer are handled here; an episode plays by [onEpisode].
 */
@Composable
fun LiveSeriesDetail(
    graph: AppGraph,
    id: String,
    actions: SeriesDetailActions,
    onEpisode: (episodeId: String, fromStart: Boolean) -> Unit,
    gone: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val words = rememberVodWords()
    val trailers = remember(context) { Trailers.available(context) }
    val vod = graph.data.vod
    val read by remember(graph, id) { vod.seriesPage(id).map { Read(it) } }.collectAsState(null)
    var failed by remember(id) { mutableStateOf(false) }
    LaunchedEffect(graph, id) { failed = !vod.loadSeries(id) }
    val page = read?.page
    if (read == null) return
    if (page == null) return gone()
    val screen = remember(page, words, trailers, failed) { seriesPageState(page, words, trailers, failed) }
    SeriesDetailScreen(
        state = screen.state,
        actions = remember(actions, screen, page.trailer) {
            SeriesDetailActions(
                onBack = actions.onBack,
                onPlay = { ref, fromStart -> screen.episodeIds[ref]?.let { onEpisode(it, fromStart) } },
                onTrailer = { page.trailer?.let { Trailers.open(context, it) } },
                onMyList = { inList -> graph.scope.launch { vod.setSeriesInMyList(id, inList) } },
                onEpisodeWatched = { ref, watched ->
                    screen.episodeIds[ref]?.let { episode -> graph.scope.launch { vod.setEpisodesWatched(id, listOf(episode), watched) } }
                },
                onMarkSeasonWatched = { season, watched -> graph.scope.launch { vod.setSeasonWatched(id, season, watched) } },
            )
        },
    )
}
