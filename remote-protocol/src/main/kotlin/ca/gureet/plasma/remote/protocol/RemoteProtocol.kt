// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Plasma's phone remote protocol (brief 5.21), shared by the TV, its web page and the Plasma Remote
 * apps (5.22): JSON messages over a WebSocket, each with its type in "t". A phone sends `hello`,
 * the TV answers `welcome`, then the phone sends `auth` with its token, or `pair` with the code the
 * TV shows the first time; after that it sends keys, digits, text, channels to tune and requests
 * for lists, and the TV sends what it shows as `state`.
 *
 * Written in common Kotlin only, so it can become a Kotlin Multiplatform module for the apps.
 */
object RemoteProtocol {
    /** The protocol's version. The TV speaks its own and the one before. */
    const val VERSION = 1

    /** What the TV can do for a phone, listed in `welcome`. */
    val FEATURES: List<String> = listOf("keys", "digits", "text", "tune", "lists", "live", "logos")

    /** Whether a TV on [VERSION] speaks with a phone on [version]. */
    fun speaks(version: Int): Boolean = version == VERSION || version == VERSION - 1

    /** Why the TV turns a phone away, in an `error` message's "reason". */
    object Reasons {
        /** The code isn't the one the TV shows. */
        const val CODE = "code"

        /** Five wrong codes: the TV shows a new one. */
        const val TRIES = "tries"

        /** The token isn't one the TV knows, or was forgotten: pair again. */
        const val AUTH = "auth"

        /** The phone's protocol isn't one the TV speaks: one of them needs updating. */
        const val VERSION = "version"
    }

    /** The JSON both sides write: unknown fields are let be, defaults are written out. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** A message's type and its fields, each as text; null for one that isn't a JSON object with a type. */
    fun parse(text: String): Pair<String, Map<String, String?>>? {
        val element = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val type = (element["t"] as? JsonPrimitive)?.contentOrNull ?: return null
        val fields = element.filterKeys { it != "t" }.mapValues { (_, value) -> (value as? JsonPrimitive)?.contentOrNull }
        return type to fields
    }

    internal fun message(type: String, fields: JsonObjectBuilder.() -> Unit = {}): String = buildJsonObject {
        put("t", type)
        fields()
    }.toString()

    internal fun tagged(type: String, body: JsonObject): String = JsonObject(mapOf("t" to JsonPrimitive(type)) + body).toString()
}

/** What the TV sends a phone. */
object TvMessages {
    /** The answer to `hello`: the TV's id (made when Plasma was installed) and name, Plasma's version, and the protocol's. */
    fun welcome(tvId: String, name: String, version: String): String = RemoteProtocol.message("welcome") {
        put("tv", tvId)
        put("name", name)
        put("version", version)
        put("protocol", RemoteProtocol.VERSION)
        put("features", JsonArray(RemoteProtocol.FEATURES.map(::JsonPrimitive)))
    }

    /** Paired: the phone's own random 256-bit token, which it keeps and sends in `auth` from then on. */
    fun paired(token: String): String = RemoteProtocol.message("paired") { put("token", token) }

    /** Turned away, for one of [RemoteProtocol.Reasons]. */
    fun error(reason: String): String = RemoteProtocol.message("error") { put("reason", reason) }

    fun pong(): String = RemoteProtocol.message("pong")

    /** What the TV shows now. */
    fun state(state: RemoteState): String = RemoteProtocol.tagged("state", RemoteProtocol.json.encodeToJsonElement(state).jsonObject)

    /** The lists, and some of one's channels, as a phone asked. */
    fun lists(lists: RemoteLists): String = RemoteProtocol.tagged("lists", RemoteProtocol.json.encodeToJsonElement(lists).jsonObject)
}

/** What a phone sends the TV. */
object PhoneMessages {
    /** The first message: the kind of client ("web", "android" or "ios"), its version, and the protocol's. */
    fun hello(client: String, app: String, protocol: Int = RemoteProtocol.VERSION): String = RemoteProtocol.message("hello") {
        put("client", client)
        put("app", app)
        put("protocol", protocol)
    }

    /** The first time: the code the TV shows, and a name for the phone. */
    fun pair(code: String, name: String): String = RemoteProtocol.message("pair") {
        put("code", code)
        put("name", name)
    }

    /** Every time after: the phone's token. It travels in a message, never in an address. */
    fun auth(token: String): String = RemoteProtocol.message("auth") { put("token", token) }

