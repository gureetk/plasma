// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
