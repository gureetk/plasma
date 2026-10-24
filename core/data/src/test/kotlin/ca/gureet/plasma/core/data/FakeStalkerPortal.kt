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

    fun actions(): List<String?> = requests.map { it.url.queryParameter("action") }

    override fun close() = server.close()
}
