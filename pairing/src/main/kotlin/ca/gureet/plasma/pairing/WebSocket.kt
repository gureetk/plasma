// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64

/**
 * A WebSocket (RFC 6455) as far as the phone remote needs one (brief 5.21): text messages both
 * ways, ping and pong, and close. A phone's frames must be masked; nothing it sends may be bigger
 * than [maxMessage].
 */
internal class WebSocket(
    private val input: InputStream,
    private val output: OutputStream,
    private val maxMessage: Int = MAX_MESSAGE,
) {
    @Volatile private var closed = false

    /** The next text message; null once the phone closes, or breaks the rules. */
    fun read(): String? {
        val message = ByteArrayOutputStream()
        var inText = false
        while (!closed) {
            val first = input.read()
            if (first < 0) return null
            val second = readByte()
            val fin = first and FIN != 0
            val opcode = first and OPCODE
            val masked = second and MASKED != 0
            var length = (second and LENGTH).toLong()
            if (length == LENGTH_16.toLong()) {
                length = (readByte().toLong() shl 8) or readByte().toLong()
            } else if (length == LENGTH_64.toLong()) {
                length = 0
                repeat(Long.SIZE_BYTES) { length = (length shl 8) or readByte().toLong() }
            }
            // A phone always masks; anything else isn't one.
            if (!masked || length < 0 || length > maxMessage) return refuse()
            val mask = ByteArray(MASK_BYTES).also(::readFully)
            val payload = ByteArray(length.toInt()).also(::readFully)
            for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % MASK_BYTES].toInt()).toByte()
            when (opcode) {
                TEXT, CONTINUATION -> {
                    // A continuation only continues a message, and a new one waits for the last to end.
                    if ((opcode == CONTINUATION) != inText) return refuse()
                    inText = !fin
                    if (message.size() + payload.size > maxMessage) return refuse()
                    message.write(payload)
                    if (fin) return String(message.toByteArray(), Charsets.UTF_8)
                }
                PING -> send(PONG, payload)
                PONG -> Unit
                CLOSE -> {
                    send(CLOSE, ByteArray(0))
                    closed = true
                    return null
                }
                else -> return refuse()
            }
        }
        return null
    }

    /** Sends [text] as one message. */
    fun send(text: String) = send(TEXT, text.toByteArray(Charsets.UTF_8))

    /** Says goodbye, if the connection's still there. */
    fun close() {
        if (closed) return
        runCatching { send(CLOSE, ByteArray(0)) }
        closed = true
    }

    @Synchronized
    private fun send(opcode: Int, payload: ByteArray) {
        if (closed) throw IOException("Closed")
        val head = ByteArrayOutputStream()
        head.write(FIN or opcode)
        when {
            payload.size < LENGTH_16 -> head.write(payload.size)
            payload.size <= MAX_16 -> {
                head.write(LENGTH_16)
                head.write(payload.size ushr 8)
                head.write(payload.size and 0xFF)
            }
            else -> {
                head.write(LENGTH_64)
                for (shift in (Long.SIZE_BYTES - 1) downTo 0) head.write(((payload.size.toLong() ushr (shift * 8)) and 0xFF).toInt())
            }
        }
        output.write(head.toByteArray())
        output.write(payload)
        output.flush()
    }

    /** Ends a connection that broke the rules (RFC 6455: 1002, protocol error). */
    private fun refuse(): String? {
        runCatching { send(CLOSE, byteArrayOf((PROTOCOL_ERROR ushr 8).toByte(), (PROTOCOL_ERROR and 0xFF).toByte())) }
        closed = true
        return null
    }

    private fun readByte(): Int = input.read().also { if (it < 0) throw EOFException() }

    private fun readFully(into: ByteArray) {
        var at = 0
        while (at < into.size) {
            val count = input.read(into, at, into.size - at)
            if (count < 0) throw EOFException()
            at += count
        }
    }

    companion object {
        /** A phone's messages are small: a key, a channel, a line of text. */
        const val MAX_MESSAGE = 16 * 1024

        private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val FIN = 0x80
        private const val OPCODE = 0x0F
        private const val MASKED = 0x80
        private const val LENGTH = 0x7F
        private const val LENGTH_16 = 126
        private const val LENGTH_64 = 127
        private const val MAX_16 = 0xFFFF
        private const val MASK_BYTES = 4
        private const val CONTINUATION = 0x0
        private const val TEXT = 0x1
        private const val CLOSE = 0x8
        private const val PING = 0x9
        private const val PONG = 0xA
        private const val PROTOCOL_ERROR = 1002

        /** Sec-WebSocket-Accept for a phone's Sec-WebSocket-Key. */
        fun accept(key: String): String =
            Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key.trim() + GUID).toByteArray(Charsets.ISO_8859_1)))

        /** The answer that turns an HTTP request into a WebSocket. */
        fun handshake(key: String): String =
            "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept(key)}\r\n\r\n"
    }
}
