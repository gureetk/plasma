// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.stalker

import java.io.IOException
import java.io.InputStream
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream

/**
 * Portal answers, which come as `{"js": …}` (brief 7.1). Big ones, a portal's thousands of
 * channels and its guide, are read as they download, a channel at a time, so they never sit in
 * memory whole.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object StalkerJson {
    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val MAP: SerialDescriptor = MapSerializer(String.serializer(), JsonElement.serializer()).descriptor
    private val LIST: SerialDescriptor = ListSerializer(JsonElement.serializer()).descriptor

    /** What `js` holds in a small answer, or null when the answer isn't one (an error page). */
    fun js(body: String): JsonElement? =
        runCatching { json.parseToJsonElement(body) }.getOrNull()?.let { (it as? JsonObject)?.get("js") }

    /**
     * Each object in the list at `js.data`. Returns how many there were: none for an answer of
     * another shape, such as `false` for an empty list.
     *
     * @throws IOException when the answer breaks off part way.
     */
    fun eachItem(stream: InputStream, onItem: (JsonObject) -> Unit): Int {
        var count = 0
        val items = object : DeserializationStrategy<Unit> {
            override val descriptor: SerialDescriptor = LIST

            override fun deserialize(decoder: Decoder) = decoder.decodeStructure(LIST) {
                while (true) {
                    val index = decodeElementIndex(LIST)
                    if (index == CompositeDecoder.DECODE_DONE) break
                    val item = decodeSerializableElement(LIST, index, JsonElement.serializer())
                    if (item is JsonObject) {
                        onItem(item)
                        count++
                    }
                }
            }
        }
        walk(stream, items) { count }
        return count
    }

    /**
     * Each entry of the object at `js.data`, such as the guide's programmes by channel id.
     * Returns how many there were.
     *
     * @throws IOException when the answer breaks off part way.
     */
    fun eachEntry(stream: InputStream, onEntry: (key: String, value: JsonElement) -> Unit): Int {
        var count = 0
        val entries = object : DeserializationStrategy<Unit> {
            override val descriptor: SerialDescriptor = MAP

            override fun deserialize(decoder: Decoder) = decoder.decodeStructure(MAP) {
                var key = ""
                while (true) {
                    val index = decodeElementIndex(MAP)
                    if (index == CompositeDecoder.DECODE_DONE) break
                    if (index % 2 == 0) {
                        key = decodeSerializableElement(MAP, index, String.serializer())
                    } else {
                        onEntry(key, decodeSerializableElement(MAP, index, JsonElement.serializer()))
                        count++
                    }
                }
            }
        }
        walk(stream, entries) { count }
        return count
    }

    /** Reads `{"js":{"data": …}}`, with [data] reading the data. Anything else is skipped over. */
    private fun walk(stream: InputStream, data: DeserializationStrategy<Unit>, read: () -> Int) {
        val js = Fields { key -> if (key == "data") data else JsonElement.serializer() }
        val root = Fields { key -> if (key == "js") js else JsonElement.serializer() }
        try {
            json.decodeFromStream(root, stream)
        } catch (wrongShape: SerializationException) {
            // `false` for an empty list, or an error page: nothing was read. Part way through, the
            // answer broke off, and a part of a list mustn't pass for all of it.
            if (read() > 0) throw IOException("The portal's answer broke off", wrongShape)
        } catch (wrongShape: IllegalArgumentException) {
            if (read() > 0) throw IOException("The portal's answer broke off", wrongShape)
        }
    }

    /** An object's fields, each read by the strategy [valueOf] gives for its name. */
    private class Fields(private val valueOf: (String) -> DeserializationStrategy<Any?>) : DeserializationStrategy<Unit> {
        override val descriptor: SerialDescriptor = MAP

        override fun deserialize(decoder: Decoder) = decoder.decodeStructure(MAP) {
            var key = ""
            while (true) {
                val index = decodeElementIndex(MAP)
                if (index == CompositeDecoder.DECODE_DONE) break
                if (index % 2 == 0) {
                    key = decodeSerializableElement(MAP, index, String.serializer())
                } else {
                    decodeSerializableElement(MAP, index, valueOf(key))
                }
            }
        }
    }

    /** A field as text: numbers and strings alike, trimmed; null for null, "" and "null". */
    fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    fun JsonObject.long(key: String): Long? = string(key)?.toDoubleOrNull()?.toLong()

    fun JsonObject.int(key: String): Int? = long(key)?.toInt()

    /** "1", 1 and true are all yes. */
    fun JsonObject.flag(key: String): Boolean = string(key)?.let { it == "1" || it.equals("true", ignoreCase = true) } == true
}
