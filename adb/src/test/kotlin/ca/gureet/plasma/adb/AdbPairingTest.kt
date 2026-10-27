// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flyfish233.crypto.spake2.Spake2Context
import com.flyfish233.crypto.spake2.Spake2Role
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pairing with wireless debugging (brief 4.24), against a fake TV playing adbd's side: SPAKE2 as
 * Bob, then each side's key sealed with AES-128-GCM. The TLS exporter, which this JVM's TLS
 * doesn't offer, is the same fixed bytes on both sides.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AdbPairingTest {
    private val key = AdbKey.generate()
    private val exported = ByteArray(64) { (it * 3).toByte() }

    /** The TV's side, showing [code]; what it learned of Plasma's key comes back in [learned]. */
    private inner class FakeTv(private val code: String) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val port: Int get() = server.localPort
        val learned = AtomicReference<String?>(null)
        val failure = AtomicReference<Throwable?>(null)
        private val tvKey = AdbKey.generate()
        private val thread = Thread({ runCatching { serve() }.onFailure { failure.set(it) } }, "fake-pairing").apply { isDaemon = true; start() }

        private fun serve() {
            server.accept().use { socket ->
                socket.soTimeout = 10_000
                val store = KeyStore.getInstance("PKCS12").apply {
                    load(null)
                    setKeyEntry("tv", tvKey.privateKey, CharArray(0), arrayOf(tvKey.certificate))
                }
                val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, CharArray(0)) }
                val context = SSLContext.getInstance("TLSv1.3").apply { init(keys.keyManagers, arrayOf<TrustManager>(Anyone), null) }
                val tls = context.socketFactory.createSocket(socket, null, socket.port, false) as SSLSocket
                tls.useClientMode = false
                tls.needClientAuth = true
                tls.startHandshake()
                val input = DataInputStream(tls.inputStream)
                val output = DataOutputStream(tls.outputStream)
                val bob = Spake2Context(Spake2Role.Bob, "adb pair server\u0000".toByteArray(), "adb pair client\u0000".toByteArray())
                val theirs = AdbPairing.receive(input, AdbPairing.SPAKE2_MESSAGE)
                AdbPairing.send(output, AdbPairing.SPAKE2_MESSAGE, bob.generateMessage(code.toByteArray() + exported))
                val shared = bob.processMessage(theirs)
                val cipher = AdbPairing.PairingCipher(AdbPairing.hkdf(shared, "adb pairing_auth aes-128-gcm key".toByteArray(), 16))
                val sealed = AdbPairing.receive(input, AdbPairing.PEER_INFO)
                // adbd ends it when Plasma's key doesn't open: the code was wrong.
                val info = cipher.open(sealed) ?: return
                learned.set(String(info, 1, info.size - 1, Charsets.UTF_8).trimEnd('\u0000'))
                AdbPairing.send(output, AdbPairing.PEER_INFO, cipher.seal(AdbPairing.peerInfo(tvKey, "tv")))
            }
        }

        fun await() {
            thread.join(15_000)
            failure.get()?.let { throw AssertionError("The fake TV failed", it) }
        }

        override fun close() {
            runCatching { server.close() }
        }
    }

    @Test fun theRightCodePairsPlasmasKey() {
        FakeTv("482913").use { tv ->
            AdbPairing.pair("127.0.0.1", tv.port, "482913", key, "Plasma", exportKey = { exported })
            tv.await()
            assertEquals(key.publicKeyText("Plasma"), tv.learned.get())
        }
    }

    @Test fun aWrongCodeFails() {
        FakeTv("482913").use { tv ->
            val failed = runCatching { AdbPairing.pair("127.0.0.1", tv.port, "111111", key, "Plasma", exportKey = { exported }) }.exceptionOrNull()
            assertTrue("Got $failed", failed is AdbPairingFailed)
            tv.await()
            assertEquals(null, tv.learned.get())
        }
    }

    @Test fun peerInfoIsEightKilobytesWithTheKeyAfterItsType() {
        val info = AdbPairing.peerInfo(key, "Plasma")
        assertEquals(AdbPairing.PEER_INFO_BYTES, info.size)
        assertEquals(0, info[0].toInt())
        val text = key.publicKeyBytes("Plasma")
        assertArrayEquals(text, info.copyOfRange(1, 1 + text.size))
        assertTrue(info.copyOfRange(1 + text.size, info.size).all { it == 0.toByte() })
    }

    @Test fun hkdfMatchesRfc5869() {
        // RFC 5869, test case 3: SHA-256, no salt, no info.
        val ikm = ByteArray(22) { 0x0B }
        val okm = AdbPairing.hkdf(ikm, ByteArray(0), 42)
        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8", okm.joinToString("") { "%02x".format(it) })
    }

    @Test fun theNonceCountsEachWay() {
        val sealer = AdbPairing.PairingCipher(ByteArray(16) { 1 })
        val opener = AdbPairing.PairingCipher(ByteArray(16) { 1 })
        val first = sealer.seal("one".toByteArray())
        val second = sealer.seal("two".toByteArray())
        assertEquals("one", String(opener.open(first)!!))
        assertEquals("two", String(opener.open(second)!!))
        // Out of turn, a message doesn't open.
        val third = sealer.seal("three".toByteArray())
        assertEquals(null, AdbPairing.PairingCipher(ByteArray(16) { 1 }).open(third))
    }

    private object Anyone : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
