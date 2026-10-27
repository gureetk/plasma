// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.AudioFacts
import ca.gureet.plasma.core.database.CategoryCount
import ca.gureet.plasma.core.database.EpisodeEntity
import ca.gureet.plasma.core.database.MovieDetailsEntity
import ca.gureet.plasma.core.database.MovieEntity
import ca.gureet.plasma.core.database.MyListEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.ResumePointEntity
import ca.gureet.plasma.core.database.SeriesDetailsEntity
import ca.gureet.plasma.core.database.SeriesEntity
import ca.gureet.plasma.core.database.SeriesLayout
import ca.gureet.plasma.core.database.VideoFacts
import ca.gureet.plasma.core.database.VodCategoryEntity
import ca.gureet.plasma.core.database.VodKinds
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.source.xtream.XtreamAudio
import ca.gureet.plasma.source.xtream.XtreamVideo
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** A category of movies or series, with how many it holds. */
data class VodCategory(val id: String, val name: String, val count: Int)

/** A movie in a list (brief 5.9); its plot, cast and file come with its detail page. */
data class Movie(
    /** "sourceId:remoteId". */
    val id: String,
    val name: String,
    val poster: String?,
    val categoryId: String?,
    val added: Instant?,
    /** Out of 10. */
    val rating: Float?,
    val year: Int?,
)

/** A series in a list (brief 5.9); its seasons and episodes come with its detail page. */
data class Series(
    /** "sourceId:remoteId". */
    val id: String,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val categoryId: String?,
    val genre: String?,
    val year: Int?,
    /** Out of 10. */
    val rating: Float?,
    /** When its latest episode arrived. */
    val updated: Instant?,
)

/** A file's picture, as its provider probed it (brief 4.14, File Info). */
data class FileVideo(
    /** ffprobe's name: "hevc", "h264". */
    val codec: String?,
    /** "Main 10". */
    val profile: String?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    /** "Dolby Vision", "HDR10+", "HDR10" or "HLG"; null for SDR or unknown. */
    val hdr: String?,
) {
    companion object {
        /** [hdr]'s names for the formats only the Android player shows (brief 5.4.3). */
        const val DOLBY_VISION = "Dolby Vision"
        const val HDR10_PLUS = "HDR10+"
    }
}

/** A file's main sound, as its provider probed it. */
data class FileAudio(
    /** ffprobe's name: "eac3", "aac". */
    val codec: String?,
    val channels: Int?,
    /** ISO 639-2, as the file tags it: "eng". */
    val language: String?,
    /** Bits a second. */
    val bitRate: Int?,
)

/** What a movie's provider says of it (brief 5.9), read when its page opens. */
data class MovieDetails(
    val plot: String?,
    val cast: List<String>,
    val director: String?,
    val genres: List<String>,
    val duration: Duration?,
    /** A larger poster than the catalog's, when there is one. */
    val poster: String?,
    val backdrop: String?,
    /** A YouTube video id. */
    val trailer: String?,
    val year: Int?,
    /** Out of 10. */
    val rating: Float?,
    /** "13+". */
    val age: String?,
    val video: FileVideo?,
    val audio: FileAudio?,
)

/** Where a movie or an episode was left (brief 5.9). */
data class Progress(val position: Duration, val duration: Duration, val watched: Boolean) {
    /** What's left of one that's been started and isn't finished; null otherwise. */
    val left: Duration?
        get() = if (watched || position.isZero || duration.isZero || position >= duration) null else duration.minus(position)

    /** The part watched of one that's been started and isn't finished. */
    val fraction: Float?
        get() = left?.let { position.toMillis().toFloat() / duration.toMillis() }
}

/** Everything a movie's page shows (brief 4.14): the catalog's, then the provider's details once read. */
data class MoviePage(
    val movie: Movie,
    /** The file's own extension: "mkv". */
    val extension: String?,
    /** Its category's name. */
    val category: String?,
    /** Null until they've been read. */
    val details: MovieDetails?,
    val inMyList: Boolean,
    val progress: Progress?,
    /** Other movies of its category (4.14, More Like This). */
    val moreLikeThis: List<Movie>,
)

