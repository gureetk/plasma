// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.tv

import ca.gureet.plasma.remote.protocol.PhoneMessages
import ca.gureet.plasma.remote.protocol.RemoteKey
import ca.gureet.plasma.remote.protocol.RemoteLists
import ca.gureet.plasma.remote.protocol.RemoteProtocol
import ca.gureet.plasma.remote.protocol.RemoteState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A connection to a TV's TLS listener (brief 5.22): the phone remote's WebSocket, already
 * pinned to the TV's certificate. [incoming] ends when the connection does.
 */
interface TvSocket {
    val incoming: ReceiveChannel<String>

    suspend fun send(text: String)

    fun close()
}

/** Opens a [TvSocket] to wherever the TV is now; throws when it can't be reached. */
fun interface TvDialer {
    suspend fun dial(): TvSocket
}

/** The app as it says hello: "android" or "ios", and its version. */
data class PhoneApp(val client: String, val version: String)

/** What `welcome` says of the TV. */
data class Welcome(val tvId: String, val name: String, val protocol: Int)

/** How pairing went. */
sealed interface PairResult {
    /** Paired: the TV and the phone's token, to keep. */
    data class Paired(val welcome: Welcome, val token: String) : PairResult

    /** Turned away, for one of [RemoteProtocol.Reasons]. */
    data class Refused(val reason: String) : PairResult

    /** The TV didn't answer as Plasma does. */
    data object NoAnswer : PairResult
}

/**
 * Pairs over [socket] (brief 5.21): `hello`, then the code the TV shows and the phone's name.
 * The TV answers `paired` with the phone's own token, or turns it away (a wrong code, five of
 * them, or a protocol one of them must update for).
 */
suspend fun pair(socket: TvSocket, app: PhoneApp, code: String, phoneName: String): PairResult {
    socket.send(PhoneMessages.hello(app.client, app.version))
    val welcome = awaitWelcome(socket) ?: return PairResult.NoAnswer
    socket.send(PhoneMessages.pair(code.trim().uppercase(), phoneName))
    while (true) {
        val (type, fields) = withTimeoutOrNull(ANSWER_MS) { nextMessage(socket) } ?: return PairResult.NoAnswer
        when (type) {
            "paired" -> return fields["token"]?.let { PairResult.Paired(welcome, it) } ?: PairResult.NoAnswer
            "error" -> return PairResult.Refused(fields["reason"].orEmpty())
        }
    }
}

private suspend fun awaitWelcome(socket: TvSocket): Welcome? = withTimeoutOrNull(ANSWER_MS) {
    var welcome: Welcome? = null
    while (welcome == null) {
        val (type, fields) = nextMessage(socket) ?: break
        if (type == "welcome") welcome = Welcome(fields["tv"].orEmpty(), fields["name"].orEmpty(), fields["protocol"]?.toIntOrNull() ?: 0)
    }
    welcome
}

/** The next message's type and fields, with the text it came as; null once the connection has ended. */
private suspend fun nextMessage(socket: TvSocket): Pair<String, Map<String, String?>>? {
    while (true) {
        val text = socket.incoming.receiveCatching().getOrNull() ?: return null
        RemoteProtocol.parse(text)?.let { return it }
    }
}

/**
 * A paired TV over the network (brief 5.21 and 5.22): connects with [dialer], says hello and
 * sends the phone's token, then keeps what the TV shows. It pings every 15 s and connects again
 * when two go unanswered or the connection ends, at once the first time and then after a little
 * longer each time, up to 5 s. A key pressed while the TV can't be reached goes nowhere.
 * The TV forgetting the phone ends it: [onForgotten] asks for pairing again.
 */
