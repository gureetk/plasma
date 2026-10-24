// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.xtream

import ca.gureet.plasma.core.network.PlasmaHttp
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest

/** The made-up world of the boards (brief appendix A), as an Xtream server would serve it. */
class XtreamClientTest {
    private val server = MockWebServer()
    private lateinit var client: XtreamClient

    @Before fun start() {
        server.start()
        val login = XtreamLogin(XtreamLogin.server(server.url("/").toString())!!, "viewer", "s3cret")
        client = XtreamClient(PlasmaHttp.client("Plasma test"), login)
    }

    @After fun stop() = server.close()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse(code = code, body = body))

    @Test fun signsInAndReadsTheAccount() = runTest {
        answer(
            """
            {"user_info":{"username":"viewer","password":"s3cret","auth":1,"status":"Active","exp_date":"1804809600",
              "is_trial":"0","active_cons":"0","max_connections":"2","allowed_output_formats":["m3u8","ts","rtmp"]},
             "server_info":{"url":"line.example.org","port":"8080","timezone":"Europe/London","timestamp_now":1792615260}}
            """,
        )

        val account = client.signIn()

        assertEquals("Active", account.status)
        assertEquals(Instant.ofEpochSecond(1_804_809_600), account.expiresAt)
        assertEquals(2, account.maxConnections)
        assertEquals(0, account.activeConnections)
        assertEquals(listOf("m3u8", "ts", "rtmp"), account.outputFormats)
        assertEquals(StreamFormat.Ts, account.defaultFormat)
        assertEquals("Europe/London", account.serverTimeZone)

        val request = server.takeRequest()
        assertEquals("/player_api.php", request.url.encodedPath)
        assertEquals("viewer", request.url.queryParameter("username"))
        assertEquals("s3cret", request.url.queryParameter("password"))
        assertNull(request.url.queryParameter("action"))
    }

    @Test fun anAccountWithoutAnExpiryOrLimitHasNeither() = runTest {
        answer("""{"user_info":{"auth":"1","status":"Active","exp_date":null,"max_connections":"0","allowed_output_formats":["m3u8"]}}""")

        val account = client.signIn()

        assertNull(account.expiresAt)
        assertNull(account.maxConnections)
        assertEquals(StreamFormat.Hls, account.defaultFormat)
    }

    @Test fun tellsAWrongLoginFromOtherFailures() = runTest {
        answer("""{"user_info":{"auth":0}}""")
        expect<XtreamException.WrongLogin> { client.signIn() }
        answer("[]")
        expect<XtreamException.WrongLogin> { client.signIn() }
        answer("", code = 401)
        expect<XtreamException.WrongLogin> { client.signIn() }
        answer("<html><body>Welcome to nginx</body></html>")
        expect<XtreamException.NotXtream> { client.signIn() }
        answer("", code = 502)
        val error = expect<XtreamException.Http> { client.signIn() }
        assertEquals(502, error.code)
    }

    @Test fun refusesAnAccountThatCantPlay() = runTest {
        answer("""{"user_info":{"auth":1,"status":"Expired","exp_date":"1700000000"}}""")
        val error = expect<XtreamException.NotActive> { client.signIn() }
        assertEquals("Expired", error.status)
    }

    @Test fun readsChannelsLeniently() = runTest {
        answer(
            """
            [{"num":"105","name":"Arena Sports 1","stream_type":"live","stream_id":"9105","stream_icon":"http://img.example.net/arena1.png",
              "epg_channel_id":"arena1.example","category_id":"4","tv_archive":"1","tv_archive_duration":"7"},
             {"num":107,"name":"Velocity Motor","stream_type":"live","stream_id":9107,"stream_icon":"","epg_channel_id":null,
              "category_id":null,"category_ids":[4,9],"tv_archive":0,"tv_archive_duration":0},
             {"num":"","name":"Pulse","stream_type":"radio_streams","stream_id":"9200","category_id":"12"},
             {"name":"No id, so left out"}]
            """,
        )

        val streams = client.liveStreams()

        assertEquals(listOf("9105", "9107", "9200"), streams.map { it.streamId })
        val arena = streams[0]
        assertEquals(105, arena.number)
        assertEquals("arena1.example", arena.epgChannelId)
        assertEquals("4", arena.categoryId)
        assertEquals(7, arena.archiveDays)
        assertEquals("http://img.example.net/arena1.png", arena.logo)
        val velocity = streams[1]
        assertNull(velocity.logo)
        assertNull(velocity.epgChannelId)
        assertEquals("4", velocity.categoryId)
        assertNull(velocity.archiveDays)
        val pulse = streams[2]
        assertNull(pulse.number)
        assertTrue(pulse.isRadio)
        assertEquals("get_live_streams", server.takeRequest().url.queryParameter("action"))
    }

    @Test fun readsCategoriesAndTreatsAnEmptyObjectAsNone() = runTest {
        answer("""[{"category_id":"4","category_name":"Sports","parent_id":0},{"category_id":"9","category_name":"4K UHD"}]""")
        assertEquals(listOf(XtreamCategory("4", "Sports"), XtreamCategory("9", "4K UHD")), client.liveCategories())
        answer("{}")
        assertEquals(emptyList<XtreamCategory>(), client.liveCategories())
    }

    @Test fun decodesTheShortGuide() = runTest {
        // "Harbour City v Redmoor" and "City League, Matchweek 9" in Base64; the second title is plain.
        answer(
            """
            {"epg_listings":[
              {"title":"SGFyYm91ciBDaXR5IHYgUmVkbW9vcg==","description":"Q2l0eSBMZWFndWUsIE1hdGNod2VlayA5",
               "start":"2026-10-21 20:45:00","start_timestamp":"1792611900","stop_timestamp":"1792620000"},
              {"title":"Match of the Week Extra","description":"","start_timestamp":"1792620000","stop_timestamp":"1792623600"}]}
            """,
        )

        val listings = client.shortEpg("9105", limit = 2)

        assertEquals("Harbour City v Redmoor", listings[0].title)
        assertEquals("City League, Matchweek 9", listings[0].description)
        assertEquals(Instant.ofEpochSecond(1_792_611_900), listings[0].start)
        assertEquals("Match of the Week Extra", listings[1].title)
        assertNull(listings[1].description)
        val request = server.takeRequest()
        assertEquals("9105", request.url.queryParameter("stream_id"))
        assertEquals("2", request.url.queryParameter("limit"))
    }

    @Test fun buildsStreamAndGuideUrls() {
        val login = XtreamLogin(XtreamLogin.server("line.example.org:8080")!!, "viewer", "p@ss/word")
        val client = XtreamClient(PlasmaHttp.client("Plasma test"), login)

        assertEquals("http://line.example.org:8080/live/viewer/p@ss%2Fword/9105.ts", client.liveUrl("9105", StreamFormat.Ts))
        assertEquals("http://line.example.org:8080/live/viewer/p@ss%2Fword/9105.m3u8", client.liveUrl("9105", StreamFormat.Hls))
        assertEquals("http://line.example.org:8080/xmltv.php?username=viewer&password=p%40ss%2Fword", client.xmltvUrl())
    }

    @Test fun buildsArchiveUrlsInTheServersTimeZone() {
        val login = XtreamLogin(XtreamLogin.server("line.example.org:8080")!!, "viewer", "s3cret")
        val client = XtreamClient(PlasmaHttp.client("Plasma test"), login)

        // 19:45 UTC is 20:45 in London in October (BST).
        assertEquals(
            "http://line.example.org:8080/timeshift/viewer/s3cret/135/2026-10-21:20-45/9105.ts",
            client.timeshiftUrl("9105", Instant.parse("2026-10-21T19:45:00Z"), 135, ZoneId.of("Europe/London")),
        )
    }

    @Test fun keepsOnlyTheServerPartOfWhatWasTyped() {
        assertEquals("http://line.example.org:8080/", XtreamLogin.server("line.example.org:8080")?.toString())
        assertEquals("http://line.example.org:8080/", XtreamLogin.server(" http://line.example.org:8080/ ")?.toString())
        assertEquals(
            "http://line.example.org:8080/",
            XtreamLogin.server("http://line.example.org:8080/player_api.php?username=viewer&password=s3cret")?.toString(),
        )
        assertEquals("https://tv.example.net/xtream", XtreamLogin.server("https://tv.example.net/xtream/get.php?type=m3u_plus")?.toString())
        assertNull(XtreamLogin.server(""))
        assertNull(XtreamLogin.server("not an address"))
    }

    @Test fun neverShowsTheCredentials() {
        val login = XtreamLogin(XtreamLogin.server("line.example.org")!!, "viewer", "s3cret")
        assertEquals("XtreamLogin(line.example.org)", login.toString())
    }

    @Test fun readsMoviesAsTheyComeAndLeniently() = runTest {
        answer(
            """
            [{"num":1,"name":"Neon Tide","stream_type":"movie","stream_id":7101,"stream_icon":"https://img.example/neon.jpg",
              "rating":"7.4","added":"1761000000","category_id":"30","container_extension":"mkv","year":"2026"},
             {"num":"2","name":"Glass Orchard (2025)","stream_id":"7102","stream_icon":"","rating_5based":3.5,
              "added":null,"category_ids":[31,30],"container_extension":".MP4"},
             {"name":"No id"},
             {"stream_id":"7103","rating":"0","category_id":"","container_extension":""}]
            """,
        )

        val movies = mutableListOf<XtreamMovie>()
        client.movies { movies += it }

        assertEquals(
            listOf(
                XtreamMovie("7101", "Neon Tide", "https://img.example/neon.jpg", "30", Instant.ofEpochSecond(1_761_000_000), 7.4f, "mkv", 2026),
                // The year from the title; the rating from its five-star form; the first of several categories.
                XtreamMovie("7102", "Glass Orchard (2025)", null, "31", null, 7f, "mp4", 2025),
                XtreamMovie("7103", "7103", null, null, null, null, null, null),
            ),
            movies,
        )
        assertEquals("get_vod_streams", server.takeRequest().url.queryParameter("action"))
    }

    @Test fun readsSeries() = runTest {
        answer(
            """
            [{"num":1,"name":"Atlas Station","series_id":8101,"cover":"https://img.example/atlas.jpg","plot":"A crew on the edge.",
              "cast":"Ana Reyes, Tom Hale","director":"Mia Stone","genre":"Sci-Fi, Drama","releaseDate":"2024-03-01",
              "last_modified":"1761000000","rating":"8.1","backdrop_path":["https://img.example/atlas-wide.jpg"],
              "youtube_trailer":"abcdefghijk","category_id":"50"}]
            """,
        )

        val series = mutableListOf<XtreamSeries>()
        client.series { series += it }

        assertEquals(
            listOf(
                XtreamSeries(
                    "8101", "Atlas Station", "https://img.example/atlas.jpg", "https://img.example/atlas-wide.jpg", "50",
                    "A crew on the edge.", "Ana Reyes, Tom Hale", "Mia Stone", "Sci-Fi, Drama", 2024, 8.1f,
                    Instant.ofEpochSecond(1_761_000_000), "abcdefghijk",
                ),
            ),
            series,
        )
        assertEquals("get_series", server.takeRequest().url.queryParameter("action"))
    }

    @Test fun anEmptyCatalogMayComeAsAnObjectOrNull() = runTest {
        answer("{}")
        answer("null")
        answer("""[{"category_id":"30","category_name":"Thrillers"}]""")

        var count = 0
        client.movies { count++ }
        client.series { count++ }
        assertEquals(0, count)
        assertEquals(listOf(XtreamCategory("30", "Thrillers")), client.movieCategories())
    }

    @Test fun aPageInsteadOfTheCatalogIsntXtream() = runTest {
        answer("<html><body>Maintenance</body></html>")
        expect<XtreamException.NotXtream> { client.movies { } }
    }

    @Test fun readsAMoviesDetailsAndTheFileAsTheServerProbedIt() = runTest {
        answer(
            """
            {"info":{"name":"Neon Tide","cover_big":"https://img.example/neon-big.jpg","plot":"A harbour town, one long night.",
              "cast":"Ana Reyes, Tom Hale, Lee Park","director":"Mia Stone","genre":"Thriller, Drama","releasedate":"2026-02-14",
              "duration_secs":"7080","duration":"01:58:00","age":"13+","rating":"7.4",
              "backdrop_path":["","https://img.example/neon-wide.jpg"],"youtube_trailer":"https://www.youtube.com/watch?v=abcdefghijk",
              "video":{"codec_name":"hevc","profile":"Main 10","width":3840,"height":2160,"avg_frame_rate":"24000/1001",
                "color_transfer":"smpte2084","side_data_list":[{"side_data_type":"DOVI configuration record"}]},
              "audio":{"codec_name":"eac3","channels":6,"bit_rate":"640000","tags":{"language":"ENG"}}},
             "movie_data":{"stream_id":7101,"name":"Neon Tide","container_extension":"mkv"}}
            """,
        )

        val info = client.movieInfo("7101")

        assertEquals(
            XtreamMovieInfo(
                plot = "A harbour town, one long night.",
                cast = "Ana Reyes, Tom Hale, Lee Park",
                director = "Mia Stone",
                genre = "Thriller, Drama",
                durationSecs = 7_080,
                poster = "https://img.example/neon-big.jpg",
                backdrop = "https://img.example/neon-wide.jpg",
                trailer = "abcdefghijk",
                year = 2026,
                rating = 7.4f,
                age = "13+",
                extension = "mkv",
                video = XtreamVideo("hevc", "Main 10", 3840, 2160, 24_000f / 1_001f, "Dolby Vision"),
                audio = XtreamAudio("eac3", 6, "eng", 640_000),
            ),
            info,
        )
        val request = server.takeRequest().url
        assertEquals("get_vod_info", request.queryParameter("action"))
        assertEquals("7101", request.queryParameter("vod_id"))
    }

    @Test fun aMovieWithoutDetailsHasNone() = runTest {
        answer("""{"info":[],"movie_data":{"stream_id":7103,"container_extension":".MP4"}}""")
        answer("""{"info":{"duration":"118 min","youtube_trailer":"Coming soon","video":{"color_transfer":"bt709"},"age":"Not rated yet"}}""")

        assertEquals(XtreamMovieInfo(null, null, null, null, null, null, null, null, null, null, null, "mp4", null, null), client.movieInfo("7103"))
        val sparse = client.movieInfo("7104")
        assertEquals(7_080, sparse.durationSecs)
        assertNull(sparse.trailer)
        assertNull(sparse.age)
        assertEquals(XtreamVideo(null, null, null, null, null, hdr = null), sparse.video)
    }

    @Test fun readsASeriesEpisodesSeasonBySeason() = runTest {
        answer(
            """
            {"seasons":[],"info":{"name":"Atlas Station","plot":"A crew on the edge.","genre":"Sci-Fi, Drama","releaseDate":"2024-03-01",
              "backdrop_path":["https://img.example/atlas-wide.jpg"],"youtube_trailer":"abcdefghijk","rating_5based":"4.2","age":"16+"},
             "episodes":{
              "2":[{"id":"9104","episode_num":"2","title":"Atlas Station - S02E02 - Dead Reckoning","container_extension":"mkv","season":2,
                    "added":"1761000000","info":{"duration":"00:58:00","movie_image":"https://img.example/s2e2.jpg","releasedate":"2026-01-09",
                    "video":{"codec_name":"h264","width":1920,"height":1080,"color_transfer":"arib-std-b67"}}},
                   {"id":"9103","episode_num":"1","title":"Atlas Station S02E01","container_extension":"mkv","season":2,"info":{"duration_secs":3300}}],
              "1":[{"id":"9101","episode_num":1,"title":"Launch","container_extension":"mp4","season":1,"info":{"plot":"Day one."}},
                   {"episode_num":2,"title":"No id"}],
              "0":[{"id":"9100","episode_num":1,"title":"S00E01 – Behind the Scenes","season":"0","info":[]}]}}
            """,
        )

        val series = client.seriesInfo("8101")

        assertEquals(listOf("9100", "9101", "9103", "9104"), series.episodes.map { it.id })
        assertEquals(listOf("Behind the Scenes", "Launch", null, "Dead Reckoning"), series.episodes.map { it.title })
        assertEquals(listOf(0 to 1, 1 to 1, 2 to 1, 2 to 2), series.episodes.map { it.season to it.number })
        val reckoning = series.episodes.last()
        assertEquals(3_480, reckoning.durationSecs)
        assertEquals("https://img.example/s2e2.jpg", reckoning.still)
        assertEquals(2026, reckoning.year)
        assertEquals(Instant.ofEpochSecond(1_761_000_000), reckoning.added)
        assertEquals("HLG", reckoning.video?.hdr)
        assertEquals(3_300, series.episodes[2].durationSecs)
        assertEquals("Day one.", series.episodes[1].plot)
        assertEquals("https://img.example/atlas-wide.jpg", series.backdrop)
        assertEquals(8.4f, series.rating)
        assertEquals(2024, series.year)
        assertEquals("16+", series.age)
        val request = server.takeRequest().url
        assertEquals("get_series_info", request.queryParameter("action"))
        assertEquals("8101", request.queryParameter("series_id"))
    }

    @Test fun episodesMayComeAsListsOrNotAtAll() = runTest {
        answer("""{"info":{},"episodes":[[{"id":"9201","episode_num":"1","season":"1"}],[{"id":"9202","episode_num":"1","season":"2"}]]}""")
        answer("""{"info":{},"episodes":[{"id":"9301","episode_num":"3"},{"id":"9301","episode_num":"3"}]}""")
        answer("""{"info":[],"episodes":[]}""")
        answer("[]")

        assertEquals(listOf("9201", "9202"), client.seriesInfo("8102").episodes.map { it.id })
        // Without a season, an episode is in the first; one listed twice is one.
        assertEquals(listOf(1 to 3), client.seriesInfo("8103").episodes.map { it.season to it.number })
        assertEquals(emptyList<XtreamEpisode>(), client.seriesInfo("8104").episodes)
        expect<XtreamException.NotXtream> { client.seriesInfo("8105") }
    }

    @Test fun buildsMovieAndEpisodeUrls() {
        val login = XtreamLogin(XtreamLogin.server("line.example.org:8080")!!, "viewer", "s3cret")
        val client = XtreamClient(PlasmaHttp.client("Plasma test"), login)

        assertEquals("http://line.example.org:8080/movie/viewer/s3cret/7101.mkv", client.movieUrl("7101", "mkv"))
        assertEquals("http://line.example.org:8080/series/viewer/s3cret/9001.mp4", client.episodeUrl("9001", null))
    }

    private inline fun <reified T : Throwable> expect(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Expected ${T::class.simpleName}, got $error", error)
        }
        fail("Expected ${T::class.simpleName}")
        throw IllegalStateException()
    }
}
