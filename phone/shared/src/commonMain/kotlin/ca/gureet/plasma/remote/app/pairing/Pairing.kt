// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.pairing

import ca.gureet.plasma.remote.app.tv.PairResult
import ca.gureet.plasma.remote.app.tv.PairedTv
import ca.gureet.plasma.remote.app.tv.PhoneApp
import ca.gureet.plasma.remote.app.tv.TvEndpoint
import ca.gureet.plasma.remote.app.tv.TvSocket
import ca.gureet.plasma.remote.app.tv.openTvSocket
import ca.gureet.plasma.remote.app.tv.pair
import kotlinx.coroutines.CancellationException

/** A TV to pair with: as found on the Wi-Fi, typed in, or scanned from its QR code (which pins its certificate). */
data class PairTarget(val name: String?, val endpoint: TvEndpoint, val pinned: String? = null)

/** How pairing went, for the screen to say. */
sealed interface PairOutcome {
    data class Paired(val tv: PairedTv) : PairOutcome

    /** The TV turned the code away: [reason] is `code`, `tries` or `version`. */
    data class Refused(val reason: String) : PairOutcome

    /** Nothing answered there, or not as Plasma does. */
    data object Unreachable : PairOutcome
}

/**
 * Pairs with [target] (brief 5.22): connects to its TLS listener (pinned when the QR code gave
 * the fingerprint, trusting the first certificate otherwise), sends the code and the phone's name,
 * and on `paired` gives the TV as it's to be kept, with the fingerprint now pinned.
 */
suspend fun pairWith(
    target: PairTarget,
    code: String,
    phoneName: String,
    app: PhoneApp,
    open: suspend (TvEndpoint, String?, (String) -> Unit) -> TvSocket = { endpoint, pinned, seen -> openTvSocket(endpoint, pinned, seen) },
): PairOutcome {
    var seen: String? = null
    val socket = try {
        open(target.endpoint, target.pinned) { seen = it }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        return PairOutcome.Unreachable
    }
    try {
        return when (val result = pair(socket, app, code, phoneName)) {
            is PairResult.Paired -> {
                val fingerprint = target.pinned ?: seen ?: return PairOutcome.Unreachable
                PairOutcome.Paired(PairedTv(result.welcome.tvId, result.welcome.name, target.endpoint.host, target.endpoint.port, fingerprint, result.token))
            }
            is PairResult.Refused -> PairOutcome.Refused(result.reason)
            PairResult.NoAnswer -> PairOutcome.Unreachable
        }
    } finally {
        socket.close()
    }
}

/** A typed address: "192.168.1.42" or a name, with ":port" for a TLS listener that isn't on 8486. */
fun typedEndpoint(text: String): TvEndpoint? {
    val trimmed = text.trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val bracketed = trimmed.startsWith("[")
    val host = if (bracketed) trimmed.substringBefore(']') + "]" else if (trimmed.count { it == ':' } == 1) trimmed.substringBefore(':') else trimmed
    val port = trimmed.removePrefix(host).removePrefix(":").toIntOrNull()
    if (host.isEmpty() || host == "[]") return null
    // A port of 8484 is the web page's: the apps' listener is 8486 there.
    return TvEndpoint(host, port?.takeIf { it in 1..65535 && it != 8484 } ?: 8486)
}
