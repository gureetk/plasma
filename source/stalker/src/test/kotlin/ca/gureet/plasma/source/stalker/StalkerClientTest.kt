// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.stalker

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlinx.coroutines.test.runTest

/** The portal client (brief 7.1) against a fake portal that answers as Ministra does. */
class StalkerClientTest {
    private val mac = "00:1A:79:4C:2E:91"

    /** A portal at [apiPath] that hands out tokens "tok1", "tok2"… and checks the box's on every call. */
    private class FakePortal(val apiPath: String = "/portal.php") : Dispatcher() {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        var validToken: String? = null
        private var issued = 0
        var profile = """{"js":{"id":"1","status":"0","blocked":"0","watchdog_timeout":88,"expire_billing_date":"0000-00-00 00:00:00"}}"""
        var mainInfo = """{"js":{"mac":"00:1A:79:4C:2E:91","phone":"2027-03-12"}}"""
        var allChannels = """
            {"js":{"total_items":3,"data":[
              {"id":"105","name":"Arena Sports 1","number":"105","cmd":"ffrt http://localhost/ch/105_","tv_genre_id":"4",
               "logo":"http://logos.example/arena1.png","xmltv_id":"arena1.example","tv_archive":"1","tv_archive_duration":"168"},
              {"id":"101","name":"Northline News","number":"101","cmd":"ffmpeg http://localhost/ch/101_","tv_genre_id":"7","logo":"/logos/northline.png",
               "enable_tv_archive":1,"tv_archive_duration":24},
              {"id":"108","name":"Courtside+","number":"","cmd":"auto http://localhost/ch/108_","tv_genre_id":"4","logo":"","tv_archive":"0"}
            ]}}
        """.trimIndent()
        var orderedPages: List<String>? = null
        var epg = """
            {"js":{"data":{
              "105":[{"id":"9001","name":"Harbour City v Redmoor","descr":"Live","start_timestamp":"1793043900","stop_timestamp":"1793052000","mark_archive":"1"},
                     {"id":"9002","name":"Match of the Week Extra","start_timestamp":1793052000,"stop_timestamp":1793055600}],
              "101":false,
              "999":[{"id":"1","name":"Nobody's","start_timestamp":"1793043900","stop_timestamp":"1793052000"}]
            }}}
        """.trimIndent()
        var linkError = ""

        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            val url = request.url
            if (url.encodedPath != apiPath) return MockResponse(code = 404)
            val action = url.queryParameter("action")
            if (action == "handshake") {
                validToken = "tok${++issued}"
                return MockResponse(body = """{"js":{"token":"$validToken","random":"5d2b"}}""")
            }
            if (request.headers["Authorization"] != "Bearer $validToken") return MockResponse(body = "Authorization failed.")
            return when (action) {
                "get_profile" -> MockResponse(body = profile)
                "get_main_info" -> MockResponse(body = mainInfo)
                "get_genres" -> MockResponse(body = """{"js":[{"id":"*","title":"All"},{"id":"4","title":"Sports"},{"id":"7","title":"News"}]}""")
                "get_all_channels" -> MockResponse(body = allChannels)
                "get_ordered_list" -> {
                    val page = url.queryParameter("p")!!.toInt()
                    MockResponse(body = orderedPages?.getOrNull(page - 1) ?: """{"js":{"total_items":0,"max_page_items":14,"data":[]}}""")
                }
                "get_epg_info" -> MockResponse(body = epg)
                "create_link" -> {
                    val id = url.queryParameter("cmd")!!.substringAfterLast('/').trimEnd('_').removeSuffix(".mpg")
                    val cmd = if (url.queryParameter("type") == "tv_archive") "auto http://stream.example/archive/$id.mpg?token=$validToken" else "ffmpeg http://stream.example/live/$id.ts?token=$validToken"
                    MockResponse(body = """{"js":{"id":"$id","cmd":"$cmd","streamer_id":0,"link_id":0,"load":0,"error":"$linkError"}}""")
                }
                "get_simple_data_table" -> MockResponse(
                    body = """{"js":{"total_items":2,"max_page_items":14,"data":[
                        {"id":"9000","start_timestamp":"1793040300","mark_archive":"1"},
                        {"id":"9001","start_timestamp":"1793043960","mark_archive":"1"}]}}""",
                )
                "get_events" -> MockResponse(body = """{"js":{"data":{"msgs":0}}}""")
                else -> MockResponse(code = 404)
            }
        }

