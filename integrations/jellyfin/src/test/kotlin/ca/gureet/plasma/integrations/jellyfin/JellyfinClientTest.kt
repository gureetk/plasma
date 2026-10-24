// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.integrations.jellyfin

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest

/** A Jellyfin server as Plasma reads it (brief 5.19), played by a mock with the boards' made-up library. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class JellyfinClientTest {
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** Answers by path; a path that isn't here is a 404. */
    private val answers = ConcurrentHashMap<String, (RecordedRequest) -> MockResponse>()
    private lateinit var client: JellyfinClient
    private lateinit var media: JellyfinServer

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return answers[request.url.encodedPath]?.invoke(request) ?: MockResponse(code = 404)
            }
        }
        server.start()
        client = JellyfinClient(ApplicationProvider.getApplicationContext(), "1.0", OkHttpClient())
        media = JellyfinServer(server.url("/").toString().trimEnd('/'), "Media")
    }

    @After fun stop() = server.close()

    private fun answer(path: String, body: String, code: Int = 200) {
        answers[path] = { MockResponse(code = code, body = body, headers = okhttp3.Headers.headersOf("Content-Type", "application/json")) }
    }

    private fun requestsTo(path: String) = requests.filter { it.url.encodedPath == path }

    /** The `DeviceId="…"` Plasma sent [request] as. */
    private fun deviceOf(request: RecordedRequest): String? =
        Regex("DeviceId=\"([^\"]*)\"").find(request.headers["Authorization"].orEmpty())?.groupValues?.get(1)

    private suspend inline fun expect(problem: JellyfinProblem, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $problem")
        } catch (e: JellyfinException) {
            assertEquals(problem, e.problem)
        }
    }

    @Test fun findsTheServerAtATypedAddress() = runTest {
        answer("/System/Info/Public", SYSTEM_INFO)

        val found = client.find(media.address)

        assertNotNull(found)
        assertEquals("Media", found!!.name)
        assertTrue(found.address, found.address.startsWith(media.address))
    }

    @Test fun nothingAtAnAddressFindsNothing() = runTest {
        // Something answers, but not as Jellyfin.
        answer("/System/Info/Public", "<html>Welcome to nginx</html>")
        assertNull(client.find(media.address))

        server.close()
        assertNull(client.find(media.address))
    }

    @Test fun quickConnectSignsInOnceTheCodeIsApproved() = runTest {
        val asked = AtomicInteger()
        answer("/QuickConnect/Enabled", "true")
        answer("/QuickConnect/Initiate", quickConnect(approved = false))
        answers["/QuickConnect/Connect"] = { request ->
            assertEquals("qc-secret", request.url.queryParameter("secret"))
            MockResponse(body = quickConnect(approved = asked.incrementAndGet() > 1))
        }
        answer("/Users/AuthenticateWithQuickConnect", SIGNED_IN)

        val code = client.quickConnect(media)
        assertEquals("482913", code.code)
        assertFalse("secret" in code.toString())
        assertFalse(client.approved(media, code))
        assertTrue(client.approved(media, code))
        val login = client.signIn(media, code)

        assertEquals(media.address, login.address)
        assertEquals("Media", login.serverName)
        assertEquals("Maya", login.userName)
        assertEquals("6a1e8d0c-4b5f-4c3e-9d2a-7f8e9a0b1c2d", login.userId)
        assertEquals("srv-1", login.serverId)
        assertEquals("tok-maya", login.token)
        assertFalse("tok-maya" in login.toString())
        // One device from the code to the sign-in, which the server ties them to.
        val devices = requests.map(::deviceOf).toSet()
        assertEquals(setOf(login.deviceId), devices)
        assertTrue(requestsTo("/Users/AuthenticateWithQuickConnect").single().body?.utf8().orEmpty().contains("qc-secret"))
        assertTrue(requests.first().headers["Authorization"].orEmpty().contains("Client=\"Plasma\""))
    }

    @Test fun quickConnectTurnedOffIsSaid() = runTest {
        answer("/QuickConnect/Enabled", "false")
        expect(JellyfinProblem.QuickConnectOff) { client.quickConnect(media) }

        // Older servers say so by refusing.
        answer("/QuickConnect/Enabled", "true")
        answer("/QuickConnect/Initiate", "", code = 401)
        expect(JellyfinProblem.QuickConnectOff) { client.quickConnect(media) }
    }

    @Test fun signsInWithAPasswordAndSaysWhenItsWrong() = runTest {
        answer("/Users/AuthenticateByName", SIGNED_IN)
        val login = client.signIn(media, "maya", "s3cret")
        assertEquals("Maya", login.userName)
        val body = requestsTo("/Users/AuthenticateByName").single().body?.utf8().orEmpty()
        assertTrue(body, "\"Username\":\"maya\"" in body && "\"Pw\":\"s3cret\"" in body)

        answer("/Users/AuthenticateByName", "Error processing request.", code = 401)
        expect(JellyfinProblem.WrongLogin) { client.signIn(media, "maya", "wrong") }

        // Each sign-in is a device of its own on the server.
        answer("/Users/AuthenticateByName", SIGNED_IN)
        assertTrue(client.signIn(media, "maya", "s3cret").deviceId != login.deviceId)
    }

    @Test fun continueWatchingHasWhatsStartedAndWhatsUpNext() = runTest {
        answer("/UserItems/Resume", RESUME)
        answer("/Shows/NextUp", NEXT_UP)
        answer("/Items", WATCHED)
        val login = signedIn()

        val items = client.continueItems(login, nextUp = true, since = Instant.parse("2026-09-22T00:00:00Z"))

        assertEquals(listOf("Kite Season", "Kingdoms of Ice", "Atlas Station"), items.map { it.title })
        val (kite, kingdoms, atlas) = items

        // A movie with a backdrop: 16:9 art, 52 minutes left of 1 h 24.
        assertEquals(JellyfinKind.Movie, kite.kind)
        assertTrue(kite.wide)
        assertTrue(kite.image!!, "/Images/Backdrop" in kite.image!! && "tag=kite-bd" in kite.image!!)
        assertEquals(1_920_000, kite.positionMs)
        assertEquals(5_040_000, kite.durationMs)
        assertEquals(Instant.parse("2026-10-21T20:10:00Z"), kite.lastWatched)
        assertFalse(kite.upNext)

        // An episode started: its series' thumb, its numbers and name.
        assertEquals(JellyfinKind.Episode, kingdoms.kind)
        assertEquals("The Long Night", kingdoms.episodeTitle)
        assertEquals(1, kingdoms.season)
        assertEquals(3, kingdoms.episode)
        assertTrue(kingdoms.image!!, "/Images/Thumb" in kingdoms.image!! && "tag=kingdoms-th" in kingdoms.image!!)
        assertTrue(kingdoms.wide)

        // Up next: an episode whose series only has a poster, as old as its series' last watch.
        assertTrue(atlas.upNext)
        assertEquals(2, atlas.season)
        assertEquals(5, atlas.episode)
        assertFalse(atlas.wide)
        assertTrue(atlas.image!!, "/Images/Primary" in atlas.image!! && "tag=atlas-poster" in atlas.image!!)
        assertEquals(Instant.parse("2026-10-20T21:30:00Z"), atlas.lastWatched)
        assertEquals(0, atlas.positionMs)

        val resume = requestsTo("/UserItems/Resume").single().url
        assertEquals("6a1e8d0c4b5f4c3e9d2a7f8e9a0b1c2d", resume.queryParameter("userId")?.replace("-", ""))
        assertEquals("Video", resume.queryParameter("mediaTypes"))
        val nextUp = requestsTo("/Shows/NextUp").single().url
        assertEquals("false", nextUp.queryParameter("enableResumable"))
        assertNotNull(nextUp.queryParameter("nextUpDateCutoff"))
        // Plasma's own sign-in goes with each request.
        assertTrue(requestsTo("/UserItems/Resume").single().headers["Authorization"].orEmpty().contains("Token=\"tok-maya\""))
    }

    @Test fun withoutNextEpisodesOnlyWhatsStarted() = runTest {
        answer("/UserItems/Resume", RESUME)

        val items = client.continueItems(signedIn(), nextUp = false, since = null)

        assertEquals(listOf("Kite Season", "Kingdoms of Ice"), items.map { it.title })
        assertTrue(requestsTo("/Shows/NextUp").isEmpty())
    }

    @Test fun aSignInTheServerForgotIsSaid() = runTest {
        answer("/UserItems/Resume", "", code = 401)
        expect(JellyfinProblem.SignedOut) { client.continueItems(signedIn(), nextUp = false, since = null) }
    }

    @Test fun aServerThatsOffIsSaid() = runTest {
        val login = signedIn()
        server.close()
        expect(JellyfinProblem.CantReach) { client.continueItems(login, nextUp = false, since = null) }
        // Signing out of it is quiet.
        client.signOut(login)
    }

    @Test fun signingOutEndsTheSessionOnTheServer() = runTest {
        answer("/Sessions/Logout", "", code = 204)

        client.signOut(signedIn())

        val logout = requestsTo("/Sessions/Logout").single()
        assertEquals("POST", logout.method)
        assertTrue(logout.headers["Authorization"].orEmpty().contains("Token=\"tok-maya\""))
    }

    private fun signedIn() = JellyfinLogin(media.address, "srv-1", "Media", "6a1e8d0c-4b5f-4c3e-9d2a-7f8e9a0b1c2d", "Maya", "tok-maya", "plasma-device-1")

    private companion object {
        val SYSTEM_INFO = """
            {"LocalAddress":"http://192.168.1.20:8096","ServerName":"Media","Version":"10.11.2","ProductName":"Jellyfin Server",
             "OperatingSystem":"","Id":"5a3b9c1d2e4f40718293a4b5c6d7e8f9","StartupWizardCompleted":true}
        """.trimIndent()

        fun quickConnect(approved: Boolean) = """
            {"Authenticated":$approved,"Secret":"qc-secret","Code":"482913","DeviceId":"d","DeviceName":"Living Room TV",
             "AppName":"Plasma","AppVersion":"1.0","DateAdded":"2026-10-22T19:40:00.0000000Z"}
        """.trimIndent()

        val SIGNED_IN = """
            {"User":{"Name":"Maya","ServerId":"srv-1","Id":"6a1e8d0c4b5f4c3e9d2a7f8e9a0b1c2d","HasPassword":true},
             "AccessToken":"tok-maya","ServerId":"srv-1"}
        """.trimIndent()

        fun userData(item: String, ticks: Long, played: String?) = """
            {"PlaybackPositionTicks":$ticks,"PlayCount":1,"IsFavorite":false,"Played":false,"Key":"$item","ItemId":"$item"
             ${played?.let { ",\"LastPlayedDate\":\"$it\"" }.orEmpty()}}
        """.trimIndent()

        val RESUME = """
            {"Items":[
              {"Name":"Kite Season","Id":"11111111111111111111111111111111","Type":"Movie","MediaType":"Video",
               "RunTimeTicks":50400000000,"PrimaryImageAspectRatio":0.6666666666666666,
               "ImageTags":{"Primary":"kite-poster"},"BackdropImageTags":["kite-bd"],
               "UserData":${userData("11111111111111111111111111111111", 19_200_000_000, "2026-10-21T20:10:00.0000000Z")}},
              {"Name":"The Long Night","Id":"22222222222222222222222222222222","Type":"Episode","MediaType":"Video",
               "SeriesName":"Kingdoms of Ice","SeriesId":"33333333333333333333333333333333","ParentIndexNumber":1,"IndexNumber":3,
               "RunTimeTicks":30000000000,"PrimaryImageAspectRatio":1.7777777777777777,"ImageTags":{"Primary":"ep-still"},
               "ParentThumbItemId":"33333333333333333333333333333333","ParentThumbImageTag":"kingdoms-th",
               "UserData":${userData("22222222222222222222222222222222", 6_000_000_000, "2026-10-19T18:00:00.0000000Z")}}
            ],"TotalRecordCount":2,"StartIndex":0}
        """.trimIndent()

        val NEXT_UP = """
            {"Items":[
              {"Name":"The Docking Bay","Id":"44444444444444444444444444444444","Type":"Episode","MediaType":"Video",
               "SeriesName":"Atlas Station","SeriesId":"55555555555555555555555555555555","ParentIndexNumber":2,"IndexNumber":5,
               "RunTimeTicks":27000000000,"SeriesPrimaryImageTag":"atlas-poster",
               "UserData":${userData("44444444444444444444444444444444", 0, null)}},
              {"Name":"The Long Night","Id":"22222222222222222222222222222222","Type":"Episode","MediaType":"Video",
               "SeriesName":"Kingdoms of Ice","SeriesId":"33333333333333333333333333333333","ParentIndexNumber":1,"IndexNumber":3,
               "UserData":${userData("22222222222222222222222222222222", 6_000_000_000, null)}}
            ],"TotalRecordCount":2,"StartIndex":0}
        """.trimIndent()

        val WATCHED = """
            {"Items":[
              {"Name":"Spin","Id":"66666666666666666666666666666666","Type":"Episode","SeriesId":"55555555555555555555555555555555",
               "UserData":${userData("66666666666666666666666666666666", 0, "2026-10-20T21:30:00.0000000Z")}},
              {"Name":"Launch","Id":"77777777777777777777777777777777","Type":"Episode","SeriesId":"55555555555555555555555555555555",
               "UserData":${userData("77777777777777777777777777777777", 0, "2026-10-18T21:00:00.0000000Z")}}
            ],"TotalRecordCount":2,"StartIndex":0}
        """.trimIndent()
    }
}
