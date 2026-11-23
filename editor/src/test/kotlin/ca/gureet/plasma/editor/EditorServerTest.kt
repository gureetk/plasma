// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.editor

import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.core.model.CategoryOrder
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Edit in a Browser's server: joining, sessions and their ends, what a browser may change, and the page. */
class EditorServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
    private val backend = FakeBackend()
    private var clock = 1_000_000L
    private val joined = CopyOnWriteArrayList<EditorBrowser>()
    private lateinit var server: EditorServer
    private lateinit var base: String

    private val texts = EditorPages.KEYS.associateWith { "[$it]" } + ("title" to "Edit in a Browser </script>")

    @Before
    fun start() {
        server = EditorServer(backend, EditorPages(texts), now = { clock }, allows = { true }, onJoined = { joined += it })
        server.start(InetAddress.getLoopbackAddress(), scope, port = 0)
        server.openJoining()
        base = requireNotNull(server.invite.value).url.substringBefore("/edit")
    }

    @After
    fun stop() {
        server.close()
        scope.cancel()
    }

    @Test
    fun `a browser comes in with the code, once`() {
        val code = code()
        val first = join(code, "Chrome · Mac")
        assertEquals(200, first.code)
        val body = first.json()
        assertTrue(body.text("token").length >= 40)
        assertEquals("Avery", body.text("profile"))
        assertEquals(listOf("Chrome · Mac"), joined.map { it.name })
        assertEquals(listOf("Chrome · Mac"), server.browsers.value.map { it.name })
        // One browser per code: the TV shows another.
        assertNotEquals(code, code())
        assertEquals("code", join(code, "Safari · iPhone").json().text("error"))
    }

    @Test
    fun `five wrong codes end the code`() {
        val code = code()
        repeat(4) { assertEquals("code", join("AAAAAA", "x").json().text("error")) }
        assertEquals("tries", join("AAAAAA", "x").json().text("error"))
        assertNotEquals(code, code())
    }

    @Test
    fun `no browser comes in while the TV's sheet is closed`() {
        code()
        server.closeJoining()
        assertNull(server.invite.value)
        assertEquals("closed", join("AAAAAA", "x").json().text("error"))
    }

    @Test
    fun `only at the TV's own address, and changes only from its own page`() {
        val token = joinedToken()
        val elsewhere = fetch(Request.Builder().url("$base/edit/api/library").header("Host", "plasma.example.net").header(EditorServer.SESSION_HEADER, token))
        assertEquals(421, elsewhere.code)
        val foreign = post("favorites", """{"add":["ch-101"]}""", token, origin = "http://plasma.example.net")
        assertEquals(403, foreign.code)
        assertEquals("origin", foreign.json().text("error"))
        assertTrue(backend.favorites.isEmpty())
        assertEquals(200, post("favorites", """{"add":["ch-101"]}""", token, origin = base).code)
        assertEquals(listOf("ch-101"), backend.favorites)
    }

    @Test
    fun `without a session, the page hears why it ended`() {
        val none = get("library", token = null)
        assertEquals(401, none.code)
        assertEquals("tv", none.json().text("reason"))
        val token = joinedToken()
        assertEquals(200, post("end", "{}", token).code)
        assertEquals("you", get("library", token).json().text("reason"))
        assertTrue(server.browsers.value.isEmpty())
    }

    @Test
    fun `a session left alone ends, and asking for changes doesn't keep it going`() {
        val token = joinedToken()
        clock += EditorServer.IDLE_MS - 1_000
        assertEquals(200, get("rev", token).code)
        clock += 2_000
        server.tick()
        val after = get("library", token)
        assertEquals(401, after.code)
        assertEquals("idle", after.json().text("reason"))
    }

    @Test
    fun `End Editing on the TV ends every session`() {
        val one = joinedToken()
        val two = joinedToken()
        assertEquals(2, server.browsers.value.size)
        server.endAll()
        assertEquals("tv", get("library", one).json().text("reason"))
        assertEquals("tv", get("library", two).json().text("reason"))
    }

    @Test
    fun `the library is the profile's that was watching when the browser came in`() {
        val token = joinedToken()
        backend.watching = EditorProfile("p2", "Jordan")
        val library = get("library", token).json()
        assertEquals("p1", backend.libraryFor)
        assertEquals("Northline News", library["channels"]!!.jsonArray[0].jsonObject.text("name"))
        assertEquals(101, library["channels"]!!.jsonArray[0].jsonObject["n"]!!.jsonPrimitive.int)
        assertFalse(library.containsKey("unlocked"))
    }

    @Test
    fun `changes reach the TV as made`() {
        val token = joinedToken()
        post("favorites", """{"add":["ch-102","ch-101"]}""", token)
        post("favorites", """{"order":["ch-101","ch-102"]}""", token)
        post("favorites", """{"remove":["ch-102"]}""", token)
        assertEquals(listOf("ch-101"), backend.favorites)
        post("channels", """{"ids":["ch-105"],"hidden":true}""", token)
        assertEquals(setOf("ch-105"), backend.hidden)
        post("numbering", """{"groupsFromOne":true}""", token)
        assertEquals(null to true, backend.numbering)
        assertEquals(400, post("numbering", """{"groupsFromOne":"yes"}""", token).code)
    }

    @Test
    fun `category changes say what they made`() {
        val token = joinedToken()
        val made = post("categories", """{"op":"create","kind":"live","source":"src-home","name":"  Kids  "}""", token).json()
        assertEquals("cat-1", made.text("id"))
        post("categories", """{"op":"order","kind":"movie","source":"src-home","order":"az"}""", token)
        post("categories", """{"op":"move","kind":"series","source":"src-home","ids":["b","a"]}""", token)
        post("categories", """{"op":"members","kind":"live","id":"cat-1","add":["ch-101"],"remove":[]}""", token)
        post("categories", """{"op":"rename","kind":"live","id":"grp-news","name":""}""", token)
        assertEquals(
            listOf(
                CategoryChange.Create(CategoryKind.Live, "src-home", "Kids"),
                CategoryChange.Order(CategoryKind.Movies, "src-home", CategoryOrder.AToZ),
                CategoryChange.Move(CategoryKind.Series, "src-home", listOf("b", "a")),
                CategoryChange.Members(CategoryKind.Live, "cat-1", listOf("ch-101"), emptyList()),
                CategoryChange.Rename(CategoryKind.Live, "grp-news", ""),
            ),
            backend.categoryChanges,
        )
        assertEquals(400, post("categories", """{"op":"create","kind":"live","source":"src-home","name":"   "}""", token).code)
        assertEquals(400, post("categories", """{"op":"order","kind":"radio","source":"src-home","order":"az"}""", token).code)
    }

    @Test
    fun `locks need the PIN typed in the browser`() {
        val token = joinedToken()
        assertEquals("pin", post("channels", """{"ids":["ch-110"],"locked":true}""", token).json().text("error"))
        assertEquals("pin", post("group", """{"id":"grp-sports","locked":true}""", token).json().text("error"))
        val wrong = post("pin", """{"pin":"0000"}""", token)
        assertEquals(403, wrong.code)
        assertEquals(4, wrong.json()["left"]!!.jsonPrimitive.int)
        assertEquals(200, post("pin", """{"pin":"1234"}""", token).code)
        assertTrue(get("library", token).json()["unlocked"]!!.jsonPrimitive.boolean)
        assertEquals(200, post("channels", """{"ids":["ch-110"],"locked":true}""", token).code)
        assertEquals(200, post("group", """{"id":"grp-sports","locked":true}""", token).code)
        assertEquals(setOf("ch-110"), backend.locked)
        assertEquals(setOf("grp-sports"), backend.lockedGroups)
        // Another browser hasn't typed it.
        assertEquals("pin", post("channels", """{"ids":["ch-110"],"locked":false}""", joinedToken()).json().text("error"))
    }

    @Test
    fun `ten wrong PINs and a session can't try again`() {
        val token = joinedToken()
        backend.triesLeft = Int.MAX_VALUE
        repeat(EditorServer.MAX_WRONG_PINS) { assertEquals(403, post("pin", """{"pin":"0000"}""", token).code) }
        val after = post("pin", """{"pin":"1234"}""", token)
        assertEquals(0, after.json()["tries"]!!.jsonPrimitive.int)
        assertFalse(get("library", token).json().containsKey("unlocked"))
    }

    @Test
    fun `the page carries the TV's words, and runs nothing it didn't serve`() {
        val page = fetch(Request.Builder().url("$base/edit"))
        val html = page.body
        assertTrue(page.header("Content-Security-Policy").contains("script-src 'self'"))
        assertEquals("DENY", page.header("X-Frame-Options"))
        // The words can't end the script element they sit in.
        assertTrue(html.contains("Edit in a Browser \\u003c/script>"))
        assertTrue(html.contains("<title>Edit in a Browser &lt;/script&gt;</title>"))
        assertFalse(html.contains("{{"))
        val script = fetch(Request.Builder().url("$base/edit/editor.js"))
        assertEquals(200, script.code)
        assertTrue(script.header("Content-Type").startsWith("text/javascript"))
        val font = fetch(Request.Builder().url("$base/edit/fonts/instrument-sans-latin-400-normal.woff2"))
        assertEquals("font/woff2", font.header("Content-Type"))
        assertTrue(font.header("Cache-Control").contains("max-age"))
        assertEquals(404, fetch(Request.Builder().url("$base/edit/../build.gradle.kts")).code)
        assertEquals(404, fetch(Request.Builder().url("$base/edit/EditorPages.class")).code)
    }

    @Test
    fun `the QR code's address comes to the page with its code`() {
        val reply = fetch(Request.Builder().url("$base/?code=7KQ4MZ"), redirects = false)
        assertEquals(302, reply.code)
        assertEquals("/edit?code=7KQ4MZ", reply.header("Location"))
    }

    @Test
    fun `logos come through the TV, for a session's own address`() {
        val media = joinedSession().text("media")
        val logo = fetch(Request.Builder().url("$base/edit/logo/$media/ch-101"))
        assertEquals(200, logo.code)
        assertEquals("image/png", logo.header("Content-Type"))
        assertEquals(404, fetch(Request.Builder().url("$base/edit/logo/someone-else/ch-101")).code)
        assertEquals(404, fetch(Request.Builder().url("$base/edit/logo/$media/ch-999")).code)
    }

    @Test
    fun `big answers are packed`() {
        val token = joinedToken()
        backend.many = 400
        val reply = fetch(Request.Builder().url("$base/edit/api/library").header(EditorServer.SESSION_HEADER, token).header("Accept-Encoding", "gzip"))
        assertEquals("gzip", reply.header("Content-Encoding"))
    }

    // ---- Helpers ----

    private fun code(): String = requireNotNull(server.invite.value).code

    private fun join(code: String, browser: String): Reply =
        fetch(Request.Builder().url("$base/edit/api/join").post("""{"code":"$code","browser":"$browser"}""".toRequestBody(JSON)).header("Origin", base))

    private fun joinedSession(): JsonObject = join(code(), "Firefox · Windows").json()

    private fun joinedToken(): String = joinedSession().text("token")

    private fun get(name: String, token: String?): Reply =
        fetch(Request.Builder().url("$base/edit/api/$name").apply { if (token != null) header(EditorServer.SESSION_HEADER, token) })

    private fun post(name: String, body: String, token: String, origin: String = base): Reply =
        fetch(Request.Builder().url("$base/edit/api/$name").post(body.toRequestBody(JSON)).header(EditorServer.SESSION_HEADER, token).header("Origin", origin))

    /** An answer read whole, so no connection is left open. */
    private class Reply(val code: Int, val body: String, private val headers: okhttp3.Headers) {
        fun header(name: String): String = requireNotNull(headers[name]) { "No $name" }

        fun json(): JsonObject = Json.parseToJsonElement(body).jsonObject
    }

    private fun fetch(request: Request.Builder, redirects: Boolean = true): Reply =
        (if (redirects) http else http.newBuilder().followRedirects(false).build()).newCall(request.build()).execute()
            .use { Reply(it.code, it.body?.string().orEmpty(), it.headers) }

    private fun JsonObject.text(name: String): String = requireNotNull(this[name]?.jsonPrimitive?.content) { "No $name in $this" }

    /** The TV, in memory, with the boards' made-up channels. */
    private class FakeBackend : EditorBackend {
        @Volatile override var revision = 1L

        @Volatile var watching = EditorProfile("p1", "Avery")

        @Volatile var libraryFor: String? = null

        @Volatile var many = 0

        @Volatile var triesLeft = 5
        val favorites = CopyOnWriteArrayList<String>()
        val hidden = mutableSetOf<String>()
        val locked = mutableSetOf<String>()
        val lockedGroups = mutableSetOf<String>()
        val categoryChanges = CopyOnWriteArrayList<CategoryChange>()
        var numbering: Pair<Boolean?, Boolean?>? = null

        override suspend fun profile() = watching

        override suspend fun library(profileId: String): EditorLibrary {
            libraryFor = profileId
            val channels = listOf(
                EditorChannel("ch-101", 101, "Northline News", "grp-news", "src-home", logo = true),
                EditorChannel("ch-102", 102, "Meridian 24", "grp-news", "src-home"),
            ) + List(many) { EditorChannel("ch-x$it", 1000 + it, "Arena Sports $it", "grp-sports", "src-home") }
            return EditorLibrary(
                rev = revision, tv = "Living Room", profile = watching.name, pin = true, favoritesFromOne = true, groupsFromOne = false,
                sources = listOf(EditorSource("src-home", "Home Portal", "stalker", true, channels.size, 0, 0)),
                groups = listOf(EditorGroup("grp-news", "src-home", "News"), EditorGroup("grp-sports", "src-home", "Sports")),
                channels = channels, favorites = favorites.toList(),
            )
        }

        override suspend fun categories(sourceId: String, kind: CategoryKind) = null

        override suspend fun titles(kind: CategoryKind, categoryId: String) = emptyList<EditorTitle>()

        override suspend fun searchTitles(kind: CategoryKind, sourceId: String, query: String) = emptyList<EditorTitle>()

        override suspend fun favorites(profileId: String, change: FavoritesChange) {
            when (change) {
                is FavoritesChange.Add -> change.ids.filter { it !in favorites }.forEach { favorites += it }
                is FavoritesChange.Remove -> favorites.removeAll(change.ids.toSet())
                is FavoritesChange.Order -> {
                    val rest = favorites.filter { it !in change.ids }
                    favorites.clear()
                    favorites += change.ids + rest
                }
            }
            revision++
        }

        override suspend fun hide(channelIds: List<String>, hidden: Boolean) {
            if (hidden) this.hidden += channelIds else this.hidden -= channelIds.toSet()
        }

        override suspend fun lock(channelIds: List<String>, locked: Boolean) {
            if (locked) this.locked += channelIds else this.locked -= channelIds.toSet()
        }

        override suspend fun lockGroup(groupId: String, locked: Boolean) {
            if (locked) lockedGroups += groupId else lockedGroups -= groupId
        }

        override suspend fun category(change: CategoryChange): String? {
            categoryChanges += change
            return if (change is CategoryChange.Create) "cat-1" else null
        }

        override suspend fun numbering(favoritesFromOne: Boolean?, groupsFromOne: Boolean?) {
            numbering = favoritesFromOne to groupsFromOne
        }

        override suspend fun checkPin(pin: String): PinCheck = when {
            pin == "1234" -> PinCheck.Right
            triesLeft > 0 -> PinCheck.Wrong(--triesLeft)
            else -> PinCheck.Blocked(60)
        }

        override fun logo(channelId: String) = if (channelId == "ch-101") EditorLogo(byteArrayOf(1, 2, 3), "image/png") else null
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
