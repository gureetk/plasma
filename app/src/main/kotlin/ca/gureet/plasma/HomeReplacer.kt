// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import android.os.Build
import ca.gureet.plasma.adb.AdbConnection
import ca.gureet.plasma.adb.AdbKey
import ca.gureet.plasma.adb.AdbPairing
import ca.gureet.plasma.adb.AdbRefused
import ca.gureet.plasma.adb.AdbServices
import ca.gureet.plasma.adb.HomeSwitch
import ca.gureet.plasma.adb.SwitchResult
import ca.gureet.plasma.feature.settings.ReplaceHomeState
import ca.gureet.plasma.feature.settings.ReplaceStatus
import ca.gureet.plasma.pairing.DebugCode
import ca.gureet.plasma.pairing.LanAddress
import ca.gureet.plasma.pairing.PairingInvite
import ca.gureet.plasma.pairing.PairingServer
import ca.gureet.plasma.pairing.PhonePageTexts
import ca.gureet.plasma.pairing.PhonePurpose
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Where Replace Google TV Home has got to on this TV (brief 4.24). */
data class ReplaceProgress(
    /** Wireless debugging's connection port while it's on (from mDNS). */
    val connectPort: Int? = null,
    /** The pairing dialog's port while it's showing (from mDNS), for the commands. */
    val pairingPort: Int? = null,
    /** The older network debugging answers on 5555: no pairing needed, the TV asks instead. */
    val networkDebugging: Boolean = false,
    /** The TV knows Plasma's key: it connected. */
    val paired: Boolean = false,
    /** Home opens Plasma now. */
    val replaced: Boolean = false,
    val status: ReplaceStatus = ReplaceStatus.Waiting,
    /** This TV's address on the home network, for the commands and the phone. */
    val address: String? = null,
    /** The phone page's QR code and address, while it's open. */
    val phone: PairingInvite? = null,
) {
    /** The step in progress (4 when done): debugging on, then paired, then replaced. */
    val step: Int
        get() = when {
            replaced -> DONE
            paired || networkDebugging -> REPLACE
            connectPort != null -> PAIR
            else -> DEBUGGING
        }

    private companion object {
        const val DEBUGGING = 1
        const val PAIR = 2
        const val REPLACE = 3
        const val DONE = 4
    }
}

/**
 * Board 24 from where Replace has got to: the steps, the commands with what's known of this TV
 * ([homeActivity], its [launchers]), and the phone page's QR code.
 */
fun replaceHomeState(progress: ReplaceProgress, homeActivity: String, launchers: List<String>, overrideOn: Boolean): ReplaceHomeState {
    val phone = progress.phone
    return ReplaceHomeState(
        step = progress.step,
        address = progress.address ?: UNKNOWN_ADDRESS,
        pairingPort = progress.pairingPort,
        // The code is only ever on the TV's screen.
        pairingCode = null,
        connectPort = progress.connectPort,
        homeActivity = homeActivity,
        launcherPackages = launchers,
        phonePort = phone?.address?.substringAfterLast(':')?.toIntOrNull() ?: PairingServer.PORT,
        status = progress.status,
        qr = phone?.qr,
        overrideOn = overrideOn,
    )
}

/** In the commands until the TV has an address on the home network. */
private const val UNKNOWN_ADDRESS = "<tv-ip>"

/**
 * Replace Google TV Home from the TV itself (brief 4.24 and 5.13, method 2), while its screen is
 * open: Plasma watches for wireless debugging, takes the pairing code a phone sends, pairs, and
 * runs the TV's own commands. [key] is Plasma's ADB key, made once and kept.
 */
