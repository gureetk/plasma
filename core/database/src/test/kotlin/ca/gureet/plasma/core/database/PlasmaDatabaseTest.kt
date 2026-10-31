// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlasmaDatabaseTest {
    private lateinit var db: PlasmaDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun close() = db.close()

    private val home = SourceEntity(id = "home", type = "xtream", name = "Home", server = "http://line.example.org/", username = "viewer", passwordEnc = "x", priority = 0, addedAt = 1)

    private fun channel(key: String, number: Int, generation: Long, sourceId: String = "home") = ChannelEntity(
        id = "$sourceId:$key", sourceId = sourceId, key = key, groupId = "$sourceId:4", name = key, position = number,
        playlistNumber = number, number = number, numberReason = "Playlist", wantedNumber = null, logo = null,
        tvgId = null, epgKey = null, catchupDays = null, isRadio = false, generation = generation,
    )

    private fun group(remoteId: String, generation: Long) =
        GroupEntity(id = "home:$remoteId", sourceId = "home", remoteId = remoteId, name = "Sports", position = 0, generation = generation)

    private fun programme(epgKey: String, start: Long, end: Long, generation: Long) = ProgrammeEntity(
        sourceId = "home", epgKey = epgKey, startAt = start, endAt = end, title = "P$start", subtitle = null,
        description = null, category = null, icon = null, season = null, episode = null, isNew = false, generation = generation,
    )

    @Test fun aNewImportReplacesTheOldOneWhole() = runTest {
        db.sources().upsert(home)
        db.channels().replaceSource("home", 1, listOf(group("4", 1)), listOf(channel("arena1", 105, 1), channel("atlas", 120, 1)))

        db.channels().replaceSource(
            "home", 2,
            listOf(group("4", 2)),
            listOf(channel("arena1", 105, 2), channel("velocity", 107, 2)),
        )

        assertEquals(listOf("home:arena1", "home:velocity"), db.channels().all().map { it.id })
        assertEquals(listOf("home:4"), db.groups().forSource("home").map { it.id })
    }

    @Test fun renumbersOtherSourcesChannelsInTheSameTransaction() = runTest {
        db.sources().upsert(home)
        db.sources().upsert(home.copy(id = "backup", priority = 1))
        db.channels().replaceSource("backup", 1, emptyList(), listOf(channel("ringside", 108, 1, sourceId = "backup")))

        db.channels().replaceSource(
            "home", 1, emptyList(), listOf(channel("courtside", 108, 1)),
            renumbered = listOf(NumberUpdate("backup:ringside", 1108, "DuplicateFix", 108)),
        )

        val ringside = db.channels().get("backup:ringside")!!
        assertEquals(1108, ringside.number)
        assertEquals("DuplicateFix", ringside.numberReason)
        assertEquals(108, ringside.wantedNumber)
    }

    @Test fun removingASourceTakesItsChannelsAndGroups() = runTest {
        db.sources().upsert(home)
        db.channels().replaceSource("home", 1, listOf(group("4", 1)), listOf(channel("arena1", 105, 1)))

        db.sources().delete("home")

        assertTrue(db.channels().all().isEmpty())
        assertTrue(db.groups().forSource("home").isEmpty())
    }

    @Test fun keepsFavoritesInTheUsersOrder() = runTest {
        val favorites = db.favorites()
        favorites.add("default", "home:wildframe", emptySet())
        favorites.add("default", "home:northline", emptySet())
        favorites.add("default", "home:velocity", emptySet())
        favorites.add("default", "home:northline", emptySet())
        assertEquals(listOf("home:wildframe", "home:northline", "home:velocity"), favorites.list("default").map { it.channelId })

        favorites.move("default", setOf("home:velocity"), after = null)
        assertEquals(listOf("home:velocity", "home:wildframe", "home:northline"), favorites.observe("default").first().map { it.channelId })
        favorites.move("default", setOf("home:velocity"), after = setOf("home:northline"))
        assertEquals(listOf("home:wildframe", "home:northline", "home:velocity"), favorites.list("default").map { it.channelId })

        favorites.remove("default", "home:wildframe")
        favorites.add("default", "backup:ringside", emptySet())
        favorites.removeForSource("backup")
        assertEquals(listOf("home:northline", "home:velocity"), favorites.list("default").map { it.channelId })
    }

    /** A merged channel may be kept as a favorite under any of its sources' ids (brief 5.1). */
    @Test fun aMergedChannelIsOneFavorite() = runTest {
        val favorites = db.favorites()
        val arena = setOf("home:arena1", "backup:arena1")
        favorites.add("default", "backup:arena1", emptySet())
        favorites.add("default", "home:velocity", emptySet())
        favorites.add("default", "home:northline", emptySet())
        // Adding it under its other source's id finds it there.
        favorites.add("default", "home:arena1", arena)
        assertEquals(listOf("backup:arena1", "home:velocity", "home:northline"), favorites.list("default").map { it.channelId })

        // It moves by any of its ids, and others move after it by any of them.
        favorites.move("default", arena, after = setOf("home:velocity"))
        assertEquals(listOf("home:velocity", "backup:arena1", "home:northline"), favorites.list("default").map { it.channelId })
        favorites.move("default", setOf("home:velocity"), after = arena)
        assertEquals(listOf("backup:arena1", "home:velocity", "home:northline"), favorites.list("default").map { it.channelId })
        // A channel that isn't a favorite, or a place that's gone, moves nothing.
        favorites.move("default", setOf("home:pulse"), after = null)
        favorites.move("default", setOf("home:northline"), after = setOf("home:pulse"))
        assertEquals(listOf("backup:arena1", "home:velocity", "home:northline"), favorites.list("default").map { it.channelId })

        favorites.removeAll("default", arena.toList())
        assertEquals(listOf("home:velocity", "home:northline"), favorites.list("default").map { it.channelId })
    }

    @Test fun keepsMoviesAndSeriesAndDropsWhatARefreshDoesntSee() = runTest {
        db.sources().upsert(home)
        val vod = db.vod()
        fun movie(id: String, category: String, added: Long?, generation: Long) =
            MovieEntity("home:$id", "home", id, "home:movie:$category", "Movie $id", null, added, 7f, 2026, "mkv", id.toInt(), generation)
        vod.upsertCategories(listOf(VodCategoryEntity("home:movie:30", "home", "movie", "30", "Thrillers", 0, 1), VodCategoryEntity("home:movie:31", "home", "movie", "31", "Dramas", 1, 1)))
        vod.upsertMovies(listOf(movie("1", "30", 5_000, 1), movie("2", "30", 9_000, 1), movie("3", "31", null, 1)))
        vod.commit("home", 1, at = 10_000)

        assertEquals(listOf("Thrillers", "Dramas"), vod.observeCategories("movie").first().map { it.name })
        assertEquals(mapOf("home:movie:30" to 2, "home:movie:31" to 1), vod.observeMovieCounts().first().associate { it.categoryId to it.count })
        assertEquals(listOf("home:1", "home:2"), vod.observeMoviesIn("home:movie:30").first().map { it.id })
        // Recently added: newest first, and only what says when it came.
        assertEquals(listOf("home:2", "home:1"), vod.observeRecentMovies(since = 0, limit = 10).first().map { it.id })
        assertEquals(10_000L, db.sources().get("home")!!.vodRefreshedAt)
        // Search finds them by name.
        assertEquals(listOf("home:1", "home:2", "home:3"), vod.searchMovies("movie*", limit = 10).map { it.id }.sorted())
        assertEquals(listOf("home:2"), vod.searchMovies("2*", limit = 10).map { it.id })

        // A refresh sees 2 and a new 4, in a new category: 1, 3 and Dramas go once it's done.
        vod.upsertCategories(listOf(VodCategoryEntity("home:movie:30", "home", "movie", "30", "Thrillers", 0, 2), VodCategoryEntity("home:movie:32", "home", "movie", "32", "Comedies", 1, 2)))
        vod.upsertMovies(listOf(movie("2", "30", 9_000, 2), movie("4", "32", 12_000, 2)))
        assertEquals(4, vod.movieCount("home"))
        vod.commit("home", 2, at = 20_000)
        assertEquals(listOf("home:2"), vod.observeMoviesIn("home:movie:30").first().map { it.id })
        assertEquals(listOf("Thrillers", "Comedies"), vod.observeCategories("movie").first().map { it.name })
        assertEquals(2, vod.movieCount("home"))
        // What's gone is gone from Search too.
        assertEquals(listOf("home:2", "home:4"), vod.searchMovies("movie*", limit = 10).map { it.id }.sorted())

        // A source that's turned off shows none.
        db.sources().setEnabled("home", false)
        assertEquals(emptyList<MovieEntity>(), vod.observeRecentMovies(since = 0, limit = 10).first())
        assertEquals(emptyList<VodCategoryEntity>(), vod.observeCategories("movie").first())
    }

    private fun movie(id: String, generation: Long = 1) =
        MovieEntity("home:$id", "home", id, null, "Movie $id", null, null, null, null, "mkv", id.toInt(), generation)

    private fun series(id: String) = SeriesEntity("home:$id", "home", id, null, "Series $id", null, null, null, null, null, null, null, null, null, null, id.toInt(), 1)

    private fun episode(id: String, seriesId: String, season: Int, number: Int, position: Int) =
        EpisodeEntity("home:$id", "home:$seriesId", id, season, number, "Episode $number", null, null, 3_000, "mkv", null, null, null, null, position)

    private fun details(movieId: String) = MovieDetailsEntity(
        movieId, "A plot.", "Ana Reyes", "Mia Stone", "Drama", 7_080, null, null, "abcdefghijk", 2026, 7.4f, "13+",
        VideoFacts("hevc", "Main 10", 3840, 2160, 24f, "HDR10"), AudioFacts("eac3", 6, "eng", 640_000), fetchedAt = 1,
    )

    @Test fun aMoviesDetailsStayThroughRefreshesAndGoWithIt() = runTest {
        db.sources().upsert(home)
        val vod = db.vod()
        vod.upsertMovies(listOf(movie("7101"), movie("7102")))
        vod.upsertMovieDetails(details("home:7101"))
        vod.upsertMovieDetails(details("home:7102").copy(video = null, audio = null))

        // A refresh that sees 7101 again keeps its details; 7102 isn't seen, and its go with it.
        vod.upsertMovies(listOf(movie("7101", generation = 2)))
        vod.commit("home", 2, at = 20_000)

        assertEquals(details("home:7101"), vod.movieDetails("home:7101"))
        assertNull(vod.movieDetails("home:7102"))
        db.sources().delete("home")
        assertNull(vod.movieDetails("home:7101"))
    }

    @Test fun aPosterFoundInAMoviesDetailsStaysThroughRefreshes() = runTest {
        // The owner's #81: a refresh writes what the catalog says, and keeps what Plasma found.
        db.sources().upsert(home)
        val vod = db.vod()
        vod.upsertMovies(listOf(movie("7101").copy(poster = "https://media.example/placeholder.png")))
        vod.setArt("home:7101", "https://media.example/neon-tide.jpg", standIn = false, at = 5_000)

        vod.upsertMovies(listOf(movie("7101", generation = 2).copy(poster = "https://media.example/placeholder.png", name = "Neon Tide")))

        val movie = vod.movie("home:7101")!!
        assertEquals("Neon Tide", movie.name)
        assertEquals("https://media.example/neon-tide.jpg", movie.art)
        assertEquals(5_000L, movie.artCheckedAt)
    }

    @Test fun aPictureManyTitlesShareIsMarkedAndKept() = runTest {
        db.sources().upsert(home)
        val vod = db.vod()
        val placeholder = "https://media.example/placeholder.png"
        vod.upsertMovies((7101..7110).map { movie(it.toString()).copy(poster = placeholder) } + movie("7199").copy(poster = "https://media.example/neon-tide.jpg"))
        vod.upsertSeries((8101..8110).map { series(it.toString()).copy(poster = placeholder) })
        // A poster the details give that's the catalog's placeholder is one too.
        vod.setArt("home:7101", placeholder, standIn = false, at = 1)

        vod.markStandInPosters("home", titles = 10)

        assertEquals(placeholder, vod.movie("home:7102")!!.poster)
        assertTrue(vod.movie("home:7102")!!.standIn)
        assertEquals(false, vod.movie("home:7199")!!.standIn)
        assertTrue(vod.series("home:8101")!!.standIn)
        assertTrue(vod.movie("home:7101")!!.artStandIn)
        assertEquals(listOf(placeholder), vod.standInPosters("home"))

        // Fewer of them share it after a refresh, which started out taking it for one: it's a poster again.
        vod.upsertMovies((7101..7105).map { movie(it.toString(), generation = 2).copy(poster = placeholder, standIn = true) })
        vod.commit("home", 2, at = 2)
        vod.markStandInPosters("home", titles = 10)
        assertEquals(false, vod.movie("home:7102")!!.standIn)
    }

    @Test fun aSeriesEpisodesAreReplacedWholeAndGoWithIt() = runTest {
        db.sources().upsert(home)
        val vod = db.vod()
        vod.upsertSeries(listOf(series("8101")))
        val seriesDetails = SeriesDetailsEntity("home:8101", "A plot.", null, null, null, null, null, 2024, null, null, fetchedAt = 1)
        vod.replaceEpisodes(seriesDetails, listOf(episode("9101", "8101", 1, 1, 0), episode("9102", "8101", 1, 2, 1)))
        vod.replaceEpisodes(seriesDetails.copy(fetchedAt = 2), listOf(episode("9102", "8101", 1, 2, 0), episode("9103", "8101", 2, 1, 1)))

        val read = vod.observeSeriesRead("home:8101").first()!!
        assertEquals(listOf("home:9102", "home:9103"), read.episodes.sortedBy { it.position }.map { it.id })
        assertEquals(2L, read.details.fetchedAt)
        db.sources().delete("home")
        assertEquals(emptyList<EpisodeEntity>(), vod.episodes("home:8101"))
        assertNull(vod.seriesDetails("home:8101"))
    }

    @Test fun whereTitlesWereLeftAndMyListOutliveRefreshesButNotTheirSource() = runTest {
        db.sources().upsert(home)
        val vod = db.vod()
        vod.upsertMovies(listOf(movie("7101"), movie("7102")))
        db.resumePoints().upsert(listOf(ResumePointEntity("default", "movie", "home:7102", "home", null, 60_000, 7_080_000, watched = false, updatedAt = 5)))
        db.myList().add(MyListEntity("default", "movie", "home:7101", "home", addedAt = 1))
        db.myList().add(MyListEntity("default", "movie", "home:7102", "home", addedAt = 2))
        db.myList().add(MyListEntity("kids", "movie", "home:7101", "home", addedAt = 3))

        assertEquals(listOf("home:7102", "home:7101"), db.myList().observeMovies("default").first().map { it.id })
        // 7102 leaves the catalog: it's off My List's movies, but kept, with where it was left.
        vod.upsertMovies(listOf(movie("7101", generation = 2)))
        vod.commit("home", 2, at = 20_000)
        assertEquals(listOf("home:7101"), db.myList().observeMovies("default").first().map { it.id })
        assertEquals(listOf("home:7101", "home:7102"), db.myList().observeIds("default", "movie").first().sorted())
        assertEquals(60_000L, db.resumePoints().observe("default", "movie", "home:7102").first()!!.positionMs)

        db.sources().delete("home")
        assertEquals(emptyList<String>(), db.myList().observeIds("kids", "movie").first())
        assertNull(db.resumePoints().observe("default", "movie", "home:7102").first())
    }

    @Test fun aSeriesIsWatchedWhenEveryEpisodeIs() = runTest {
        db.sources().upsert(home)
        val vod = db.vod()
        vod.upsertSeries(listOf(series("8101"), series("8102")))
        val read = SeriesDetailsEntity("home:8101", null, null, null, null, null, null, null, null, null, fetchedAt = 1)
        vod.replaceEpisodes(read, listOf(episode("9101", "8101", 1, 1, 0), episode("9102", "8101", 1, 2, 1)))
        vod.replaceEpisodes(read.copy(seriesId = "home:8102"), listOf(episode("9201", "8102", 1, 1, 0)))
        fun watched(id: String, seriesId: String, watched: Boolean = true) =
            ResumePointEntity("default", "episode", "home:$id", "home", "home:$seriesId", 0, 0, watched, updatedAt = 1)

        db.resumePoints().upsert(listOf(watched("9101", "8101"), watched("9201", "8102")))
        assertEquals(listOf("home:8102"), db.resumePoints().observeWatchedSeries("default").first())
        db.resumePoints().upsert(listOf(watched("9102", "8101")))
        assertEquals(listOf("home:8101", "home:8102"), db.resumePoints().observeWatchedSeries("default").first().sorted())
        assertEquals(emptyList<String>(), db.resumePoints().observeWatchedSeries("kids").first())
        assertEquals(2, db.resumePoints().observeSeries("default", "home:8101").first().size)
        db.resumePoints().remove("default", "episode", listOf("home:9101"))
        assertEquals(listOf("home:8102"), db.resumePoints().observeWatchedSeries("default").first())
    }

    @Test fun continueWatchingReadsTheLatestFirstAndLeavesOutWhatsHidden() = runTest {
        db.sources().upsert(home)
        db.sources().upsert(home.copy(id = "backup", name = "Backup", priority = 1))
        fun point(id: String, kind: String, at: Long, seriesId: String? = null, sourceId: String = "home") =
            ResumePointEntity("default", kind, id, sourceId, seriesId, 60_000, 3_000_000, watched = false, updatedAt = at)
        db.resumePoints().upsert(
            listOf(
                point("home:7101", "movie", at = 10),
                point("home:9101", "episode", at = 30, seriesId = "home:8101"),
                point("home:9102", "episode", at = 20, seriesId = "home:8101"),
                point("backup:7201", "movie", at = 40, sourceId = "backup"),
            ),
        )

        assertEquals(listOf("backup:7201", "home:9101", "home:9102", "home:7101"), db.resumePoints().observeRecent("default", since = 0, limit = 20).first().map { it.contentId })
        assertEquals(listOf("backup:7201", "home:9101"), db.resumePoints().observeRecent("default", since = 25, limit = 20).first().map { it.contentId })

        // Hidden ones stay, marked; a source that's off leaves its own out.
        db.resumePoints().hideSeries("default", "home:8101")
        db.resumePoints().hideMovie("default", "home:7101")
        db.sources().setEnabled("backup", false)
        val recent = db.resumePoints().observeRecent("default", since = 0, limit = 20).first()
        assertEquals(listOf("home:9101", "home:9102", "home:7101"), recent.map { it.contentId })
        assertTrue(recent.all { it.hidden })
    }

    @Test fun remembersTheLastFiftyChannels() = runTest {
        val recents = db.recentChannels()
        repeat(55) { recents.watched("default", "home:c$it", at = it.toLong()) }
        recents.watched("default", "home:c0", at = 100)

        val list = recents.observe("default").first()
        assertEquals(50, list.size)
        assertEquals("home:c0", list.first().channelId)
        assertEquals("home:c54", list[1].channelId)
    }

    @Test fun keepsTheOldGuideUntilTheNewOneIsIn() = runTest {
        db.sources().upsert(home)
        val programmes = db.programmes()
        programmes.insertAll(listOf(programme("home:arena1.example", 100, 200, 1), programme("home:arena1.example", 200, 300, 1)))
        programmes.commit("home", generation = 1, at = 10)

        // A refresh writes its guide while the old one shows, untouched.
        programmes.insertAll(listOf(programme("home:arena1.example", 150, 250, 2)))
        assertEquals(3, programmes.countForSource("home"))
        assertEquals(listOf(100L, 200L), programmes.forKey("home:arena1.example", 0, 1_000).map { it.startAt })
        assertEquals(listOf(100L, 200L), programmes.observeWindow(0, 1_000).first().map { it.startAt })

        programmes.commit("home", generation = 2, at = 20)

        assertEquals(listOf(150L), programmes.forKey("home:arena1.example", 0, 1_000).map { it.startAt })
        assertEquals(1, programmes.countForSource("home"))
        assertEquals(20L, db.sources().get("home")?.guideRefreshedAt)
    }

    @Test fun findsWhatsOnInAWindowAndPrunesThePast() = runTest {
        db.sources().upsert(home.copy(guideGeneration = 1))
        val programmes = db.programmes()
        programmes.insertAll(
            listOf(
                programme("a", 0, 100, 1),
                programme("a", 100, 200, 1),
                programme("a", 200, 300, 1),
                programme("b", 50, 250, 1),
            ),
        )

        val window = programmes.observeWindow(from = 150, to = 210).first()
        assertEquals(listOf("a" to 100L, "a" to 200L, "b" to 50L), window.map { it.epgKey to it.startAt })

        programmes.prune(before = 150)
        assertEquals(3, programmes.countForSource("home"))
        assertNull(programmes.forKey("a", 0, 1_000).firstOrNull { it.startAt == 0L })
    }

    @Test fun aNewGuideOffsetMovesTheProgrammesAlreadyLoaded() = runTest {
        db.sources().upsert(home.copy(guideGeneration = 1, epgOffsetMinutes = 60))
        db.sources().upsert(home.copy(id = "backup", priority = 1, guideGeneration = 1))
        val programmes = db.programmes()
        programmes.insertAll(listOf(programme("a", 3_600_000, 7_200_000, 1), programme("b", 0, 3_600_000, 1).copy(sourceId = "backup")))

        // From +1 h to −1 h: two hours earlier.
        programmes.setGuideOffset("home", -60)

        assertEquals(-60, db.sources().get("home")?.epgOffsetMinutes)
        assertEquals(listOf(-3_600_000L to 0L), programmes.forKey("a", -10_000_000, 10_000_000).map { it.startAt to it.endAt })
        // Other sources' guides stay where they were.
        assertEquals(listOf(0L), programmes.forKey("b", -10_000_000, 10_000_000).map { it.startAt })

        // The same offset again moves nothing.
        programmes.setGuideOffset("home", -60)
        assertEquals(listOf(-3_600_000L), programmes.forKey("a", -10_000_000, 10_000_000).map { it.startAt })
    }

    @Test fun aSourceTurnedOffLeavesTheListsButKeepsItsChannels() = runTest {
        db.sources().upsert(home)
        db.sources().upsert(home.copy(id = "backup", priority = 1))
        db.channels().replaceSource("home", 1, listOf(group("4", 1)), listOf(channel("arena1", 105, 1), channel("atlas", 120, 1)))
        db.channels().replaceSource("backup", 1, emptyList(), listOf(channel("ringside", 108, 1, sourceId = "backup")))

        db.sources().setEnabled("home", false)

        assertEquals(listOf("backup:ringside"), db.channels().observeAll().first().map { it.id })
        assertEquals(emptyList<String>(), db.groups().observeAll().first().map { it.id })
        // Portals & Playlists still counts them, to show what turning it on brings back.
        assertEquals(mapOf("home" to 2, "backup" to 1), db.channels().observeCountsBySource().first().associate { it.sourceId to it.channels })

        db.sources().setEnabled("home", true)
        assertEquals(listOf("home:arena1", "backup:ringside", "home:atlas"), db.channels().observeAll().first().map { it.id })
    }

    @Test fun playbackForThisSourceIsKeptWithTheSource() = runTest {
        db.sources().upsert(home)

        db.sources().setPlayback(
            "home", streamFormat = "m3u8", bufferChoice = 3, audioOutputChoice = null, catchupOffsetMinutes = 120,
            engineChoice = 1, useHub = false, userAgent = "Custom/1.0", referrer = "http://lists.example.com/",
            extraHeadersEnc = "sealed", udpxy = "192.168.1.1:4022",
        )

        val source = db.sources().get("home")!!
        assertEquals("m3u8", source.streamFormat)
        assertEquals(3, source.bufferChoice)
        assertNull(source.audioOutputChoice)
        assertEquals(120, source.catchupOffsetMinutes)
        assertEquals(1, source.engineChoice)
        assertEquals(false, source.useHub)
        assertEquals("Custom/1.0", source.userAgent)
        assertEquals("http://lists.example.com/", source.referrer)
        assertEquals("sealed", source.extraHeadersEnc)
        assertEquals("192.168.1.1:4022", source.udpxy)
    }
}
