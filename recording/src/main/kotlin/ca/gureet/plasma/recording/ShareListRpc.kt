// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The folders a server shares, for the network folder setup's list (brief 5.6), asked the way
 * Windows asks: NetrShareEnum at level 1 (MS-SRVS 3.1.4.8) over DCE/RPC on the `srvsvc` pipe
 * (MS-RPCE), which smbj leaves out. The requests are made, and the replies read, here; [SmbShares]
 * sends them.
 */
internal object ShareListRpc {
    /** A share as NetrShareEnum lists it: its name, and its type (MS-SRVS 2.2.2.4). */
    data class Share(val name: String, val type: Int) {
        /** A folder: not a printer, the IPC pipe, or an administrative share such as `C$`. */
        val isFolder: Boolean
            get() = type and BASE_TYPE == DISK_TREE && type and SPECIAL == 0 && !name.endsWith('$') && name.isNotBlank()
    }

    /**
     * Asks for the shares over the open `srvsvc` pipe: [transact] writes a request and reads the
     * first fragment of its reply, [read] reads what's left. The folders, A to Z. Throws
     * [IOException] when the server's answer can't be used.
     */
    fun folders(transact: (ByteArray) -> ByteArray, read: (ByteArray) -> Int): List<String> {
        bindAccepted(reply(transact(bind()), read).first())
        return shares(stubOf(reply(transact(enumRequest()), read)))
            .filter { it.isFolder }
            .map { it.name }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /** Binds to SRVSVC 3.0 with NDR (MS-RPCE 2.2.2.13), without authentication: SMB has signed in already. */
    fun bind(callId: Int = 1): ByteArray = ByteBuffer.allocate(BIND_LENGTH).order(ByteOrder.LITTLE_ENDIAN).apply {
        header(BIND, BIND_LENGTH, callId)
        putShort(MAX_FRAG.toShort())
        putShort(MAX_FRAG.toShort())
        putInt(0)
        // One presentation context: SRVSVC 3.0, in NDR 2.0.
        put(1)
        put(0)
        putShort(0)
        putShort(0)
        put(1)
        put(0)
        put(SRVSVC)
        putShort(SRVSVC_MAJOR.toShort())
        putShort(0)
        put(NDR)
        putInt(NDR_VERSION)
    }.array()

    /** Checks the server took the bind: a bind_ack whose first result is acceptance. */
    fun bindAccepted(pdu: ByteArray) {
        val type = typeOf(pdu)
        if (type != BIND_ACK) throw IOException("The server turned the bind down ($type)")
        val buffer = ByteBuffer.wrap(pdu).order(ByteOrder.LITTLE_ENDIAN)
        // After the secondary address, aligned to 4: the results.
        val addressLength = buffer.getShort(ACK_ADDRESS).toInt() and 0xFFFF
        val results = align4(ACK_ADDRESS + 2 + addressLength)
        if (results + 6 > pdu.size || pdu[results].toInt() and 0xFF == 0) throw IOException("The bind came back without a result")
        val result = buffer.getShort(results + 4).toInt() and 0xFFFF
        if (result != ACCEPTANCE) throw IOException("The server didn't accept SRVSVC ($result)")
    }

    /** NetrShareEnum at level 1, for every share at once, from the start. */
    fun enumRequest(callId: Int = 2): ByteArray {
        val stub = NdrWriter().apply {
            // ServerName: "", which the server ignores (MS-SRVS 3.1.4.8).
            u32(REFERENT)
            u32(1)
            u32(0)
            u32(1)
            u16(0)
            // InfoStruct: level 1, with an empty container for the shares.
            u32(1)
            u32(1)
            u32(REFERENT + 4)
            u32(0)
            u32(0)
            // PreferedMaximumLength: all of them.
            u32(-1)
            // ResumeHandle: from the first.
            u32(REFERENT + 8)
            u32(0)
        }.bytes()
        val length = REQUEST_HEADER + stub.size
        return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN).apply {
            header(REQUEST, length, callId)
            putInt(stub.size)
            putShort(0)
            putShort(NET_SHARE_ENUM.toShort())
            put(stub)
        }.array()
    }

