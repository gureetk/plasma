// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Just enough DER to write a self-signed certificate (X.690). */
internal object Der {
    fun sequence(vararg parts: ByteArray): ByteArray = tlv(SEQUENCE, join(parts))

    fun set(vararg parts: ByteArray): ByteArray = tlv(SET, join(parts))

    fun integer(value: BigInteger): ByteArray = tlv(INTEGER, value.toByteArray())

    fun integer(value: Long): ByteArray = integer(BigInteger.valueOf(value))

    fun boolean(value: Boolean): ByteArray = tlv(BOOLEAN, byteArrayOf(if (value) 0xFF.toByte() else 0))

    fun nothing(): ByteArray = byteArrayOf(NULL.toByte(), 0)

    fun utf8(text: String): ByteArray = tlv(UTF8_STRING, text.toByteArray(Charsets.UTF_8))

    fun printable(text: String): ByteArray = tlv(PRINTABLE_STRING, text.toByteArray(Charsets.US_ASCII))

    fun octets(bytes: ByteArray): ByteArray = tlv(OCTET_STRING, bytes)

    /** A BIT STRING of whole bytes, or with [unusedBits] at the end of the last one unused. */
    fun bits(bytes: ByteArray, unusedBits: Int = 0): ByteArray = tlv(BIT_STRING, byteArrayOf(unusedBits.toByte()) + bytes)

    /** A time as RFC 5280 wants it: UTCTime before 2050, GeneralizedTime from then on. */
    fun time(instant: Instant): ByteArray {
        val utc = instant.atOffset(ZoneOffset.UTC)
        return if (utc.year < UTC_TIME_UNTIL) {
            tlv(UTC_TIME, utc.format(UTC_TIME_FORMAT).toByteArray(Charsets.US_ASCII))
        } else {
            tlv(GENERALIZED_TIME, utc.format(GENERALIZED_TIME_FORMAT).toByteArray(Charsets.US_ASCII))
        }
    }

    /** An object identifier, as "1.2.840.113549.1.1.11". */
    fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((arcs[0] * OID_FIRST + arcs[1]).toInt())
        for (arc in arcs.drop(2)) {
            var rest = arc
            val groups = ArrayList<Int>()
            do {
                groups += (rest and SEVEN_BITS).toInt()
                rest = rest shr 7
            } while (rest > 0)
            for (i in groups.indices.reversed()) out.write(if (i > 0) groups[i] or MORE else groups[i])
        }
        return tlv(OID, out.toByteArray())
    }

    /** Content tagged `[n]`, explicitly: as a certificate's version and extensions are. */
    fun explicit(tag: Int, content: ByteArray): ByteArray = tlv(CONTEXT_CONSTRUCTED or tag, content)

    fun tlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val length = content.size
        if (length < LONG_FORM) {
            out.write(length)
        } else {
            val bytes = BigInteger.valueOf(length.toLong()).toByteArray().dropWhile { it == 0.toByte() }
            out.write(LONG_FORM or bytes.size)
            out.write(bytes.toByteArray())
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun join(parts: Array<out ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for (part in parts) out.write(part)
        return out.toByteArray()
    }

    private const val BOOLEAN = 0x01
    private const val INTEGER = 0x02
    private const val BIT_STRING = 0x03
    private const val OCTET_STRING = 0x04
    private const val NULL = 0x05
    private const val OID = 0x06
    private const val UTF8_STRING = 0x0C
    private const val PRINTABLE_STRING = 0x13
    private const val UTC_TIME = 0x17
    private const val GENERALIZED_TIME = 0x18
    private const val SEQUENCE = 0x30
    private const val SET = 0x31
    private const val CONTEXT_CONSTRUCTED = 0xA0
    private const val LONG_FORM = 0x80
    private const val MORE = 0x80
    private const val SEVEN_BITS = 0x7FL
    private const val OID_FIRST = 40
    private const val UTC_TIME_UNTIL = 2050
    private val UTC_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'")
    private val GENERALIZED_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'")
}
