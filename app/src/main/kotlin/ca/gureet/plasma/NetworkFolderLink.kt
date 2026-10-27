// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.feature.settings.NetworkFolderSetup
import ca.gureet.plasma.feature.settings.ShareLogin
import ca.gureet.plasma.feature.settings.ShareServer
import ca.gureet.plasma.feature.settings.ShareSetupProblem
import ca.gureet.plasma.feature.settings.ShareStep
import ca.gureet.plasma.recording.ShareException
import ca.gureet.plasma.recording.ShareProblem
import ca.gureet.plasma.recording.SmbShares
import ca.gureet.plasma.recording.checkWritable
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Settings › Recording › Copy to Network Folder's setup, over the network (brief 4.34 and 5.6):
 * servers that announce SMB shares over mDNS, then their shares and folders through smbj, and the
 * test write before the folder's kept, its password sealed (6.10).
 */
class NetworkFolderLink(private val context: Context, private val graph: AppGraph) : NetworkFolderSetup {
    /**
     * NAS boxes announce `_smb._tcp` (most Synology, QNAP, TrueNAS and Samba setups); Windows PCs
     * don't, hence Enter Address. It ends a few seconds in.
     */
    override fun discover(): Flow<ShareServer> = callbackFlow {
        val nsd = context.getSystemService(NsdManager::class.java)
        if (nsd == null) {
            close()
            return@callbackFlow
        }
        // Before Android 14, one service is resolved at a time.
        val resolving = Mutex()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                launch {
                    val server = resolving.withLock { resolve(nsd, info) } ?: return@launch
                    trySend(server)
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceLost(info: NsdServiceInfo) = Unit

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        try {
            nsd.discoverServices(SMB_SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (refused: RuntimeException) {
            close()
            return@callbackFlow
        }
        launch {
            delay(DISCOVERY_MS)
            close()
        }
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }.flowOn(Dispatchers.IO)

    override suspend fun shares(server: ShareServer, login: ShareLogin): ShareStep<List<String>> = step(listing = Listing.Shares) {
        SmbShares.shares(server.host, login.username, login.password, null)
    }

    override suspend fun folders(server: ShareServer, login: ShareLogin, share: String, path: String): ShareStep<List<String>> = step(listing = Listing.Folders) {
        SmbShares.open(server.host, share, login.username, login.password, null).use { it.folders(path) }
    }

    override suspend fun use(server: ShareServer, login: ShareLogin, share: String, path: String): ShareStep<Unit> {
        val step = step(listing = Listing.None) {
            SmbShares.open(server.host, share, login.username, login.password, null).use { session ->
                session.checkWritable(path)
                NetworkFolder(server.host, share, path, login.username, login.password, freeBytes = session.freeBytes())
            }
        }
        if (step !is ShareStep.Done) return step as ShareStep.Failed
        return try {
            graph.data.networkFolders.save(step.value)
            graph.notice(Notice(context.getString(R.string.network_folder_saved, step.value.shown), PlasmaIcons.NetworkFolder, Notice.LONG_MS))
            ShareStep.Done(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            // Keeping it failed.
            ShareStep.Failed(ShareSetupProblem.Refused)
        }
    }

    /** What a step asks for: what the server refusing means depends on it. */
    private enum class Listing { Shares, Folders, None }

    private suspend fun <T> step(listing: Listing, work: () -> T): ShareStep<T> = withContext(Dispatchers.IO) {
        try {
            ShareStep.Done(work())
        } catch (failed: ShareException) {
            ShareStep.Failed(
                when (failed.problem) {
                    ShareProblem.CantReach -> ShareSetupProblem.CantReach
                    ShareProblem.WrongLogin -> ShareSetupProblem.WrongLogin
                    ShareProblem.NoShare -> ShareSetupProblem.NoShare
                    // A folder that can't be listed isn't one that can't be written to.
                    ShareProblem.ReadOnly -> if (listing == Listing.None) ShareSetupProblem.ReadOnly else ShareSetupProblem.Refused
                    ShareProblem.Refused -> if (listing == Listing.Shares) ShareSetupProblem.NoList else ShareSetupProblem.Refused
                },
            )
        }
    }

    /**
     * Where [info] answers, as the setup lists it: its name, and the server's name on this network
     * when the router knows it ("nas.home.lan"), else its address. Null when it can't be found.
     */
    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): ShareServer? {
        val resolved = resolved(nsd, info) ?: return null
        val address: InetAddress = resolved.host ?: return null
        val host = withContext(Dispatchers.IO) {
            val named = runCatching { address.canonicalHostName }.getOrNull()
            // A name the router gave it reads better and outlives a new address; a .local one needs mDNS to reach.
            if (named != null && named != address.hostAddress && !named.endsWith(".local", ignoreCase = true)) {
                named
            } else if (address is Inet4Address) {
                address.hostAddress
            } else {
                null
            }
        } ?: return null
        return ShareServer(resolved.serviceName.ifBlank { host }, host)
    }

    /** [info] with its address, from NSD; null when it doesn't say in time. */
    @Suppress("DEPRECATION")
    private suspend fun resolved(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
        withTimeoutOrNull(RESOLVE_MS) {
            suspendCancellableCoroutine { done ->
                try {
                    nsd.resolveService(
                        info,
                        object : NsdManager.ResolveListener {
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                if (done.isActive) done.resume(resolved)
                            }

                            override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                                if (done.isActive) done.resume(null)
                            }
                        },
                    )
                } catch (refused: RuntimeException) {
                    if (done.isActive) done.resume(null)
                }
            }
        }

    private companion object {
        const val SMB_SERVICE = "_smb._tcp"

        /** How long the setup looks for servers, and waits for each to say where it is. */
        const val DISCOVERY_MS = 5_000L
        const val RESOLVE_MS = 3_000L
    }
}
