// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * A TV's adbd on 127.0.0.1 for tests, answering one connection: over the older network debugging
 * (a token to sign, and keys it [knows] or is [allowed] to learn), or over wireless debugging
 * ([tls]: STLS, then TLS with the client's certificate checked against [paired]). Its shell runs
 * [shell] on each command.
 */
class FakeAdbd(
    private val tls: Boolean = false,
    private val knows: List<AdbKey> = emptyList(),
    private val allowed: Boolean = true,
    private val paired: List<AdbKey> = emptyList(),
    private val shell: (String) -> String = { "" },
) : Closeable {
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort

    /** The commands it ran, and the key text a client sent to be allowed. */
    val commands = CopyOnWriteArrayList<String>()
    val offered = AtomicReference<String?>(null)
    val failure = AtomicReference<Throwable?>(null)
    private val serverKey = AdbKey.generate()
    private val thread = Thread({ runCatching { serve() }.onFailure { failure.set(it) } }, "fake-adbd").apply { isDaemon = true; start() }

    private fun serve() {
        server.accept().use { socket ->
            socket.soTimeout = 10_000
            var wire = AdbWire(socket.getInputStream(), socket.getOutputStream())
            val hello = wire.read()
            check(hello.command == AdbMessage.CNXN) { "Expected CNXN, got $hello" }
            if (tls) {
                wire.write(AdbMessage.STLS, AdbMessage.STLS_VERSION, 0)
                check(wire.read().command == AdbMessage.STLS)
                val secure = runCatching { secure(socket) }.getOrNull() ?: return
                wire = AdbWire(secure.inputStream, secure.outputStream)
            } else {
                val token = ByteArray(20) { (it * 7).toByte() }
                wire.write(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token)
                val signed = wire.read()
                check(signed.command == AdbMessage.AUTH && signed.arg0 == AdbMessage.AUTH_SIGNATURE)
                if (knows.none { verifies(it, token, signed.payload) }) {
                    wire.write(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token)
                    val key = wire.read()
                    check(key.command == AdbMessage.AUTH && key.arg0 == AdbMessage.AUTH_PUBLIC_KEY)
                    offered.set(String(key.payload, Charsets.UTF_8))
                    if (!allowed) {
                        wire.write(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token)
                        return
                    }
                }
            }
            wire.write(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_PAYLOAD, "device::ro.product.name=fake;features=cmd".toByteArray())
            while (true) {
                val open = runCatching { wire.read() }.getOrNull() ?: return
                if (open.command != AdbMessage.OPEN) continue
                val service = String(open.payload, Charsets.UTF_8).trimEnd('\u0000')
                val command = service.removePrefix("shell:")
                commands += command
                val remoteId = 1000 + commands.size
                wire.write(AdbMessage.OKAY, remoteId, open.arg0)
                // The output in two parts, each acknowledged, then the end.
                val output = shell(command).toByteArray()
                for (part in listOf(output.copyOfRange(0, output.size / 2), output.copyOfRange(output.size / 2, output.size))) {
                    wire.write(AdbMessage.WRTE, remoteId, open.arg0, part)
                    val ack = wire.read()
                    check(ack.command == AdbMessage.OKAY && ack.arg0 == open.arg0 && ack.arg1 == remoteId) { "Expected OKAY, got $ack" }
                }
                wire.write(AdbMessage.CLSE, remoteId, open.arg0)
                val close = wire.read()
                check(close.command == AdbMessage.CLSE && close.arg0 == open.arg0 && close.arg1 == remoteId) { "Expected CLSE, got $close" }
            }
        }
    }

    /** TLS as adbd's server side: its own self-signed certificate, and the client's asked for. */
    private fun secure(socket: Socket): SSLSocket {
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null)
            setKeyEntry("adbd", serverKey.privateKey, CharArray(0), arrayOf(serverKey.certificate))
        }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, CharArray(0)) }
        val context = SSLContext.getInstance("TLSv1.3").apply { init(keys.keyManagers, arrayOf<TrustManager>(PairedOnly()), null) }
        val secure = context.socketFactory.createSocket(socket, null, socket.port, false) as SSLSocket
        secure.useClientMode = false
        secure.needClientAuth = true
        secure.startHandshake()
        return secure
    }

    private fun verifies(key: AdbKey, token: ByteArray, signature: ByteArray): Boolean {
        val block = runCatching {
            Cipher.getInstance("RSA/ECB/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key.publicKey)
                doFinal(signature)
            }
        }.getOrNull() ?: return false
        return block.copyOfRange(block.size - token.size, block.size).contentEquals(token)
    }

    fun await() {
        thread.join(TimeUnit.SECONDS.toMillis(15))
        failure.get()?.let { throw AssertionError("The fake adbd failed", it) }
    }

    override fun close() {
        runCatching { server.close() }
    }

    /** adbd takes any certificate whose key was paired, and ends the handshake for any other. */
    private inner class PairedOnly : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val key = chain?.firstOrNull()?.publicKey
            if (paired.none { it.publicKey == key }) throw CertificateException("Not paired")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