        fun actions(): List<String?> = requests.map { it.url.queryParameter("action") }
    }

    private val server = MockWebServer()
    private val http = OkHttpClient()

    @After fun tearDown() = server.close()

    private fun portal(fake: FakePortal = FakePortal(), typed: String = "/c/"): Pair<FakePortal, StalkerClient> {
        server.dispatcher = fake
        server.start()
        val client = StalkerClient(http, StalkerPortal.of(server.url(typed).toString())!!, StalkerDevice(mac, timeZone = "Europe/London"))
        return fake to client
    }

    @Test fun aPortalIsFoundHoweverItsTypedIn() {
        fun endpoints(text: String) = StalkerPortal.of(text)!!.endpoints.map { it.toString() }

        assertEquals(
            listOf("http://portal.example.net/portal.php", "http://portal.example.net/server/load.php", "http://portal.example.net/stalker_portal/server/load.php"),
            endpoints("portal.example.net/c/"),
        )
        assertEquals(
            listOf("http://portal.example.net:8080/stalker_portal/server/load.php", "http://portal.example.net:8080/portal.php"),
            endpoints("http://portal.example.net:8080/stalker_portal/c/"),
        )
        assertEquals("http://portal.example.net/portal.php", endpoints("http://portal.example.net/portal.php").first())
        assertEquals("http://portal.example.net:8080/stalker_portal/c/", StalkerPortal.of("http://portal.example.net:8080/stalker_portal/c/")!!.referer.toString())
        assertNull(StalkerPortal.of("not a portal"))
    }

    @Test fun macAddressesAreReadHoweverTheyreWritten() {
        assertEquals(mac, StalkerDevice.normalizeMac("00:1a:79:4c:2e:91"))
        assertEquals(mac, StalkerDevice.normalizeMac("00-1A-79-4C-2E-91"))
        assertEquals(mac, StalkerDevice.normalizeMac(" 001A794C2E91 "))
        assertNull(StalkerDevice.normalizeMac("00:1A:79:4C:2E"))
        assertNull(StalkerDevice.normalizeMac("00:1A:79:4C:2E:9G"))
        val made = StalkerDevice.newMac(Random(7))
        assertTrue(made, made.startsWith("00:1A:79:"))
        assertEquals(made, StalkerDevice.normalizeMac(made))
    }

    @Test fun signsInAsAMagBox() = runTest {
        val (fake, client) = portal()

        val account = client.connect()

        assertTrue(account.active)
        assertEquals(Instant.parse("2027-03-12T00:00:00Z"), account.expiresAt)
        assertEquals(88, account.watchdogSeconds)
        assertEquals(listOf("handshake", "get_profile", "get_main_info"), fake.actions())
        val handshake = fake.requests.first()
        assertTrue(handshake.headers["Cookie"]!!.startsWith("mac=00%3A1A%3A79%3A4C%3A2E%3A91; stb_lang=en; timezone=Europe%2FLondon"))
        assertTrue(handshake.headers["User-Agent"]!!.contains("MAG200"))
        assertEquals("Model: MAG250; Link: WiFi", handshake.headers["X-User-Agent"])
        assertEquals(server.url("/c/").toString(), handshake.headers["Referer"])
        assertNull(handshake.headers["Authorization"])
        val profile = fake.requests[1].url
        assertEquals("Bearer tok1", fake.requests[1].headers["Authorization"])
        assertEquals("MAG250", profile.queryParameter("stb_type"))
        assertEquals(13, profile.queryParameter("sn")!!.length)
    }

    @Test fun findsAStalkerPortalUnderItsOwnPath() = runTest {
        val (fake, client) = portal(FakePortal(apiPath = "/stalker_portal/server/load.php"), typed = "/stalker_portal/c/")

        client.connect()

        assertEquals("/stalker_portal/server/load.php", fake.requests.first().url.encodedPath)
    }

    @Test fun readsChannelsGenresAndArchives() = runTest {
        val (_, client) = portal()

        assertEquals(listOf(StalkerGenre("4", "Sports"), StalkerGenre("7", "News")), client.genres())
        val channels = client.channels()

        assertEquals(listOf("105", "101", "108"), channels.map { it.id })
        val arena = channels[0]
        assertEquals(105, arena.number)
        assertEquals("ffrt http://localhost/ch/105_", arena.cmd)
        assertEquals("4", arena.genreId)
        assertEquals("arena1.example", arena.xmltvId)
        assertEquals("http://logos.example/arena1.png", arena.logo)
        // 168 hours is a week; Ministra's 24 hours is a day.
        assertEquals(7, arena.archiveDays)
        assertEquals(1, channels[1].archiveDays)
        assertEquals(server.url("/logos/northline.png").toString(), channels[1].logo)
        assertNull(channels[2].number)
        assertNull(channels[2].logo)
        assertNull(channels[2].archiveDays)
    }

    @Test fun aPortalThatOnlyListsChannelsPageByPageIsReadToTheEnd() = runTest {
        val fake = FakePortal().apply {
            allChannels = """{"js":{"data":false}}"""
            orderedPages = listOf(
                """{"js":{"total_items":3,"max_page_items":2,"data":[{"id":"1","name":"One","cmd":"ffmpeg http://localhost/ch/1_"},{"id":"2","name":"Two","cmd":"ffmpeg http://localhost/ch/2_"}]}}""",
                """{"js":{"total_items":3,"max_page_items":2,"data":[{"id":"3","name":"Three","cmd":"ffmpeg http://localhost/ch/3_"}]}}""",
            )
        }
        val (_, client) = portal(fake)

        assertEquals(listOf("One", "Two", "Three"), client.channels().map { it.name })
    }

    @Test fun linksAreAskedForAtPlayTimeAndTheBoxSignsInAgainWhenTheTokenLapses() = runTest {
        val (fake, client) = portal()
        client.connect()

        assertEquals("http://stream.example/live/105.ts?token=tok1", client.createLink("ffrt http://localhost/ch/105_"))

        // The portal let the token go: one more handshake, and the call goes through.
        fake.validToken = "lapsed"
        assertEquals("http://stream.example/live/105.ts?token=tok2", client.createLink("ffrt http://localhost/ch/105_"))
        assertEquals(2, fake.actions().count { it == "handshake" })
    }

    @Test fun aPortalWithNoLinkSaysWhy() = runTest {
        val (fake, client) = portal()
        fake.linkError = "limit"

        try {
            client.createLink("ffrt http://localhost/ch/105_")
            fail("Expected NoLink")
        } catch (noLink: StalkerException.NoLink) {
            assertEquals("limit", noLink.reason)
        }
    }

    @Test fun theGuideComesAChannelAtATime() = runTest {
        val (_, client) = portal()
        val guide = LinkedHashMap<String, List<StalkerProgramme>>()

        val channels = client.guide(hours = 24) { channelId, programmes -> guide[channelId] = programmes }

        assertEquals(3, channels)
        assertEquals(listOf("105", "101", "999"), guide.keys.toList())
        val (match, extra) = guide.getValue("105")
        assertEquals("Harbour City v Redmoor", match.title)
        assertEquals(Instant.ofEpochSecond(1_793_043_900), match.start)
        assertEquals(Instant.ofEpochSecond(1_793_052_000), match.end)
        assertEquals("9001", match.id)
        assertTrue(match.archived)
        assertFalse(extra.archived)
        // No programmes comes as false.
        assertEquals(emptyList<StalkerProgramme>(), guide.getValue("101"))
    }

    @Test fun anAnswerThatBreaksOffIsntTakenForTheWholeList() = runTest {
        val fake = FakePortal().apply {
            allChannels = """{"js":{"data":[{"id":"1","name":"One","cmd":"ffmpeg http://localhost/ch/1_"},{"id":"2","na"""
        }
        val (_, client) = portal(fake)

        try {
            client.channels()
            fail("Expected an IOException")
        } catch (brokenOff: IOException) {
            assertEquals("The portal's answer broke off", brokenOff.message)
        }
    }

    @Test fun programmesPlayFromTheArchive() = runTest {
        val (_, client) = portal()

        // The guide says 23:05; the portal's archive has it from 23:06.
        val id = client.archivedProgramme("105", Instant.ofEpochSecond(1_793_043_900))
        assertEquals("9001", id)
        assertEquals("http://stream.example/archive/9001.mpg?token=tok1", client.archiveLink(id!!))
        assertNull(client.archivedProgramme("105", Instant.ofEpochSecond(1_793_000_000)))
    }

    @Test fun theWatchdogIsCalledInsideThePortalsTimeout() = runTest {
        val (fake, client) = portal()

        client.watchdog()
        // The profile gave 88 seconds.
        assertEquals(java.time.Duration.ofSeconds(58), client.watchdogInterval)

        assertEquals("get_events", fake.requests.last().url.queryParameter("action"))
        assertEquals("watchdog", fake.requests.last().url.queryParameter("type"))
    }

    @Test fun endDatesAreReadAsPortalsWriteThem() = runTest {
        val (fake, client) = portal()

        fake.mainInfo = """{"js":{"mac":"$mac","phone":"March 12, 2027, 12:00 am"}}"""
        assertEquals(Instant.parse("2027-03-12T00:00:00Z"), client.connect().expiresAt)

        fake.mainInfo = """{"js":{"mac":"$mac","phone":"Unlimited"}}"""
        assertEquals("Unlimited", client.connect().expiryText)
        assertNull(client.connect().expiresAt)

        fake.mainInfo = """{"js":{"mac":"$mac","phone":"+1 555 0100"}}"""
        fake.profile = """{"js":{"id":"1","blocked":"0","expire_billing_date":"2027-01-31 23:59:00"}}"""
        val account = client.connect()
        assertEquals(Instant.parse("2027-01-31T23:59:00Z"), account.expiresAt)
        assertNull(account.expiryText)
    }

    @Test fun aBlockedBoxARefusedOneAndAnAddressThatIsntAPortalSaySo() = runTest {
        val (fake, client) = portal()

        fake.profile = """{"js":{"id":"1","blocked":"1"}}"""
        expect<StalkerException.Blocked> { client.connect() }

        val nothing = StalkerClient(http, StalkerPortal.of(server.url("/elsewhere/c/").toString())!!, StalkerDevice(mac, timeZone = "UTC"))
        // Every endpoint answers 404: something's there, but not a portal.
        val elsewhere = FakePortal(apiPath = "/nowhere.php")
        server.dispatcher = elsewhere
        expect<StalkerException.NotStalker> { nothing.connect() }

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse(code = 401)
        }
        expect<StalkerException.Refused> { client.connect() }
    }

    @Test fun anEndpointThatRefusesEveryoneDoesntHideThePortalsOwn() = runTest {
        val fake = FakePortal(apiPath = "/stalker_portal/server/load.php")
        server.dispatcher = object : Dispatcher() {
            // A web server that forbids what isn't there, with the portal under its own path.
            override fun dispatch(request: RecordedRequest) =
                if (request.url.encodedPath == "/portal.php") MockResponse(code = 403) else fake.dispatch(request)
        }
        server.start()
        val client = StalkerClient(http, StalkerPortal.of(server.url("/").toString())!!, StalkerDevice(mac, timeZone = "UTC"))

        assertTrue(client.connect().active)
        assertEquals("/stalker_portal/server/load.php", fake.requests.first { it.url.queryParameter("action") == "get_profile" }.url.encodedPath)
    }

    @Test fun anAddressNothingAnswersAtCantBeReached() = runTest {
        server.start()
        val address = server.url("/c/").toString()
        server.close()
        val client = StalkerClient(http, StalkerPortal.of(address)!!, StalkerDevice(mac, timeZone = "UTC"))

        try {
            client.connect()
            fail("Expected an IOException")
        } catch (unreachable: IOException) {
            assertFalse(unreachable is StalkerException)
        }
    }

    @Test fun archiveLengthsInHoursOrDays() {
        assertEquals(7, StalkerClient.archiveDaysOf(168))
        assertEquals(1, StalkerClient.archiveDaysOf(24))
        assertEquals(7, StalkerClient.archiveDaysOf(7))
        assertEquals(2, StalkerClient.archiveDaysOf(36))
        assertNull(StalkerClient.archiveDaysOf(0))
        assertNull(StalkerClient.archiveDaysOf(null))
    }

    private suspend inline fun <reified T : Throwable> expect(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return
            throw AssertionError("Expected ${T::class.simpleName}, got $error", error)
        }
        fail("Expected ${T::class.simpleName}")
    }
}
