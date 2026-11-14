// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.editor

import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.pairing.Http
import ca.gureet.plasma.pairing.HttpRequest
import ca.gureet.plasma.pairing.PairingInvite
import ca.gureet.plasma.pairing.PairingServer
import ca.gureet.plasma.pairing.QrCodes
import ca.gureet.plasma.pairing.TooLarge
import ca.gureet.plasma.pairing.isHomeNetwork
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.BindException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** A browser in Edit in a Browser: what it called itself, and since when. */
data class EditorBrowser(val id: String, val name: String, val since: Long)

/** Why a browser's session ended, as the page is told. */
object EndReasons {
    /** Nothing came from it for 15 minutes. */
    const val IDLE = "idle"

    /** The TV ended it: End Editing, or Plasma stopped. */
    const val TV = "tv"

    /** The browser's own End. */
    const val YOU = "you"
}

/**
 * Edit in a Browser's server: the page at `http://<tv>:8485/edit` (or another port, while
 * something else has 8485), and the JSON it reads and changes the TV's channels, favorites and
 * categories through.
 *
 * A browser comes in with the code the TV shows while Settings › Portals & Playlists › Edit in a
 * Browser is open: the code is good for one browser and 10 minutes, and 5 wrong tries end it, as
 * for Add a Source's phone page (5.15). It then has a random 256-bit token of its own, kept on the
 * TV only as a hash, which every request carries in a header, never in the address. Its session
 * lasts until it ends it, the TV ends it, or 15 minutes pass with nothing from it but the page
 * asking whether anything changed. Locks need the parental PIN typed in the browser, counted with
 * the TV's own tries, and ten wrong ones end the session's chances.
 *
 * Only the home network gets an answer, only at the TV's own address (so a page elsewhere can't
 * reach it by a name of its own), and a change only from the TV's own page (its Origin). Nothing
 * secret goes to the page: no addresses, logins or passwords, and logos come through the TV.
 */
