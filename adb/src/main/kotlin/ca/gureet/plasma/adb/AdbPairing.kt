// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import android.net.ssl.SSLSockets
import android.os.Build
import com.flyfish233.crypto.spake2.Spake2Context
import com.flyfish233.crypto.spake2.Spake2Role
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLSocket

/** The code didn't match the one the TV showed, or the TV ended pairing. */
class AdbPairingFailed(message: String) : IOException(message)

/**
 * Pairing with the TV's wireless debugging (brief 5.13 and 4.24, Android 11+), as adb's own
 * `pairing_connection` does: TLS 1.3 to the pairing port, SPAKE2 on the six-digit code and the
 * TLS session's exported key, then each side sends the other its key, sealed with AES-128-GCM
 * under a key HKDF makes from SPAKE2's. Afterwards the TV knows Plasma's key, and wireless
 * debugging lets it in without asking.
 */
object AdbPairing {
    /**
     * Pairs [key], named [name] on the TV, at [host]:[port] with the [code] the TV shows.
     * [exportKey] gives the TLS session's exported key material (RFC 5705).
     *
     * @throws AdbPairingFailed when the code is wrong or the TV ends it.
     * @throws IOException when the port can't be reached.
     */
    fun pair(
        host: String,
        port: Int,
        code: String,
        key: AdbKey,
        name: String,
        exportKey: (SSLSocket) -> ByteArray = ::exportedKey,
    ) {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), CONNECT_MS)
        socket.soTimeout = READ_MS
        socket.tcpNoDelay = true
        val tls = try {
            AdbTls.client(socket, host, port, key)
        } catch (failed: IOException) {
            runCatching { socket.close() }
            throw failed
        }
        tls.use {
            val password = code.toByteArray(Charsets.UTF_8) + exportKey(tls)
            val input = DataInputStream(tls.inputStream)
            val output = DataOutputStream(tls.outputStream)
            val spake = Spake2Context(Spake2Role.Alice, CLIENT_NAME, SERVER_NAME)
            try {
                // Each side sends its SPAKE2 message, then works out the shared key.
                send(output, SPAKE2_MESSAGE, spake.generateMessage(password))
                val theirs = receive(input, SPAKE2_MESSAGE)
                val shared = try {
                    spake.processMessage(theirs)
                } catch (bad: IllegalArgumentException) {
                    throw AdbPairingFailed("The TV's pairing message didn't check out")
                }
                val cipher = PairingCipher(hkdf(shared, INFO, AES_KEY_BYTES))
                // Then its key, sealed: the TV can only open it with the same code.
                send(output, PEER_INFO, cipher.seal(peerInfo(key, name)))
                val reply = receive(input, PEER_INFO)
                cipher.open(reply) ?: throw AdbPairingFailed("Wrong pairing code")
            } finally {
                spake.destroy()
            }
        }
    }

    /** adb's PeerInfo: a type (an adb key), then the key's text, NUL-padded to 8 KiB in all. */
    internal fun peerInfo(key: AdbKey, name: String): ByteArray {
        val info = ByteArray(PEER_INFO_BYTES)
        info[0] = PEER_ADB_KEY
        val text = key.publicKeyBytes(name)
        text.copyInto(info, 1, 0, minOf(text.size, PEER_INFO_BYTES - 1))
        return info
    }

    /** A pairing packet: version 1, its type, the payload's size (big-endian), then the payload. */
    internal fun send(output: DataOutputStream, type: Byte, payload: ByteArray) {
        output.writeByte(HEADER_VERSION.toInt())
        output.writeByte(type.toInt())
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }

    internal fun receive(input: DataInputStream, type: Byte): ByteArray {
        val version: Byte
        val got: Byte
        val size: Int
        try {
            version = input.readByte()
            got = input.readByte()
            size = input.readInt()
        } catch (ended: EOFException) {
            // adbd ends the exchange rather than answer a wrong code.
            throw AdbPairingFailed("Wrong pairing code")
        }
        if (version != HEADER_VERSION) throw AdbPairingFailed("Pairing version $version isn't known")
        // A TV that refuses the code ends the exchange rather than answer.
        if (got != type || size <= 0 || size > MAX_PAYLOAD) throw AdbPairingFailed("Wrong pairing code")
        val payload = ByteArray(size)
        input.readFully(payload)
        return payload
    }

    /** HKDF with SHA-256 (RFC 5869), no salt, as BoringSSL's `HKDF()` that adb calls. */
    internal fun hkdf(secret: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(HMAC)
        // No salt is a salt of zeros as long as the hash.
        mac.init(SecretKeySpec(ByteArray(mac.macLength), HMAC))
        val pseudoRandom = mac.doFinal(secret)
        mac.init(SecretKeySpec(pseudoRandom, HMAC))
        val out = ByteArray(length)
        var block = ByteArray(0)
        var filled = 0
        var counter = 1
        while (filled < length) {
            mac.update(block)
            mac.update(info)
            mac.update(counter.toByte())
            block = mac.doFinal()
            val take = minOf(block.size, length - filled)
            block.copyInto(out, filled, 0, take)
            filled += take
            counter++
        }
        return out
    }

    /** RFC 5705's exporter on Android's own TLS, with adb's label (Android 12 and later). */
    private fun exportedKey(tls: SSLSocket): ByteArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw AdbPairingFailed("Pairing from the TV needs Android 12 or later")
        return SSLSockets.exportKeyingMaterial(tls, EXPORT_LABEL, null, EXPORT_BYTES) ?: throw AdbPairingFailed("This TV's TLS can't export a key")
    }

    /** AES-128-GCM, with a nonce that counts the messages each way. */
    internal class PairingCipher(key: ByteArray) {
        private val key = SecretKeySpec(key, "AES")
        private var sealed = 0L
        private var opened = 0L

        fun seal(plain: ByteArray): ByteArray = crypt(Cipher.ENCRYPT_MODE, nonce(sealed++), plain)

        /** The plain text, or null when it doesn't open: the other side had another key. */
        fun open(box: ByteArray): ByteArray? = try {
            crypt(Cipher.DECRYPT_MODE, nonce(opened++), box)
        } catch (wrong: javax.crypto.AEADBadTagException) {
            null
        }

        private fun crypt(mode: Int, nonce: ByteArray, input: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            return cipher.doFinal(input)
        }

        /** The counter, little-endian, in the first eight of twelve bytes. */
        private fun nonce(counter: Long): ByteArray = ByteBuffer.allocate(NONCE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(counter).array()
    }

    /** SPAKE2's names, NUL included, as adb's `pairing_auth.cpp` has them. */
    private val CLIENT_NAME = "adb pair client\u0000".toByteArray(Charsets.UTF_8)
    private val SERVER_NAME = "adb pair server\u0000".toByteArray(Charsets.UTF_8)
    private val INFO = "adb pairing_auth aes-128-gcm key".toByteArray(Charsets.UTF_8)

    /** The exporter's label, with its NUL: adb passes the whole C string. */
    private const val EXPORT_LABEL = "adb-label\u0000"
    private const val EXPORT_BYTES = 64
    private const val AES_KEY_BYTES = 16
    private const val TAG_BITS = 128
    private const val NONCE_BYTES = 12
    private const val HMAC = "HmacSHA256"

    private const val HEADER_VERSION: Byte = 1
    internal const val SPAKE2_MESSAGE: Byte = 0
    internal const val PEER_INFO: Byte = 1
    private const val PEER_ADB_KEY: Byte = 0
    internal const val PEER_INFO_BYTES = 8192
    private const val MAX_PAYLOAD = 2 * PEER_INFO_BYTES

    private const val CONNECT_MS = 5_000
    private const val READ_MS = 15_000
}
