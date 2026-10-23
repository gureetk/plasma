// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import okhttp3.OkHttpClient
import java.io.IOException
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
    /** How long to wait for data before treating the source as failed (Connection Timeout, 5.4.8). */
    val noDataTimeoutMs: Long = 15_000,
    /** Reconnect at once, then after 1, 2 and 4 s (5.4.8). */
    val reconnectDelaysMs: List<Long> = listOf(0, 1_000, 2_000, 4_000),
    /** After this much data, a dropped connection counts as a new problem with fresh retries. */
    val healthyBytes: Long = 1_000_000,
)

/** Opening one more stream would go over the account's connections (brief 5.1, Connection budget). */
class ConnectionLimitReached(val budgetKey: String, val maxConnections: Int) :
    IOException("All $maxConnections connections of this account are in use")

/**
 * A stream the hub serves on [url], until [close]. Sessions for the same provider URL share one
 * upstream connection, so watching and recording a channel use one connection (brief 5.4.2).
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

    override fun close() {
        if (isClosed) return
        isClosed = true
        sockets.forEach { runCatching { it.close() } }
        onClose(this)
    }
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
    private val server = ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress())
    private val sessions = ConcurrentHashMap<String, HubSession>()
    private val upstreams = HashMap<String, Upstream>()
    private val random = SecureRandom()
    @Volatile private var closed = false

    val port: Int get() = server.localPort

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
        val upstream = upstreams[request.url]?.takeIf { it.isAlive } ?: newUpstream(request)
        upstream.retain()
        val token = newToken()
        val session = HubSession(token, "http://127.0.0.1:$port/live/$token.ts", upstream, ::onSessionClosed)
        sessions[token] = session
        return session
    }

    /** Upstream connections open now for [budgetKey]. */
    @Synchronized
    fun connectionsInUse(budgetKey: String): Int = upstreams.values.count { it.isAlive && it.request.budget?.key == budgetKey }

    private fun newUpstream(request: UpstreamRequest): Upstream {
        val budget = request.budget
        if (budget != null && connectionsInUse(budget.key) >= budget.maxConnections) {
            throw ConnectionLimitReached(budget.key, budget.maxConnections)
        }
        return Upstream(request, client, config).also {
            upstreams[request.url] = it
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
            while (true) {
                val header = input.readLine() ?: return
                if (header.isEmpty()) break
            }
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0)
            if (method != "GET" && method != "HEAD") return answer(out, 405, "Method Not Allowed")
            val token = parts.getOrNull(1).orEmpty().substringBefore('?').removePrefix("/live/").substringBefore('.')
            val session = sessions[token]?.takeUnless { it.isClosed } ?: return answer(out, 404, "Not Found")
            out.write(STREAM_HEADERS)
            out.flush()
            if (method == "HEAD") return
            socket.soTimeout = 0
            session.sockets += socket
            val upstream = session.upstream
            val start = if (upstream.readersServed.getAndIncrement() == 0) 0 else upstream.ring.latestPat() ?: upstream.ring.written
            val cursor = upstream.ring.cursor(start)
            val chunk = ByteArray(CHUNK)
            while (!session.isClosed) {
                val count = cursor.read(chunk, WAIT_MS)
                if (count < 0) break
                if (count > 0) {
                    out.write(chunk, 0, count)
                    out.flush()
                }
            }
            session.sockets -= socket
        } catch (error: IOException) {
            // The engine hung up, or the session closed under it.
        }
    }

    private fun answer(out: OutputStream, code: Int, reason: String) {
        out.write("HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private companion object {
        const val BACKLOG = 16
        const val TOKEN_BYTES = 18
        const val REQUEST_TIMEOUT_MS = 10_000
        const val WAIT_MS = 500L
        const val CHUNK = 64 * 1024

        val STREAM_HEADERS = (
            "HTTP/1.1 200 OK\r\n" +
                "Content-Type: video/mp2t\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray(Charsets.ISO_8859_1)
    }
}