    /**
     * A reply's fragments, from [first] on: a fragment can come in several reads, and a reply in
     * several fragments, the last one flagged.
     */
    fun reply(first: ByteArray, read: (ByteArray) -> Int): List<ByteArray> {
        val fragments = ArrayList<ByteArray>()
        var pending = first
        while (true) {
            while (pending.size < COMMON_HEADER || pending.size < fragLength(pending)) {
                val more = ByteArray(MAX_FRAG)
                val count = read(more)
                if (count <= 0) throw IOException("The reply stopped short")
                pending += more.copyOf(count)
            }
            val length = fragLength(pending)
            if (length < COMMON_HEADER) throw IOException("A fragment of $length bytes")
            fragments += pending.copyOf(length)
            pending = pending.copyOfRange(length, pending.size)
            if (fragments.last()[FLAGS].toInt() and LAST_FRAG != 0) return fragments
            if (fragments.size >= MAX_FRAGMENTS) throw IOException("The reply doesn't end")
        }
    }

    /** The NDR a reply carries, from its response fragments; a fault throws. */
    fun stubOf(fragments: List<ByteArray>): ByteArray {
        val stub = ByteArrayOutputStream()
        for (fragment in fragments) {
            when (val type = typeOf(fragment)) {
                RESPONSE -> {
                    val end = fragLength(fragment) - authLength(fragment)
                    if (end < RESPONSE_HEADER) throw IOException("A response of ${fragment.size} bytes")
                    stub.write(fragment, RESPONSE_HEADER, end - RESPONSE_HEADER)
                }
                FAULT -> {
                    val status = if (fragment.size >= RESPONSE_HEADER + 4) ByteBuffer.wrap(fragment).order(ByteOrder.LITTLE_ENDIAN).getInt(RESPONSE_HEADER) else 0
                    throw IOException("The server answered with fault 0x${Integer.toHexString(status)}")
                }
                else -> throw IOException("The server answered with PDU type $type")
            }
        }
        return stub.toByteArray()
    }

    /** NetrShareEnum's answer at level 1: the shares, in the server's order. */
    fun shares(stub: ByteArray): List<Share> = try {
        val reader = NdrReader(stub)
        val level = reader.u32()
        val arm = reader.u32()
        if (level != 1 || arm != 1) throw IOException("Shares at level $level")
        val shares = ArrayList<Share>()
        // The container, then its array: each share's name and remark come after all of them.
        if (reader.u32() != 0) {
            reader.u32()
            if (reader.u32() != 0) {
                val count = reader.u32()
                if (count < 0 || count > MAX_SHARES) throw IOException("$count shares")
                val entries = List(count) { Triple(reader.u32(), reader.u32(), reader.u32()) }
                for ((name, type, remark) in entries) {
                    val shareName = if (name != 0) reader.string() else null
                    if (remark != 0) reader.string()
                    if (shareName != null) shares += Share(shareName, type)
                }
            }
        }
        // TotalEntries, then ResumeHandle, then what NetrShareEnum returned.
        reader.u32()
        if (reader.u32() != 0) reader.u32()
        val status = reader.u32()
        if (status != 0 && status != ERROR_MORE_DATA) throw IOException("NetrShareEnum returned $status")
        shares
    } catch (garbled: IndexOutOfBoundsException) {
        throw IOException("The share list came back garbled", garbled)
    }

    private fun ByteBuffer.header(type: Int, length: Int, callId: Int) {
        put(RPC_VERSION.toByte())
        put(0)
        put(type.toByte())
        put((FIRST_FRAG or LAST_FRAG).toByte())
        // Little-endian, ASCII, IEEE floating point.
        put(LITTLE_ENDIAN_DREP.toByte())
        put(0)
        put(0)
        put(0)
        putShort(length.toShort())
        putShort(0)
        putInt(callId)
    }

    private fun typeOf(pdu: ByteArray): Int = if (pdu.size > TYPE) pdu[TYPE].toInt() and 0xFF else -1

    private fun fragLength(pdu: ByteArray): Int = (pdu[FRAG_LENGTH].toInt() and 0xFF) or ((pdu[FRAG_LENGTH + 1].toInt() and 0xFF) shl 8)

    private fun authLength(pdu: ByteArray): Int {
        val length = (pdu[AUTH_LENGTH].toInt() and 0xFF) or ((pdu[AUTH_LENGTH + 1].toInt() and 0xFF) shl 8)
        // The verifier and its 8-byte header, when there is one.
        return if (length == 0) 0 else length + AUTH_HEADER
    }

