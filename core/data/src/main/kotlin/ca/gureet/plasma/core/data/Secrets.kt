// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps credentials encrypted at rest (brief 6.10). */
interface SecretBox {
    fun seal(plain: String): String

    fun open(sealed: String): String
}

/**
 * AES-GCM with a key the Android Keystore holds and never hands out, so a copy of Plasma's files
 * doesn't carry readable passwords.
 */
class KeystoreSecretBox(private val alias: String = "plasma-credentials") : SecretBox {
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sealed)
    }

    override fun open(sealed: String): String {
        val bytes = Base64.getDecoder().decode(sealed)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * For the many small secrets a playlist brings, such as channel addresses with a username and
 * password in them (brief 6.10): AES-GCM in software, quick enough for thousands of channels, with
 * a data key the [SecretBox] keeps sealed.
 */
class FieldCipher internal constructor(private val key: SecretKey) {
    private val ciphers = ThreadLocal.withInitial { Cipher.getInstance(TRANSFORMATION) }
    private val random = SecureRandom()

    fun seal(plain: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = ciphers.get().apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    fun open(sealed: String): String {
        val bytes = Base64.getDecoder().decode(sealed)
        val cipher = ciphers.get().apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES)) }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** Plasma's secrets: one at a time with the Keystore, or many at once with the [FieldCipher]. */
class Vault(private val secrets: SecretBox, private val settings: SettingsStore) : SecretBox by secrets {
    private val lock = Mutex()

    @Volatile
    private var fields: FieldCipher? = null

    /** The field cipher, its data key made the first time and kept sealed in the settings. */
    suspend fun fields(): FieldCipher = fields ?: lock.withLock {
        fields ?: run {
            val sealed = settings.sealedDataKey()
            val raw = if (sealed != null) {
                Base64.getDecoder().decode(secrets.open(sealed))
            } else {
                ByteArray(DATA_KEY_BYTES).also(SecureRandom()::nextBytes).also { settings.setSealedDataKey(secrets.seal(Base64.getEncoder().encodeToString(it))) }
            }
            FieldCipher(SecretKeySpec(raw, "AES")).also { fields = it }
        }
    }

    private companion object {
        const val DATA_KEY_BYTES = 32
    }
}
