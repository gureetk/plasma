// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ca.gureet.plasma.remote.RemoteTls
import ca.gureet.plasma.remote.protocol.RemoteAdvert
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import javax.security.auth.x500.X500Principal

/**
 * The TV's certificate for the Plasma Remote apps' TLS listener (brief 5.22): an EC P-256 key
 * that Android's Keystore makes the first time and keeps, with a self-signed certificate. The key
 * never leaves the Keystore, and goes with Plasma's data, so a reinstalled TV, or one whose data
 * was cleared, has another certificate, and an app paired before asks to pair again.
 */
internal object RemoteCertificate {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "plasma-remote-tls"

    /** The TV's identity, made the first time; null where the Keystore can't make or give it. */
    fun load(): RemoteTls? = runCatching {
        val keys = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!keys.containsAlias(ALIAS)) make()
        val key = keys.getKey(ALIAS, null) as PrivateKey
        val certificate = keys.getCertificate(ALIAS) as X509Certificate
        RemoteTls.of(key, certificate)
    }.getOrNull()

    private fun make() {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            // TLS signs a digest it has made itself, which reaches the Keystore as "NONEwithECDSA".
            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setCertificateSubject(X500Principal("CN=Plasma"))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply { initialize(spec) }.generateKeyPair()
    }
}

/**
 * The TV's mDNS advert (brief 5.22): `_plasma-remote._tcp` under the TV's name, at the apps' TLS
 * port, with the advert's TXT entries, while the phone remote's server runs. Android gives the
 * service another name if another TV on the network has this one.
 */
internal class RemoteAdvertiser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    /** Advertises [advert] until the returned function is called. */
    fun advertise(advert: RemoteAdvert): () -> Unit {
        val manager = nsd ?: return {}
        val info = NsdServiceInfo().apply {
            serviceName = advert.name
            serviceType = RemoteAdvert.SERVICE_TYPE
            port = advert.tls
            advert.txt().forEach { (key, value) -> setAttribute(key, value) }
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}

            override fun onServiceUnregistered(info: NsdServiceInfo) {}

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        val registered = runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }.isSuccess
        return { if (registered) runCatching { manager.unregisterService(registration) } }
    }
}
