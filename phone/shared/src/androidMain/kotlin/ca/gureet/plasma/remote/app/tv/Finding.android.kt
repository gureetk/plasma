// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ca.gureet.plasma.remote.protocol.RemoteAdvert
import java.io.File
import java.net.Inet4Address
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The paired TVs' file, sealed with an AES key that Android's Keystore makes and keeps (brief
 * 5.22: tokens in Keystore-backed storage). It's in the app's no-backup folder, so it's never
 * copied off the phone; and the key never leaves the Keystore.
 */
class KeystoreVault(context: Context, name: String = "paired-tvs") : Vault {
    private val file = File(context.noBackupFilesDir, "$name.sealed")

    private fun key(): SecretKey {
        val keys = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keys.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    override fun read(): ByteArray? = runCatching {
        val sealed = file.takeIf { it.exists() }?.readBytes() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }.getOrNull()

    override fun write(bytes: ByteArray?) {
        if (bytes == null) {
            file.delete()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(bytes)
        // Written whole and then moved, so a crash halfway leaves what was there.
        val next = File(file.parentFile, file.name + ".new")
        next.writeBytes(sealed)
        if (!next.renameTo(file)) {
            file.delete()
            next.renameTo(file)
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "plasma-remote-paired-tvs"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

/**
 * TVs on the Wi-Fi through Android's NsdManager. Each service found is resolved, one at a time
 * (Android resolves one at a time), for its address and its TXT entries.
 */
class NsdTvFinder(context: Context) : TvFinder {
    private val nsd = context.getSystemService(NsdManager::class.java)

    override fun tvs(): Flow<List<FoundTv>> = callbackFlow {
        val manager = nsd ?: run {
            send(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val found = LinkedHashMap<String, FoundTv>()
        val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        send(emptyList())

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                toResolve.trySend(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                // NsdManager's own thread: the map is shared with the resolver below.
                synchronized(found) { if (found.remove(info.serviceName) != null) trySend(found.values.toList()) }
            }

            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        launch {
            for (info in toResolve) {
                val resolved = resolve(manager, info) ?: continue
                val advert = RemoteAdvert.fromTxt(resolved.attributes.mapValues { (_, value) -> value?.decodeToString() }) ?: continue
                val host = hostOf(resolved) ?: continue
                send(synchronized(found) { found[resolved.serviceName] = FoundTv(advert, host); found.values.toList() })
            }
        }
        runCatching { manager.discoverServices(RemoteAdvert.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }
        awaitClose {
            runCatching { manager.stopServiceDiscovery(discovery) }
            toResolve.close()
        }
    }

    @Suppress("DEPRECATION") // resolveService works on every Android Plasma Remote runs on; its successor is Android 14's.
    private suspend fun resolve(manager: NsdManager, info: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { resolved ->
        runCatching {
            manager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(info: NsdServiceInfo) {
                    if (resolved.isActive) resolved.resume(info)
                }

                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    if (resolved.isActive) resolved.resume(null)
                }
            })
        }.onFailure { if (resolved.isActive) resolved.resume(null) }
    }

    /** The TV's IPv4 address where it has one; an IPv6 one without its interface's name otherwise. */
    @Suppress("DEPRECATION")
    private fun hostOf(info: NsdServiceInfo): String? {
        val addresses = if (android.os.Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
        val address = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull() ?: return null
        return address.hostAddress?.substringBefore('%')
    }
}
