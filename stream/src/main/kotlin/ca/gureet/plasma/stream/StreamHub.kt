// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import okhttp3.OkHttpClient
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.StateFlow

data class HubConfig(
    /** The memory kept per upstream (brief 5.4.2). */
    val ringBytes: Int = 8 * 1024 * 1024,
    /** The rewind buffer as Settings has it now (brief 5.5), read as each upstream opens and pauses. */
    val rewind: () -> RewindSettings = { RewindSettings.Off },
    /** How long to wait for data before treating the source as failed (Connection Timeout, 5.4.8). */
    val noDataTimeoutMs: Long = 15_000,
    /** Reconnect at once, then after 1, 2 and 4 s (5.4.8). */
    val reconnectDelaysMs: List<Long> = listOf(0, 1_000, 2_000, 4_000),
    /** After this much data, a dropped connection counts as a new problem with fresh retries. */
    val healthyBytes: Long = 1_000_000,
    /** The wall clock, for when the rewind buffer's segments came in. */
    val clock: () -> Long = System::currentTimeMillis,
)

/**
 * Settings › Rewind and Local Catch-up's rewind buffer (brief 5.5, 4.31): [mode], how long it keeps
 * ([keepMs]) and the space it may take ([maxBytes]), in [folder].
 */
data class RewindSettings(val mode: RewindMode, val keepMs: Long, val maxBytes: Long, val folder: java.io.File?) {
    companion object {
        val Off = RewindSettings(RewindMode.Off, 0, 0, null)
    }
}

/** Rewind Buffer (brief 5.5): Off, When I Pause (the default, nothing written until then) or Always. */
enum class RewindMode { Off, WhenPaused, Always }

/** Opening one more stream would go over the account's connections (brief 5.1, Connection budget). */
class ConnectionLimitReached(val budgetKey: String, val maxConnections: Int) :
    IOException("All $maxConnections connections of this account are in use")

/**
 * A stream the hub serves on [url], until [close]. Sessions for the same channel
 * ([UpstreamRequest.shareKey]) share one upstream connection, so watching and recording a
 * channel use one connection (brief 5.4.2).
 */
class HubSession internal constructor(
    val token: String,
    /** `http://127.0.0.1:<port>/live/<token>.ts`: the only URL the engine ever sees. */
    val url: String,
    internal val upstream: Upstream,
    private val onClose: (HubSession) -> Unit,
) : AutoCloseable {
    @Volatile var isClosed = false
        private set

    internal val sockets: MutableSet<Socket> = Collections.newSetFromMap(ConcurrentHashMap())

    val state: StateFlow<UpstreamState> get() = upstream.state

    /** Bytes from the provider so far: the hub's view of whether data arrives (brief 5.1). */
    val bytesReceived: Long get() = upstream.bytesReceived

    /** Where the engine has read to, in the stream's bytes: what it shows, give or take its own buffer. */
    @Volatile var served: Long = 0
        internal set

    /**
     * Paused (brief 5.5): the stream goes on into the rewind buffer from what memory still holds,
     * so playing on carries on from the byte the engine stopped at. False when there's no buffer.
     */
    fun hold(): Boolean = upstream.hold()

    /** Whether Settings has a rewind buffer for [hold] to start. */
    val canHold: Boolean get() = upstream.canHold

    /** The rewind buffer while there is one: what it holds, and where a time is in it. */
    val rewind: Rewind? get() = upstream.spill?.let(::Rewind)

    /**
     * When the oldest moment the stream can be rewound to came in (brief 5.5): the rewind buffer's,
     * or memory's until a pause starts it. Null when Settings has no buffer.
     */
    val rewindSince: Long? get() = upstream.rewindSince

    /**
     * Reads the stream here, in this process, as a recording does (brief 5.6): from the start of
     * what's held when nobody's read it yet, otherwise from the latest PAT, as a new engine would.
     */
    fun reader(): HubReader {
        val start = if (upstream.readersServed.getAndIncrement() == 0) 0 else upstream.ring.latestPat() ?: upstream.ring.written
        return HubReader(this, upstream.ring.cursor(start))
    }

    /** The stream from [position] on, keyframe and all, for an engine to open after a seek (brief 5.5). */
    fun urlAt(position: Long): String = "$url?at=$position"

    /** The live edge again, for Go Live. */
    val liveUrl: String get() = "$url?at=live"

    override fun close() {
        if (isClosed) return
        isClosed = true
        sockets.forEach { runCatching { it.close() } }
        onClose(this)
    }
}

