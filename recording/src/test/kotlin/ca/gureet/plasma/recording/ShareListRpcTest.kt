// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The shares a server lists for the network folder setup (brief 5.6): NetrShareEnum over DCE/RPC,
 * byte for byte as MS-RPCE and MS-SRVS lay it out, against replies made here the same way.
 */
class ShareListRpcTest {
    private fun hex(text: String): ByteArray = text.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

    @Test fun theBindAsksForSrvsvcInNdr() {
        val bind = ShareListRpc.bind(callId = 1)

        assertEquals(72, bind.size)
        // Version 5.0, bind, first and last fragment, little-endian; 72 bytes, no authentication, call 1.
        assertArrayEquals(hex("05 00 0b 03 10 00 00 00 48 00 00 00 01 00 00 00"), bind.copyOfRange(0, 16))
        // 4280-byte fragments either way, a new association, one context with one transfer syntax.
        assertArrayEquals(hex("b8 10 b8 10 00 00 00 00 01 00 00 00 00 00 01 00"), bind.copyOfRange(16, 32))
        // SRVSVC 3.0, then NDR 2.0.
        assertArrayEquals(hex("c8 4f 32 4b 70 16 d3 01 12 78 5a 47 bf 6e e1 88 03 00 00 00"), bind.copyOfRange(32, 52))
        assertArrayEquals(hex("04 5d 88 8a eb 1c c9 11 9f e8 08 00 2b 10 48 60 02 00 00 00"), bind.copyOfRange(52, 72))
    }

    @Test fun netrShareEnumAsksForEveryShareAtLevelOne() {
        val request = ShareListRpc.enumRequest(callId = 2)

        assertEquals(76, request.size)
        assertArrayEquals(hex("05 00 00 03 10 00 00 00 4c 00 00 00 02 00 00 00"), request.copyOfRange(0, 16))
        // 52 bytes of NDR, context 0, NetrShareEnum (15).
        assertArrayEquals(hex("34 00 00 00 00 00 0f 00"), request.copyOfRange(16, 24))
        assertArrayEquals(
            hex(
                // ServerName: a pointer to "", padded to 4.
                "00 00 02 00 01 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 " +
                    // Level 1, union arm 1, a pointer to an empty container.
                    "01 00 00 00 01 00 00 00 04 00 02 00 00 00 00 00 00 00 00 00 " +
                    // All of them, from the start.
                    "ff ff ff ff 08 00 02 00 00 00 00 00",
            ),
            request.copyOfRange(24, 76),
        )
    }

    @Test fun theBindIsAcceptedOrNot() {
        ShareListRpc.bindAccepted(bindAck(result = 0))
        for (refused in listOf(bindAck(result = 2), bindAck(result = 0, type = 13))) {
            try {
                ShareListRpc.bindAccepted(refused)
                fail("A refused bind was taken")
            } catch (expected: IOException) {
                // As it should.
            }
        }
    }

    @Test fun theFoldersAreTheDiskSharesAnyoneCanUse() {
        val stub = sharesStub(
            listOf(
                Triple("Media", 0, "Films and recordings"),
                Triple("IPC$", 0x80000003.toInt(), "IPC Service"),
                Triple("backups", 0, null),
                Triple("Laser", 1, "The printer"),
                Triple("C$", Int.MIN_VALUE, "Default share"),
            ),
        )

        val shares = ShareListRpc.shares(stub)
        assertEquals(listOf("Media", "IPC$", "backups", "Laser", "C$"), shares.map { it.name })
        assertEquals(listOf("Media", "backups"), shares.filter { it.isFolder }.map { it.name })

        // Over the pipe: the bind, then the shares, A to Z.
        val sent = mutableListOf<Int>()
        val folders = ShareListRpc.folders(
            transact = { request ->
                sent += request[2].toInt()
                if (request[2].toInt() == 11) bindAck(result = 0) else response(stub, flags = 3)
            },
            read = { error("Nothing more to read") },
        )
        assertEquals(listOf(11, 0), sent)
        assertEquals(listOf("backups", "Media"), folders)
    }

