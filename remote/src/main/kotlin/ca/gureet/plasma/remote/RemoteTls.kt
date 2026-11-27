// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.remote

import java.net.Socket
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager

/**
 * The TV's identity on the Plasma Remote apps' TLS listener (brief 5.22): a server context that
 * holds the TV's own key and self-signed certificate, and the certificate's [fingerprint], which
 * the phone remote's QR code carries and the apps pin. On the TV the key is made and kept by
 * Android's Keystore, so it never leaves it and goes with Plasma's data: a reinstalled TV has
 * another certificate, and the apps ask to pair again.
 */
class RemoteTls(val context: SSLContext, val fingerprint: String) {
    companion object {
        /** A server context with [key] and its [certificate] alone. */
        fun of(key: PrivateKey, certificate: X509Certificate): RemoteTls {
            val context = SSLContext.getInstance("TLS").apply { init(arrayOf(OneKey(key, certificate)), null, null) }
            return RemoteTls(context, fingerprintOf(certificate))
        }

        /** The SHA-256 of [certificate]'s DER encoding, in base64url without padding: what the apps pin. */
        fun fingerprintOf(certificate: X509Certificate): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(certificate.encoded))

        /** TLS 1.3 and 1.2 only. */
        internal val PROTOCOLS = setOf("TLSv1.3", "TLSv1.2")
    }
}

/**
 * One key and its certificate, offered for whatever kind of key the handshake asks for that
 * this one is ("EC" for an EC key). A key manager made from a key store would have to read the
 * key out of it, which Android's Keystore doesn't allow for every kind of key.
 */
private class OneKey(private val key: PrivateKey, private val certificate: X509Certificate) : X509ExtendedKeyManager() {
    private fun fits(keyType: String?) = keyType == null || keyType == key.algorithm || keyType == "${key.algorithm}_${key.algorithm}"

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = ALIAS.takeIf { fits(keyType) }

    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? = ALIAS.takeIf { fits(keyType) }

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = if (fits(keyType)) arrayOf(ALIAS) else null

    override fun getPrivateKey(alias: String?): PrivateKey? = key.takeIf { alias == ALIAS }

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? = if (alias == ALIAS) arrayOf(certificate) else null

    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

    private companion object {
        const val ALIAS = "tv"
    }
}