/** A session's stream read in this process ([HubSession.reader]). */
class HubReader internal constructor(private val session: HubSession, private val cursor: Ring.Cursor) {
    /**
     * Copies what's come into [into], waiting up to [waitMs] for something to. The count, 0 when
     * nothing came in time, or −1 once the stream has ended or the session is closed.
     */
    fun read(into: ByteArray, waitMs: Long): Int = if (session.isClosed) -1 else cursor.read(into, waitMs)
}

/** A stream Plasma reads from its own storage, as local catch-up is (brief 5.5). */
interface LocalReader : AutoCloseable {
    /**
     * Copies what's there into [into], waiting up to [waitMs] for something to be. The count, 0
     * when nothing came in time, or −1 at the end.
     */
    fun read(into: ByteArray, waitMs: Long): Int
}

/**
 * A stream from Plasma's own storage ([StreamHub.serve]) that the hub serves on [url], until
 * [close]. Each request reads it afresh from [open], from the start or the byte [urlAt] says.
 */
class LocalSession internal constructor(
    val token: String,
    /** `http://127.0.0.1:<port>/local/<token>.ts`. */
    val url: String,
    internal val open: (at: Long?) -> LocalReader?,
    private val onClose: (LocalSession) -> Unit,
) : AutoCloseable {
    /** Where to open it from byte [position] on, as chase play seeks (brief 5.6). */
    fun urlAt(position: Long): String = "$url?at=$position"

    @Volatile var isClosed = false
        private set

    internal val sockets: MutableSet<Socket> = Collections.newSetFromMap(ConcurrentHashMap())

    override fun close() {
        if (isClosed) return
        isClosed = true
        sockets.forEach { runCatching { it.close() } }
        onClose(this)
    }
}

/**
 * A file Plasma reads from elsewhere, from any point, as a recording on the network folder is
 * (brief 5.6): its [length], and its bytes from an offset.
 */
interface RangeSource {
    val length: Long

    /** Its bytes from [offset] on, until the stream's closed. */
    fun open(offset: Long): InputStream
}

/**
 * A file ([StreamHub.serveFile]) the hub serves on [url] with byte ranges, so every engine can
 * seek in it, until [close].
 */
class FileSession internal constructor(
    val token: String,
    /** `http://127.0.0.1:<port>/file/<token>.ts`. */
    val url: String,
    internal val source: RangeSource,
    private val onClose: (FileSession) -> Unit,
) : AutoCloseable {
    @Volatile var isClosed = false
        private set

    internal val sockets: MutableSet<Socket> = Collections.newSetFromMap(ConcurrentHashMap())

    override fun close() {
        if (isClosed) return
        isClosed = true
        sockets.forEach { runCatching { it.close() } }
        onClose(this)
    }
}

/** What a request's `Range` asks for of a file (RFC 9110, 14.2): all of it, a part, or nothing it has. */
sealed interface ByteRange {
    data object Whole : ByteRange

    data class Part(val first: Long, val last: Long) : ByteRange

    data object Unsatisfiable : ByteRange

    companion object {
        /**
         * [header] for a file of [length] bytes: `bytes=N-`, `bytes=N-M` or `bytes=-K`, the first
         * of several. A header it can't read is ignored, as the RFC says.
         */
        fun of(header: String?, length: Long): ByteRange {
            val spec = header?.trim()?.takeIf { it.startsWith("bytes=", ignoreCase = true) }?.substring(BYTES.length)
                ?.substringBefore(',')?.trim() ?: return Whole
            val start = spec.substringBefore('-', "").trim()
            val end = spec.substringAfter('-', "").trim()
            if ('-' !in spec) return Whole
            if (start.isEmpty()) {
                // The last K bytes.
                val suffix = end.toLongOrNull() ?: return Whole
                return if (suffix <= 0 || length == 0L) Unsatisfiable else Part(maxOf(0, length - suffix), length - 1)
            }
            val first = start.toLongOrNull() ?: return Whole
            val last = if (end.isEmpty()) length - 1 else end.toLongOrNull()?.let { minOf(it, length - 1) } ?: return Whole
            return when {
                first >= length -> Unsatisfiable
                last < first -> Whole
                else -> Part(first, last)
            }
        }

        private const val BYTES = "bytes="
    }
}