    @Test fun aReplyCanComeInPieces() {
        val stub = sharesStub(listOf(Triple("Media", 0, "Films"), Triple("Music", 0, null)))
        val half = stub.size / 2
        val first = response(stub.copyOfRange(0, half), flags = 1)
        val second = response(stub.copyOfRange(half, stub.size), flags = 2)
        // The first fragment's tail, and the second, come in reads of their own.
        val reads = ArrayDeque(listOf(first.copyOfRange(30, first.size), second.copyOfRange(0, 10), second.copyOfRange(10, second.size)))

        val fragments = ShareListRpc.reply(first.copyOfRange(0, 30)) { buffer ->
            val next = reads.removeFirst()
            next.copyInto(buffer)
            next.size
        }

        assertEquals(2, fragments.size)
        assertEquals(listOf("Media", "Music"), ShareListRpc.shares(ShareListRpc.stubOf(fragments)).map { it.name })
    }

    @Test fun aFaultOrAGarbledReplyIsAnError() {
        val fault = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(hex("05 00 03 03 10 00 00 00"))
            putShort(32)
            putShort(0)
            putInt(2)
            putInt(4)
            putShort(0)
            putShort(0)
            // nca_s_op_rng_error.
            putInt(0x1c010002)
        }.array()
        for (wrong in listOf({ ShareListRpc.stubOf(listOf(fault)) }, { ShareListRpc.shares(byteArrayOf(1, 0, 0, 0, 1, 0)) }, { ShareListRpc.shares(sharesStub(emptyList(), status = 5)) })) {
            try {
                wrong()
                fail("It went through")
            } catch (expected: IOException) {
                // As it should.
            }
        }
    }

    /** A bind_ack for `\PIPE\srvsvc`, with [result] for the one context. */
    private fun bindAck(result: Int, type: Int = 12): ByteArray {
        val address = "\\PIPE\\srvsvc\u0000".toByteArray(Charsets.US_ASCII)
        val length = 68
        return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(5)
            put(0)
            put(type.toByte())
            put(3)
            put(hex("10 00 00 00"))
            putShort(length.toShort())
            putShort(0)
            putInt(1)
            putShort(4280)
            putShort(4280)
            putInt(0x53f0)
            putShort(address.size.toShort())
            put(address)
            // 26 + 13 = 39, aligned to 40.
            put(0)
            put(1)
            put(0)
            putShort(0)
            putShort(result.toShort())
            putShort(0)
            put(hex("04 5d 88 8a eb 1c c9 11 9f e8 08 00 2b 10 48 60 02 00 00 00"))
        }.array()
    }

    /** A response PDU carrying [stub], with [flags] for first and last fragment. */
    private fun response(stub: ByteArray, flags: Int): ByteArray {
        val length = 24 + stub.size
        return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(5)
            put(0)
            put(2)
            put(flags.toByte())
            put(hex("10 00 00 00"))
            putShort(length.toShort())
            putShort(0)
            putInt(2)
            putInt(stub.size)
            putShort(0)
            put(0)
            put(0)
            put(stub)
        }.array()
    }

    /** NetrShareEnum's answer at level 1, as a server writes it: each name and remark after the array. */
    private fun sharesStub(shares: List<Triple<String, Int, String?>>, status: Int = 0): ByteArray {
        val out = Ndr()
        out.u32(1)
        out.u32(1)
        out.u32(0x20000)
        out.u32(shares.size)
        if (shares.isEmpty()) {
            out.u32(0)
        } else {
            out.u32(0x20004)
            out.u32(shares.size)
            var referent = 0x20008
            for ((_, type, remark) in shares) {
                out.u32(referent)
                referent += 4
                out.u32(type)
                out.u32(if (remark == null) 0 else referent)
                if (remark != null) referent += 4
            }
            for ((name, _, remark) in shares) {
                out.string(name)
                if (remark != null) out.string(remark)
            }
        }
        out.u32(shares.size)
        out.u32(0)
        out.u32(status)
        return out.bytes()
    }

    private class Ndr {
        private val out = ByteArrayOutputStream()

        fun u32(value: Int) {
            while (out.size() % 4 != 0) out.write(0)
            for (shift in 0 until 32 step 8) out.write((value shr shift) and 0xFF)
        }

        fun string(text: String) {
            val count = text.length + 1
            u32(count)
            u32(0)
            u32(count)
            out.write((text + "\u0000").toByteArray(Charsets.UTF_16LE))
        }

        fun bytes(): ByteArray = out.toByteArray()
    }
}
