// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone remote's messages (brief 5.21), as both sides write and read them. */
class RemoteProtocolTest {
    @Test fun whatAPhoneSendsReadsBackAsTheTvReadsIt() {
        assertEquals("hello" to mapOf("client" to "ios", "app" to "1.0", "protocol" to "1"), RemoteProtocol.parse(PhoneMessages.hello("ios", "1.0")))
        assertEquals("key" to mapOf("key" to "play_pause", "action" to "down"), RemoteProtocol.parse(PhoneMessages.key(RemoteKey.PlayPause, down = true)))
        assertEquals("tune" to mapOf("channel" to "home:105", "list" to "favorites"), RemoteProtocol.parse(PhoneMessages.tune("home:105", "favorites")))
        assertEquals("lists" to mapOf("from" to "0"), RemoteProtocol.parse(PhoneMessages.lists()))
        assertEquals("text" to mapOf("text" to "night \"owl\""), RemoteProtocol.parse(PhoneMessages.text("night \"owl\"")))
    }

    @Test fun anythingButAJsonObjectWithATypeIsLetBe() {
        assertNull(RemoteProtocol.parse("hello"))
        assertNull(RemoteProtocol.parse("[1, 2]"))
        assertNull(RemoteProtocol.parse("""{"key":"ok"}"""))
        // Fields a newer phone adds are read as text or nothing, never an error.
        assertEquals("ping" to mapOf("extra" to null), RemoteProtocol.parse("""{"t":"ping","extra":{"a":1}}"""))
    }

    @Test fun theTvsStateCarriesItsTypeAndEveryField() {
        val state = TvMessages.state(RemoteState(front = false, keys = mapOf("red" to "Favorite"), field = RemoteField("Search", "night", kind = RemoteField.SEARCH)))
        val json = Json.parseToJsonElement(state).jsonObject

        assertEquals("state", json["t"]!!.jsonPrimitive.content)
        assertEquals("false", json["front"]!!.jsonPrimitive.content)
        assertEquals("Favorite", json["keys"]!!.jsonObject["red"]!!.jsonPrimitive.content)
        assertEquals("search", json["field"]!!.jsonObject["kind"]!!.jsonPrimitive.content)
    }

    @Test fun theWelcomeSaysWhichProtocolAndWhatTheTvDoes() {
        val welcome = Json.parseToJsonElement(TvMessages.welcome("tv-1", "Living Room TV", "1.0")).jsonObject

        assertEquals(RemoteProtocol.VERSION, welcome["protocol"]!!.jsonPrimitive.content.toInt())
        assertEquals(RemoteProtocol.FEATURES, welcome["features"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun theTvSpeaksItsOwnProtocolAndTheOneBefore() {
        assertTrue(RemoteProtocol.speaks(RemoteProtocol.VERSION))
        assertTrue(RemoteProtocol.speaks(RemoteProtocol.VERSION - 1))
        assertFalse(RemoteProtocol.speaks(RemoteProtocol.VERSION + 1))
        assertFalse(RemoteProtocol.speaks(RemoteProtocol.VERSION - 2))
    }

    @Test fun keysAndDigitsByTheirNames() {
        assertEquals(RemoteKey.ChannelUp, RemoteKey.of("channel_up"))
        assertEquals(RemoteKey.Seven, RemoteKey.digit('7'))
        assertNull(RemoteKey.digit('x'))
        assertNull(RemoteKey.of("power"))
    }
}
