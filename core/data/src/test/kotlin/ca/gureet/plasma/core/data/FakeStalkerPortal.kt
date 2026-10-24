// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A Stalker portal at `/portal.php` that knows one box, [mac], and answers as Ministra does (brief
 * 7.1): a token per handshake, checked on every call, with three channels in two genres.
 */
class FakeStalkerPortal(val mac: String = "00:1A:79:4C:2E:91") : AutoCloseable {
    val server = MockWebServer()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var token: String? = null
    private var issued = 0

    var blocked = false

    /** A portal that wants a login too: the one it takes, "login:password", or null for none. */
    var wantsLogin: String? = null
    private var authorized = false
    var channels = """
        [{"id":"105","name":"Arena Sports 1","number":"105","cmd":"ffrt http://localhost/ch/105_","tv_genre_id":"4","tv_archive":"1","tv_archive_duration":"168"},
         {"id":"101","name":"Northline News","number":"101","cmd":"ffmpeg http://localhost/ch/101_","tv_genre_id":"7"},
         {"id":"108","name":"Courtside+","number":"108","cmd":"ffmpeg http://localhost/ch/108_","tv_genre_id":"4"}]
    """.trimIndent()

    /** Harbour City v Redmoor, 19:45–22:00 UTC on 21 October 2026, on Arena Sports 1. */
    var guide = """
        {"105":[{"id":"9001","name":"Harbour City v Redmoor","descr":"Live","start_timestamp":"1792611900","stop_timestamp":"1792620000","mark_archive":"1"}],
         "101":[{"id":"9101","name":"Business Wrap","start_timestamp":"1792614600","stop_timestamp":"1792616400"}],
         "999":[{"id":"1","name":"Nobody's","start_timestamp":"1792611900","stop_timestamp":"1792620000"}]}
    """.trimIndent()

    /**
     * The video library (brief 5.9): Neon Tide, a movie whose file is asked for apart; Atlas
     * Station, whose two episodes come with it; and North Of, whose seasons come when asked. The
     * series module has Night Desk.
     */
    var library = """
        [{"id":"7101","name":"Neon Tide","cmd":"/media/7101.mpg","has_files":1,"category_id":"12","screenshot_uri":"http://img.example/neon.jpg",
          "description":"A harbour town, one long night.","actors":"Ana Reyes, Tom Hale","director":"Mia Stone","genres_str":"Thriller, Drama",
          "year":"2026","time":"118","rating_imdb":"7.4","age":"13+","added":"2026-10-20 18:30:00"},
         {"id":"8101","name":"Atlas Station","cmd":"/media/8101.mpg","category_id":"14","series":[1,2],"description":"A crew on the edge.","year":"2024"},
         {"id":"8201","name":"North Of","cmd":"","category_id":"14","is_series":"1"}]
    """.trimIndent()

    /** Whether the portal has a series module. */
    var seriesModule = true

