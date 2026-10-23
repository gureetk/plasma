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

    val baseUrl: String get() = server.url("/").toString()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = respond(request)
        }
        server.start()
    }

    private fun respond(request: RecordedRequest): MockResponse {
        val url = request.url
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
            else -> MockResponse(body = "[]")
        }
    }

    private fun signedIn(request: RecordedRequest) =
        request.url.queryParameter("username") == "viewer" && request.url.queryParameter("password") == "s3cret"

    override fun close() = server.close()
}

/** Keeps "secrets" readable in tests, where the Android Keystore isn't available. */
class FakeSecretBox : SecretBox {
    override fun seal(plain: String): String = "sealed:" + plain.reversed()

    override fun open(sealed: String): String = sealed.removePrefix("sealed:").reversed()
}
