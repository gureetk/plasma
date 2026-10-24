// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** The TV turned Plasma's key down, or never answered "Allow debugging?". */
class AdbRefused(message: String) : IOException(message)

/**
 * A connection to this TV's adbd (brief 5.13, method 2), on which Plasma runs shell commands.
 * One command at a time; close it when done.
 */
class AdbConnection private constructor(private val socket: Socket, private val wire: AdbWire) : Closeable {
    private var nextId = 1

    /**
     * Runs [command] in the TV's shell and returns what it printed (its output and errors
     * together), once it ends.
     */
    fun shell(command: String): String = synchronized(this) { run(command) }

    private fun run(command: String): String {
        val localId = nextId++
        wire.write(AdbMessage.OPEN, localId, 0, "shell:$command\u0000".toByteArray(Charsets.UTF_8))
        val out = ByteArrayOutputStream()
        var remoteId = 0
        while (true) {
            val message = wire.read()
            // Only this stream's messages: anything else is a stream long gone.
            if (message.arg1 != localId) continue
            when (message.command) {
                AdbMessage.OKAY -> remoteId = message.arg0
                AdbMessage.WRTE -> {
                    out.write(message.payload)
                    wire.write(AdbMessage.OKAY, localId, message.arg0)
                }
                AdbMessage.CLSE -> {
                    // Opened and now ended: closed from this side too. Refused: nothing to close.
                    if (remoteId != 0) wire.write(AdbMessage.CLSE, localId, remoteId)
                    if (remoteId == 0 && message.arg0 == 0) throw IOException("The TV turned the shell down")
                    return out.toString(Charsets.UTF_8.name())
                }
            }
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        /**
         * Connects to adbd at [host]:[port] with [key], named [name] on the TV.
         *
         * Over wireless debugging the TV starts TLS and checks the key was paired. Over the older
         * network debugging it asks for a signature; a key it doesn't know is sent whole, and the
         * TV asks "Allow debugging?", which [onAsking] is told of, waiting [askMs] for the answer.
         *
         * @throws AdbRefused when the TV turns the key down or no one answers in time.
         * @throws IOException when it can't be reached.
         */
        fun connect(
            host: String,
            port: Int,
            key: AdbKey,
            name: String,
            onAsking: () -> Unit = {},
            connectMs: Int = CONNECT_MS,
            readMs: Int = READ_MS,
            askMs: Int = ASK_MS,
        ): AdbConnection {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), connectMs)
                socket.tcpNoDelay = true
                socket.soTimeout = readMs
                var wire = AdbWire(socket.getInputStream(), socket.getOutputStream())
                var secure: Socket = socket
                wire.write(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_PAYLOAD, BANNER)
                var signed = false
                var asked = false
                var overTls = false
                while (true) {
                    val message = try {
                        wire.read()
                    } catch (late: SocketTimeoutException) {
                        throw if (asked) AdbRefused("No answer to Allow debugging") else late
                    } catch (ended: SSLException) {
                        // With TLS 1.3 the TV turns an unpaired key down once the handshake is done.
                        throw if (overTls) AdbRefused("This TV isn't paired with Plasma: ${ended.message}") else ended
                    }
                    when (message.command) {
                        AdbMessage.STLS -> {
                            wire.write(AdbMessage.STLS, AdbMessage.STLS_VERSION, 0)
                            val tls = try {
                                AdbTls.client(socket, host, port, key)
                            } catch (refused: IOException) {
                                // adbd ends the handshake for a key that was never paired.
                                throw AdbRefused("This TV isn't paired with Plasma: ${refused.message}")
                            }
                            secure = tls
                            overTls = true
                            wire = AdbWire(tls.inputStream, tls.outputStream)
                        }
                        AdbMessage.AUTH -> {
                            if (message.arg0 != AdbMessage.AUTH_TOKEN) throw IOException("Unexpected $message")
                            if (!signed) {
                                signed = true
                                wire.write(AdbMessage.AUTH, AdbMessage.AUTH_SIGNATURE, 0, key.sign(message.payload))
                            } else if (!asked) {
                                // The TV doesn't know the key: it asks "Allow debugging?".
                                asked = true
                                onAsking()
                                socket.soTimeout = askMs
                                wire.write(AdbMessage.AUTH, AdbMessage.AUTH_PUBLIC_KEY, 0, key.publicKeyBytes(name))
                            } else {
                                throw AdbRefused("Allow debugging was turned down")
                            }
                        }
                        AdbMessage.CNXN -> {
                            socket.soTimeout = readMs
                            return AdbConnection(secure, wire)
                        }
                        else -> throw IOException("Unexpected $message")
                    }
                }
            } catch (failed: IOException) {
                runCatching { socket.close() }
                throw failed
            }
        }

        /** What Plasma says it is: a host, with the one feature it uses. */
        private val BANNER = "host::features=cmd".toByteArray(Charsets.UTF_8)

        private const val CONNECT_MS = 5_000
        private const val READ_MS = 30_000

        /** Time to read "Allow debugging?" and answer it. */
        private const val ASK_MS = 60_000
    }
}
