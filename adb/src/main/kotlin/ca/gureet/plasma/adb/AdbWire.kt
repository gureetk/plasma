// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** An ADB message: a command, its two arguments and its payload (adb's protocol.txt). */
internal class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray) {
    override fun toString(): String = "${nameOf(command)}($arg0, $arg1, ${payload.size} bytes)"

    companion object {
        const val CNXN = 0x4E584E43
        const val AUTH = 0x48545541
        const val OPEN = 0x4E45504F
        const val OKAY = 0x59414B4F
        const val CLSE = 0x45534C43
        const val WRTE = 0x45545257
        const val STLS = 0x534C5453

        /** The protocol version Plasma speaks: without checksums, which older TVs still accept. */
        const val VERSION = 0x01000001
        const val STLS_VERSION = 0x01000000

        /** The largest payload Plasma takes; the TV's own limit comes in its CNXN. */
        const val MAX_PAYLOAD = 256 * 1024

        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_PUBLIC_KEY = 3

        fun nameOf(command: Int): String = String(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(command).array(), Charsets.US_ASCII)
    }
}

/** Reads and writes ADB messages: a 24-byte little-endian header, then the payload. */
internal class AdbWire(input: InputStream, private val output: OutputStream) {
    private val input = DataInputStream(input)

    fun write(command: Int, arg0: Int, arg1: Int, payload: ByteArray = EMPTY) {
        val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(arg0)
            .putInt(arg1)
            .putInt(payload.size)
            .putInt(checksum(payload))
            .putInt(command.inv())
            .array()
        synchronized(output) {
            output.write(header + payload)
            output.flush()
        }
    }

    fun read(): AdbMessage {
        val bytes = ByteArray(HEADER_BYTES)
        input.readFully(bytes)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val command = header.getInt()
        val arg0 = header.getInt()
        val arg1 = header.getInt()
        val length = header.getInt()
        header.getInt() // The checksum: TLS and TCP already check the bytes.
        val magic = header.getInt()
        if (magic != command.inv()) throw IOException("Not an ADB message")
        if (length < 0 || length > AdbMessage.MAX_PAYLOAD * 4) throw IOException("An ADB message too large: $length bytes")
        val payload = ByteArray(length)
        input.readFully(payload)
        return AdbMessage(command, arg0, arg1, payload)
    }

    private companion object {
        const val HEADER_BYTES = 24
        val EMPTY = ByteArray(0)

        /** The sum of the payload's bytes, which the oldest TVs still check. */
        fun checksum(payload: ByteArray): Int = payload.sumOf { it.toInt() and 0xFF }
    }
}