/** The rewind buffer as a session sees it (brief 5.5). */
class Rewind internal constructor(private val spill: Spill) {
    /** When the oldest moment it holds came in; null while it's empty. */
    val since: Long? get() = spill.oldestTime

    /** Where to open the stream for [time]: the keyframe at or before it, or the oldest held. */
    fun positionAt(time: Long): Long? = spill.positionAt(time)

    /** When the byte at [position] came in; null when it isn't held. */
    fun timeAt(position: Long): Long? = spill.timeAt(position)

    /** Whether it has started letting its oldest moments go, as it does once it holds all it may. */
    val full: Boolean get() = spill.full
}

/**
 * The stream hub (brief 5.4.2): Plasma opens live MPEG-TS streams itself, with the source's
 * headers, and serves them to the engine over 127.0.0.1. It listens on loopback only, on a random
 * port, and each session's path carries an unguessable token (brief 6.10).
 */
class StreamHub(http: OkHttpClient, private val config: HubConfig = HubConfig()) : AutoCloseable {
    private val client = http.newBuilder()
        .readTimeout(config.noDataTimeoutMs, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val server = ServerSocket(0, BACKLOG, LOOPBACK)
    private val sessions = ConcurrentHashMap<String, HubSession>()
    private val locals = ConcurrentHashMap<String, LocalSession>()
    private val files = ConcurrentHashMap<String, FileSession>()
    private val upstreams = HashMap<String, Upstream>()
    private val random = SecureRandom()
    @Volatile private var closed = false

    val port: Int get() = server.localPort

    /** Where it listens: [LOOPBACK], the address in every URL it hands out. */
    internal val listeningOn: InetAddress get() = server.inetAddress

    init {
        Thread(::accept, "plasma-hub-server").apply { isDaemon = true }.start()
    }

    /**
     * Opens [request] (or joins the upstream already open for it) and returns a session to hand
     * the engine. Throws [ConnectionLimitReached] when a new connection would break the budget.
     */
    @Synchronized
    fun open(request: UpstreamRequest): HubSession {
        check(!closed) { "The stream hub is closed" }
        val shared = upstreams[request.shareKey]?.takeIf { it.isAlive }
        val upstream = shared ?: newUpstream(request)
        // Joining what a recording or local catch-up reads: watching starts its rewind buffer (5.5).
        if (shared != null && request.viewing) shared.watched()
        upstream.retain()
        val token = newToken()
        val session = HubSession(token, "http://$HOST:$port/live/$token.ts", upstream, ::onSessionClosed)
        sessions[token] = session
        return session
    }

    /**
     * Serves what [open] reads, as an engine plays local catch-up (brief 5.5) or a recording still
     * being written (5.6): it opens [LocalSession.url], and each request reads afresh, from the
     * byte its `?at=` says ([LocalSession.urlAt]) or null for the start. Null from [open] answers
     * Not Found.
     */
    fun serve(open: (at: Long?) -> LocalReader?): LocalSession {
        check(!closed) { "The stream hub is closed" }
        val token = newToken()
        return LocalSession(token, "http://$HOST:$port/local/$token.ts", open) { locals.remove(it.token) }
            .also { locals[token] = it }
    }

    /**
     * Serves [source] with byte ranges, as an engine plays a recording from the network folder
     * (brief 5.6): it opens [FileSession.url], and seeks with `Range`.
     */
    fun serveFile(source: RangeSource): FileSession {
        check(!closed) { "The stream hub is closed" }
        val token = newToken()
        return FileSession(token, "http://$HOST:$port/file/$token.ts", source) { files.remove(it.token) }
            .also { files[token] = it }
    }

    /** The space the rewind buffers take now (Settings › Rewind and Local Catch-up's Storage, 4.31). */
    @Synchronized
    fun rewindBytes(): Long = upstreams.values.sumOf { it.spill?.size ?: 0 }

    /** Upstream connections open now for [budgetKey], and those held outside the hub ([reserve]). */
    @Synchronized
    fun connectionsInUse(budgetKey: String): Int =
        upstreams.values.count { it.isAlive && it.request.budget?.key == budgetKey } + (reserved[budgetKey] ?: 0)

    /** Connections held outside the hub, by budget key. */
    private val reserved = HashMap<String, Int>()

    /**
     * Counts one of [budget]'s connections as in use until the answer is closed, for a stream the
     * hub doesn't hold, as an HLS channel being recorded is (brief 5.6). Throws
     * [ConnectionLimitReached] when there's none left.
     */
    @Synchronized
    fun reserve(budget: ConnectionBudget): AutoCloseable {
        if (connectionsInUse(budget.key) >= budget.maxConnections) throw ConnectionLimitReached(budget.key, budget.maxConnections)
        reserved[budget.key] = (reserved[budget.key] ?: 0) + 1
        var released = false
        return AutoCloseable {
            synchronized(this) {
                if (!released) {
                    released = true
                    reserved[budget.key] = ((reserved[budget.key] ?: 1) - 1).coerceAtLeast(0)
                }
            }
        }
    }

    private fun newUpstream(request: UpstreamRequest): Upstream {
        val budget = request.budget
        if (budget != null && connectionsInUse(budget.key) >= budget.maxConnections) {
            throw ConnectionLimitReached(budget.key, budget.maxConnections)
        }
        return Upstream(request, client, config, newToken()).also {
            upstreams[request.shareKey] = it
            it.start()
        }
    }

    @Synchronized
    private fun onSessionClosed(session: HubSession) {
        sessions.remove(session.token)
        session.upstream.release()
        upstreams.entries.removeAll { (_, upstream) -> !upstream.isAlive }
    }

    override fun close() {
        closed = true
        sessions.values.toList().forEach { it.close() }
        locals.values.toList().forEach { it.close() }
        files.values.toList().forEach { it.close() }
        synchronized(this) { upstreams.values.forEach { it.close() } }
        runCatching { server.close() }
    }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun accept() {
        while (!closed) {
            val socket = try {
                server.accept()
            } catch (error: SocketException) {
                if (closed) return else continue
            }
            Thread({ serve(socket) }, "plasma-hub-reader").apply { isDaemon = true }.start()
        }
    }

    /** Serves one engine connection: a GET for a session's path, answered with the stream. */
    private fun serve(socket: Socket) {
        socket.use { handle(it) }
    }

    private fun handle(socket: Socket) {
        try {
            val out = socket.getOutputStream()
            socket.soTimeout = REQUEST_TIMEOUT_MS
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val requestLine = input.readLine() ?: return
            var range: String? = null
            while (true) {
                val header = input.readLine() ?: return
                if (header.isEmpty()) break
                if (header.startsWith(RANGE, ignoreCase = true)) range = header.substring(RANGE.length)
            }
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0)
            if (method != "GET" && method != "HEAD") return answer(out, 405, "Method Not Allowed")
            val target = parts.getOrNull(1).orEmpty()
            if (target.startsWith(LOCAL)) return serveLocal(socket, out, method == "HEAD", target)
            if (target.startsWith(FILE)) return serveFile(socket, out, method == "HEAD", target, range)
            val token = target.substringBefore('?').removePrefix("/live/").substringBefore('.')
            val at = target.substringAfter("?at=", "").takeIf { it.isNotEmpty() }
            val session = sessions[token]?.takeUnless { it.isClosed } ?: return answer(out, 404, "Not Found")
            out.write(STREAM_HEADERS)
            out.flush()
            if (method == "HEAD") return
            socket.soTimeout = 0
            session.sockets += socket
            val upstream = session.upstream
            val start = when {
                at == "live" -> upstream.ring.latestPat() ?: upstream.ring.written
                at != null -> {
                    // After a seek: the stream's tables first, so the engine knows what it reads (5.5).
                    upstream.scanner.tables()?.let { out.write(it) }
                    at.toLongOrNull() ?: upstream.ring.written
                }
                upstream.readersServed.getAndIncrement() == 0 -> 0
                else -> upstream.ring.latestPat() ?: upstream.ring.written
            }
            val cursor = upstream.ring.cursor(start)
            val chunk = ByteArray(CHUNK)
            while (!session.isClosed) {
                val count = cursor.read(chunk, WAIT_MS)
                if (count < 0) break
                if (count > 0) {
                    out.write(chunk, 0, count)
                    out.flush()
                    session.served = cursor.position
                }
            }
            session.sockets -= socket
        } catch (error: IOException) {
            // The engine hung up, or the session closed under it.
        }
    }

