// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.remote

import ca.gureet.plasma.pairing.Http
import ca.gureet.plasma.pairing.HttpRequest
import ca.gureet.plasma.pairing.PairingInvite
import ca.gureet.plasma.pairing.PairingServer
import ca.gureet.plasma.pairing.QrCodes
import ca.gureet.plasma.pairing.TooLarge
import ca.gureet.plasma.pairing.isHomeNetwork
import ca.gureet.plasma.remote.protocol.RemoteKey
import ca.gureet.plasma.remote.protocol.RemoteLists
import ca.gureet.plasma.remote.protocol.RemoteProtocol
import ca.gureet.plasma.remote.protocol.RemoteState
import ca.gureet.plasma.remote.protocol.TvMessages
import java.io.Closeable
import java.io.IOException
import java.net.BindException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** What a paired phone asks of the TV (brief 5.21), once it's let in. */
interface RemoteHandler {
    /** A key, pressed ([down]) or let go: long-press OK and a held D-pad repeat as on the remote. */
    fun key(key: RemoteKey, down: Boolean)

    /** The whole text of the focused field, as typed on the phone. */
    fun text(text: String) {}

    /** Done, on the phone's keyboard. */
    fun submit() {}

    /** A channel tapped on the Channels tab, in [listId], the list it was tapped in, if the phone says. */
    fun tune(channelId: String, listId: String?) {}

    /** Go Live, on the now-playing card: back to the live edge from the rewind buffer. */
    fun goLive() {}

    /** The profile's lists, with as many of [listId]'s channels as fit, from [from]; null for the first list. */
    fun lists(listId: String?, from: Int): RemoteLists? = null

    /** A channel's logo, which the TV fetches so phones never ask the provider: its bytes and type. */
    fun logo(channelId: String): Pair<ByteArray, String>? = null

    /** A paired phone went away: a key it was holding is let go. */
    fun disconnected() {}
}

/** A phone paired as a remote (brief 5.21): its name, and its token, kept only as a hash. */
data class PairedPhone(val id: String, val name: String, val tokenHash: String, val pairedAt: Long, val lastSeen: Long)

/** Where the paired phones are kept, on the TV only. */
interface PhoneStore {
    suspend fun phones(): List<PairedPhone>

    suspend fun save(phone: PairedPhone)

    suspend fun remove(id: String)
}

/** The TV as phones see it (brief 5.21): its id, made when Plasma was installed, its name, and Plasma's version. */
data class RemoteTv(val id: String, val name: String, val version: String)

/**
 * The phone remote's server (brief 5.21): the web page at `http://<tv>:8484/remote`, and a
 * WebSocket at `/remote/ws` that paired phones send keys, text and channels through, and get
 * what the TV shows back.
 *
 * Only phones on the home network get an answer, and only the TV's own page may open the
 * WebSocket (its Origin is checked). A phone pairs with the code the TV shows, good for one phone
 * and 10 minutes, 5 wrong tries ending it; then it gets a random 256-bit token of its own, which
 * the TV keeps only as a hash, and Forget revokes. A phone that floods the TV is cut off.
 */
