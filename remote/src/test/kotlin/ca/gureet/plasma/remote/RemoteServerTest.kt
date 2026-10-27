// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.remote

import ca.gureet.plasma.remote.protocol.RemoteChannel
import ca.gureet.plasma.remote.protocol.RemoteField
import ca.gureet.plasma.remote.protocol.RemoteKey
import ca.gureet.plasma.remote.protocol.RemoteList
import ca.gureet.plasma.remote.protocol.RemoteLists
import ca.gureet.plasma.remote.protocol.RemoteState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** The phone remote (brief 5.21): its page, pairing, and what a paired phone can do, against the server here. */
class RemoteServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
    private val servers = mutableListOf<RemoteServer>()
    private val keys = CopyOnWriteArrayList<String>()
    private val tuned = CopyOnWriteArrayList<String>()
    private val typed = CopyOnWriteArrayList<String>()
    private val wentLive = CopyOnWriteArrayList<Unit>()
    private val pairedPhones = CopyOnWriteArrayList<PairedPhone>()

    /** The paired phones, kept as the TV would, but in memory. */
    private val store = object : PhoneStore {
        val phones = LinkedHashMap<String, PairedPhone>()

        override suspend fun phones(): List<PairedPhone> = synchronized(phones) { phones.values.toList() }

        override suspend fun save(phone: PairedPhone) {
            synchronized(phones) { phones[phone.id] = phone }
        }

        override suspend fun remove(id: String) {
            synchronized(phones) { phones.remove(id) }
        }
    }

    private val handler = object : RemoteHandler {
        override fun key(key: RemoteKey, down: Boolean) {
            keys += key.wire + if (down) "↓" else "↑"
        }

        override fun text(text: String) {
            typed += text
        }

        override fun submit() {
            typed += "⏎"
        }

        override fun tune(channelId: String, listId: String?) {
            tuned += channelId + listId?.let { " in $it" }.orEmpty()
        }

        override fun goLive() {
            wentLive += Unit
        }

        override fun lists(listId: String?, from: Int): RemoteLists = RemoteLists(
            lists = listOf(RemoteList("favorites", "Favorites", 2, countsFromOne = true), RemoteList("home:sports", "Sports", 186)),
            list = listId ?: "favorites",
            from = from,
            channels = listOf(RemoteChannel("home:108", "1", "Courtside+", now = "Late Night Hoops", progress = 0.4f)),
        )

        override fun logo(channelId: String): Pair<ByteArray, String>? = if (channelId == "home:108") byteArrayOf(1, 2, 3) to "image/png" else null
    }

    private val page = object : RemotePage {
        override fun html() = "<p>Plasma Remote</p>"

        override fun file(name: String) = if (name == "remote.js") RemoteFile("// script".toByteArray(), "text/javascript") else null
    }

    @After fun tearDown() {
        servers.forEach { it.close() }
        scope.cancel()
    }

    private fun server(): RemoteServer =
        RemoteServer(RemoteTv("tv-1", "Living Room TV", "1.0"), store, handler, page, unnamed = "Phone", onPaired = { pairedPhones += it }).also { server ->
            server.start(InetAddress.getLoopbackAddress(), scope, port = 0)
            servers += server
        }

    private val RemoteServer.host: String get() = invite.value!!.address

    /** A phone's connection: what it's sent back, one message at a time. */
    private inner class Phone(server: RemoteServer, origin: String = "http://${server.host}") {
        val messages = LinkedBlockingQueue<JsonObject>()
        val closed = LinkedBlockingQueue<Int>()
        val socket: WebSocket = http.newWebSocket(
            Request.Builder().url("ws://${server.host}/remote/ws").header("Origin", origin).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    messages += Json.parseToJsonElement(text).jsonObject
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed += response?.code ?: -1
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    closed += code
                    webSocket.close(1000, null)
                }
            },
        )

        fun send(text: String) = socket.send(text)

        fun next(): JsonObject = requireNotNull(messages.poll(5, TimeUnit.SECONDS)) { "Nothing came" }

        fun JsonObject.type(): String = this["t"]!!.jsonPrimitive.content
    }

    private fun Phone.pair(code: String): String {
        send("""{"t":"hello","client":"web","protocol":1}""")
        assertEquals("welcome", next().type())
        send("""{"t":"pair","code":"$code","name":"Maya’s iPhone"}""")
        val paired = next()
        assertEquals("paired", paired.type())
        assertEquals("state", next().type())
        return paired["token"]!!.jsonPrimitive.content
    }

    @Test fun thePageIsServedOnlyToTheHomeNetworkWithItsOwnPolicy() {
        val server = server()

        http.newCall(Request.Builder().url("http://${server.host}/remote?code=7KQ4MZ").build()).execute().use { response ->
            assertEquals(200, response.code)
            assertTrue("Plasma Remote" in response.body.string())
            val policy = response.header("Content-Security-Policy")!!
            assertTrue("script-src 'self'" in policy)
            assertTrue("connect-src ws://${server.host}" in policy)
        }
        // The page sends the channel's id percent-encoded.
        http.newCall(Request.Builder().url("http://${server.host}/remote/logo/home%3A108").build()).execute().use { assertEquals("image/png", it.header("Content-Type")) }
        http.newCall(Request.Builder().url("http://${server.host}/remote/logo/home:999").build()).execute().use { assertEquals(404, it.code) }
        http.newCall(Request.Builder().url("http://${server.host}/remote/remote.js").build()).execute().use { assertEquals("// script", it.body.string()) }
        http.newCall(Request.Builder().url("http://${server.host}/remote/../secrets").build()).execute().use { assertEquals(404, it.code) }
    }

    @Test fun anotherSitesPageCantOpenTheConnection() {
        val server = server()
        val phone = Phone(server, origin = "http://example.org")

        assertEquals(403, phone.closed.poll(5, TimeUnit.SECONDS))
    }

    @Test fun aPhonePairsWithTheCodeThenSendsKeysChannelsAndText() {
        val server = server()
        val code = server.invite.value!!.code
        val phone = Phone(server)

        val token = phone.pair(code)

        assertEquals(1, store.phones.size)
        val kept = store.phones.values.single()
        assertEquals("Maya’s iPhone", kept.name)
        // Only a hash of the token is kept.
        assertNotEquals(token, kept.tokenHash)
        assertEquals(RemoteServer.hash(token), kept.tokenHash)
        assertEquals(listOf(kept), pairedPhones)
        // The code was good for one phone.
        assertNotEquals(code, server.invite.value!!.code)

        phone.send("""{"t":"key","key":"ok","action":"down"}""")
        phone.send("""{"t":"key","key":"ok","action":"up"}""")
        phone.send("""{"t":"digits","digits":"10"}""")
        phone.send("""{"t":"tune","channel":"home:105","list":"favorites"}""")
        phone.send("""{"t":"live"}""")
        phone.send("""{"t":"text","text":"night"}""")
        phone.send("""{"t":"submit"}""")
        phone.send("""{"t":"lists","list":"home:sports","from":0}""")
        val lists = phone.next()
        assertEquals("lists", with(phone) { lists.type() })
        assertEquals("home:sports", lists["list"]!!.jsonPrimitive.content)

        assertEquals(listOf("ok↓", "ok↑", "1↓", "1↑", "0↓", "0↑"), keys)
        assertEquals(listOf("home:105 in favorites"), tuned)
        assertEquals(1, wentLive.size)
        assertEquals(listOf("night", "⏎"), typed)
        assertEquals(setOf(kept.id), server.connected.value)
    }

    @Test fun aPairedPhoneComesBackWithItsTokenAndForgetLetsItGo() {
        val server = server()
        val token = Phone(server).pair(server.invite.value!!.code)

        val again = Phone(server)
        again.send("""{"t":"hello","client":"web","protocol":1}""")
        again.next()
        again.send("""{"t":"auth","token":"$token"}""")
        assertEquals("state", with(again) { next().type() })
        server.publish(RemoteState(field = RemoteField("Search", "night")))
        val state = again.next()
        assertEquals("Search", state["field"]!!.jsonObject["label"]!!.jsonPrimitive.content)

        server.forget(store.phones.keys.single())

        assertTrue(store.phones.isEmpty())
        val third = Phone(server)
        third.send("""{"t":"hello","client":"web","protocol":1}""")
        third.next()
        third.send("""{"t":"auth","token":"$token"}""")
        assertEquals("auth", third.next()["reason"]!!.jsonPrimitive.content)
    }

    @Test fun beforePairingNothingButTheCodeWorks() {
        val server = server()
        val phone = Phone(server)
        phone.send("""{"t":"hello","client":"web","protocol":1}""")
        phone.next()

        phone.send("""{"t":"key","key":"ok","action":"down"}""")
        assertEquals("auth", phone.next()["reason"]!!.jsonPrimitive.content)
        repeat(4) {
            phone.send("""{"t":"pair","code":"WRONG1","name":"x"}""")
            assertEquals("code", phone.next()["reason"]!!.jsonPrimitive.content)
        }
        val code = server.invite.value!!.code
        phone.send("""{"t":"pair","code":"WRONG1","name":"x"}""")
        // Five wrong tries: the TV shows a new code.
        assertEquals("tries", phone.next()["reason"]!!.jsonPrimitive.content)
        assertNotEquals(code, server.invite.value!!.code)
        assertTrue(keys.isEmpty())
        assertTrue(store.phones.isEmpty())
    }

    @Test fun aPhoneOnAProtocolTheTvDoesntSpeakIsWelcomedThenToldSo() {
        val server = server()
        val phone = Phone(server)

        phone.send("""{"t":"hello","client":"ios","protocol":9}""")

        // The welcome says which protocol the TV speaks, so the app can say which to update.
        assertEquals(1, with(phone) { next() }["protocol"]!!.jsonPrimitive.content.toInt())
        assertEquals("version", phone.next()["reason"]!!.jsonPrimitive.content)
        phone.send("""{"t":"pair","code":"${server.invite.value!!.code}","name":"x"}""")
        assertEquals(null, phone.messages.poll(500, TimeUnit.MILLISECONDS))
        assertTrue(store.phones.isEmpty())
    }

    @Test fun aPhoneThatGivesNoNameIsCalledPhone() {
        val server = server()
        val phone = Phone(server)
        phone.send("""{"t":"hello","client":"web","protocol":1}""")
        phone.next()

        phone.send("""{"t":"pair","code":"${server.invite.value!!.code}","name":"  "}""")

        assertEquals("paired", with(phone) { next().type() })
        assertEquals("Phone", store.phones.values.single().name)
    }

    @Test fun aPhoneThatFloodsTheTvIsCutOff() {
        val server = server()
        val phone = Phone(server)
        phone.pair(server.invite.value!!.code)

        repeat(200) { phone.send("""{"t":"ping"}""") }

        assertNotNull(phone.closed.poll(5, TimeUnit.SECONDS))
        assertTrue(server.connected.value.isEmpty())
    }

    @Test fun anUnmaskedFrameIsRefused() {
        val server = server()
        Socket(InetAddress.getLoopbackAddress(), server.host.substringAfter(':').toInt()).use { socket ->
            val out = socket.getOutputStream()
            out.write(
                ("GET /remote/ws HTTP/1.1\r\nHost: ${server.host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\nOrigin: http://${server.host}\r\n\r\n").toByteArray(),
            )
            val input = socket.getInputStream()
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) head.append(input.read().toChar())
            // RFC 6455's own example key.
            assertTrue("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=" in head)
            // "hello", unmasked: the TV answers with a close.
            val hello = "{\"t\":\"hello\"}".toByteArray()
            out.write(byteArrayOf(0x81.toByte(), hello.size.toByte()) + hello)
            assertEquals(0x88, input.read())
        }
    }
}
