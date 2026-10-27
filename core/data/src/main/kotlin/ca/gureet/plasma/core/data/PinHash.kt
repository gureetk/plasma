// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A PIN kept as a salted hash (brief 5.12 and 6.10): PBKDF2 with HMAC-SHA256, as
 * "pbkdf2:<iterations>:<salt>:<hash>", the salt and the hash in Base64. The PIN itself is never kept.
 */
internal object PinHash {
    fun of(pin: String, iterations: Int = ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        return listOf(SCHEME, iterations.toString(), encode(salt), encode(derive(pin, salt, iterations))).joinToString(":")
    }

    /** Whether [pin] is the one [stored] was made from. */
    fun matches(stored: String, pin: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != PARTS || parts[0] != SCHEME) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = decode(parts[2]) ?: return false
        val hash = decode(parts[3]) ?: return false
        return MessageDigest.isEqual(derive(pin, salt, iterations), hash)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance(ALGORITHM).generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)).encoded

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray? = runCatching { Base64.getDecoder().decode(text) }.getOrNull()

    private const val SCHEME = "pbkdf2"
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10_000
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256
    private const val PARTS = 4
}
