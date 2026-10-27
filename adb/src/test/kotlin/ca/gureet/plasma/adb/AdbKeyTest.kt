// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Plasma's ADB key against adb's own: the same key text, signatures and certificate adbd expects. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AdbKeyTest {
    /** A throwaway key made for this test with `adb keygen` (platform-tools 37.0.1), and adb's text for it. */
    private val adbKey: AdbKey = AdbKey.of(pem(resource("adbkey")))
    private val adbText: String = resource("adbkey.pub").trim()

    @Test fun theKeyTextIsAdbsOwn() {
        // adb named it "@vm"; the layout and its Base64 must match byte for byte.
        assertEquals(adbText, adbKey.publicKeyText("@vm"))
    }

    @Test fun whatIsSentEndsInANul() {
        val bytes = adbKey.publicKeyBytes("Plasma")
        assertEquals(0, bytes.last().toInt())
        assertEquals(adbText.substringBefore(' ') + " Plasma", String(bytes, 0, bytes.size - 1, Charsets.UTF_8))
    }

    @Test fun aTokenIsSignedAsADigest() {
        val token = ByteArray(20) { it.toByte() }
        val signature = adbKey.sign(token)
        assertEquals(256, signature.size)
        // Opened with the public key: PKCS #1 v1.5 padding, SHA-1's DigestInfo, then the token itself.
        val block = Cipher.getInstance("RSA/ECB/NoPadding").run {
            init(Cipher.DECRYPT_MODE, adbKey.publicKey)
            doFinal(signature)
        }
        assertEquals(0, block[0].toInt())
        assertEquals(1, block[1].toInt())
        assertTrue((2 until 220).all { block[it] == 0xFF.toByte() })
        assertEquals(0, block[220].toInt())
        val digestInfo = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2B, 0x0E, 0x03, 0x02, 0x1A, 0x05, 0x00, 0x04, 0x14)
        assertArrayEquals(digestInfo + token, block.copyOfRange(221, 256))
    }

    @Test fun theCertificateIsSelfSignedForTheKey() {
        val now = Instant.parse("2026-10-24T08:00:00Z")
        val certificate = adbKey.selfSigned(now)
        certificate.verify(adbKey.publicKey)
        assertEquals(adbKey.publicKey, certificate.publicKey)
        assertEquals(certificate.subjectX500Principal, certificate.issuerX500Principal)
        assertEquals("CN=Adb,O=Android,C=US", certificate.subjectX500Principal.name)
        assertEquals(BigInteger.ONE, certificate.serialNumber)
        assertEquals(3, certificate.version)
        assertEquals(now, certificate.notBefore.toInstant())
        assertEquals(Instant.parse("2036-10-21T08:00:00Z"), certificate.notAfter.toInstant())
        // A CA (basic constraints), for signatures and certificates (key usage).
        assertTrue(certificate.basicConstraints >= 0)
        assertTrue(certificate.keyUsage[0] && certificate.keyUsage[5] && certificate.keyUsage[6])
        assertEquals("SHA256withRSA", certificate.sigAlgName)
    }

    @Test fun aKeptKeyComesBackTheSame() {
        val fresh = AdbKey.generate()
        val kept = AdbKey.of(fresh.encoded())
        assertEquals(fresh.publicKey, kept.publicKey)
        assertEquals(fresh.publicKeyText("Plasma"), kept.publicKeyText("Plasma"))
        assertArrayEquals(digest(fresh.sign(ByteArray(20))), digest(kept.sign(ByteArray(20))))
    }

    @Test fun derWritesLongLengthsAndLargeArcs() {
        // 300 bytes need two length bytes; 113549 needs three base-128 digits.
        val long = Der.octets(ByteArray(300))
        assertArrayEquals(byteArrayOf(0x04, 0x82.toByte(), 0x01, 0x2C), long.copyOfRange(0, 4))
        assertArrayEquals(
            byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B),
            Der.oid("1.2.840.113549.1.1.11"),
        )
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun resource(name: String): String = javaClass.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun pem(text: String): ByteArray =
        Base64.getMimeDecoder().decode(text.lines().filterNot { it.startsWith("-----") }.joinToString(""))
}