class NetworkTv(
    override val name: String,
    private val token: String,
    private val app: PhoneApp,
    private val dialer: TvDialer,
    private val onForgotten: (reason: String) -> Unit = {},
    /** A channel's logo from the TV, over the same pinned connection. */
    private val fetchLogo: suspend (channelId: String) -> ByteArray? = { null },
) : TvLink {
    private val _status = MutableStateFlow(LinkStatus.Connecting)
    override val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow(RemoteState())
    override val state: StateFlow<RemoteState> = _state.asStateFlow()

    private var socket: TvSocket? = null

    /** What the phone sends, in the order it's sent, while a connection is up. */
    private val outgoing = Channel<String>(Channel.UNLIMITED)
    private var listsAsked: CompletableDeferred<RemoteLists?>? = null
    private var unanswered = 0

    /** Keeps the connection up for as long as [scope] runs: the app's own, while it's in front. */
    fun keepConnected(scope: CoroutineScope): Job = scope.launch {
        var failures = 0
        while (isActive) {
            val ended = runCatching { session() }.getOrElse { Ended.Lost }
            socket = null
            if (ended == Ended.Forgotten) return@launch
            failures++
            // Can't reach it: said once a first try again hasn't got through either.
            if (failures >= 2) _status.value = LinkStatus.Unreachable
            delay(retryAfter(failures))
            if (failures >= 2) _status.value = LinkStatus.Connecting
        }
    }

    private enum class Ended { Lost, Forgotten }

    private suspend fun session(): Ended = coroutineScope {
        val socket = dialer.dial()
        // Keys pressed while the TV couldn't be reached go nowhere.
        while (outgoing.tryReceive().isSuccess) Unit
        this@NetworkTv.socket = socket
        try {
            socket.send(PhoneMessages.hello(app.client, app.version))
            socket.send(PhoneMessages.auth(token))
            unanswered = 0
            val writer = launch { for (text in outgoing) socket.send(text) }
            val pings = launch {
                while (isActive) {
                    delay(PING_MS)
                    if (unanswered >= 2) {
                        socket.close()
                        return@launch
                    }
                    unanswered++
                    outgoing.trySend(PhoneMessages.ping())
                }
            }
            try {
                for (text in socket.incoming) {
                    val (type, fields) = RemoteProtocol.parse(text) ?: continue
                    unanswered = 0
                    when (type) {
                        "state" -> {
                            _state.value = RemoteProtocol.json.decodeFromString(RemoteState.serializer(), text)
                            _status.value = LinkStatus.Connected
                        }
                        "lists" -> listsAsked?.complete(RemoteProtocol.json.decodeFromString(RemoteLists.serializer(), text))
                        "error" -> {
                            val reason = fields["reason"].orEmpty()
                            if (reason == RemoteProtocol.Reasons.AUTH || reason == RemoteProtocol.Reasons.VERSION) {
                                onForgotten(reason)
                                return@coroutineScope Ended.Forgotten
                            }
                        }
                    }
                }
            } finally {
                pings.cancel()
                writer.cancel()
            }
            Ended.Lost
        } finally {
            socket.close()
            listsAsked?.complete(null)
        }
    }

    private fun sendNow(text: String) {
        if (socket != null) outgoing.trySend(text)
    }

    override fun key(key: RemoteKey, down: Boolean) = sendNow(PhoneMessages.key(key, down))

    override fun digits(digits: String) = sendNow(PhoneMessages.digits(digits))

    override fun text(text: String) = sendNow(PhoneMessages.text(text))

    override fun submit() = sendNow(PhoneMessages.submit())

    override fun tune(channelId: String, listId: String?) = sendNow(PhoneMessages.tune(channelId, listId))

    override fun goLive() = sendNow(PhoneMessages.live())

    override suspend fun lists(listId: String?, from: Int): RemoteLists? {
        if (socket == null) return null
        val asked = CompletableDeferred<RemoteLists?>()
        listsAsked = asked
        sendNow(PhoneMessages.lists(listId, from))
        return withTimeoutOrNull(ANSWER_MS) { asked.await() }
    }

    override suspend fun logo(channelId: String): ByteArray? = fetchLogo(channelId)

    companion object {
        const val PING_MS = 15_000L

        /** 0.5 s, 1 s, 2 s, then 5 s at most between tries. */
        fun retryAfter(failures: Int): Long = when (failures) {
            1 -> 0L
            2 -> 500L
            3 -> 1_000L
            4 -> 2_000L
            else -> 5_000L
        }
    }
}

/** How long the TV has to answer a message that asks for one. */
internal const val ANSWER_MS = 5_000L
