// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.room.withTransaction
import ca.gureet.plasma.core.database.AudioFacts
import ca.gureet.plasma.core.database.CategoryCount
import ca.gureet.plasma.core.database.EpisodeEntity
import ca.gureet.plasma.core.database.MovieDetailsEntity
import ca.gureet.plasma.core.database.MovieEntity
import ca.gureet.plasma.core.database.MyListEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.PosterLook
import ca.gureet.plasma.core.database.ResumePointEntity
import ca.gureet.plasma.core.database.SeriesDetailsEntity
import ca.gureet.plasma.core.database.SeriesEntity
import ca.gureet.plasma.core.database.SeriesLayout
import ca.gureet.plasma.core.database.VideoFacts
import ca.gureet.plasma.core.database.VodKinds
import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.core.model.CategoryOrdering
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.source.xtream.XtreamAudio
import ca.gureet.plasma.source.xtream.XtreamMovieInfo
import ca.gureet.plasma.source.xtream.XtreamVideo
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** A category of movies or series, with how many it holds. */
data class VodCategory(
    val id: String,
    /** The name it shows under: the user's, or the provider's. */
    val name: String,
    val count: Int,
    /** The provider's name for it, which Lock Adult Groups' and Kids Words' words go by, whatever it's renamed. */
    val providerName: String = name,
    /** Hidden by the user (Settings › Portals & Playlists › the source › Categories), with its titles. */
    val hidden: Boolean = false,
    /** Made by the user, with titles the user picked; it never hides them itself. */
    val custom: Boolean = false,
    /** The provider marks it adult, or one of its titles (Lock Adult Groups, brief 5.12, #125). */
    val adult: Boolean = false,
)

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
    /** The movie's category, or the series': what parental controls go by (brief 5.12). */
    val categoryId: String? = null,
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
    /** Settings › Portals & Playlists › Replace Placeholder Posters (the owner's #81), on unless turned off. */
    replacePlaceholders: Flow<Boolean> = flowOf(true),
    /** The pictures known not to load on this TV (#228): a poster among them shows as a placeholder does. */
    private val unloadable: Flow<UnloadablePictures.Known> = flowOf(UnloadablePictures.Known.NONE),
) {
    private val replace = replacePlaceholders.distinctUntilChanged()

    /** How each poster shown is chosen: by Replace Placeholder Posters, and past the pictures known not to load. */
    private val choosing: Flow<Choosing> = combine(replace, unloadable) { replacing, known -> Choosing(replacing, known) }

    /**
     * The movie categories, the primary source's first, each source's in the order its Categories
     * page says and under the names the user gave them: hidden ones too, marked, and the user's own,
     * empty or not. A provider's with no movies is left out.
     */
    val movieCategories: Flow<List<VodCategory>> = arranged(CategoryKind.Movies, db.vod().observeMovieCounts(), db.vod().observePickedMovieCounts())

    /** The series categories, as [movieCategories]. */
    val seriesCategories: Flow<List<VodCategory>> = arranged(CategoryKind.Series, db.vod().observeSeriesCounts(), db.vod().observePickedSeriesCounts())

    /** How many movies each source has, by source id, sources turned off included, as channels are counted (#189). */
    val movieCountsBySource: Flow<Map<String, Int>> = db.vod().observeMovieCountsBySource().map { list -> list.associate { it.sourceId to it.count } }

    /** How many series each source has, as [movieCountsBySource] (#189). */
    val seriesCountsBySource: Flow<Map<String, Int>> = db.vod().observeSeriesCountsBySource().map { list -> list.associate { it.sourceId to it.count } }

    /** The movies of [categoryId]: a provider's, or those the user put in one of the user's own. */
    fun moviesIn(categoryId: String): Flow<List<Movie>> = db.vod().observeCategoryOwn(categoryId).distinctUntilChanged().flatMapLatest { own ->
        if (own == true) db.vod().observeMoviesPicked(categoryId) else db.vod().observeMoviesIn(categoryId)
    }.movies()

    /** The series of [categoryId], as [moviesIn]. */
    fun seriesIn(categoryId: String): Flow<List<Series>> = db.vod().observeCategoryOwn(categoryId).distinctUntilChanged().flatMapLatest { own ->
        if (own == true) db.vod().observeSeriesPicked(categoryId) else db.vod().observeSeriesIn(categoryId)
    }.series()

    /** Recently Added (4.13): movies added in the last 30 days, newest first, at most 300. */
    fun recentMovies(): Flow<List<Movie>> =
        db.vod().observeRecentMovies(since = clock.now().minus(RECENT).toEpochMilli(), limit = RECENT_LIMIT).movies()

    /** New Episodes (4.15): series with an episode added in the last 14 days, latest first, at most 300. */
    fun newEpisodes(): Flow<List<Series>> =
        db.vod().observeRecentSeries(since = clock.now().minus(NEW_EPISODES).toEpochMilli(), limit = RECENT_LIMIT).series()

    /** Movies whose name has a word starting with each word of [query] (Search, brief 5.10). */
    suspend fun searchMovies(query: String, limit: Int = SEARCH_LIMIT): List<Movie> {
        val choice = choosing.first()
        return SearchRepository.matchOf(query)?.let { match -> db.vod().searchMovies(match, limit).map { it.toMovie(choice) } }.orEmpty()
    }

    /** Series whose name has a word starting with each word of [query]. */
    suspend fun searchSeries(query: String, limit: Int = SEARCH_LIMIT): List<Series> {
        val choice = choosing.first()
        return SearchRepository.matchOf(query)?.let { match -> db.vod().searchSeries(match, limit).map { it.toSeries(choice) } }.orEmpty()
    }

    suspend fun movie(id: String): Movie? = db.vod().movie(id)?.toMovie(choosing.first())

    suspend fun series(id: String): Series? = db.vod().series(id)?.toSeries(choosing.first())

    // ---- Pages ----------------------------------------------------------------------------------

    /** A movie's page (4.14), kept current; null when there's no such movie. */
    fun moviePage(id: String): Flow<MoviePage?> = db.vod().observeMovie(id).flatMapLatest { movie ->
        if (movie == null) return@flatMapLatest flowOf(null)
        combine(
            db.vod().observeMovieDetails(id),
            profile.watching { db.myList().observeIds(it, VodKinds.MOVIE) },
            profile.watching { db.resumePoints().observe(it, VodKinds.MOVIE, id) },
            movie.categoryId?.let { db.vod().observeMoviesBeside(it, id, MORE_LIKE_THIS) } ?: flowOf(emptyList()),
            choosing,
        ) { details, listed, point, beside, choice ->
            MoviePage(
                movie = movie.toMovie(choice),
                extension = movie.extension,
                category = movie.categoryId?.let { categoryName(it) },
                // The details' poster can be the same placeholder the catalog gives, or one that doesn't load.
                details = details?.toDetails()?.let { if ((choice.replacing && movie.isStandIn(it.poster)) || it.poster in choice.unloadable) it.copy(poster = null) else it },
                inMyList = id in listed,
                progress = point?.toProgress(),
                moreLikeThis = beside.map { it.toMovie(choice) },
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
            choosing,
        ) { read, points, listed, choice ->
            val details = read?.details
            SeriesPage(
                series = series.toSeries(choice),
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

    private suspend fun categoryName(id: String): String? = db.vod().category(id)?.let { it.customName ?: it.name }

    /**
     * Reads a movie's details from its provider, unless they were read in the last week. False
     * when they couldn't be read; the page shows what the catalog has meanwhile.
     */
    suspend fun loadMovie(id: String): Boolean {
        val movie = db.vod().movie(id) ?: return false
        val known = db.vod().movieDetails(id)
        if (known != null && fresh(known.fetchedAt, MOVIE_DETAILS)) return true
        if (!readable(movie.sourceId)) return known != null
        return reading { keepMovieDetails(movie, sources.xtreamClient(movie.sourceId).movieInfo(movie.remoteId)) } != null
    }

    /**
     * Reads [id]'s details from its provider for its own poster, when the catalog only has the
     * provider's placeholder for it, or no picture (the owner's #81), or one that doesn't load on
     * this TV (#228): Xtream's `get_vod_info` often has the real one, which is where TiviMate shows
     * it from. A movie is asked once a week at most, found or not, and not once one is found; the
     * details are kept for its page too. False when it wasn't asked; a provider out of reach throws.
     */
    suspend fun findPoster(id: String): Boolean {
        val found = lookUpPoster(id) ?: return false
        keepPosters(listOf(found))
        return true
    }

    /** A movie's details as [lookUpPoster] read them, to be kept by [keepPosters]. */
    class PosterDetails internal constructor(internal val movie: MovieEntity, internal val info: XtreamMovieInfo)

    /** [findPoster]'s reading, without keeping what it read: null when the movie wasn't asked. */
    suspend fun lookUpPoster(id: String): PosterDetails? {
        if (!replace.first()) return null
        val movie = db.vod().movie(id) ?: return null
        if (!wantsPoster(movie.poster, movie.standIn, movie.art, movie.artStandIn, movie.artCheckedAt, unloadable.first())) return null
        if (!readable(movie.sourceId)) return null
        return PosterDetails(movie, sources.xtreamClient(movie.sourceId).movieInfo(movie.remoteId))
    }

    /** A series' details' posters as [lookUpSeriesPoster] read them, to be kept by [keepSeriesPosters]. */
    class SeriesPosterDetails internal constructor(internal val series: SeriesEntity, internal val posters: List<String>)

    /**
     * Reads [id]'s details from its provider for its own poster, as [lookUpPoster] does a movie's
     * (#228): Xtream's `get_series_info`, which often names the catalog's poster again, but now and
     * then one where the catalog has a placeholder or none. Null when the series wasn't asked; a
     * provider out of reach throws.
     */
    suspend fun lookUpSeriesPoster(id: String): SeriesPosterDetails? {
        if (!replace.first()) return null
        val series = db.vod().series(id) ?: return null
        if (series.layout != null || !series.wantsPoster(unloadable.first())) return null
        if (!readable(series.sourceId)) return null
        return SeriesPosterDetails(series, sources.xtreamClient(series.sourceId).seriesInfo(series.remoteId).posters)
    }

    /** Keeps what [lookUpSeriesPoster] read for each of [found], together, as [keepPosters] does. */
    suspend fun keepSeriesPosters(found: List<SeriesPosterDetails>) {
        db.withTransaction { found.forEach { keepSeriesArt(it.series, it.posters) } }
    }

    /** A list [PosterSweep] goes through (#228): Recently Added or New Episodes, or a category. */
    class SweepList internal constructor(internal val series: Boolean, internal val categoryId: String?)

    /**
     * The lists [PosterSweep] goes through, in their order on screen (#228): Recently Added and the
     * movie categories, then New Episodes and the series categories, but hidden ones, those the
     * provider marks adult, and the user's own, whose titles are in a provider's too. None while
     * Replace Placeholder Posters is off: no poster is looked for then.
     */
    suspend fun sweepLists(): List<SweepList> {
        if (!replace.first()) return emptyList()
        fun shown(list: List<VodCategory>) = list.filter { !it.hidden && !it.adult && !it.custom }
        return buildList {
            add(SweepList(series = false, categoryId = null))
            shown(movieCategories.first()).forEach { add(SweepList(series = false, it.id)) }
            add(SweepList(series = true, categoryId = null))
            shown(seriesCategories.first()).forEach { add(SweepList(series = true, it.id)) }
        }
    }

    /**
     * The titles in [list] at its [round]th [SWEEP_PAGE] that look for their own poster (#228),
     * none of them maybe; null once the list has run out.
     */
    suspend fun sweepPage(list: SweepList, round: Int): List<PosterSweep.Title>? {
        val offset = round * SWEEP_PAGE
        val pictures = db.pictures()
        val page = when {
            list.categoryId != null && list.series -> pictures.seriesIn(list.categoryId, offset, SWEEP_PAGE)
            list.categoryId != null -> pictures.moviesIn(list.categoryId, offset, SWEEP_PAGE)
            offset >= RECENT_LIMIT -> emptyList()
            list.series -> pictures.recentSeries(clock.now().minus(NEW_EPISODES).toEpochMilli(), offset, SWEEP_PAGE)
            else -> pictures.recentMovies(clock.now().minus(RECENT).toEpochMilli(), offset, SWEEP_PAGE)
        }
        if (page.isEmpty()) return null
        val known = unloadable.first()
        return page.filter { it.wantsPoster(known) }.map { PosterSweep.Title(it.id, list.series) }
    }

    /**
     * [title]'s details couldn't be read for its poster (#228): it isn't looked for again for a
     * week, as one whose details had none, so a title the provider can't answer for doesn't hold
     * the sweep up.
     */
    suspend fun lookedFor(title: PosterSweep.Title) {
        val at = clock.now().toEpochMilli()
        if (title.series) db.vod().markSeriesArtChecked(title.id, at) else db.vod().markArtChecked(title.id, at)
    }

    /**
     * Whether a title with this [poster] from its catalog and this [art] from its details, read
     * last at [checkedAt], looks for its own poster in its details (#81, #228): neither is one it
     * shows, as each is a placeholder, missing or known not to load, and its details haven't been
     * read for it in the last week.
     */
    private fun wantsPoster(poster: String?, standIn: Boolean, art: String?, artStandIn: Boolean, checkedAt: Long?, known: UnloadablePictures.Known): Boolean {
        if (poster != null && !standIn && poster !in known) return false
        if (art != null && !artStandIn && art !in known) return false
        return checkedAt == null || !fresh(checkedAt, MOVIE_DETAILS)
    }

    /** [wantsPoster] for this series; [evenIfLookedLately], however lately its details were read. */
    private fun SeriesEntity.wantsPoster(known: UnloadablePictures.Known, evenIfLookedLately: Boolean = false): Boolean =
        wantsPoster(poster, standIn, art, artStandIn, artCheckedAt.takeUnless { evenIfLookedLately }, known)

    /** [wantsPoster] for a title as [ca.gureet.plasma.core.database.PicturesDao] gives it. */
    internal fun PosterLook.wantsPoster(known: UnloadablePictures.Known): Boolean = wantsPoster(poster, standIn, art, artStandIn, artCheckedAt, known)

    /**
     * Keeps the poster [posters], from [series]' details, name for it (#228), as [keepMovieDetails]
     * keeps a movie's: the first that isn't a placeholder, the catalog's or one the details of
     * [StandIns.TITLES] or more of the source's series give, and isn't known not to load. None is
     * kept too, with when it was looked for.
     */
    private suspend fun keepSeriesArt(series: SeriesEntity, posters: List<String>) {
        val vod = db.vod()
        val known = unloadable.first()
        val placeholders = vod.standInSeriesPosters(series.sourceId).toSet()
        val counted = posters.filter { it !in placeholders && it !in known }.map { it to vod.seriesArtCount(series.sourceId, it, series.id) + 1 }
        val (art, shared) = counted.firstOrNull { it.second < StandIns.TITLES } ?: counted.firstOrNull() ?: (null to 0)
        val standIn = art != null && shared >= StandIns.TITLES
        vod.setSeriesArt(series.id, art, standIn, clock.now().toEpochMilli())
        if (art != null && standIn) vod.markSeriesArtStandIn(series.sourceId, art)
    }

    /**
     * Keeps what [lookUpPoster] read for each of [found], together (#228): the lists showing them
     * change once for them all.
     */
    suspend fun keepPosters(found: List<PosterDetails>) {
        db.withTransaction { found.forEach { keepMovieDetails(it.movie, it.info) } }
    }

    /**
     * Keeps what [movie]'s provider says of it in [info] (brief 5.9): its details, for its page;
     * its file, which the details may know better than the catalog did; and for a movie whose
     * catalog picture is a placeholder, missing or doesn't load (#228), its own poster from them
     * (the owner's #81). The details' poster can be a placeholder too: the catalog's, or one the
     * details of [StandIns.TITLES] or more of the source's movies give, which is marked so it
     * isn't shown. One known not to load isn't taken.
     */
    private suspend fun keepMovieDetails(movie: MovieEntity, info: XtreamMovieInfo) {
        val vod = db.vod()
        val now = clock.now().toEpochMilli()
        vod.upsertMovieDetails(
            MovieDetailsEntity(
                movieId = movie.id,
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
                fetchedAt = now,
            ),
        )
        val extension = info.extension
        if (extension != null && extension != movie.extension) vod.setExtension(movie.id, extension)
        val known = unloadable.first()
        if (movie.poster != null && !movie.standIn && movie.poster !in known) return
        val placeholders = vod.standInPosters(movie.sourceId).toSet()
        // Each poster the details name, but placeholders and ones known not to load, with how many of the source's movies would have it then.
        val counted = info.posters.filter { it !in placeholders && it !in known }.map { it to vod.artCount(movie.sourceId, it, movie.id) + 1 }
        val (art, shared) = counted.firstOrNull { it.second < StandIns.TITLES } ?: counted.firstOrNull() ?: (null to 0)
        val standIn = art != null && shared >= StandIns.TITLES
        vod.setArt(movie.id, art, standIn, now)
        if (art != null && standIn) vod.markArtStandIn(movie.sourceId, art)
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
            // Its own poster, while its details are here, for a series that shows none (#228).
            if (replace.first() && series.wantsPoster(unloadable.first(), evenIfLookedLately = true)) keepSeriesArt(series, info.posters)
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
    val myListMovies: Flow<List<Movie>> = profile.watching { db.myList().observeMovies(it) }.movies()

    /** My List's series (4.15), the latest added first. */
    val myListSeries: Flow<List<Series>> = profile.watching { db.myList().observeSeries(it) }.series()

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
                art = details?.backdrop ?: details?.poster?.takeUnless { (replace.first() && movie.isStandIn(it)) || it in unloadable.first() } ?: movie.shown(choosing.first()),
                duration = details?.durationSecs?.let { Duration.ofSeconds(it.toLong()) },
                video = details?.video?.toInfo(),
                audio = details?.audio?.toInfo(),
                command = movie.cmd?.let { PortalCommand(it, fileApart = movie.fileApart) },
                categoryId = movie.categoryId,
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
                art = episode.still ?: db.vod().seriesDetails(series.id)?.backdrop ?: series.backdrop ?: series.shown(choosing.first()),
                duration = episode.durationSecs?.let { Duration.ofSeconds(it.toLong()) },
                video = episode.video?.toInfo(),
                audio = episode.audio?.toInfo(),
                command = episode.cmd?.let { PortalCommand(it, episode.cmdEpisode) },
                categoryId = series.categoryId,
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
                    entries += ContinueEntry(playable, db.vod().movieDetails(movie.id)?.backdrop, movie.shown(choosing.first()), progress, updated)
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
        return ContinueEntry(episode, still ?: backdrop, series.shown(choosing.first()), progress, updated)
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

    /**
     * [kind]'s categories of the sources turned on: the sources in their order, each one's in the
     * order its Categories page says, with how many titles each has ([counts], and for the user's
     * own [picked]).
     */
    private fun arranged(kind: CategoryKind, counts: Flow<List<CategoryCount>>, picked: Flow<List<CategoryCount>>): Flow<List<VodCategory>> {
        // Only what orders the categories: a refresh writes to its source as it goes.
        val sources = db.sources().observeAll().map { list -> list.map { SourceOrder(it.id, it.enabled, it.order(kind)) } }.distinctUntilChanged()
        return combine(db.vod().observeCategories(kind.id), sources, counts, picked) { list, ordered, counted, own ->
            val byId = (counted + own).associate { it.categoryId to it.count }
            val bySource = list.groupBy { it.sourceId }
            ordered.filter { it.enabled }.flatMap { source ->
                CategoryOrdering.sorted(bySource[source.id].orEmpty(), source.order) { it.place() }.mapNotNull { category ->
                    val count = byId[category.id] ?: 0
                    if (count == 0 && !category.custom) return@mapNotNull null
                    VodCategory(category.id, category.customName ?: category.name, count, category.name, category.hidden, category.custom, category.adult)
                }
            }
        }.distinctUntilChanged()
    }

    /** Lists of movies as they show, with each one's poster as [choosing] says. */
    private fun Flow<List<MovieEntity>>.movies(): Flow<List<Movie>> = combine(this, choosing) { list, choice -> list.map { it.toMovie(choice) } }

    private fun Flow<List<SeriesEntity>>.series(): Flow<List<Series>> = combine(this, choosing) { list, choice -> list.map { it.toSeries(choice) } }

    private fun MovieEntity.toMovie(choice: Choosing) = Movie(id, name, shown(choice), categoryId, addedAt?.let(Instant::ofEpochMilli), rating, year)

    private fun SeriesEntity.toSeries(choice: Choosing) = Series(id, name, shown(choice), backdrop, categoryId, genre, year, rating, updatedAt?.let(Instant::ofEpochMilli))

    /**
     * The poster a movie shows (the owner's #81): replacing placeholders, its own from the catalog,
     * or else one found in its details, or none, so its title is set in type; otherwise the
     * catalog's picture as the provider gives it. Either way, never one known not to load on this
     * TV (#228): the next one, or the title in type.
     */
    private fun MovieEntity.shown(choice: Choosing): String? = when {
        choice.replacing -> poster.takeUnless { standIn || it in choice.unloadable } ?: art.takeUnless { artStandIn || it in choice.unloadable }
        else -> poster.takeUnless { it in choice.unloadable }
    }

    /**
     * A series' poster, as [MovieEntity.shown] chooses a movie's: replacing placeholders, its own
     * from the catalog, or else one found in its details (#228), or none; otherwise the catalog's
     * picture as the provider gives it. Never one known not to load on this TV (#228).
     */
    private fun SeriesEntity.shown(choice: Choosing): String? = when {
        choice.replacing -> poster.takeUnless { standIn || it in choice.unloadable } ?: art.takeUnless { artStandIn || it in choice.unloadable }
        else -> poster.takeUnless { it in choice.unloadable }
    }

    /** [picture] is a placeholder this movie has: its catalog's, as its details often give it too, or its details'. */
    private fun MovieEntity.isStandIn(picture: String?): Boolean =
        picture != null && (standIn && picture == poster || artStandIn && picture == art)

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

        /** How many of each list [PosterSweep] takes in a round: about a screen of posters. */
        const val SWEEP_PAGE = 20
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

/** How a poster is chosen: by Replace Placeholder Posters (the owner's #81), and past the pictures known not to load (#228). */
private data class Choosing(val replacing: Boolean, val unloadable: UnloadablePictures.Known)