    val baseUrl: String get() = server.url("/c/").toString()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = respond(request)
        }
        server.start()
    }

    private fun respond(request: RecordedRequest): MockResponse {
        requests += request
        val url = request.url
        if (url.encodedPath != "/portal.php") return MockResponse(code = 404)
        // A box the portal doesn't know is turned down.
        if (request.headers["Cookie"]?.contains("mac=${mac.replace(":", "%3A")}") != true) return MockResponse(code = 401)
        val action = url.queryParameter("action")
        if (action == "handshake") {
            token = "tok${++issued}"
            authorized = false
            return MockResponse(body = """{"js":{"token":"$token"}}""")
        }
        if (request.headers["Authorization"] != "Bearer $token") return MockResponse(body = "Authorization failed.")
        if (action == "do_auth") {
            authorized = "${url.queryParameter("login")}:${url.queryParameter("password")}" == wantsLogin
            return MockResponse(body = """{"js":$authorized}""")
        }
        if (wantsLogin != null && !authorized && action != "get_profile") return MockResponse(body = "Authorization failed.")
        when (url.queryParameter("type")) {
            "vod" -> return library(url)
            "series" -> return if (seriesModule) serials(url) else MockResponse(code = 404)
        }
        return when (action) {
            "get_profile" -> MockResponse(body = """{"js":{"id":"1","blocked":"${if (blocked) 1 else 0}","watchdog_timeout":90}}""")
            "get_main_info" -> MockResponse(body = """{"js":{"mac":"$mac","phone":"2027-03-12"}}""")
            "get_genres" -> MockResponse(body = """{"js":[{"id":"*","title":"All"},{"id":"4","title":"Sports"},{"id":"7","title":"News"}]}""")
            "get_all_channels" -> MockResponse(body = """{"js":{"data":$channels}}""")
            "get_epg_info" -> MockResponse(body = """{"js":{"data":$guide}}""")
            "create_link" -> {
                val id = url.queryParameter("cmd")!!.substringAfterLast('/').trimEnd('_').removeSuffix(".mpg")
                val kind = if (url.queryParameter("type") == "tv_archive") "archive" else "live"
                MockResponse(body = """{"js":{"cmd":"ffmpeg http://stream.example/$kind/$id.ts?token=$token","error":""}}""")
            }
            "get_simple_data_table" -> MockResponse(
                body = """{"js":{"total_items":1,"max_page_items":14,"data":[{"id":"9001","start_timestamp":"1792611900","mark_archive":"1"}]}}""",
            )
            "get_events" -> MockResponse(body = """{"js":{"data":{"msgs":0}}}""")
            else -> MockResponse(code = 404)
        }
    }

    private fun library(url: okhttp3.HttpUrl): MockResponse = when (url.queryParameter("action")) {
        "get_categories" -> MockResponse(body = """{"js":[{"id":"*","title":"All"},{"id":"12","title":"Thrillers"},{"id":"14","title":"Series"}]}""")
        "get_ordered_list" -> {
            val season = url.queryParameter("season_id")
            MockResponse(
                body = when {
                    url.queryParameter("movie_id") == null ->
                        if (url.queryParameter("p") == "1") """{"js":{"total_items":3,"max_page_items":14,"data":$library}}""" else EMPTY
                    season == null -> """{"js":{"total_items":1,"max_page_items":14,"data":[{"id":"70011"}]}}"""
                    season == "0" -> """{"js":{"total_items":2,"max_page_items":14,"data":[
                        {"id":"82012","name":"Season 2","season_number":"2","is_season":true},
                        {"id":"82011","name":"Season 1","season_number":"1","is_season":true}]}}"""
                    season == "82011" -> """{"js":{"total_items":2,"max_page_items":14,"data":[
                        {"id":"92002","name":"The Pass","series_number":"2","is_episode":1},
                        {"id":"92001","name":"First Snow","series_number":"1","is_episode":1,"time":"52"}]}}"""
                    else -> """{"js":{"total_items":1,"max_page_items":14,"data":[{"id":"92101","name":"Thaw","series_number":"1","is_episode":1}]}}"""
                },
            )
        }
        "create_link" -> {
            val cmd = url.queryParameter("cmd")!!
            MockResponse(body = """{"js":{"cmd":"ffmpeg http://stream.example/vod/${cmd.substringAfterLast('/')}?series=${url.queryParameter("series")}&token=$token","error":""}}""")
        }
        else -> MockResponse(code = 404)
    }

    private fun serials(url: okhttp3.HttpUrl): MockResponse = when (url.queryParameter("action")) {
        "get_categories" -> MockResponse(body = """{"js":[{"id":"*","title":"All"},{"id":"21","title":"Drama"}]}""")
        "get_ordered_list" -> MockResponse(
            body = when {
                url.queryParameter("movie_id") != null -> """{"js":{"total_items":1,"max_page_items":14,"data":[
                    {"id":"301","name":"Season 1","cmd":"/media/series/301.mpg","series":[1,2,3]}]}}"""
                url.queryParameter("p") == "1" -> """{"js":{"total_items":1,"max_page_items":14,"data":[
                    {"id":"30","name":"Night Desk","category_id":"21","description":"After midnight in the newsroom."}]}}"""
                else -> EMPTY
            },
        )
        else -> MockResponse(code = 404)
    }

    fun actions(): List<String?> = requests.map { it.url.queryParameter("action") }

    override fun close() = server.close()

    private companion object {
        const val EMPTY = """{"js":{"total_items":0,"max_page_items":14,"data":[]}}"""
    }
}