class HomeReplacer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val texts: PhonePageTexts,
    private val key: suspend () -> AdbKey,
) {
    private val _progress = MutableStateFlow(ReplaceProgress())
    val progress: StateFlow<ReplaceProgress> = _progress.asStateFlow()

    private val jobs = ArrayList<Job>()

    /** Starts watching for wireless debugging, and the phone page. */
    fun start() {
        if (jobs.isNotEmpty()) return
        val services = AdbServices(context)
        jobs += scope.launch {
            services.connectPort().collect { port ->
                _progress.update { it.copy(connectPort = port) }
                // Paired before (Plasma keeps its key): nothing to pair.
                if (port != null && !_progress.value.paired) checkPaired(port)
            }
        }
        jobs += scope.launch { services.pairingPort().collect { port -> _progress.update { it.copy(pairingPort = port) } } }
        jobs += scope.launch {
            val open = withContext(Dispatchers.IO) { answers(LOOPBACK, NETWORK_DEBUGGING_PORT) }
            _progress.update { it.copy(networkDebugging = open) }
        }
        jobs += scope.launch { openPhonePage() }
    }

    /** The screen closed: the phone page and mDNS stop. */
    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    /**
     * Replaces Google TV Home: Plasma's Home activity [plasmaHome] becomes Home, then [launchers]
     * are turned off (step 3).
     */
    fun replace(plasmaHome: String, launchers: List<String>) {
        if (_progress.value.status == ReplaceStatus.Working) return
        _progress.update { it.copy(status = ReplaceStatus.Working) }
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) { connect().use { adb -> HomeSwitch(adb::shell).replace(plasmaHome, launchers) } }
            } catch (refused: AdbRefused) {
                _progress.update { it.copy(paired = false, status = ReplaceStatus.Waiting) }
                return@launch
            } catch (failed: IOException) {
                SwitchResult.Failed("", failed.message.orEmpty())
            }
            _progress.update {
                when (result) {
                    is SwitchResult.Done -> it.copy(replaced = true, status = ReplaceStatus.Waiting)
                    is SwitchResult.Failed -> it.copy(status = ReplaceStatus.Failed(listOf(result.step, result.output).filter { line -> line.isNotBlank() }.joinToString("\n")))
                }
            }
        }
    }

    /** The page a phone types the TV's pairing code on, open while this screen is (brief 5.15). */
    private suspend fun openPhonePage() {
        val server = PairingServer(texts, onSubmission = {}, purpose = PhonePurpose.DebugPairing, onDebugCode = ::received)
        try {
            val address = withContext(Dispatchers.IO) {
                try {
                    LanAddress.find()?.also { server.start(it, scope) }
                } catch (failed: IOException) {
                    null
                }
            } ?: return
            _progress.update { it.copy(address = address.hostAddress) }
            server.invite.collect { invite -> _progress.update { it.copy(phone = invite) } }
        } finally {
            server.close()
        }
    }

    /** A phone sent the pairing code and port the TV shows. */
    private fun received(code: DebugCode) {
        scope.launch { pair(code) }
    }

    private suspend fun pair(code: DebugCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            _progress.update { it.copy(status = ReplaceStatus.NeedsComputer) }
            return
        }
        _progress.update { it.copy(status = ReplaceStatus.Working) }
        val paired = try {
            withContext(Dispatchers.IO) { AdbPairing.pair(LOOPBACK, code.port, code.code, key(), NAME) }
            true
        } catch (wrong: IOException) {
            false
        }
        if (!paired) {
            _progress.update { it.copy(status = ReplaceStatus.WrongCode) }
            return
        }
        // Paired: the TV now lets Plasma in on wireless debugging's own port.
        val port = withTimeoutOrNull(CONNECT_PORT_WAIT_MS) { _progress.first { it.connectPort != null }.connectPort }
        if (port != null) checkPaired(port)
        _progress.update { it.copy(paired = true, status = ReplaceStatus.Waiting) }
    }

    /** Whether Plasma's key gets in on [port] already. */
    private suspend fun checkPaired(port: Int) {
        val inside = withContext(Dispatchers.IO) {
            try {
                AdbConnection.connect(LOOPBACK, port, key(), NAME).close()
                true
            } catch (failed: IOException) {
                false
            }
        }
        if (inside) _progress.update { it.copy(paired = true) }
    }

    /** Wireless debugging's port when it's on, else the older network debugging's. */
    private suspend fun connect(): AdbConnection {
        val port = _progress.value.connectPort ?: NETWORK_DEBUGGING_PORT
        return AdbConnection.connect(LOOPBACK, port, key(), NAME)
    }

    companion object {
        /** How the TV lists Plasma under Paired devices. */
        const val NAME = "Plasma"
        const val LOOPBACK = "127.0.0.1"
        const val NETWORK_DEBUGGING_PORT = 5555
        private const val CONNECT_PORT_WAIT_MS = 5_000L
        private const val PROBE_MS = 500

        /** Whether something answers at [host]:[port]: older TVs' network debugging. */
        fun answers(host: String, port: Int): Boolean = try {
            Socket().use { it.connect(InetSocketAddress(host, port), PROBE_MS) }
            true
        } catch (closed: IOException) {
            false
        }
    }
}
