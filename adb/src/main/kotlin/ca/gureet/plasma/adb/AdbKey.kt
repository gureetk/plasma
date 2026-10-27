// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher

/**
 * Plasma's ADB key (brief 5.13): an RSA key the TV learns once, by pairing with wireless debugging
 * or by "Allow debugging?" over the older network debugging, and knows Plasma by afterwards.
 */
class AdbKey private constructor(internal val privateKey: PrivateKey, val publicKey: RSAPublicKey) {
    /** The key as adb writes it: Android's own layout, Base64, then a space and [name]. */
    fun publicKeyText(name: String): String = Base64.getEncoder().encodeToString(androidLayout(publicKey)) + " " + name

    /** What AUTH and pairing send: the text, ending in a NUL as adb's C strings do. */
    internal fun publicKeyBytes(name: String): ByteArray = (publicKeyText(name) + "\u0000").toByteArray(Charsets.UTF_8)

    /**
     * Signs an AUTH token as adb does: the token taken for a SHA-1 digest, in a PKCS #1 v1.5
     * signature (adbd checks it with `RSA_verify(NID_sha1, …)`).
     */
    internal fun sign(token: ByteArray): ByteArray {
        val block = ByteArray(MODULUS_BYTES)
        val digest = SHA1_DIGEST_INFO + token
        // 00 01 FF…FF 00, then the digest, filling the modulus.
        block[1] = 1
        val padEnd = MODULUS_BYTES - digest.size - 1
        for (i in 2 until padEnd) block[i] = 0xFF.toByte()
        digest.copyInto(block, padEnd + 1)
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        return cipher.doFinal(block)
    }

    /** A self-signed certificate for the key, which TLS presents to the TV (wireless debugging and pairing). */
    val certificate: X509Certificate by lazy { selfSigned(Instant.now()) }

    /** The private key, PKCS #8, to keep sealed between runs. */
    fun encoded(): ByteArray = privateKey.encoded

    /** Built as adb's own is: C=US, O=Android, CN=Adb, good for ten years, a CA for itself. */
    internal fun selfSigned(now: Instant): X509Certificate {
        val name = Der.sequence(
            Der.set(Der.sequence(Der.oid(OID_COUNTRY), Der.printable("US"))),
            Der.set(Der.sequence(Der.oid(OID_ORGANIZATION), Der.utf8("Android"))),
            Der.set(Der.sequence(Der.oid(OID_COMMON_NAME), Der.utf8("Adb"))),
        )
        val algorithm = Der.sequence(Der.oid(OID_SHA256_WITH_RSA), Der.nothing())
        val extensions = Der.explicit(
            EXTENSIONS_TAG,
            Der.sequence(
                // Basic constraints: a CA, critical.
                Der.sequence(Der.oid(OID_BASIC_CONSTRAINTS), Der.boolean(true), Der.octets(Der.sequence(Der.boolean(true)))),
                // Key usage: digital signature, certificate and CRL signing, critical.
                Der.sequence(Der.oid(OID_KEY_USAGE), Der.boolean(true), Der.octets(Der.bits(byteArrayOf(KEY_USAGE_BITS.toByte()), unusedBits = 1))),
            ),
        )
        val tbs = Der.sequence(
            Der.explicit(VERSION_TAG, Der.integer(VERSION_3)),
            Der.integer(1),
            algorithm,
            name,
            Der.sequence(Der.time(now), Der.time(now + VALIDITY)),
            name,
            publicKey.encoded,
            extensions,
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(tbs)
            sign()
        }
        val der = Der.sequence(tbs, algorithm, Der.bits(signature))
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }

    companion object {
        /** A new 2048-bit key, as adb makes. */
        fun generate(): AdbKey {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(MODULUS_BYTES * Byte.SIZE_BITS) }.generateKeyPair()
            return AdbKey(pair.private, pair.public as RSAPublicKey)
        }

        /** The key kept from before, from its [pkcs8] private key. */
        fun of(pkcs8: ByteArray): AdbKey {
            val factory = KeyFactory.getInstance("RSA")
            val private = factory.generatePrivate(PKCS8EncodedKeySpec(pkcs8)) as RSAPrivateCrtKey
            val public = factory.generatePublic(RSAPublicKeySpec(private.modulus, private.publicExponent)) as RSAPublicKey
            return AdbKey(private, public)
        }

        /**
         * Android's layout for an adb key (`android_pubkey_encode`): the modulus size in 32-bit
         * words, −1/n mod 2³², the modulus, R² mod n (R = 2²⁰⁴⁸), and the exponent, all little-endian.
         */
        internal fun androidLayout(key: RSAPublicKey): ByteArray {
            val n = key.modulus
            require(n.bitLength() <= MODULUS_BYTES * Byte.SIZE_BITS) { "adb keys are 2048-bit" }
            val word = BigInteger.ONE.shiftLeft(Int.SIZE_BITS)
            val n0inv = word - n.mod(word).modInverse(word)
            val rr = BigInteger.ONE.shiftLeft(MODULUS_BYTES * Byte.SIZE_BITS).modPow(BigInteger.valueOf(2), n)
            return ByteBuffer.allocate(LAYOUT_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(MODULUS_BYTES / Int.SIZE_BYTES)
                .putInt(n0inv.toInt())
                .put(littleEndian(n))
                .put(littleEndian(rr))
                .putInt(key.publicExponent.toInt())
                .array()
        }

        private fun littleEndian(value: BigInteger): ByteArray {
            val big = value.toByteArray().dropWhile { it == 0.toByte() }
            val out = ByteArray(MODULUS_BYTES)
            for ((i, byte) in big.reversed().withIndex()) out[i] = byte
            return out
        }

        private const val MODULUS_BYTES = 256
        private const val LAYOUT_BYTES = 3 * Int.SIZE_BYTES + 2 * MODULUS_BYTES

        /** DigestInfo for SHA-1 (RFC 8017, 9.2), which the token follows. */
        private val SHA1_DIGEST_INFO = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2B, 0x0E, 0x03, 0x02, 0x1A, 0x05, 0x00, 0x04, 0x14)

        private const val OID_COUNTRY = "2.5.4.6"
        private const val OID_ORGANIZATION = "2.5.4.10"
        private const val OID_COMMON_NAME = "2.5.4.3"
        private const val OID_SHA256_WITH_RSA = "1.2.840.113549.1.1.11"
        private const val OID_BASIC_CONSTRAINTS = "2.5.29.19"
        private const val OID_KEY_USAGE = "2.5.29.15"
        private const val VERSION_TAG = 0
        private const val EXTENSIONS_TAG = 3
        private const val VERSION_3 = 2L

        /** Digital signature (bit 0), key certificate signing (5) and CRL signing (6). */
        private const val KEY_USAGE_BITS = 0x86
        private val VALIDITY: Duration = Duration.ofDays(3650)
    }
}
