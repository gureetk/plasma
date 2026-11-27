// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import ca.gureet.plasma.remote.app.pairing.PairOutcome
import ca.gureet.plasma.remote.app.pairing.PairTarget
import ca.gureet.plasma.remote.app.pairing.pairWith
import ca.gureet.plasma.remote.app.pairing.target
import ca.gureet.plasma.remote.app.pairing.typedEndpoint
import ca.gureet.plasma.remote.app.tv.FoundTv
import ca.gureet.plasma.remote.app.tv.NotThePairedTv
import ca.gureet.plasma.remote.app.tv.PairedTv
import ca.gureet.plasma.remote.app.tv.PhoneApp
import ca.gureet.plasma.remote.app.tv.TvEndpoint
import ca.gureet.plasma.remote.app.tv.TvSocket
import ca.gureet.plasma.remote.protocol.RemoteAdvert
import ca.gureet.plasma.remote.protocol.RemoteInvite
import ca.gureet.plasma.remote.protocol.RemoteProtocol
import ca.gureet.plasma.remote.protocol.TvMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runTest

/** Pairing from the app's screens (brief 5.22): the TV kept with its fingerprint pinned. */
class PairingTest {
    private val app = PhoneApp("ios", "0.1.0")
    private val target = PairTarget("Living Room TV", TvEndpoint("192.168.1.42", 8486))

    /** A TV that pairs with 7KQ4MZ, showing a certificate whose fingerprint is "SEEN". */
    private val tv: suspend (TvEndpoint, String?, (String) -> Unit) -> TvSocket = { _, pinned, seen ->
        if (pinned != null && pinned != "SEEN") throw NotThePairedTv()
        seen("SEEN")
        object : TvSocket {
            val channel = Channel<String>(Channel.UNLIMITED)
            override val incoming: ReceiveChannel<String> get() = channel
            override suspend fun send(text: String) {
                val (type, fields) = RemoteProtocol.parse(text) ?: return
                when (type) {
                    "hello" -> channel.send(TvMessages.welcome("tv-1", "Living Room TV", "1.0.0"))
                    "pair" -> channel.send(if (fields["code"] == "7KQ4MZ") TvMessages.paired("t0k3n") else TvMessages.error(RemoteProtocol.Reasons.CODE))
                }
            }
            override fun close() = channel.close().let {}
        }
    }

    @Test fun aTypedCodeTrustsTheFirstCertificateAndPinsIt() = runTest {
        val outcome = pairWith(target, "7kq4mz", "Maya’s iPhone", app, tv)
        assertEquals(PairOutcome.Paired(PairedTv("tv-1", "Living Room TV", "192.168.1.42", 8486, "SEEN", "t0k3n")), outcome)
    }

    @Test fun aScannedCodePinsWhatTheQrCodeSaid() = runTest {
        val outcome = pairWith(target.copy(pinned = "SEEN"), "7KQ4MZ", "Phone", app, tv)
        assertEquals("SEEN", (outcome as PairOutcome.Paired).tv.fingerprint)
        // Another certificate than the QR code's: no pairing.
        assertEquals(PairOutcome.NotThatTv, pairWith(target.copy(pinned = "OTHER"), "7KQ4MZ", "Phone", app, tv))
    }

    @Test fun aWrongCodeSaysSo() = runTest {
        assertEquals(PairOutcome.Refused(RemoteProtocol.Reasons.CODE), pairWith(target, "AAAAAA", "Phone", app, tv))
    }

    @Test fun aScannedCodePairsWithTheTvAtItsAddress() {
        val invite = RemoteInvite.parse("http://192.168.1.42:8484/remote?code=7KQ4MZ&fp=" + "A".repeat(43))!!
        // Not found on the Wi-Fi: the apps' listener at the code's address, and no name.
        assertEquals(PairTarget(null, TvEndpoint("192.168.1.42", 8486), "A".repeat(43), "7KQ4MZ"), invite.target(emptyList()))
        // Found: its name and the TLS port its advert gives.
        val found = listOf(FoundTv(RemoteAdvert("tv-1", "Living Room TV", 1, 8490), "192.168.1.42"))
        assertEquals(PairTarget("Living Room TV", TvEndpoint("192.168.1.42", 8490), "A".repeat(43), "7KQ4MZ"), invite.target(found))
    }

    @Test fun aTypedAddressGoesToTheAppsListener() {
        assertEquals(TvEndpoint("192.168.1.42", 8486), typedEndpoint(" 192.168.1.42 "))
        assertEquals(TvEndpoint("192.168.1.42", 8486), typedEndpoint("http://192.168.1.42:8484/remote"))
        assertEquals(TvEndpoint("192.168.1.42", 8490), typedEndpoint("192.168.1.42:8490"))
        assertEquals(TvEndpoint("[fe80::1]", 8486), typedEndpoint("[fe80::1]"))
        assertEquals(TvEndpoint("living-room.local", 8486), typedEndpoint("living-room.local"))
        assertNull(typedEndpoint("  "))
        assertNull(typedEndpoint("two words"))
    }
}
