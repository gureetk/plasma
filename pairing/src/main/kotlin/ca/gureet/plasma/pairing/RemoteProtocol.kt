// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

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
 * The phone remote's keys (brief 5.21), by the names the page and the apps send: they reach
 * Plasma as the remote's own would.
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

/** The profile's lists for the Channels tab (brief 5.21), and some of one's channels. */
@Serializable
data class RemoteLists(val lists: List<RemoteList>, val list: String, val from: Int, val channels: List<RemoteChannel>)

/** A list: Favorites, a group… with how many channels it has, and whether they count from 1 (5.2). */
@Serializable
data class RemoteList(val id: String, val name: String, val count: Int, val countsFromOne: Boolean = false)

/** A channel on the Channels tab: its number in the list, its name and what's on, how far in (0–1). */
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
 * 2 movies"), and its kind, "search" or "text", for the Type tab's icon.
 */
@Serializable
data class RemoteField(val label: String, val text: String, val results: String? = null, val kind: String = "text")

/** The messages both ways, as JSON objects with a type in "t". */
internal object RemoteMessages {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    const val PROTOCOL = 1

    /** A phone's message: its type, and its fields as text; null for one that isn't JSON. */
    fun parse(text: String): Pair<String, Map<String, String?>>? {
        val element = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val type = (element["t"] as? JsonPrimitive)?.contentOrNull ?: return null
        val fields = element.filterKeys { it != "t" }.mapValues { (_, value) -> (value as? JsonPrimitive)?.contentOrNull }
        return type to fields
    }

    /** What the TV can do for a phone, in "welcome". */
    val FEATURES = listOf("keys", "digits", "text", "tune", "lists", "live", "logos")

    fun welcome(tvId: String, name: String, version: String): String = message("welcome") {
        put("tv", tvId)
        put("name", name)
        put("version", version)
        put("protocol", PROTOCOL)
        put("features", JsonArray(FEATURES.map(::JsonPrimitive)))
    }

    fun paired(token: String): String = message("paired") { put("token", token) }

    fun error(reason: String): String = message("error") { put("reason", reason) }

    fun pong(): String = message("pong") {}

    fun state(state: RemoteState): String = with(json.encodeToJsonElement(state).jsonObject) { tagged("state", this) }

    fun lists(lists: RemoteLists): String = with(json.encodeToJsonElement(lists).jsonObject) { tagged("lists", this) }

    private fun tagged(type: String, body: JsonObject): String = JsonObject(mapOf("t" to JsonPrimitive(type)) + body).toString()

    private fun message(type: String, fields: JsonObjectBuilder.() -> Unit): String = buildJsonObject {
        put("t", type)
        fields()
    }.toString()
}
