// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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
    /**
     * What it shares a connection by: the channel, so watching and recording it use one connection
     * even when a portal hands each a different address (brief 5.6). The address by default.
     */
    val shareKey: String = url,
    /**
     * Whether an engine watches it. Only then does Always write the rewind buffer from the start
     * (brief 5.5); a recording, or local catch-up, reads it without one.
     */
    val viewing: Boolean = true,
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
    /** A name of its own, for its rewind buffer's folder. */
    private val id: String = "upstream",
) {
    val ring = Ring(config.ringBytes)

    /** Where pictures start in the stream, and its tables (brief 5.5). */
    val scanner = TsScanner()

    /** The rewind buffer, from a pause on, or from the start with Always (brief 5.5). */
    @Volatile var spill: Spill? = null
        private set

    /**
     * How what memory holds came in, while there's no rewind buffer: when, and where its keyframes
     * are. A pause writes it out as it came, so its segments start at keyframes, with their times.
     */
    private val arrivals = ArrayDeque<Arrival>()

    /** Guards [spill] and [arrivals], and keeps a chunk's writes to them and to the [ring] together. */
    private val spillLock = Any()
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

    fun start() {
        if (request.viewing) watched()
        thread.start()
    }

    /** An engine watches it now: with Always, the rewind buffer starts, from what memory holds (5.5). */
    fun watched() {
        if (config.rewind().mode == RewindMode.Always) hold()
    }

    /** Whether Settings has a rewind buffer: it's on, with somewhere to go. */
    val canHold: Boolean get() = spill != null || config.rewind().let { it.mode != RewindMode.Off && it.folder != null }

    /**
     * When the oldest moment live TV can be rewound to came in (brief 5.5): the rewind buffer's,
     * or before it starts, memory's. Null without a buffer.
     */
    val rewindSince: Long?
        get() {
            spill?.let { return it.oldestTime }
            if (!canHold) return null
            return synchronized(spillLock) { arrivals.firstOrNull()?.time }
        }

    /**
     * Starts the rewind buffer, if Settings has one and it hasn't started, with what memory still
     * holds (brief 5.5). True when there's a buffer.
     */
    fun hold(): Boolean = synchronized(spillLock) {
        if (spill != null) return true
        if (closed) return false
        val settings = config.rewind()
        val folder = settings.folder ?: return false
        if (settings.mode == RewindMode.Off) return false
        val buffer = try {
            Spill(File(folder, id), settings.keepMs, settings.maxBytes, config.clock)
        } catch (unavailable: IOException) {
            return false
        } catch (unavailable: SecurityException) {
            return false
        }
        // Memory first, as it came.
        val (from, bytes) = ring.held()
        for (arrival in arrivals) {
            val start = maxOf(arrival.position, from)
            val end = arrival.position + arrival.length
            if (end > start) buffer.write(start, bytes, (start - from).toInt(), (end - start).toInt(), arrival.keyframes, arrival.time)
        }
        arrivals.clear()
        spill = buffer
        ring.behind = buffer
        true
    }

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
        // Changing channel ends the rewind buffer and frees its space (brief 5.5).
        synchronized(spillLock) {
            ring.behind = null
            spill?.close()
            spill = null
        }
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
            // A new connection: a packet the last one left half done is gone.
            scanner.resync()
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
                keep(chunk, count)
            }
            return StreamFailure.Ended
        }
    }

    /**
     * Keeps [count] bytes of [chunk]: in the rewind buffer first, so a reader that memory has let go
     * of finds them there, then in memory. Internal for its tests.
     */
    internal fun keep(chunk: ByteArray, count: Int) {
        val position = ring.written
        val found = ArrayList<Long>(1)
        scanner.feed(chunk, 0, count) { found += it }
        val now = config.clock()
        synchronized(spillLock) {
            val spill = spill
            if (spill != null) {
                spill.write(position, chunk, 0, count, found, now)
                ring.write(chunk, 0, count)
            } else {
                ring.write(chunk, 0, count)
                arrived(position, count, found, now)
            }
        }
    }

    /** Notes how [count] bytes from [position] came in, and lets go of what memory no longer holds. */
    private fun arrived(position: Long, count: Int, keyframes: List<Long>, time: Long) {
        val last = arrivals.lastOrNull()
        val arrival = if (last != null && time - last.time < ARRIVALS_MS && last.position + last.length == position) {
            last
        } else {
            Arrival(position, time).also { arrivals.addLast(it) }
        }
        arrival.length += count
        arrival.keyframes += keyframes
        val oldest = ring.oldest
        while (arrivals.isNotEmpty() && arrivals.first().let { it.position + it.length } <= oldest) arrivals.removeFirst()
    }

    /** Bytes that came in together, from [position] at [time]. */
    private class Arrival(val position: Long, val time: Long) {
        var length = 0L
        val keyframes = ArrayList<Long>(1)
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

        /** Bytes that come within this long of each other count as one arrival. */
        const val ARRIVALS_MS = 250L
    }
}