class EditorServer(
    private val backend: EditorBackend,
    private val page: EditorPage,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val allows: (InetAddress) -> Boolean = ::isHomeNetwork,
    /** A browser came in: the TV says so. */
    private val onJoined: (EditorBrowser) -> Unit = {},
    /** How long a session lasts with nothing from its browser. */
    private val idleMs: Long = IDLE_MS,
) : Closeable {
    private val _invite = MutableStateFlow<PairingInvite?>(null)

    /** What the TV's sheet shows while browsers may come in: the address, the code and its QR code. */
    val invite: StateFlow<PairingInvite?> = _invite.asStateFlow()

    private val _browsers = MutableStateFlow<List<EditorBrowser>>(emptyList())

    /** The browsers editing now. */
    val browsers: StateFlow<List<EditorBrowser>> = _browsers.asStateFlow()

    private var server: ServerSocket? = null
    private var host = ""
    private var code: Code? = null
    private var timer: Job? = null

    /** Sessions by their token's hash. */
    private val sessions = ConcurrentHashMap<String, Session>()

    /** Tokens of sessions that ended, with why, so a page that comes back hears it; the last few only. */
    private val ended = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > ENDED_KEPT
    }

    @Volatile private var closed = false

    private val serving = AtomicInteger()
    private val deadlines = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "plasma-editor-deadline").apply { isDaemon = true } }

    private class Code(val text: String, val expiresAt: Long) {
        var attempts = 0
    }

    private inner class Session(
        val browser: EditorBrowser,
        /** For logos, whose addresses can't carry a header: not the token, which never goes in one. */
        val media: String,
        val profile: EditorProfile,
    ) {
        @Volatile var lastActive: Long = now()

        @Volatile var unlocked = false
        var wrongPins = 0
        private var windowStart = 0L
        private var inWindow = 0

        @Synchronized
        fun flooding(): Boolean {
            val at = now()
            if (at - windowStart >= RATE_WINDOW_MS) {
                windowStart = at
                inWindow = 0
            }
            return ++inWindow > RATE_LIMIT
        }
    }

    /**
     * Starts answering at [address], on port 8485 or, when something else has it, another free
     * port. Codes and sessions are kept in time in [scope].
     *
     * @throws IOException when no port can be opened.
     */
    fun start(address: InetAddress, scope: CoroutineScope, port: Int = PORT) {
        check(server == null) { "Already started" }
        val socket = try {
            ServerSocket(port, BACKLOG, address)
        } catch (taken: BindException) {
            ServerSocket(0, BACKLOG, address)
        }
        server = socket
        val name = address.hostAddress.substringBefore('%')
        host = (if (address is Inet6Address) "[$name]" else name) + ":${socket.localPort}"
        Thread(::accept, "plasma-editor").apply { isDaemon = true }.start()
        timer = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    /** The TV's sheet is showing: a code lets one browser in, and a new one shows after it. */
    fun openJoining() = synchronized(this) {
        if (code == null) newCode()
    }

    /** The sheet closed: no other browser can come in, and those in carry on. */
    fun closeJoining() = synchronized(this) {
        code = null
        _invite.value = null
    }

    /** End Editing on the TV: every browser's session ends. */
    fun endAll() = endWhere(EndReasons.TV) { true }

    override fun close() {
        closed = true
        timer?.cancel()
        runCatching { server?.close() }
        deadlines.shutdownNow()
        closeJoining()
        endAll()
    }

    /** Codes that ran out are replaced, and sessions left alone too long end. */
    internal fun tick() {
        synchronized(this) {
            val current = code
            if (current != null && now() >= current.expiresAt) newCode()
        }
        val at = now()
        endWhere(EndReasons.IDLE) { at - it.lastActive >= idleMs }
    }

    private fun endWhere(reason: String, which: (Session) -> Boolean) {
        synchronized(this) {
            val gone = sessions.filterValues(which).keys
            if (gone.isEmpty()) return
            gone.forEach { hash ->
                sessions.remove(hash)
                ended[hash] = reason
            }
            updateBrowsers()
        }
    }

    private fun updateBrowsers() {
        _browsers.value = sessions.values.map { it.browser }.sortedBy { it.since }
    }

    private fun accept() {
        val socket = server ?: return
        while (!closed) {
            val client = try {
                socket.accept()
            } catch (error: SocketException) {
                if (closed) return else continue
            } catch (error: IOException) {
                continue
            }
            // A browser asks for a few things at once (a list's logos); far more is something else.
            if (serving.incrementAndGet() > MAX_REQUESTS) {
                serving.decrementAndGet()
                runCatching { client.close() }
                continue
            }
            Thread({
                try {
                    serve(client)
                } finally {
                    serving.decrementAndGet()
                }
            }, "plasma-editor-request").apply { isDaemon = true }.start()
        }
    }

    private fun serve(socket: Socket) {
        // A request that trickles in a byte at a time is cut off, however slow each read.
        val deadline = runCatching { deadlines.schedule({ runCatching { socket.close() } }, REQUEST_DEADLINE_MS, TimeUnit.MILLISECONDS) }.getOrNull()
        socket.use {
            try {
                socket.soTimeout = READ_TIMEOUT_MS
                val out = socket.getOutputStream()
                if (!allows(socket.inetAddress)) return write(out, Reply.status(403), gzip = false)
                val request = try {
                    Http.read(socket.getInputStream(), MAX_BODY_BYTES) ?: return
                } catch (big: TooLarge) {
                    return write(out, Reply.json(413, error("large")), gzip = false)
                }
                val reply = answer(request)
                write(out, reply, gzip = request.headers["accept-encoding"]?.contains("gzip") == true)
            } catch (error: IOException) {
                // The browser went away, or took too long.
            } finally {
                deadline?.cancel(false)
            }
        }
    }

    /** The answer to [request]. */
    internal fun answer(request: HttpRequest): Reply {
        // Only at the address the TV showed: a page that points a name of its own at the TV
        // (DNS rebinding) would send its own name here.
        if (request.headers["host"] != host) return Reply.status(421)
        val path = request.path
        return when {
            path == "/" -> Reply.redirect(request.query["code"]?.let { "$PAGE_PATH?code=${URLEncoder.encode(it, Charsets.UTF_8)}" } ?: PAGE_PATH)
            path.startsWith(API_PATH) -> api(request, path.removePrefix(API_PATH))
            path.startsWith(LOGO_PATH) -> logo(request, path.removePrefix(LOGO_PATH))
            request.method != "GET" -> Reply.status(405)
            path == PAGE_PATH || path == "$PAGE_PATH/" -> Reply(200, HTML, page.html().toByteArray(Charsets.UTF_8), pagePolicy())
            path.startsWith("$PAGE_PATH/") -> page.file(path.removePrefix("$PAGE_PATH/"))
                ?.let { Reply(200, it.type, it.bytes, pagePolicy(), cache = it.cache) }
                ?: Reply.status(404)
            else -> Reply.status(404)
        }
    }

    /** The page may run its own script and styles, load its fonts and the TV's logos, and ask the TV, and nothing else. */
    private fun pagePolicy(): String =
        "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self'; " +
            "manifest-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

    private fun api(request: HttpRequest, name: String): Reply {
        val post = request.method == "POST"
        if (!post && request.method != "GET") return Reply.status(405)
        // A change only from the TV's own page: a page elsewhere sends its own Origin.
        if (post && request.headers["origin"].let { it != null && it != "http://$host" }) return Reply.json(403, error("origin"))
        if (name == "join") return if (post) join(request) else Reply.status(405)
        val token = request.headers[SESSION_HEADER] ?: return Reply.json(401, endedBecause(null))
        val hash = hash(token)
        val session = sessions[hash] ?: return Reply.json(401, endedBecause(hash))
        if (session.flooding()) return Reply.json(429, error("busy"))
        // The page asking whether anything changed doesn't keep a session going: anything else does.
        if (name != "rev") session.lastActive = now()
        return try {
            when {
                !post -> read(session, name, request.query)
                else -> {
                    val body = runCatching { Json.parseToJsonElement(String(request.body, Charsets.UTF_8)).jsonObject }.getOrNull()
                        ?: return Reply.json(400, error("body"))
                    change(session, hash, name, body)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            // Nothing about why goes to the page: it says the change didn't go through.
            Reply.json(500, error("failed"))
        }
    }

    private fun read(session: Session, name: String, query: Map<String, String>): Reply = when (name) {
        "rev" -> Reply.json(200, buildJsonObject { put("rev", backend.revision) })
        "library" -> {
            val library = runBlocking { backend.library(session.profile.id) }.copy(unlocked = session.unlocked)
            Reply.json(200, json.encodeToJsonElement(library))
        }
        "categories" -> {
            val kind = kindOf(query["kind"]) ?: return Reply.json(400, error("kind"))
            val source = query["source"]?.takeIf { it.isNotEmpty() } ?: return Reply.json(400, error("source"))
            runBlocking { backend.categories(source, kind) }?.let { Reply.json(200, json.encodeToJsonElement(it)) } ?: Reply.json(404, error("source"))
        }
        "titles" -> {
            val kind = kindOf(query["kind"])?.takeIf { it != CategoryKind.Live } ?: return Reply.json(400, error("kind"))
            val category = query["category"]
            val source = query["source"]
            val titles = when {
                category != null -> runBlocking { backend.titles(kind, category) }
                source != null -> runBlocking { backend.searchTitles(kind, source, query["q"].orEmpty().take(MAX_NAME)) }.take(MAX_TITLES)
                else -> return Reply.json(400, error("category"))
            }
            Reply.json(200, buildJsonObject { put("titles", json.encodeToJsonElement(titles)) })
        }
        else -> Reply.json(404, error("unknown"))
    }

    private fun change(session: Session, hash: String, name: String, body: JsonObject): Reply {
        when (name) {
            "favorites" -> {
                val change = when {
                    "add" in body -> FavoritesChange.Add(body.ids("add"))
                    "remove" in body -> FavoritesChange.Remove(body.ids("remove"))
                    "order" in body -> FavoritesChange.Order(body.ids("order"))
                    else -> return Reply.json(400, error("body"))
                }
                runBlocking { backend.favorites(session.profile.id, change) }
            }
            "channels" -> {
                val ids = body.ids("ids")
                val hidden = body.flag("hidden")
                val locked = body.flag("locked")
                when {
                    hidden != null -> runBlocking { backend.hide(ids, hidden) }
                    locked != null -> {
                        if (!session.unlocked) return Reply.json(403, error("pin"))
                        runBlocking { backend.lock(ids, locked) }
                    }
                    else -> return Reply.json(400, error("body"))
                }
            }
            "group" -> {
                val id = body.text("id") ?: return Reply.json(400, error("body"))
                val locked = body.flag("locked") ?: return Reply.json(400, error("body"))
                if (!session.unlocked) return Reply.json(403, error("pin"))
                runBlocking { backend.lockGroup(id, locked) }
            }
            "categories" -> {
                val change = categoryChange(body) ?: return Reply.json(400, error("body"))
                val made = runBlocking { backend.category(change) }
                return Reply.json(200, buildJsonObject { if (made != null) put("id", made) })
            }
            "numbering" -> {
                val favorites = body.flag("favoritesFromOne")
                val groups = body.flag("groupsFromOne")
                if (favorites == null && groups == null) return Reply.json(400, error("body"))
                runBlocking { backend.numbering(favorites, groups) }
            }
            "pin" -> return pin(session, body.text("pin").orEmpty())
            "end" -> {
                synchronized(this) {
                    sessions.remove(hash)
                    ended[hash] = EndReasons.YOU
                    updateBrowsers()
                }
            }
            else -> return Reply.json(404, error("unknown"))
        }
        return Reply.json(200, buildJsonObject { put("ok", true) })
    }

    private fun categoryChange(body: JsonObject): CategoryChange? {
        val kind = kindOf(body.text("kind")) ?: return null
        val source = body.text("source")
        val id = body.text("id")
        return when (body.text("op")) {
            "hide" -> CategoryChange.Hide(kind, id ?: return null, body.flag("hidden") ?: return null)
            "hideAll" -> CategoryChange.HideAll(kind, source ?: return null, body.flag("hidden") ?: return null)
            "order" -> CategoryChange.Order(kind, source ?: return null, orderOf(body.text("order")) ?: return null)
            "move" -> CategoryChange.Move(kind, source ?: return null, body.ids("ids"))
            "rename" -> CategoryChange.Rename(kind, id ?: return null, body.text("name").orEmpty().trim().take(MAX_NAME))
            "create" -> body.text("name")?.trim()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() }?.let { CategoryChange.Create(kind, source ?: return null, it) }
            "delete" -> CategoryChange.Delete(kind, id ?: return null)
            "members" -> CategoryChange.Members(kind, id ?: return null, body.ids("add"), body.ids("remove"))
            else -> null
        }
    }

    /**
     * The parental PIN, typed in the browser to change locks (5.12): right, the session can change
     * them until it ends. Wrong tries count with the TV's own, and after ten this session can't try
     * again: a browser can't wear the PIN down.
     */
    private fun pin(session: Session, typed: String): Reply {
        if (session.wrongPins >= MAX_WRONG_PINS) return Reply.json(403, buildJsonObject { put("ok", false); put("tries", 0) })
        val digits = typed.filter { it.isDigit() }.take(PIN_DIGITS)
        return when (val check = runBlocking { backend.checkPin(digits) }) {
            PinCheck.Right -> {
                session.unlocked = true
                Reply.json(200, buildJsonObject { put("ok", true) })
            }
            is PinCheck.Wrong -> {
                session.wrongPins++
                Reply.json(403, buildJsonObject { put("ok", false); put("left", check.left) })
            }
            is PinCheck.Blocked -> Reply.json(429, buildJsonObject { put("ok", false); put("wait", check.seconds) })
            PinCheck.Off -> Reply.json(409, buildJsonObject { put("ok", false); put("off", true) })
        }
    }

    /**
     * A browser comes in with the code the TV shows, and a name for the TV's sheet ("Chrome ·
     * Mac"). It's handed its token once, and the TV shows a new code for the next one.
     */
    private fun join(request: HttpRequest): Reply {
        val body = runCatching { Json.parseToJsonElement(String(request.body, Charsets.UTF_8)).jsonObject }.getOrNull()
            ?: return Reply.json(400, error("body"))
        val browserName = body.text("browser").orEmpty().filter { !it.isISOControl() }.trim().take(MAX_NAME).ifEmpty { "Browser" }
        if (synchronized(this) { code == null }) return Reply.json(403, error("closed"))
        // Who's watching, asked before the lock is taken: the app may ask its main thread.
        val profile = runBlocking { backend.profile() }
        val token = randomText(TOKEN_BYTES)
        val session = Session(EditorBrowser(UUID.randomUUID().toString(), browserName, now()), randomText(MEDIA_BYTES), profile)
        synchronized(this) {
            val wrong = checkCode(body.text("code").orEmpty())
            if (wrong != null) return Reply.json(403, error(wrong))
            if (sessions.size >= MAX_SESSIONS) return Reply.json(409, error("full"))
            // One browser per code: the TV shows a new one.
            newCode()
            sessions[hash(token)] = session
            updateBrowsers()
        }
        onJoined(session.browser)
        return Reply.json(
            200,
            buildJsonObject {
                put("token", token)
                put("media", session.media)
                put("profile", profile.name)
            },
        )
    }

    /** A logo, for a session's own address of them; the browser never asks the provider. */
    private fun logo(request: HttpRequest, rest: String): Reply {
        if (request.method != "GET") return Reply.status(405)
        val media = rest.substringBefore('/')
        val session = sessions.values.firstOrNull { same(it.media, media) } ?: return Reply.status(404)
        session.lastActive = now()
        // The id as the page sent it, percent-encoded; a "+" in it is a "+".
        val channelId = runCatching { URLDecoder.decode(rest.substringAfter('/', "").replace("+", "%2B"), Charsets.UTF_8) }.getOrNull() ?: return Reply.status(404)
        val logo = backend.logo(channelId) ?: return Reply.status(404)
        return Reply(200, logo.type, logo.bytes, IMAGE_POLICY, cache = true)
    }

    /** Null when [typed] is the code; otherwise why it isn't, with the try counted. */
    private fun checkCode(typed: String): String? {
        val current = code ?: return "closed"
        val normalized = typed.uppercase().filter { it.isLetterOrDigit() }
        if (same(normalized, current.text)) return null
        current.attempts++
        if (current.attempts < PairingServer.MAX_ATTEMPTS) return "code"
        newCode()
        return "tries"
    }

    private fun newCode() {
        val text = String(CharArray(PairingServer.CODE_LENGTH) { PairingServer.CODE_ALPHABET[random.nextInt(PairingServer.CODE_ALPHABET.length)] })
        code = Code(text, now() + PairingServer.LIFETIME_MS)
        val url = "http://$host$PAGE_PATH?code=$text"
        _invite.value = PairingInvite(host, text, url, QrCodes.encode(url))
    }

    private fun endedBecause(hash: String?): JsonObject = buildJsonObject {
        put("error", "ended")
        put("reason", hash?.let { synchronized(this@EditorServer) { ended[it] } } ?: EndReasons.TV)
    }

    private fun error(what: String): JsonObject = buildJsonObject { put("error", what) }

    private fun randomText(bytes: Int): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))

    /** Compares in constant time, so how long a wrong guess takes says nothing. */
    private fun same(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun write(out: OutputStream, reply: Reply, gzip: Boolean) {
        val packed = gzip && reply.body.size >= GZIP_FROM && reply.type.let { it.startsWith("application/json") || it.startsWith("text/") }
        val body = if (packed) gzipped(reply.body) else reply.body
        val head = buildString {
            append("HTTP/1.1 ").append(reply.status).append(' ').append(reasonOf(reply.status)).append("\r\n")
            if (reply.location != null) append("Location: ").append(reply.location).append("\r\n")
            if (body.isNotEmpty() || reply.status == 200) append("Content-Type: ").append(reply.type).append("\r\n")
            if (packed) append("Content-Encoding: gzip\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append(if (reply.cache) "Cache-Control: private, max-age=86400" else "Cache-Control: no-store").append("\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("X-Frame-Options: DENY\r\n")
            append("Referrer-Policy: no-referrer\r\n")
            append("Content-Security-Policy: ").append(reply.policy).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private fun gzipped(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { buffer -> GZIPOutputStream(buffer).use { it.write(bytes) } }.toByteArray()

    private fun reasonOf(status: Int): String = when (status) {
        200 -> "OK"
        302 -> "Found"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        409 -> "Conflict"
        413 -> "Content Too Large"
        421 -> "Misdirected Request"
        429 -> "Too Many Requests"
        else -> "Error"
    }

    /** An answer: its status, its body of [type], the content security policy it goes under, and whether a browser may keep it. */
    internal class Reply(
        val status: Int,
        val type: String,
        val body: ByteArray,
        val policy: String = NOTHING_POLICY,
        val cache: Boolean = false,
        val location: String? = null,
    ) {
        val text: String get() = String(body, Charsets.UTF_8)

        companion object {
            fun status(status: Int) = Reply(status, "text/plain; charset=utf-8", ByteArray(0))

            fun json(status: Int, body: JsonElement) = Reply(status, JSON, body.toString().toByteArray(Charsets.UTF_8))

            fun redirect(location: String) = Reply(302, "text/plain; charset=utf-8", ByteArray(0), location = location)
        }
    }

    companion object {
        /**
         * Beside the phone remote's 8484: editing can go on for a while, and the phone remote's
         * phones keep its address, so neither takes the other's port.
         */
        const val PORT = 8485

        const val PAGE_PATH = "/edit"
        const val API_PATH = "/edit/api/"
        const val LOGO_PATH = "/edit/logo/"

        /** The header every request after joining carries its token in. */
        const val SESSION_HEADER = "x-plasma-session"

        /** 15 minutes with nothing from a browser ends its session. */
        const val IDLE_MS = 15 * 60_000L

        /** A few browsers at once: a computer and a phone, say. */
        const val MAX_SESSIONS = 4

        /** Wrong PINs one session may type, whatever the wait between them. */
        const val MAX_WRONG_PINS = 10

        private const val PIN_DIGITS = 4
        private const val MAX_NAME = 60
        private const val MAX_TITLES = 200
        private const val MAX_IDS = 50_000
        private const val TOKEN_BYTES = 32
        private const val MEDIA_BYTES = 18
        private const val ENDED_KEPT = 32
        private const val TICK_MS = 15_000L
        private const val BACKLOG = 16
        private const val MAX_REQUESTS = 24
        private const val READ_TIMEOUT_MS = 10_000
        private const val REQUEST_DEADLINE_MS = 60_000L

        /** A favorites order of thousands of channels fits: far less than a phone's photo. */
        private const val MAX_BODY_BYTES = 2 * 1024 * 1024

        /** Logos come in bursts as a list scrolls, so the limit is generous. */
        private const val RATE_LIMIT = 80
        private const val RATE_WINDOW_MS = 1_000L
        private const val GZIP_FROM = 1024

        private const val HTML = "text/html; charset=utf-8"
        internal const val JSON = "application/json; charset=utf-8"
        private const val NOTHING_POLICY = "default-src 'none'; frame-ancestors 'none'"
        private const val IMAGE_POLICY = "default-src 'none'"

        private val json = Json {
            encodeDefaults = false
            explicitNulls = false
        }

        /** A token as the TV keeps it: SHA-256, which is enough for 256 random bits. */
        fun hash(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

        private fun JsonObject.flag(name: String): Boolean? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

        /** The strings in the array [name], at most [MAX_IDS] of them; anything else in it is left out. */
        private fun JsonObject.ids(name: String): List<String> =
            (this[name] as? JsonArray).orEmpty().asSequence().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }.take(MAX_IDS).toList()
    }
}

/** Edit in a Browser's page: its HTML, with the TV's words in it, and its other files. */
interface EditorPage {
    fun html(): String

    /** One of the page's files by its name under `/edit/` ("editor.js", "fonts/…"); null for none. */
    fun file(name: String): EditorFile?
}

/** A file of the page: its bytes and type, and whether a browser may keep it a while. */
class EditorFile(val bytes: ByteArray, val type: String, val cache: Boolean = false)