    private fun align4(at: Int): Int = (at + 3) and 3.inv()

    /** NDR's little-endian scalars, each aligned to its size from the start of the stub. */
    private class NdrWriter {
        private val out = ByteArrayOutputStream()

        fun u16(value: Int) {
            align(2)
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }

        fun u32(value: Int) {
            align(4)
            for (shift in 0 until 32 step 8) out.write((value shr shift) and 0xFF)
        }

        fun bytes(): ByteArray = out.toByteArray()

        private fun align(size: Int) {
            while (out.size() % size != 0) out.write(0)
        }
    }

    private class NdrReader(private val bytes: ByteArray) {
        private var at = 0

        fun u32(): Int {
            at = (at + 3) and 3.inv()
            if (at + 4 > bytes.size) throw IndexOutOfBoundsException("u32 at $at of ${bytes.size}")
            val value = (bytes[at].toInt() and 0xFF) or
                ((bytes[at + 1].toInt() and 0xFF) shl 8) or
                ((bytes[at + 2].toInt() and 0xFF) shl 16) or
                ((bytes[at + 3].toInt() and 0xFF) shl 24)
            at += 4
            return value
        }

        /** A conformant varying UTF-16 string, without its terminator. */
        fun string(): String {
            val max = u32()
            u32()
            val actual = u32()
            if (actual < 0 || actual > max || at + actual * 2 > bytes.size) throw IndexOutOfBoundsException("A string of $actual")
            val text = String(bytes, at, actual * 2, Charsets.UTF_16LE)
            at += actual * 2
            return text.trimEnd('\u0000')
        }
    }

    private const val RPC_VERSION = 5
    private const val LITTLE_ENDIAN_DREP = 0x10
    private const val BIND = 11
    private const val BIND_ACK = 12
    private const val REQUEST = 0
    private const val RESPONSE = 2
    private const val FAULT = 3
    private const val FIRST_FRAG = 0x01
    private const val LAST_FRAG = 0x02

    /** Where a PDU's header keeps its type, flags, length and authentication's length. */
    private const val TYPE = 2
    private const val FLAGS = 3
    private const val FRAG_LENGTH = 8
    private const val AUTH_LENGTH = 10
    private const val AUTH_HEADER = 8
    private const val COMMON_HEADER = 16
    private const val REQUEST_HEADER = 24
    private const val RESPONSE_HEADER = 24
    private const val BIND_LENGTH = 72

    /** Where a bind_ack's secondary address starts: after the frame sizes and the association group. */
    private const val ACK_ADDRESS = 24
    private const val ACCEPTANCE = 0

    /** The largest fragment either side sends, as Windows has it. */
    const val MAX_FRAG = 4280
    private const val MAX_FRAGMENTS = 256
    private const val MAX_SHARES = 4096

    /** NetrShareEnum's number in SRVSVC. */
    private const val NET_SHARE_ENUM = 15
    private const val REFERENT = 0x00020000
    private const val ERROR_MORE_DATA = 234

    /** Share types (MS-SRVS 2.2.2.4): a folder is the disk tree, and the special bit marks C$ and the like. */
    private const val BASE_TYPE = 0x0F
    private const val DISK_TREE = 0
    private const val SPECIAL = Int.MIN_VALUE

    private const val SRVSVC_MAJOR = 3
    private const val NDR_VERSION = 2

    /** 4b324fc8-1670-01d3-1278-5a47bf6ee188, as a GUID is sent. */
    private val SRVSVC = byteArrayOf(
        0xc8.toByte(), 0x4f, 0x32, 0x4b, 0x70, 0x16, 0xd3.toByte(), 0x01,
        0x12, 0x78, 0x5a, 0x47, 0xbf.toByte(), 0x6e, 0xe1.toByte(), 0x88.toByte(),
    )

    /** 8a885d04-1ceb-11c9-9fe8-08002b104860: NDR, the transfer syntax. */
    private val NDR = byteArrayOf(
        0x04, 0x5d, 0x88.toByte(), 0x8a.toByte(), 0xeb.toByte(), 0x1c, 0xc9.toByte(), 0x11,
        0x9f.toByte(), 0xe8.toByte(), 0x08, 0x00, 0x2b, 0x10, 0x48, 0x60,
    )
}
