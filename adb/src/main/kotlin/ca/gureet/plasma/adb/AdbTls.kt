// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import android.annotation.SuppressLint
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

/**
 * TLS as adb uses it (wireless debugging and pairing, Android 11+): TLS 1.3, with Plasma's key and
 * its self-signed certificate as the client's. Like adb's own client, Plasma takes the TV's
 * certificate as it comes: the TV is this TV, reached on 127.0.0.1, and pairing is what proves
 * each side to the other.
 */
internal object AdbTls {
    fun context(key: AdbKey): SSLContext = SSLContext.getInstance(PROTOCOL).apply {
        init(arrayOf(KeyOf(key)), arrayOf(AnyTv), SecureRandom())
    }

    /** Starts TLS over [socket], already connected to [host]:[port], and finishes the handshake. */
    fun client(socket: Socket, host: String, port: Int, key: AdbKey): SSLSocket {
        val tls = context(key).socketFactory.createSocket(socket, host, port, true) as SSLSocket
        tls.enabledProtocols = arrayOf(PROTOCOL)
        tls.useClientMode = true
        tls.startHandshake()
        return tls
    }

    private const val PROTOCOL = "TLSv1.3"
    private const val ALIAS = "adb"

    /** Offers Plasma's key, whatever the TV asks for: adbd checks the key itself, not the certificate. */
    private class KeyOf(private val key: AdbKey) : X509ExtendedKeyManager() {
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS

        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS

        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)

        override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(key.certificate)

        override fun getPrivateKey(alias: String?): PrivateKey = key.privateKey

        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null

        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    }

    /** The TV's certificate is its own, self-signed; as with adb's client, there's nothing to check it against. */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private object AnyTv : X509ExtendedTrustManager() {
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = Unit

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = Unit

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