    /** A key pressed ([down]) or let go: the TV repeats a held one, as the remote's own. */
    fun key(key: RemoteKey, down: Boolean): String = RemoteProtocol.message("key") {
        put("key", key.wire)
        put("action", if (down) "down" else "up")
    }

    /** Number entry: the digits, each as its number key. */
    fun digits(digits: String): String = RemoteProtocol.message("digits") { put("digits", digits) }

    /** The whole text of the TV's focused field, as it is on the phone (sent 150 ms after typing stops). */
    fun text(text: String): String = RemoteProtocol.message("text") { put("text", text) }

    /** Done, on the phone's keyboard. */
    fun submit(): String = RemoteProtocol.message("submit")

    /** A channel tapped on the Channels tab, and the list it was tapped in. */
    fun tune(channelId: String, listId: String? = null): String = RemoteProtocol.message("tune") {
        put("channel", channelId)
        if (listId != null) put("list", listId)
    }

    /** The lists, and [from] onward of [listId]'s channels; null for the TV's first list. */
    fun lists(listId: String? = null, from: Int = 0): String = RemoteProtocol.message("lists") {
        if (listId != null) put("list", listId)
        put("from", from)
    }

    /** Go Live: back to the live edge from the rewind buffer. */
    fun live(): String = RemoteProtocol.message("live")

    /** Every 15 s; two unanswered and the phone connects again. */
    fun ping(): String = RemoteProtocol.message("ping")
}

/**
 * The phone remote's keys, by the names phones send: they reach Plasma as the remote's own
 * would (brief 5.21).
 */
enum class RemoteKey(val wire: String) {
    Up("up"), Down("down"), Left("left"), Right("right"), Ok("ok"),
    Back("back"), Home("home"), Menu("menu"),
    PlayPause("play_pause"), Rewind("rewind"), FastForward("fast_forward"),
    ChannelUp("channel_up"), ChannelDown("channel_down"),
    Red("red"), Green("green"), Yellow("yellow"), Blue("blue"),
    Zero("0"), One("1"), Two("2"), Three("3"), Four("4"), Five("5"), Six("6"), Seven("7"), Eight("8"), Nine("9"),
    ;

    companion object {
        fun of(wire: String): RemoteKey? = entries.firstOrNull { it.wire == wire }

        /** The number key for [digit]. */
        fun digit(digit: Char): RemoteKey? = of(digit.toString()).takeIf { digit.isDigit() }
    }
}

/** The profile's lists for the Channels tab (brief 5.21), and some of one's channels. */
@Serializable
data class RemoteLists(val lists: List<RemoteList>, val list: String, val from: Int, val channels: List<RemoteChannel>)

/** A list: Favorites, a group… with how many channels it has, and whether they count from 1 (5.2). */
@Serializable
data class RemoteList(val id: String, val name: String, val count: Int, val countsFromOne: Boolean = false)

/**
 * A channel on the Channels tab: its number in the list, its name, what's on and how far in (0–1),
 * and whether it's the one playing.
 */
@Serializable
data class RemoteChannel(val id: String, val number: String, val name: String, val now: String? = null, val progress: Float? = null, val playing: Boolean = false)

/**
 * What the phone shows of the TV (brief 5.21): whether Plasma's in front, what plays, the colour
 * keys' labels on this screen ("red" to "Favorite"…), and the focused text field, if any.
 */
@Serializable
data class RemoteState(
    val front: Boolean = true,
    val playing: RemotePlaying? = null,
    val keys: Map<String, String> = emptyMap(),
    val field: RemoteField? = null,
)

/**
 * What plays: "105 · Arena Sports 1" and the program, with the timeline: how far into the program
 * live is and the picture is (0–1), how far behind live the picture is, in seconds, and whether
 * it's paused, so the phone can count on by itself.
 */
@Serializable
data class RemotePlaying(
    val channelId: String,
    val number: String,
    val name: String,
    val title: String? = null,
    val live: Float? = null,
    val progress: Float? = null,
    val behindLive: Long = 0,
    val paused: Boolean = false,
)

/**
 * The text field with focus on the TV: what it's for, what's in it, what it's found ("4 programs ·
 * 2 movies"), and its kind, [SEARCH] or [TEXT], for the Type tab's icon.
 */
@Serializable
data class RemoteField(val label: String, val text: String, val results: String? = null, val kind: String = TEXT) {
    companion object {
        const val SEARCH = "search"
        const val TEXT = "text"
    }
}
