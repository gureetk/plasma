// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import ca.gureet.plasma.adb.AdbConnection
import ca.gureet.plasma.adb.AdbKey
import ca.gureet.plasma.adb.AdbServices
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs [block] on this TV's shell over Plasma's pairing with it (brief 5.13), with Plasma's [key]:
 * on wireless debugging's port when mDNS finds it within a few seconds, else on the older network
 * debugging's. Null when the TV can't be reached or doesn't let Plasma in.
 */
suspend fun <T> onTvShell(context: Context, key: AdbKey, block: (AdbConnection) -> T): T? {
    val port = withTimeoutOrNull(FIND_PORT_MS) { AdbServices(context).connectPort().first { it != null } }
    return withContext(Dispatchers.IO) {
        try {
            AdbConnection.connect(HomeReplacer.LOOPBACK, port ?: HomeReplacer.NETWORK_DEBUGGING_PORT, key, HomeReplacer.NAME).use(block)
        } catch (failed: IOException) {
            null
        }
    }
}

/** How long mDNS gets to find wireless debugging before Plasma tries the older port. */
private const val FIND_PORT_MS = 4_000L