/** An episode of a series (brief 4.16). */
data class Episode(
    /** "sourceId:remoteId". */
    val id: String,
    /** 0 holds the specials. */
    val season: Int,
    val number: Int,
    /** Its own title; the provider doesn't always have one. */
    val title: String?,
    val plot: String?,
    val still: String?,
    val duration: Duration?,
    val year: Int?,
    val added: Instant?,
    val extension: String?,
    val video: FileVideo?,
    val audio: FileAudio?,
)

/** What a series' provider says of it with its episodes (brief 5.9). */
data class SeriesDetails(
    val plot: String?,
    val cast: List<String>,
    val director: String?,
    val genres: List<String>,
    val backdrop: String?,
    /** A YouTube video id. */
    val trailer: String?,
    val year: Int?,
    /** Out of 10. */
    val rating: Float?,
    val age: String?,
)

/**
 * A Stalker portal's way to a movie's or an episode's file (brief 7.1), which becomes an address
 * only when it plays: [cmd], and the [episode] it picks when that's a whole season's or series'.
 */
data class PortalCommand(
    val cmd: String,
    val episode: Int? = null,
    /** The portal names the file apart, and is asked for it first. */
    val fileApart: Boolean = false,
)

/** A movie or an episode, as the player plays it (brief 4.9 and 5.9). */
data class Playable(
    val kind: Kind,
    /** The movie's or the episode's id. */
    val id: String,
    val sourceId: String,
    /** The provider's id, which its address uses. */
    val remoteId: String,
    /** The file's own extension: "mkv". */
    val extension: String?,
    /** The movie's title, or the series'. */
    val title: String,
    /** The episode's series; null for a movie. */
    val seriesId: String? = null,
    val season: Int? = null,
    val number: Int? = null,
    /** The episode's own title. */
    val episodeTitle: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    /** A backdrop, or else a poster or still, to show while the picture opens. */
    val art: String? = null,
    /** How long it is, as the provider said; the player knows better once it plays. */
    val duration: Duration? = null,
    val video: FileVideo? = null,
    val audio: FileAudio? = null,
    /** How a portal plays it (Stalker); null for Xtream, whose address is built from [remoteId]. */
    val command: PortalCommand? = null,
) {
    enum class Kind { Movie, Episode }
}

/**
 * Something to carry on with (brief 5.9): a movie or an episode that was started, or the episode
 * after one that was watched.
 */
data class ContinueEntry(
    /** What plays. */
    val playable: Playable,
    /** 16:9 art: a movie's backdrop, an episode's still or its series' backdrop. */
    val wide: String?,
    /** The movie's poster, or the series'. */
    val poster: String?,
    /** Where it was left; null for a next episode, not started. */
    val progress: Progress?,
    /** When it last played. */
    val updated: Instant,
)

/** Everything a series' page shows (brief 4.16). */
data class SeriesPage(
    val series: Series,
    val plot: String?,
    val cast: List<String>,
    val director: String?,
    /** A YouTube video id. */
    val trailer: String?,
    val category: String?,
    /** Null until the series has been read with its episodes. */
    val details: SeriesDetails?,
    /** Every episode, season by season, the specials last; none until they've been read. */
    val episodes: List<Episode>,
    /** Where its episodes were left, by episode id. */
    val progress: Map<String, Progress>,
    val inMyList: Boolean,
)

