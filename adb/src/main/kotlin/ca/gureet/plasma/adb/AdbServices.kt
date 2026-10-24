// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * This TV's own adb services as mDNS announces them (brief 4.24): wireless debugging's connection
 * port while it's on, and the pairing port while "Pair device with pairing code" is showing.
 * Other devices on the network announce theirs too; only those at one of this TV's addresses count.
 */
class AdbServices(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    /** The port of this TV's wireless debugging while it's on, else null. */
    fun connectPort(): Flow<Int?> = port(CONNECT)

    /** The port of this TV's pairing dialog while it's showing, else null. */
    fun pairingPort(): Flow<Int?> = port(PAIRING)

    private fun port(type: String): Flow<Int?> = callbackFlow {
        val manager = nsd
        if (manager == null) {
            trySend(null)
            awaitClose()
            return@callbackFlow
        }
        val ports = LinkedHashMap<String, Int>()
        val resolver = Executors.newSingleThreadExecutor { task -> Thread(task, "plasma-adb-mdns").apply { isDaemon = true } }
        fun publish() {
            trySend(ports.values.lastOrNull())
        }
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                // One resolve at a time, as older Androids allow.
                resolver.execute { resolve(manager, info)?.let { (host, port) -> if (isThisTv(host)) synchronized(ports) { ports[info.serviceName] = port; publish() } } }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                synchronized(ports) {
                    if (ports.remove(info.serviceName) != null) publish()
                }
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                trySend(null)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        trySend(null)
        manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery)
        awaitClose {
            runCatching { manager.stopServiceDiscovery(discovery) }
            resolver.shutdownNow()
        }
    }.distinctUntilChanged()

    /** Where [info] is, waiting for the answer: its address and port, or null when it can't be found. */
    @Suppress("DEPRECATION") // resolveService works on every version; its successor is Android 14's.
    private fun resolve(manager: NsdManager, info: NsdServiceInfo): Pair<InetAddress, Int>? {
        val answer = java.util.concurrent.ArrayBlockingQueue<Pair<InetAddress, Int>?>(1)
        manager.resolveService(
            info,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                    answer.offer(null)
                }

                override fun onServiceResolved(service: NsdServiceInfo) {
                    val host = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) service.hostAddresses.firstOrNull() else service.host
                    answer.offer(host?.let { it to service.port })
                }
            },
        )
        return answer.poll(RESOLVE_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
    }

    companion object {
        const val CONNECT = "_adb-tls-connect._tcp"
        const val PAIRING = "_adb-tls-pairing._tcp"
        private const val RESOLVE_SECONDS = 5L

        /** Whether [host] is one of this TV's own addresses. */
        internal fun isThisTv(host: InetAddress, own: () -> List<InetAddress> = ::ownAddresses): Boolean =
            host.isLoopbackAddress || own().any { it == host }

        private fun ownAddresses(): List<InetAddress> = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().flatMap { it.inetAddresses.toList() }
        } catch (unavailable: java.net.SocketException) {
            emptyList()
        }
    }
}
