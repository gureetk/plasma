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