/**
 * The movies and series of the sources that are turned on (brief 4.13, 4.15 and 5.9), as their
 * imports keep them, and what the profile did with them: where each was left, which are watched,
 * and My List. Lists come a category at a time, so a big catalog never loads whole. A title's
 * details are read from its provider when its page opens, and kept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VodRepository(
    private val db: PlasmaDatabase,
    private val sources: SourceRepository,
    private val clock: PlasmaClock,
    private val profile: ProfileSource = ProfileSource.fixed(),
) {
    /** The movie categories, in each source's order, the primary source's first; empty ones are left out. */
    val movieCategories: Flow<List<VodCategory>> = counted(db.vod().observeCategories(VodKinds.MOVIE), db.vod().observeMovieCounts())

    /** The series categories, as [movieCategories]. */
    val seriesCategories: Flow<List<VodCategory>> = counted(db.vod().observeCategories(VodKinds.SERIES), db.vod().observeSeriesCounts())

    fun moviesIn(categoryId: String): Flow<List<Movie>> = db.vod().observeMoviesIn(categoryId).map { list -> list.map { it.toMovie() } }

    fun seriesIn(categoryId: String): Flow<List<Series>> = db.vod().observeSeriesIn(categoryId).map { list -> list.map { it.toSeries() } }

    /** Recently Added (4.13): movies added in the last 30 days, newest first, at most 300. */
    fun recentMovies(): Flow<List<Movie>> =
        db.vod().observeRecentMovies(since = clock.now().minus(RECENT).toEpochMilli(), limit = RECENT_LIMIT).map { list -> list.map { it.toMovie() } }

    /** New Episodes (4.15): series with an episode added in the last 14 days, latest first, at most 300. */
    fun newEpisodes(): Flow<List<Series>> =
        db.vod().observeRecentSeries(since = clock.now().minus(NEW_EPISODES).toEpochMilli(), limit = RECENT_LIMIT).map { list -> list.map { it.toSeries() } }

    /** Movies whose name has a word starting with each word of [query] (Search, brief 5.10). */
    suspend fun searchMovies(query: String, limit: Int = SEARCH_LIMIT): List<Movie> =
        SearchRepository.matchOf(query)?.let { match -> db.vod().searchMovies(match, limit).map { it.toMovie() } }.orEmpty()

    /** Series whose name has a word starting with each word of [query]. */
    suspend fun searchSeries(query: String, limit: Int = SEARCH_LIMIT): List<Series> =
        SearchRepository.matchOf(query)?.let { match -> db.vod().searchSeries(match, limit).map { it.toSeries() } }.orEmpty()

    suspend fun movie(id: String): Movie? = db.vod().movie(id)?.toMovie()

    suspend fun series(id: String): Series? = db.vod().series(id)?.toSeries()

    // ---- Pages ----------------------------------------------------------------------------------

    /** A movie's page (4.14), kept current; null when there's no such movie. */
    fun moviePage(id: String): Flow<MoviePage?> = db.vod().observeMovie(id).flatMapLatest { movie ->
        if (movie == null) return@flatMapLatest flowOf(null)
        combine(
            db.vod().observeMovieDetails(id),
            profile.watching { db.myList().observeIds(it, VodKinds.MOVIE) },
            profile.watching { db.resumePoints().observe(it, VodKinds.MOVIE, id) },
            movie.categoryId?.let { db.vod().observeMoviesBeside(it, id, MORE_LIKE_THIS) } ?: flowOf(emptyList()),
        ) { details, listed, point, beside ->
            MoviePage(
                movie = movie.toMovie(),
                extension = movie.extension,
                category = movie.categoryId?.let { categoryName(it) },
                details = details?.toDetails(),
                inMyList = id in listed,
                progress = point?.toProgress(),
                moreLikeThis = beside.map { it.toMovie() },
            )
        }
    }

    /** A series' page (4.16), kept current; null when there's no such series. */
    fun seriesPage(id: String): Flow<SeriesPage?> = db.vod().observeSeries(id).flatMapLatest { series ->
        if (series == null) return@flatMapLatest flowOf(null)
        combine(
            db.vod().observeSeriesRead(id),
            profile.watching { db.resumePoints().observeSeries(it, id) },
            profile.watching { db.myList().observeIds(it, VodKinds.SERIES) },
        ) { read, points, listed ->
            val details = read?.details
            SeriesPage(
                series = series.toSeries(),
                plot = details?.plot ?: series.plot,
                cast = names(details?.cast ?: series.cast),
                director = details?.director ?: series.director,
                trailer = details?.trailer ?: series.trailer,
                category = series.categoryId?.let { categoryName(it) },
                details = details?.toDetails(),
                // The specials (season 0) after the seasons.
                episodes = read?.episodes.orEmpty().sortedWith(compareBy({ it.season == 0 }, { it.position })).map { it.toEpisode() },
                progress = points.associate { it.contentId to it.toProgress() },
                inMyList = id in listed,
            )
        }
    }

    private suspend fun categoryName(id: String): String? = db.vod().category(id)?.name

    /**
     * Reads a movie's details from its provider, unless they were read in the last week. False
     * when they couldn't be read; the page shows what the catalog has meanwhile.
     */
    suspend fun loadMovie(id: String): Boolean {
        val movie = db.vod().movie(id) ?: return false
        val known = db.vod().movieDetails(id)
        if (known != null && fresh(known.fetchedAt, MOVIE_DETAILS)) return true
        if (!readable(movie.sourceId)) return known != null
        return reading {
            val info = sources.xtreamClient(movie.sourceId).movieInfo(movie.remoteId)
            db.vod().upsertMovieDetails(
                MovieDetailsEntity(
                    movieId = id,
                    plot = info.plot,
                    cast = info.cast,
                    director = info.director,
                    genre = info.genre,
                    durationSecs = info.durationSecs,
                    poster = info.poster,
                    backdrop = info.backdrop,
                    trailer = info.trailer,
                    year = info.year,
                    rating = info.rating,
                    age = info.age,
                    video = info.video?.toFacts(),
                    audio = info.audio?.toFacts(),
                    fetchedAt = clock.now().toEpochMilli(),
                ),
            )
            // The details may know its file better than the catalog did.
            val extension = info.extension
            if (extension != null && extension != movie.extension) db.vod().setExtension(id, extension)
        } != null
    }

    /**
     * Reads a series and its episodes from its provider, unless that was done in the last day and
     * the catalog hasn't seen a change since. False when they couldn't be read.
     */
    suspend fun loadSeries(id: String): Boolean {
        val series = db.vod().series(id) ?: return false
        val known = db.vod().seriesDetails(id)
        val updated = series.updatedAt
        val changed = updated != null && known != null && updated > known.fetchedAt
        if (known != null && !changed && fresh(known.fetchedAt, SERIES_DETAILS)) return true
        when (series.layout) {
            null -> Unit
            // A portal's episodes that came with the series are as its catalog last read them.
            SeriesLayout.WITH_TITLE -> return known != null
            else -> return reading { loadPortalSeries(series) } != null
        }
        if (!readable(series.sourceId)) return known != null
        return reading {
            val info = sources.xtreamClient(series.sourceId).seriesInfo(series.remoteId)
            db.vod().replaceEpisodes(
                SeriesDetailsEntity(
                    seriesId = id,
                    plot = info.plot,
                    cast = info.cast,
                    director = info.director,
                    genre = info.genre,
                    backdrop = info.backdrop,
                    trailer = info.trailer,
                    year = info.year,
                    rating = info.rating,
                    age = info.age,
                    fetchedAt = clock.now().toEpochMilli(),
                ),
                info.episodes.mapIndexed { i, episode ->
                    EpisodeEntity(
                        id = "${series.sourceId}:${episode.id}",
                        seriesId = id,
                        remoteId = episode.id,
                        season = episode.season,
                        number = episode.number,
                        title = episode.title,
                        plot = episode.plot,
                        still = episode.still,
                        durationSecs = episode.durationSecs,
                        extension = episode.extension,
                        year = episode.year,
                        addedAt = episode.added?.toEpochMilli(),
                        video = episode.video?.toFacts(),
                        audio = episode.audio?.toFacts(),
                        position = i,
                    )
                },
            )
        } != null
    }

    /**
     * A portal's series and its episodes (brief 7.1): a season at a time, each episode its own
     * file, or from the portal's series module, each season one command for its episodes. What the
     * series says of itself came with the catalog.
     */
    private suspend fun loadPortalSeries(series: SeriesEntity) {
        val client = sources.stalkerClient(series.sourceId)
        val episodes = ArrayList<EpisodeEntity>()
        fun add(season: Int, number: Int, remoteId: String, cmd: String, cmdEpisode: Int?, title: String? = null, still: String? = null, plot: String? = null, durationSecs: Int? = null) {
            episodes += EpisodeEntity(
                id = "${series.id}:$remoteId",
                seriesId = series.id,
                remoteId = remoteId,
                season = season,
                number = number,
                title = title,
                plot = plot,
                still = still,
                durationSecs = durationSecs,
                extension = null,
                year = null,
                addedAt = null,
                video = null,
                audio = null,
                position = episodes.size,
                cmd = cmd,
                cmdEpisode = cmdEpisode,
            )
        }
        // The seasons in order, whatever order the portal lists them in.
        if (series.layout == SeriesLayout.SERIES_MODULE) {
            val seasons = client.serialSeasons(series.remoteId).mapIndexed { i, season -> (season.number ?: (i + 1)) to season }.sortedBy { it.first }
            for ((number, season) in seasons) {
                for (episode in season.episodes) add(number, episode, "${season.id}:$episode", season.cmd, episode)
            }
        } else {
            val seasons = client.seasons(series.remoteId).mapIndexed { i, season -> (season.number ?: (i + 1)) to season }.sortedBy { it.first }
            for ((number, season) in seasons) {
                client.episodes(series.remoteId, season.id).sortedBy { it.number ?: Int.MAX_VALUE }.forEachIndexed { j, episode ->
                    add(number, episode.number ?: (j + 1), episode.id, "/media/file_${episode.id}.mpg", null, episode.name, episode.still, episode.plot, episode.durationSecs)
                }
            }
        }
        db.vod().replaceEpisodes(
            SeriesDetailsEntity(
                seriesId = series.id,
                plot = series.plot,
                cast = series.cast,
                director = series.director,
                genre = series.genre,
                backdrop = null,
                trailer = null,
                year = series.year,
                rating = series.rating,
                age = null,
                fetchedAt = clock.now().toEpochMilli(),
            ),
            episodes,
        )
    }

    /** Only Xtream sources have details to read so far; a portal's come with its catalog. */
    private suspend fun readable(sourceId: String): Boolean =
        db.sources().get(sourceId)?.let { SourceType.of(it.type) == SourceType.Xtream } == true

    private fun fresh(fetchedAt: Long, age: Duration) = Duration.between(Instant.ofEpochMilli(fetchedAt), clock.now()) < age

    /**
     * What [read] returns, or null when it fails: the provider out of reach or not answering as
     * Xtream, a stored login that can't be used, or the title gone from the catalog meanwhile. A
     * page without its details still works.
     */
    private suspend fun <T> reading(read: suspend () -> T): T? = try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        null
    }

    // ---- My List --------------------------------------------------------------------------------

    /** The ids of the movies on My List. */
    val myListMovieIds: Flow<Set<String>> = profile.watching { db.myList().observeIds(it, VodKinds.MOVIE) }.map { it.toSet() }

    /** The ids of the series on My List. */
    val myListSeriesIds: Flow<Set<String>> = profile.watching { db.myList().observeIds(it, VodKinds.SERIES) }.map { it.toSet() }

    /** My List's movies (4.13), the latest added first. */
    val myListMovies: Flow<List<Movie>> = profile.watching { db.myList().observeMovies(it) }.map { list -> list.map { it.toMovie() } }

    /** My List's series (4.15), the latest added first. */
    val myListSeries: Flow<List<Series>> = profile.watching { db.myList().observeSeries(it) }.map { list -> list.map { it.toSeries() } }

    suspend fun setMovieInMyList(id: String, inList: Boolean) {
        val sourceId = db.vod().movie(id)?.sourceId ?: return
        setInMyList(VodKinds.MOVIE, id, sourceId, inList)
    }

    suspend fun setSeriesInMyList(id: String, inList: Boolean) {
        val sourceId = db.vod().series(id)?.sourceId ?: return
        setInMyList(VodKinds.SERIES, id, sourceId, inList)
    }

    private suspend fun setInMyList(kind: String, id: String, sourceId: String, inList: Boolean) {
        val profileId = profile.now()
        if (inList) {
            db.myList().add(MyListEntity(profileId, kind, id, sourceId, clock.now().toEpochMilli()))
        } else {
            db.myList().remove(profileId, kind, id)
        }
    }

    // ---- Playing --------------------------------------------------------------------------------

    /** The movie or episode [id], as the player plays it; null when it isn't in the catalog. */
    suspend fun playable(kind: Playable.Kind, id: String): Playable? = when (kind) {
        Playable.Kind.Movie -> db.vod().movie(id)?.let { movie ->
            val details = db.vod().movieDetails(id)
            Playable(
                kind = kind,
                id = id,
                sourceId = movie.sourceId,
                remoteId = movie.remoteId,
                extension = movie.extension,
                title = movie.name,
                year = details?.year ?: movie.year,
                genres = genres(details?.genre),
                art = details?.backdrop ?: details?.poster ?: movie.poster,
                duration = details?.durationSecs?.let { Duration.ofSeconds(it.toLong()) },
                video = details?.video?.toInfo(),
                audio = details?.audio?.toInfo(),
                command = movie.cmd?.let { PortalCommand(it, fileApart = movie.fileApart) },
            )
        }
        Playable.Kind.Episode -> db.vod().episode(id)?.let { episode ->
            val series = db.vod().series(episode.seriesId) ?: return null
            Playable(
                kind = kind,
                id = id,
                sourceId = series.sourceId,
                remoteId = episode.remoteId,
                extension = episode.extension,
                title = series.name,
                seriesId = series.id,
                season = episode.season,
                number = episode.number,
                episodeTitle = episode.title,
                year = episode.year,
                art = episode.still ?: db.vod().seriesDetails(series.id)?.backdrop ?: series.backdrop ?: series.poster,
                duration = episode.durationSecs?.let { Duration.ofSeconds(it.toLong()) },
                video = episode.video?.toInfo(),
                audio = episode.audio?.toInfo(),
                command = episode.cmd?.let { PortalCommand(it, episode.cmdEpisode) },
            )
        }
    }

    /**
     * Where [playable] starts when it's played (brief 5.9): where it was left, or from its start
     * when it was watched or barely begun.
     */
    suspend fun resumeFrom(playable: Playable): Duration {
        val point = db.resumePoints().points(profile.now(), playable.kindName, listOf(playable.id)).firstOrNull() ?: return Duration.ZERO
        return point.toProgress().takeIf { it.left != null }?.position ?: Duration.ZERO
    }

    /**
     * Keeps where [playable] has got to, [position] of [length] (brief 5.9): saved as it plays and
     * when it stops. At 92 % it's watched; less than 10 s in isn't kept, unless it was watched.
     */
    suspend fun savePlace(playable: Playable, position: Duration, length: Duration) {
        val watched = !length.isZero && position.toMillis() >= length.toMillis() * WATCHED
        // A guest keeps no history (5.11).
        if (!watched && position < MIN_PLACE || !profile.keepsHistory()) return
        db.resumePoints().upsert(
            listOf(
                ResumePointEntity(
                    profileId = profile.now(),
                    kind = playable.kindName,
                    contentId = playable.id,
                    sourceId = playable.sourceId,
                    seriesId = playable.seriesId,
                    positionMs = position.toMillis(),
                    durationMs = length.toMillis(),
                    watched = watched,
                    updatedAt = clock.now().toEpochMilli(),
                ),
            ),
        )
    }

    /**
     * The episode after [episodeId] in its series (brief 5.9): the next in its season, or the
     * first of the next. The specials come after the seasons, so the last episode has none.
     */
    suspend fun nextEpisode(episodeId: String): Playable? {
        val episode = db.vod().episode(episodeId) ?: return null
        val all = db.vod().episodes(episode.seriesId).sortedWith(compareBy({ it.season == 0 }, { it.position }))
        val next = all.getOrNull(all.indexOfFirst { it.id == episodeId } + 1) ?: return null
        if (next.season == 0 && episode.season != 0) return null
        return playable(Playable.Kind.Episode, next.id)
    }

    private val Playable.kindName: String get() = if (kind == Playable.Kind.Movie) VodKinds.MOVIE else VodKinds.EPISODE

    // ---- Continue Watching ----------------------------------------------------------------------

    /**
     * Continue Watching (brief 5.9), kept current: the movies and episodes started and not
     * finished, played since [since], the latest first, at most [limit]. A series shows once, by
     * its latest episode; when that was watched, the next one shows instead, with [nextEpisodes].
     * What was removed from it stays out until it plays again.
     */
    fun continueWatching(since: Instant?, nextEpisodes: Boolean, limit: Int = CONTINUE_LIMIT): Flow<List<ContinueEntry>> =
        profile.watching { db.resumePoints().observeRecent(it, since?.toEpochMilli() ?: 0, RECENT_POINTS) }.map { points -> entriesOf(points, nextEpisodes, limit) }

    private suspend fun entriesOf(points: List<ResumePointEntity>, nextEpisodes: Boolean, limit: Int): List<ContinueEntry> {
        val entries = ArrayList<ContinueEntry>()
        val seen = HashSet<String>()
        for (point in points) {
            if (entries.size >= limit) break
            val updated = Instant.ofEpochMilli(point.updatedAt)
            when (point.kind) {
                VodKinds.MOVIE -> {
                    val progress = point.toProgress().takeIf { !point.hidden && it.left != null } ?: continue
                    val movie = db.vod().movie(point.contentId) ?: continue
                    val playable = playable(Playable.Kind.Movie, movie.id) ?: continue
                    entries += ContinueEntry(playable, db.vod().movieDetails(movie.id)?.backdrop, movie.poster, progress, updated)
                }
                VodKinds.EPISODE -> {
                    // A series counts once, by its latest episode, hidden or not.
                    val seriesId = point.seriesId ?: continue
                    if (!seen.add(seriesId) || point.hidden) continue
                    val progress = point.toProgress()
                    val entry = when {
                        progress.left != null -> playable(Playable.Kind.Episode, point.contentId)?.let { episodeEntry(it, progress, updated) }
                        point.watched && nextEpisodes -> nextEpisode(point.contentId)?.takeIf { !watched(it) }?.let { episodeEntry(it, null, updated) }
                        else -> null
                    }
                    entries += entry ?: continue
                }
            }
        }
        return entries
    }

    private suspend fun episodeEntry(episode: Playable, progress: Progress?, updated: Instant): ContinueEntry? {
        val series = db.vod().series(episode.seriesId ?: return null) ?: return null
        val still = db.vod().episode(episode.id)?.still
        val backdrop = db.vod().seriesDetails(series.id)?.backdrop ?: series.backdrop
        return ContinueEntry(episode, still ?: backdrop, series.poster, progress, updated)
    }

    private suspend fun watched(playable: Playable): Boolean =
        db.resumePoints().points(profile.now(), playable.kindName, listOf(playable.id)).firstOrNull()?.watched == true

    /**
     * Remove from Continue Watching (brief 4.1): a movie, or an episode's whole series, until it's
     * played again.
     */
    suspend fun removeFromContinue(kind: Playable.Kind, id: String) {
        val profileId = profile.now()
        when (kind) {
            Playable.Kind.Movie -> db.resumePoints().hideMovie(profileId, id)
            Playable.Kind.Episode -> db.vod().episode(id)?.seriesId?.let { db.resumePoints().hideSeries(profileId, it) }
        }
    }

    // ---- Watched --------------------------------------------------------------------------------

    /** Where each movie the profile started or marked was left, by movie id. */
    val movieProgress: Flow<Map<String, Progress>> =
        profile.watching { db.resumePoints().observeAll(it, VodKinds.MOVIE) }.map { list -> list.associate { it.contentId to it.toProgress() } }

    /** The series whose every episode the profile has watched. */
    val watchedSeriesIds: Flow<Set<String>> = profile.watching { db.resumePoints().observeWatchedSeries(it) }.map { it.toSet() }

    /** Mark as Watched (4.13), or as Unwatched, which forgets where it was left too. */
    suspend fun setMovieWatched(id: String, watched: Boolean) {
        val movie = db.vod().movie(id) ?: return
        mark(VodKinds.MOVIE, listOf(id), movie.sourceId, seriesId = null, watched)
    }

    /** The episodes [episodeIds] of the series [seriesId] watched, or unwatched (4.16). */
    suspend fun setEpisodesWatched(seriesId: String, episodeIds: List<String>, watched: Boolean) {
        val series = db.vod().series(seriesId) ?: return
        mark(VodKinds.EPISODE, episodeIds, series.sourceId, seriesId, watched)
    }

    /** Green on a season (4.16): all its episodes watched, or unwatched. */
    suspend fun setSeasonWatched(seriesId: String, season: Int, watched: Boolean) =
        setEpisodesWatched(seriesId, db.vod().episodes(seriesId).filter { it.season == season }.map { it.id }, watched)

    /**
     * Mark as Watched on a series' poster (4.15): every episode, read from the provider first if
     * they haven't been. False when they couldn't be.
     */
    suspend fun setSeriesWatched(id: String, watched: Boolean): Boolean {
        if (db.vod().episodes(id).isEmpty() && !loadSeries(id)) return false
        setEpisodesWatched(id, db.vod().episodes(id).map { it.id }, watched)
        return true
    }

    private suspend fun mark(kind: String, ids: List<String>, sourceId: String, seriesId: String?, watched: Boolean) {
        if (ids.isEmpty()) return
        val profileId = profile.now()
        if (!watched) {
            ids.chunked(CHUNK).forEach { db.resumePoints().remove(profileId, kind, it) }
            return
        }
        val now = clock.now().toEpochMilli()
        val known = ids.chunked(CHUNK).flatMap { db.resumePoints().points(profileId, kind, it) }.associateBy { it.contentId }
        db.resumePoints().upsert(
            ids.map { id ->
                ResumePointEntity(profileId, kind, id, sourceId, seriesId, positionMs = 0, durationMs = known[id]?.durationMs ?: 0, watched = true, updatedAt = now)
            },
        )
    }

    // ---- Mapping --------------------------------------------------------------------------------

    private fun counted(categories: Flow<List<VodCategoryEntity>>, counts: Flow<List<CategoryCount>>): Flow<List<VodCategory>> =
        combine(categories, counts) { list, counted ->
            val byId = counted.associate { it.categoryId to it.count }
            list.mapNotNull { category -> byId[category.id]?.takeIf { it > 0 }?.let { VodCategory(category.id, category.name, it) } }
        }

    private fun MovieEntity.toMovie() = Movie(id, name, poster, categoryId, addedAt?.let(Instant::ofEpochMilli), rating, year)

    private fun SeriesEntity.toSeries() = Series(id, name, poster, backdrop, categoryId, genre, year, rating, updatedAt?.let(Instant::ofEpochMilli))

    private fun MovieDetailsEntity.toDetails() = MovieDetails(
        plot = plot,
        cast = names(cast),
        director = director,
        genres = genres(genre),
        duration = durationSecs?.let { Duration.ofSeconds(it.toLong()) },
        poster = poster,
        backdrop = backdrop,
        trailer = trailer,
        year = year,
        rating = rating,
        age = age,
        video = video?.toInfo(),
        audio = audio?.toInfo(),
    )

    private fun SeriesDetailsEntity.toDetails() = SeriesDetails(plot, names(cast), director, genres(genre), backdrop, trailer, year, rating, age)

    private fun EpisodeEntity.toEpisode() = Episode(
        id = id,
        season = season,
        number = number,
        title = title,
        plot = plot,
        still = still,
        duration = durationSecs?.let { Duration.ofSeconds(it.toLong()) },
        year = year,
        added = addedAt?.let(Instant::ofEpochMilli),
        extension = extension,
        video = video?.toInfo(),
        audio = audio?.toInfo(),
    )

    private fun ResumePointEntity.toProgress() = Progress(Duration.ofMillis(positionMs), Duration.ofMillis(durationMs), watched)

    private fun VideoFacts.toInfo() = FileVideo(codec, profile, width, height, frameRate, hdr)

    private fun AudioFacts.toInfo() = FileAudio(codec, channels, language, bitRate)

    private fun XtreamVideo.toFacts() = VideoFacts(codec, profile, width, height, frameRate, hdr)

    private fun XtreamAudio.toFacts() = AudioFacts(codec, channels, language, bitRate)

    companion object {
        private val RECENT: Duration = Duration.ofDays(30)
        private val NEW_EPISODES: Duration = Duration.ofDays(14)
        private const val RECENT_LIMIT = 300
        private const val SEARCH_LIMIT = 100
        private const val MORE_LIKE_THIS = 20
        private const val CHUNK = 500

        /** A title played this far is watched (brief 5.9). */
        private const val WATCHED = 0.92

        /** Continue Watching has the 20 latest (brief 5.9), made from this many of what was played. */
        private const val CONTINUE_LIMIT = 20
        private const val RECENT_POINTS = 200

        /** A title played less than this isn't worth carrying on from. */
        private val MIN_PLACE: Duration = Duration.ofSeconds(10)

        /** A movie's details are read again after a week. */
        val MOVIE_DETAILS: Duration = Duration.ofDays(7)

        /** A series' episodes after a day, or sooner when the catalog sees it change. */
        val SERIES_DETAILS: Duration = Duration.ofDays(1)

        private const val MOST_NAMES = 10
        private const val MOST_GENRES = 3

        /** "Ana Reyes, Tom Hale" as names, the first ten. */
        fun names(text: String?): List<String> =
            text.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MOST_NAMES)

        /** "Thriller, Drama" or "Action / Adventure" as genres, the first three. */
        fun genres(text: String?): List<String> =
            text.orEmpty().split(',', '/', '|').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MOST_GENRES)
    }
}
