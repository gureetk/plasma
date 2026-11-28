// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.remote

import ca.gureet.plasma.remote.protocol.RemoteAdvert
import ca.gureet.plasma.remote.protocol.RemoteInvite
import ca.gureet.plasma.remote.protocol.RemoteKey
import ca.gureet.plasma.remote.protocol.RemoteProtocol
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** The Plasma Remote apps' TLS listener (brief 5.22): pinned to the TV's certificate, for the apps alone. */
class RemoteTlsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val servers = mutableListOf<RemoteServer>()
    private val keys = CopyOnWriteArrayList<String>()

    /** The test's own TV certificate: an EC P-256 key, self-signed, as the TV makes one. */
    private val identity: Pair<PrivateKey, X509Certificate> by lazy {
        val store = KeyStore.getInstance("PKCS12")
        requireNotNull(javaClass.getResourceAsStream("/test-tv.p12")).use { store.load(it, PASSWORD) }
        store.getKey("tv", PASSWORD) as PrivateKey to store.getCertificate("tv") as X509Certificate
    }

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

        override fun logo(channelId: String): Pair<ByteArray, String>? = if (channelId == "home:108") byteArrayOf(1, 2, 3) to "image/png" else null

        override fun poster(kind: String, id: String): Pair<ByteArray, String>? = if (id == "m:1") byteArrayOf(4, 5, 6) to "image/jpeg" else null
    }

    private val page = object : RemotePage {
        override fun html() = "<p>Plasma Remote</p>"

        override fun file(name: String): RemoteFile? = null
    }

    @After fun tearDown() {
        servers.forEach { it.close() }
        scope.cancel()
    }

    private fun server(tls: RemoteTls? = RemoteTls.of(identity.first, identity.second), tlsPort: Int = 0): RemoteServer =
        RemoteServer(RemoteTv("tv-1", "Living Room TV", "1.0"), store, handler, page, unnamed = "Phone", tls = tls).also { server ->
            server.start(InetAddress.getLoopbackAddress(), scope, port = 0, tlsPort = tlsPort)
            servers += server
        }

    private val RemoteServer.invited: RemoteInvite get() = requireNotNull(RemoteInvite.parse(invite.value!!.url))

    private val RemoteServer.secureHost: String get() = "${InetAddress.getLoopbackAddress().hostAddress}:${advert!!.tls}"

    /**
     * An app's connection, as the apps make it: the TV's certificate is trusted when its SHA-256
     * is [pinned], and its name isn't looked at (the TV's address changes; its certificate doesn't).
     */
    private fun pinnedTo(pinned: String): OkHttpClient {
        val trust = object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
                if (chain.isEmpty() || RemoteTls.fingerprintOf(chain[0]) != pinned) throw CertificateException("Not the TV that was paired")
            }

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException()

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return OkHttpClient.Builder()
            .sslSocketFactory(context.socketFactory, trust)
            .hostnameVerifier { _, _ -> true }
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private inner class App(server: RemoteServer, client: OkHttpClient, origin: String? = null) {
        val messages = LinkedBlockingQueue<JsonObject>()
        val failed = LinkedBlockingQueue<Any>()
        val socket: WebSocket = client.newWebSocket(
            Request.Builder().url("wss://${server.secureHost}/remote/ws").apply { if (origin != null) header("Origin", origin) }.build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    messages += Json.parseToJsonElement(text).jsonObject
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    failed += response?.code ?: t
                }
            },
        )

        fun next(): JsonObject = requireNotNull(messages.poll(5, TimeUnit.SECONDS)) { "Nothing came" }

        fun JsonObject.type(): String = this["t"]!!.jsonPrimitive.content
    }

    @Test fun anAppPinnedToTheQrCodesFingerprintPairsAndSendsKeys() {
        val server = server()
        val invite = server.invited
        assertEquals(RemoteTls.fingerprintOf(identity.second), invite.fingerprint)
        val app = App(server, pinnedTo(invite.fingerprint!!))

        app.socket.send("""{"t":"hello","client":"android","app":"1.0","protocol":1}""")
        assertEquals("welcome", with(app) { next().type() })
        app.socket.send("""{"t":"pair","code":"${invite.code}","name":"Maya’s Pixel"}""")
        assertEquals("paired", with(app) { next().type() })
        assertEquals("state", with(app) { next().type() })
        app.socket.send("""{"t":"key","key":"ok","action":"down"}""")
        app.socket.send("""{"t":"key","key":"ok","action":"up"}""")

        val deadline = System.currentTimeMillis() + 5_000
        while (keys.size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(listOf("ok↓", "ok↑"), keys)
        assertEquals("Maya’s Pixel", store.phones.values.single().name)
        // The apps' listener: the TV's Phone Remote page says "App" for it (brief 4.33).
        assertTrue(store.phones.values.single().app)
    }

    @Test fun anAppPinnedToAnotherCertificateIsRefused() {
        // What a reinstalled TV looks like to an app paired before: its certificate is another.
        val server = server()
        val app = App(server, pinnedTo("A".repeat(43)))

        val failure = app.failed.poll(5, TimeUnit.SECONDS)
        assertTrue("A handshake failure, not $failure", failure is SSLHandshakeException)
        assertTrue(store.phones.isEmpty())
    }

    @Test fun aBrowserPageCantOpenTheAppsConnection() {
        val server = server()
        val app = App(server, pinnedTo(server.invited.fingerprint!!), origin = "http://${server.invite.value!!.address}")

        assertEquals(403, app.failed.poll(5, TimeUnit.SECONDS))
    }

    @Test fun theAppsListenerServesLogosAndTheAdvertButNoPage() {
        val server = server()
        val client = pinnedTo(server.invited.fingerprint!!)

        client.newCall(Request.Builder().url("https://${server.secureHost}/remote/logo/home%3A108").build()).execute().use { assertEquals("image/png", it.header("Content-Type")) }
        client.newCall(Request.Builder().url("https://${server.secureHost}/remote/poster/movies/m%3A1").build()).execute().use { assertEquals("image/jpeg", it.header("Content-Type")) }
        client.newCall(Request.Builder().url("https://${server.secureHost}/remote").build()).execute().use { assertEquals(404, it.code) }
        client.newCall(Request.Builder().url("https://${server.secureHost}/").build()).execute().use { assertEquals(404, it.code) }
        client.newCall(Request.Builder().url("https://${server.secureHost}${RemoteAdvert.PATH}").build()).execute().use { assertEquals(server.advert, RemoteAdvert.fromJson(it.body.string())) }
    }

    @Test fun theAdvertSaysWhereTheListenerIsOnThePagesAddressToo() {
        val server = server()
        val advert = requireNotNull(server.advert)

        assertEquals(RemoteAdvert("tv-1", "Living Room TV", RemoteProtocol.VERSION, advert.tls), advert)
        OkHttpClient().newCall(Request.Builder().url("http://${server.invite.value!!.address}${RemoteAdvert.PATH}").build()).execute().use { response ->
            assertEquals(200, response.code)
            assertTrue(response.header("Content-Type")!!.startsWith("application/json"))
            assertEquals(advert, RemoteAdvert.fromJson(response.body.string()))
        }
    }

    @Test fun aTakenPortGivesTheListenerAnotherWhichTheAdvertNames() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { taken ->
            val server = server(tlsPort = taken.localPort)

            assertNotEquals(taken.localPort, server.advert!!.tls)
            assertTrue(server.advert!!.tls > 0)
        }
    }

    @Test fun withoutTheTvsCertificateThereIsNoListenerAndNoFingerprint() {
        val server = server(tls = null)

        assertNull(server.advert)
        assertNull(server.invited.fingerprint)
        assertEquals("http://${server.invite.value!!.address}/remote?code=${server.invite.value!!.code}", server.invite.value!!.url)
        OkHttpClient().newCall(Request.Builder().url("http://${server.invite.value!!.address}${RemoteAdvert.PATH}").build()).execute().use { assertEquals(404, it.code) }
    }

    @Test fun closingStopsTheAppsListenerToo() {
        val server = server()
        val port = server.advert!!.tls

        server.close()

        assertNull(server.advert)
        // The port is free again, once the thread that was waiting on it has woken.
        val deadline = System.currentTimeMillis() + 2_000
        while (runCatching { ServerSocket(port, 1, InetAddress.getLoopbackAddress()).close() }.isFailure) {
            assertTrue("Port $port still taken", System.currentTimeMillis() < deadline)
            Thread.sleep(20)
        }
    }

    private companion object {
        val PASSWORD = "test-only".toCharArray()
    }
}
