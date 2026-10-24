// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/** A channel the fake server lists. */
data class FakeStream(
    val id: String,
    val num: Int?,
    val name: String,
    val category: String = "4",
    val epgId: String? = null,
    val archiveDays: Int = 0,
)

/** A movie or series the fake server lists; [added] is when it (or its latest episode) came, in Unix seconds. */
data class FakeTitle(val id: String, val name: String, val category: String, val added: Long?)

/** An episode of a series the fake server lists, its title as panels write it. */
data class FakeEpisode(val id: String, val season: Int, val number: Int, val title: String, val minutes: Int?)

/**
 * An Xtream server for tests, serving the boards' made-up world (brief appendix A) to whoever signs
 * in as viewer / s3cret. Change [streams], [guide] or [guideCode] between imports to play a refresh.
 */
class FakeXtreamServer : AutoCloseable {
    val server = MockWebServer()

    var streams = listOf(
        FakeStream("9101", 101, "Northline News", category = "7", epgId = "northline.example"),
        FakeStream("9105", 105, "Arena Sports 1", epgId = "arena1.example", archiveDays = 7),
        FakeStream("9107", 107, "Velocity Motor", epgId = "velocity.example"),
        FakeStream("9108", 108, "Courtside+"),
        FakeStream("9200", null, "Pulse", category = "12"),
    )

    var categories = listOf("4" to "Sports", "7" to "News", "12" to "Music")

    /** Movies: id, name, category, added (Unix s). */
    var movies = listOf(
        FakeTitle("7101", "Neon Tide", "30", added = 1_792_500_000),
        FakeTitle("7102", "Glass Orchard", "30", added = 1_780_000_000),
        FakeTitle("7103", "Low Orbit", "31", added = null),
    )
    var movieCategories = listOf("30" to "Thrillers", "31" to "Science Fiction")
    var movieCode = 200

    /** Series: id, name, category, latest episode (Unix s). */
    var series = listOf(
        FakeTitle("8101", "Atlas Station", "50", added = 1_792_400_000),
        FakeTitle("8102", "Kingsway", "51", added = 1_700_000_000),
    )
    var seriesCategories = listOf("50" to "Drama", "51" to "Crime")

    /** What `get_vod_info` and `get_series_info` answer with: a status other than 200 fails them. */
    var detailsCode = 200

    /** Atlas Station's episodes, as `get_series_info` lists them: by season, the specials (0) among them. */
    var episodes = listOf(
        FakeEpisode("9101", 1, 1, "Atlas Station - S01E01 - Launch", 52),
        FakeEpisode("9102", 1, 2, "Atlas Station - S01E02 - Drift", 49),
        FakeEpisode("9104", 2, 1, "Atlas Station S02E01", null),
        FakeEpisode("9100", 0, 1, "Behind the Scenes", 20),
    )

    var guide = """
        <tv>
          <channel id="arena1.example"><display-name>Arena Sports 1</display-name></channel>
          <channel id="northline.example"><display-name>Northline News</display-name></channel>
          <channel id="courtside.example"><display-name>Courtside+</display-name></channel>
          <programme start="20261021194500 +0000" stop="20261021220000 +0000" channel="arena1.example"><title>Harbour City v Redmoor</title></programme>
          <programme start="20261021220000 +0000" stop="20261021230000 +0000" channel="arena1.example"><title>Match of the Week Extra</title></programme>
          <programme start="20261021203000 +0000" stop="20261021210000 +0000" channel="northline.example"><title>Business Wrap</title></programme>
          <programme start="20261021203000 +0000" stop="20261021230000 +0000" channel="courtside.example"><title>City Lights v Northport</title></programme>
          <programme start="20261021200000 +0000" stop="20261021213000 +0000" channel="unknown.example"><title>Nobody's</title></programme>
        </tv>
    """.trimIndent()

    var guideCode = 200
    var accountStatus = "Active"

    /** What `get.php` answers, as Xtream panels give their lists out as M3U playlists. */
    var playlistCode = 200
    var playlistBody: String? = null

