// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.tv

import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer

/**
 * The Android app's connection to a TV's TLS listener (brief 5.22), against a server with a
 * self-signed certificate of its own, as a TV has: pinned to its fingerprint, refused for any other.
 */
class PinnedTest {
    private val certificate = HeldCertificate.Builder().commonName("Plasma").build()
    private val fingerprint = fingerprintOf(MessageDigest.getInstance("SHA-256").digest(certificate.certificate.encoded))
    private val server = MockWebServer().apply {
        useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory())
        start()
    }
    private val endpoint = TvEndpoint(server.hostName, server.port)

    @AfterTest fun stop() = server.close()

    /** The TV's side: says hello as the socket opens and answers each message with "pong". */
    private fun tvAnswers() = server.enqueue(
        MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"t":"welcome"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                webSocket.send("""{"t":"pong"}""")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
        }).build(),
    )

    @Test fun pinnedToTheTvsFingerprintItConnects() = runBlocking {
        tvAnswers()
        val socket = withTimeout(5_000) { openTvSocket(endpoint, fingerprint) }

        assertEquals("""{"t":"welcome"}""", withTimeout(5_000) { socket.incoming.receive() })
        socket.send("""{"t":"ping"}""")
        assertEquals("""{"t":"pong"}""", withTimeout(5_000) { socket.incoming.receive() })
        socket.close()
    }

    @Test fun pinnedToAnotherCertificateItsRefused() = runBlocking {
        // What a reinstalled TV looks like to a phone paired before.
        tvAnswers()
        assertFailsWith<Exception> { withTimeout(5_000) { openTvSocket(endpoint, "A".repeat(43)) } }
        Unit
    }

    @Test fun withNoPinTheFirstCertificateIsTakenAndItsFingerprintGiven() = runBlocking {
        tvAnswers()
        var seen: String? = null
        val socket = withTimeout(5_000) { openTvSocket(endpoint, pinned = null) { seen = it } }

        assertEquals(fingerprint, seen)
        socket.close()
    }

    @Test fun logosComeOverTheSamePinnedConnection() = runBlocking {
        server.enqueue(MockResponse.Builder().body(Buffer().write(byteArrayOf(1, 2, 3))).build())
        assertEquals(listOf<Byte>(1, 2, 3), fetchFromTv(endpoint, fingerprint, "/remote/logo/tv%3A105")?.toList())
        assertEquals("/remote/logo/tv%3A105", server.takeRequest().target)

        server.enqueue(MockResponse.Builder().body("x").build())
        assertNull(fetchFromTv(endpoint, "A".repeat(43), "/remote/logo/tv%3A105"))
    }
}