    /** A request for a [LocalSession]: what it reads, from the start, until it ends or the engine hangs up. */
    private fun serveLocal(socket: Socket, out: OutputStream, head: Boolean, target: String) {
        val token = target.substringBefore('?').removePrefix(LOCAL).substringBefore('.')
        val at = target.substringAfter("?at=", "").takeIf { it.isNotEmpty() }?.toLongOrNull()
        val session = locals[token]?.takeUnless { it.isClosed } ?: return answer(out, 404, "Not Found")
        val reader = (if (head) null else session.open(at)) ?: return if (head) {
            out.write(STREAM_HEADERS)
            out.flush()
        } else {
            answer(out, 404, "Not Found")
        }
        reader.use {
            out.write(STREAM_HEADERS)
            out.flush()
            socket.soTimeout = 0
            session.sockets += socket
            val chunk = ByteArray(CHUNK)
            while (!session.isClosed) {
                val count = reader.read(chunk, WAIT_MS)
                if (count < 0) break
                if (count > 0) {
                    out.write(chunk, 0, count)
                    out.flush()
                }
            }
            session.sockets -= socket
        }
    }

    /** A request for a [FileSession]: the part of the file it asks for, or all of it. */
    private fun serveFile(socket: Socket, out: OutputStream, head: Boolean, target: String, range: String?) {
        val token = target.substringBefore('?').removePrefix(FILE).substringBefore('.')
        val session = files[token]?.takeUnless { it.isClosed } ?: return answer(out, 404, "Not Found")
        val length = session.source.length
        val asked = ByteRange.of(range, length)
        val (first, last) = when (asked) {
            ByteRange.Whole -> 0L to length - 1
            is ByteRange.Part -> asked.first to asked.last
            ByteRange.Unsatisfiable -> {
                out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$length\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                out.flush()
                return
            }
        }
        val count = (last - first + 1).coerceAtLeast(0)
        val partial = asked is ByteRange.Part
        val headers = buildString {
            append("HTTP/1.1 ").append(if (partial) "206 Partial Content" else "200 OK").append("\r\n")
            append("Content-Type: video/mp2t\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Content-Length: ").append(count).append("\r\n")
            if (partial) append("Content-Range: bytes ").append(first).append('-').append(last).append('/').append(length).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.ISO_8859_1))
        out.flush()
        if (head || count == 0L) return
        socket.soTimeout = 0
        session.sockets += socket
        try {
            session.source.open(first).use { input ->
                val chunk = ByteArray(CHUNK)
                var left = count
                while (left > 0 && !session.isClosed) {
                    val read = input.read(chunk, 0, minOf(chunk.size.toLong(), left).toInt())
                    if (read < 0) break
                    out.write(chunk, 0, read)
                    left -= read
                }
                out.flush()
            }
        } finally {
            session.sockets -= socket
        }
    }

    private fun answer(out: OutputStream, code: Int, reason: String) {
        out.write("HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    internal companion object {
        /**
         * 127.0.0.1, where the hub listens and sends the engines. Not Java's own loopback address:
         * on Android, InetAddress.getLoopbackAddress() is ::1, which an engine sent to 127.0.0.1
         * can't reach, so no live channel played on a real TV while every test here passed.
         */
        const val HOST = "127.0.0.1"
        val LOOPBACK: InetAddress = InetAddress.getByAddress(HOST, byteArrayOf(127, 0, 0, 1))

        const val BACKLOG = 16
        const val TOKEN_BYTES = 18
        const val REQUEST_TIMEOUT_MS = 10_000
        const val WAIT_MS = 500L
        const val CHUNK = 64 * 1024
        const val LOCAL = "/local/"
        const val FILE = "/file/"
        const val RANGE = "Range:"

        val STREAM_HEADERS = (
            "HTTP/1.1 200 OK\r\n" +
                "Content-Type: video/mp2t\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray(Charsets.ISO_8859_1)
    }
}