    val baseUrl: String get() = server.url("/").toString()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = respond(request)
        }
        server.start()
    }

    /** The account's live channels as `get.php?type=m3u_plus` writes them, with a movie mixed in. */
    fun playlist(): String = buildString {
        appendLine("""#EXTM3U url-tvg="${baseUrl}xmltv.php?username=viewer&password=s3cret"""")
        val names = categories.toMap()
        for (s in streams) {
            append("""#EXTINF:-1 tvg-id="${s.epgId.orEmpty()}" tvg-name="${s.name}" tvg-logo="" """)
            s.num?.let { append("""tvg-chno="$it" """) }
            appendLine("""group-title="${names[s.category].orEmpty()}",${s.name}""")
            appendLine("${baseUrl}live/viewer/s3cret/${s.id}.ts")
        }
        appendLine("""#EXTINF:-1 tvg-name="The Lighthouse" group-title="Movies",The Lighthouse""")
        appendLine("${baseUrl}movie/viewer/s3cret/20.mkv")
    }

    /** Every request, in order. */
    val requests = java.util.concurrent.CopyOnWriteArrayList<RecordedRequest>()

    private fun respond(request: RecordedRequest): MockResponse {
        requests += request
        val url = request.url
        if (url.encodedPath.endsWith("/get.php")) {
            if (!signedIn(request)) return MockResponse(code = 401)
            return MockResponse(code = playlistCode, body = if (playlistCode == 200) playlistBody ?: playlist() else "")
        }
        if (url.encodedPath.endsWith("/xmltv.php")) {
            if (!signedIn(request)) return MockResponse(code = 401)
            return MockResponse(code = guideCode, body = if (guideCode == 200) guide else "")
        }
        if (!url.encodedPath.endsWith("/player_api.php")) return MockResponse(code = 404)
        if (!signedIn(request)) return MockResponse(body = """{"user_info":{"auth":0}}""")
        return when (url.queryParameter("action")) {
            null -> MockResponse(
                body = """
                    {"user_info":{"auth":1,"status":"$accountStatus","exp_date":"1804809600","max_connections":"2","active_cons":"0",
                     "allowed_output_formats":["m3u8","ts"]},"server_info":{"timezone":"Europe/London"}}
                """,
            )
            "get_live_categories" -> MockResponse(
                body = categories.joinToString(",", "[", "]") { (id, name) -> """{"category_id":"$id","category_name":"$name"}""" },
            )
            "get_live_streams" -> MockResponse(
                body = streams.joinToString(",", "[", "]") { s ->
                    """{"num":${s.num ?: "\"\""},"name":"${s.name}","stream_type":"live","stream_id":"${s.id}","stream_icon":"",""" +
                        """"epg_channel_id":${s.epgId?.let { "\"$it\"" } ?: "null"},"category_id":"${s.category}",""" +
                        """"tv_archive":${if (s.archiveDays > 0) 1 else 0},"tv_archive_duration":${s.archiveDays}}"""
                },
            )
            "get_vod_categories" -> MockResponse(body = categoryList(movieCategories))
            "get_series_categories" -> MockResponse(body = categoryList(seriesCategories))
            "get_vod_streams" -> MockResponse(
                code = movieCode,
                body = if (movieCode != 200) "" else movies.joinToString(",", "[", "]") { m ->
                    """{"name":"${m.name}","stream_type":"movie","stream_id":"${m.id}","stream_icon":"","rating":"7.2",""" +
                        """"added":${m.added?.let { "\"$it\"" } ?: "null"},"category_id":"${m.category}","container_extension":"mkv","year":"2026"}"""
                },
            )
            "get_series" -> MockResponse(
                body = series.joinToString(",", "[", "]") { t ->
                    """{"name":"${t.name}","series_id":"${t.id}","cover":"","plot":"","genre":"Drama","releaseDate":"2024-03-01",""" +
                        """"last_modified":${t.added?.let { "\"$it\"" } ?: "null"},"category_id":"${t.category}"}"""
                },
            )
            "get_vod_info" -> {
                if (detailsCode != 200) return MockResponse(code = detailsCode)
                val movie = movies.firstOrNull { it.id == url.queryParameter("vod_id") } ?: return MockResponse(body = """{"info":[],"movie_data":[]}""")
                MockResponse(
                    body = """
                        {"info":{"plot":"${movie.name}: a night in the harbour.","cast":"Ana Reyes, Tom Hale","director":"Mia Stone",
                          "genre":"Thriller, Drama","duration_secs":7080,"age":"13+","releasedate":"2026-02-14",
                          "backdrop_path":["https://img.example/${movie.id}-wide.jpg"],"youtube_trailer":"abcdefghijk",
                          "video":{"codec_name":"hevc","profile":"Main 10","width":3840,"height":2160,"color_transfer":"smpte2084"},
                          "audio":{"codec_name":"eac3","channels":6,"tags":{"language":"eng"}}},
                         "movie_data":{"stream_id":"${movie.id}","container_extension":"mp4"}}
                    """,
                )
            }
            "get_series_info" -> {
                if (detailsCode != 200) return MockResponse(code = detailsCode)
                val show = series.firstOrNull { it.id == url.queryParameter("series_id") } ?: return MockResponse(body = """{"info":[],"episodes":[]}""")
                val listed = if (show.id == "8101") episodes else emptyList()
                MockResponse(
                    body = """{"info":{"name":"${show.name}","plot":"A crew on the edge.","genre":"Sci-Fi, Drama","releaseDate":"2024-03-01","age":"16+"},""" +
                        """"episodes":""" + listed.groupBy { it.season }.entries.joinToString(",", "{", "}") { (season, list) ->
                            "\"$season\":" + list.joinToString(",", "[", "]") { e ->
                                """{"id":"${e.id}","episode_num":"${e.number}","season":$season,"title":"${e.title}","container_extension":"mkv",""" +
                                    """"info":{${e.minutes?.let { "\"duration_secs\":${it * 60}" }.orEmpty()}}}"""
                            }
                        } + "}",
                )
            }
            else -> MockResponse(body = "[]")
        }
    }

    private fun categoryList(list: List<Pair<String, String>>) =
        list.joinToString(",", "[", "]") { (id, name) -> """{"category_id":"$id","category_name":"$name"}""" }

    private fun signedIn(request: RecordedRequest) =
        request.url.queryParameter("username") == "viewer" && request.url.queryParameter("password") == "s3cret"

    override fun close() = server.close()
}

/** Keeps "secrets" readable in tests, where the Android Keystore isn't available. */
class FakeSecretBox : SecretBox {
    override fun seal(plain: String): String = "sealed:" + plain.reversed()

    override fun open(sealed: String): String = sealed.removePrefix("sealed:").reversed()
}
