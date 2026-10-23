// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the hub asks a provider for. */
data class UpstreamRequest(
    /** The provider's stream URL. It carries credentials: the hub never logs or shows it. */
    val url: String,
    /** The source's user agent, referrer and extra headers (brief 5.1). */
    val headers: Map<String, String> = emptyMap(),
    /** Which account's connection limit this counts against, if it has one (brief 5.1). */
    val budget: ConnectionBudget? = null,
)

data class ConnectionBudget(val key: String, val maxConnections: Int)

/** Why an upstream stopped (brief 5.4.9). */
sealed interface StreamFailure {
    /** Whether trying again is pointless: the provider refused, or sent a web page. */
    val isFinal: Boolean get() = false

    /** 401 or 403: a wrong login, an expired account or too many connections. Shown, not retried. */
    data class Refused(val code: Int) : StreamFailure {
        override val isFinal get() = true
    }

    /** Another HTTP error. */
    data class Http(val code: Int) : StreamFailure

    /** A web page instead of video, as some providers send with status 200. */
    data object NotVideo : StreamFailure {
        override val isFinal get() = true
    }

    /**
     * An HLS or DASH playlist rather than a stream: an address that doesn't say so. The engine
     * plays it from the provider itself (brief 5.4.2).
     */
    data object Playlist : StreamFailure {
        override val isFinal get() = true
    }

    /** An address or header the hub can't send at all, such as `udp://`: the engine may play it itself. */
    data class Unusable(val detail: String) : StreamFailure {
        override val isFinal get() = true
    }

    /** Nothing arrived in time. */
    data object NoData : StreamFailure

    /** The server couldn't be reached, or the connection dropped. */
    data class Network(val detail: String) : StreamFailure

    /** The provider closed the stream. */
    data object Ended : StreamFailure
}

sealed interface UpstreamState {
    data object Connecting : UpstreamState

    data object Streaming : UpstreamState

    /** Trying again after [failure]: [attempt] counts from 1. */
    data class Reconnecting(val attempt: Int, val failure: StreamFailure) : UpstreamState

    /** Gave up: a source fault, which failover handles (brief 5.1). */
    data class Failed(val failure: StreamFailure) : UpstreamState

    data object Closed : UpstreamState
}

/**
 * One connection to a provider, copied into a [Ring] for every session that shares it. When the
 * stream drops or ends, it reconnects at once and then after 1, 2 and 4 s (brief 5.4.8).
 */
internal class Upstream(
    val request: UpstreamRequest,
    private val http: OkHttpClient,
    private val config: HubConfig,
) {
    val ring = Ring(config.ringBytes)
    private val _state = MutableStateFlow<UpstreamState>(UpstreamState.Connecting)
    val state: StateFlow<UpstreamState> = _state

    @Volatile var bytesReceived: Long = 0
        private set

    /** Readers served so far: the first starts at the beginning, later ones at the latest PAT. */
    val readersServed = AtomicInteger(0)

    @Volatile private var closed = false
    @Volatile private var call: Call? = null
    private val holders = AtomicInteger(0)
    private val thread = Thread(::run, "plasma-hub-upstream").apply { isDaemon = true }

    val isAlive: Boolean get() = _state.value !is UpstreamState.Failed && _state.value != UpstreamState.Closed

    fun start() = thread.start()

    fun retain() {
        holders.incrementAndGet()
    }

    /** Called as each session closes; the last one closes the upstream, freeing the provider connection. */
    fun release() {
        if (holders.decrementAndGet() <= 0) close()
    }

    fun close() {
        if (closed) return
        closed = true
        call?.cancel()
        ring.end()
        _state.value = UpstreamState.Closed
        thread.interrupt()
    }

    private fun run() {
        var attempt = 0
        while (!closed) {
            val before = bytesReceived
            val failure = try {
                stream()
            } catch (error: IOException) {
                if (closed) break
                classify(error)
            } catch (error: IllegalArgumentException) {
                // OkHttp turns down what isn't an http(s) address, and header values it can't send.
                StreamFailure.Unusable(error.javaClass.simpleName)
            } catch (error: RuntimeException) {
                if (closed) break
                StreamFailure.Network(error.javaClass.simpleName)
            }
            if (closed) break
            // A connection that delivered for a while earns a fresh set of retries.
            if (bytesReceived - before >= config.healthyBytes) attempt = 0
            if (failure.isFinal || attempt >= config.reconnectDelaysMs.size) {
                _state.value = UpstreamState.Failed(failure)
                ring.end()
                break
            }
            _state.value = UpstreamState.Reconnecting(attempt + 1, failure)
            try {
                Thread.sleep(config.reconnectDelaysMs[attempt])
            } catch (interrupted: InterruptedException) {
                break
            }
            attempt++
        }
    }

    /** Connects and copies the stream into the ring until it stops; returns why. */
    private fun stream(): StreamFailure {
        val builder = Request.Builder().url(request.url)
        // A playlist's own user agent may carry characters a header can't; those are left out.
        request.headers.forEach { (name, value) -> builder.header(name, value.filter { it in ' '..'~' }) }
        val call = http.newCall(builder.build())
        this.call = call
        if (closed) call.cancel()
        call.execute().use { response ->
            when {
                response.code == 401 || response.code == 403 -> return StreamFailure.Refused(response.code)
                !response.isSuccessful -> return StreamFailure.Http(response.code)
            }
            val type = response.header("Content-Type").orEmpty().lowercase()
            if ("mpegurl" in type || "dash+xml" in type) return StreamFailure.Playlist
            if (type.startsWith("text/html") || type.startsWith("application/json")) return StreamFailure.NotVideo
            val source = response.body.source()
            val chunk = ByteArray(CHUNK)
            var first = true
            while (!closed) {
                val count = source.read(chunk)
                if (count == -1) return StreamFailure.Ended
                if (count == 0) continue
                if (first) {
                    if (startsWith(chunk, count, HLS_TAG)) return StreamFailure.Playlist
                    if (looksLikeAPage(chunk, count)) return StreamFailure.NotVideo
                    first = false
                    _state.value = UpstreamState.Streaming
                }
                // Counted before readers can see it, so the count never lags what they've read.
                bytesReceived += count
                ring.write(chunk, 0, count)
            }
            return StreamFailure.Ended
        }
    }

    private fun classify(error: IOException): StreamFailure = when (error) {
        is SocketTimeoutException -> StreamFailure.NoData
        is UnknownHostException, is ConnectException -> StreamFailure.Network("Can't reach the server")
        is InterruptedIOException -> StreamFailure.NoData
        else -> StreamFailure.Network(error.javaClass.simpleName)
    }

    private fun startsWith(bytes: ByteArray, count: Int, prefix: ByteArray): Boolean =
        count >= prefix.size && prefix.indices.all { bytes[it] == prefix[it] }

    /** An error page served as a stream: video never starts with "<" or "{". */
    private fun looksLikeAPage(bytes: ByteArray, count: Int): Boolean {
        for (i in 0 until count) {
            val char = bytes[i].toInt().toChar()
            if (!char.isWhitespace()) return char == '<' || char == '{'
        }
        return false
    }

    private companion object {
        const val CHUNK = 64 * 1024
        val HLS_TAG = "#EXTM3U".toByteArray()
    }
}