class RemoteServer(
    private val tv: RemoteTv,
    private val store: PhoneStore,
    private val handler: RemoteHandler,
    private val page: RemotePage,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val allows: (InetAddress) -> Boolean = ::isHomeNetwork,
    /** The name a phone gets when it doesn't give one, in the TV's language ("Phone"). */
    private val unnamed: String,
    /** A phone just paired: the TV says "Maya's iPhone is now a remote". */
    private val onPaired: (PairedPhone) -> Unit = {},
) : Closeable {
    private val _invite = MutableStateFlow<PairingInvite?>(null)

    /** What Settings › Remote & Buttons › Phone Remote shows: the address, the code and its QR code. */
    val invite: StateFlow<PairingInvite?> = _invite.asStateFlow()

    private val _connected = MutableStateFlow<Set<String>>(emptySet())

    /** The paired phones connected now, by id. */
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    private var server: ServerSocket? = null
    private var host = ""
    private var code: Code? = null
    private var timer: Job? = null
    private val sessions = CopyOnWriteArraySet<Session>()
    private val connections = AtomicInteger()

    @Volatile private var closed = false

    @Volatile private var state = RemoteState()

    private class Code(val text: String, val expiresAt: Long) {
        var attempts = 0
    }

    /** A phone's connection, and the phone once it's let in. */
    private inner class Session(val socket: Socket, val ws: WebSocket) {
        @Volatile var phone: PairedPhone? = null

        /** A phone on a protocol the TV doesn't speak gets its welcome, then nothing more. */
        @Volatile var speaks = true
        private var windowStart = 0L
        private var inWindow = 0

        /** Whether the phone has sent too much, too fast. */
        fun flooding(): Boolean {
            val at = now()
            if (at - windowStart >= RATE_WINDOW_MS) {
                windowStart = at
                inWindow = 0
            }
            return ++inWindow > RATE_LIMIT
        }

        fun send(text: String) {
            try {
                ws.send(text)
            } catch (gone: IOException) {
                runCatching { socket.close() }
            }
        }
    }

    /**
     * Starts answering at [address], on port 8484 or, when something else has it, another free
     * port. A new code comes every 10 minutes, kept in [scope].
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
        synchronized(this) { newCode() }
        Thread(::accept, "plasma-remote").apply { isDaemon = true }.start()
        timer = scope.launch {
            while (true) {
                val wait = synchronized(this@RemoteServer) { code?.expiresAt?.minus(now()) } ?: return@launch
                delay(wait.coerceAtLeast(0) + 1)
                synchronized(this@RemoteServer) { expireIfDue() }
            }
        }
    }

    /** What the TV shows now goes to every phone. */
    fun publish(state: RemoteState) {
        this.state = state
        val message = TvMessages.state(state)
        sessions.filter { it.phone != null }.forEach { it.send(message) }
    }

    /** Forget: the phone's token stops working, and it's let go at once. */
    fun forget(phoneId: String) {
        runBlocking { store.remove(phoneId) }
        sessions.filter { it.phone?.id == phoneId }.forEach { runCatching { it.socket.close() } }
        updateConnected()
    }

    override fun close() {
        closed = true
        timer?.cancel()
        runCatching { server?.close() }
        sessions.forEach { runCatching { it.socket.close() } }
        sessions.clear()
        synchronized(this) { code = null }
        _invite.value = null
        _connected.value = emptySet()
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
            if (connections.incrementAndGet() > MAX_CONNECTIONS) {
                connections.decrementAndGet()
                runCatching { client.close() }
                continue
            }
            Thread({
                try {
                    serve(client)
                } finally {
                    connections.decrementAndGet()
                }
            }, "plasma-remote-connection").apply { isDaemon = true }.start()
        }
    }

    private fun serve(socket: Socket) {
        socket.use {
            try {
                socket.soTimeout = READ_TIMEOUT_MS
                val out = socket.getOutputStream()
                if (!allows(socket.inetAddress)) return Http.respond(out, 403, null)
                val request = try {
                    Http.read(socket.getInputStream()) ?: return
                } catch (big: TooLarge) {
                    return Http.respond(out, 413, null)
                }
                if (request.method != "GET") return Http.respond(out, 405, null)
                when {
                    request.path == WS_PATH -> upgrade(socket, request)
                    request.path == PAGE_PATH || request.path == "$PAGE_PATH/" ->
                        Http.respondWith(out, 200, HTML, page.html().toByteArray(Charsets.UTF_8), pagePolicy())
                    request.path.startsWith(LOGO_PATH) -> {
                        // The id as the page sent it, percent-encoded; a "+" in it is a "+".
                        val channelId = URLDecoder.decode(request.path.removePrefix(LOGO_PATH).replace("+", "%2B"), Charsets.UTF_8)
                        val logo = handler.logo(channelId)
                        if (logo == null) Http.respond(out, 404, null) else Http.respondWith(out, 200, logo.second, logo.first, IMAGE_POLICY, cache = true)
                    }
                    request.path.startsWith("$PAGE_PATH/") -> {
                        val file = page.file(request.path.removePrefix("$PAGE_PATH/"))
                        if (file == null) Http.respond(out, 404, null) else Http.respondWith(out, 200, file.type, file.bytes, pagePolicy(), cache = file.cache)
                    }
                    else -> Http.respond(out, 404, null)
                }
            } catch (error: IOException) {
                // The phone went away.
            }
        }
    }

    /** The page may run its own script, load its fonts and the TV's logos, and talk to this WebSocket, and nothing else. */
    private fun pagePolicy(): String =
        "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; img-src 'self'; font-src 'self'; manifest-src 'self'; " +
            "connect-src ws://$host; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

    private fun upgrade(socket: Socket, request: HttpRequest) {
        val out = socket.getOutputStream()
        val key = request.headers["sec-websocket-key"]
        val upgrade = request.headers["upgrade"]?.equals("websocket", ignoreCase = true) == true
        // Only the TV's own page may open it: a page from anywhere else can't drive the TV.
        if (key == null || !upgrade || request.headers["origin"] != "http://$host") return Http.respond(out, 403, null)
        out.write(WebSocket.handshake(key).toByteArray(Charsets.ISO_8859_1))
        out.flush()
        // A phone that stays connected sends a ping every 15 s.
        socket.soTimeout = IDLE_TIMEOUT_MS
        val session = Session(socket, WebSocket(socket.getInputStream(), out))
        sessions += session
        try {
            while (!closed) {
                val text = session.ws.read() ?: break
                if (session.flooding()) break
                val (type, fields) = RemoteProtocol.parse(text) ?: continue
                handle(session, type, fields)
            }
        } catch (error: IOException) {
            // Gone, or quiet for too long.
        } finally {
            sessions -= session
            session.ws.close()
            updateConnected()
            if (session.phone != null) handler.disconnected()
        }
    }

    private fun handle(session: Session, type: String, fields: Map<String, String?>) {
        when (type) {
            "hello" -> {
                session.send(TvMessages.welcome(tv.id, tv.name, tv.version))
                // The TV speaks its own protocol and the one before (5.21); the app says which to update.
                session.speaks = RemoteProtocol.speaks(fields["protocol"]?.toIntOrNull() ?: RemoteProtocol.VERSION)
                if (!session.speaks) session.send(TvMessages.error(RemoteProtocol.Reasons.VERSION))
            }
            "ping" -> session.send(TvMessages.pong())
            else -> if (session.speaks) handleSpoken(session, type, fields)
        }
    }

    private fun handleSpoken(session: Session, type: String, fields: Map<String, String?>) {
        when (type) {
            "pair" -> pair(session, fields["code"].orEmpty(), fields["name"].orEmpty())
            "auth" -> auth(session, fields["token"].orEmpty())
            else -> {
                if (session.phone == null) return session.send(TvMessages.error(RemoteProtocol.Reasons.AUTH))
                when (type) {
                    "key" -> {
                        val key = RemoteKey.of(fields["key"].orEmpty()) ?: return
                        handler.key(key, down = fields["action"] != "up")
                    }
                    "digits" -> fields["digits"].orEmpty().take(MAX_DIGITS).mapNotNull(RemoteKey::digit).forEach { key ->
                        handler.key(key, down = true)
                        handler.key(key, down = false)
                    }
                    "text" -> handler.text(fields["text"].orEmpty().take(MAX_TEXT))
                    "submit" -> handler.submit()
                    "tune" -> fields["channel"]?.let { handler.tune(it, fields["list"]) }
                    "live" -> handler.goLive()
                    "lists" -> handler.lists(fields["list"], fields["from"]?.toIntOrNull() ?: 0)?.let { session.send(TvMessages.lists(it)) }
                }
            }
        }
    }

    private fun pair(session: Session, typed: String, name: String) {
        val wrong = synchronized(this) { checkCode(typed) }
        if (wrong != null) return session.send(TvMessages.error(wrong))
        val token = ByteArray(TOKEN_BYTES).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val at = now()
        val phone = PairedPhone(UUID.randomUUID().toString(), name.trim().take(MAX_NAME).ifEmpty { unnamed }, hash(token), at, at)
        runBlocking { store.save(phone) }
        // One phone per code: the TV shows a new one.
        synchronized(this) { newCode() }
        session.phone = phone
        session.send(TvMessages.paired(token))
        session.send(TvMessages.state(state))
        updateConnected()
        onPaired(phone)
    }

    private fun auth(session: Session, token: String) {
        val hashed = hash(token)
        val phone = runBlocking { store.phones() }.firstOrNull { same(it.tokenHash, hashed) }
        if (phone == null) return session.send(TvMessages.error(RemoteProtocol.Reasons.AUTH))
        val seen = phone.copy(lastSeen = now())
        runBlocking { store.save(seen) }
        session.phone = seen
        session.send(TvMessages.state(state))
        updateConnected()
    }

    private fun updateConnected() {
        _connected.value = sessions.mapNotNullTo(HashSet()) { it.phone?.id }
    }

    /** Null when [typed] is the code; otherwise why it isn't, with the try counted. */
    private fun checkCode(typed: String): String? {
        expireIfDue()
        val current = code ?: return RemoteProtocol.Reasons.CODE
        val normalized = typed.uppercase().filter { it.isLetterOrDigit() }
        if (same(normalized, current.text)) return null
        current.attempts++
        if (current.attempts < MAX_ATTEMPTS) return RemoteProtocol.Reasons.CODE
        newCode()
        return RemoteProtocol.Reasons.TRIES
    }

    private fun expireIfDue() {
        val current = code ?: return
        if (now() >= current.expiresAt) newCode()
    }

    private fun newCode() {
        val text = String(CharArray(PairingServer.CODE_LENGTH) { PairingServer.CODE_ALPHABET[random.nextInt(PairingServer.CODE_ALPHABET.length)] })
        code = Code(text, now() + PairingServer.LIFETIME_MS)
        val url = "http://$host$PAGE_PATH?code=$text"
        _invite.value = PairingInvite(host, text, url, QrCodes.encode(url))
    }

    /** Compares in constant time, so how long a wrong guess takes says nothing. */
    private fun same(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    companion object {
        const val PORT = 8484
        const val PAGE_PATH = "/remote"
        const val WS_PATH = "/remote/ws"
        const val LOGO_PATH = "/remote/logo/"

        private const val MAX_ATTEMPTS = PairingServer.MAX_ATTEMPTS
        private const val TOKEN_BYTES = 32
        private const val MAX_NAME = 40
        private const val MAX_DIGITS = 4
        private const val MAX_TEXT = 200
        private const val BACKLOG = 8
        private const val MAX_CONNECTIONS = 16
        private const val READ_TIMEOUT_MS = 10_000
        private const val IDLE_TIMEOUT_MS = 45_000

        /** At most 30 messages a second per phone (5.21): a held key sends only its down and up. */
        private const val RATE_LIMIT = 30
        private const val RATE_WINDOW_MS = 1_000L

        private const val HTML = "text/html; charset=utf-8"
        private const val IMAGE_POLICY = "default-src 'none'"

        /** A token as the TV keeps it: SHA-256, which is enough for 256 random bits. */
        fun hash(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** The phone remote's web page (brief 4.33): its HTML, with the TV's words in it, and its other files. */
interface RemotePage {
    fun html(): String

    /** One of the page's files by its name under `/remote/` ("remote.js", "icon-192.png"); null for none. */
    fun file(name: String): RemoteFile?
}

/** A file of the page: its bytes and type, and whether a phone may keep it a while. */
class RemoteFile(val bytes: ByteArray, val type: String, val cache: Boolean = false)
