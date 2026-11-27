// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.tv

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Where a TV's apps' listener is: its address now, and the TLS port its advert gives (8486, or another free one). */
data class TvEndpoint(val host: String, val port: Int) {
    /** A path on the listener, as an address (`/remote/ws`, `/remote/logo/<id>`). */
    fun url(scheme: String, path: String): String = "$scheme://${if (':' in host && !host.startsWith("[")) "[$host]" else host}:$port$path"
}

/**
 * Opens the TV's WebSocket on its TLS listener, `wss://<tv>:<port>/remote/ws` (brief 5.22),
 * trusting only a certificate whose fingerprint is [pinned]: the TV's name and the certificate's
 * dates aren't looked at, since the address changes and the certificate is the TV's own. With
 * [pinned] null, for a code typed in (which carries no fingerprint), the certificate is taken the
 * first time and [seen] gets its fingerprint, to pin from then on.
 *
 * @throws NotThePairedTv when the TV answers with another certificate than [pinned].
 * @throws Exception when the TV can't be reached.
 */
expect suspend fun openTvSocket(endpoint: TvEndpoint, pinned: String?, seen: (String) -> Unit = {}): TvSocket

/**
 * The TV answered with another certificate than the one pinned: Plasma was reinstalled or reset
 * on it, or another device has its address. The phone doesn't connect, and asks to pair again.
 */
class NotThePairedTv : Exception("Not the TV that was paired")

/** A file from the TV's TLS listener, pinned as [openTvSocket] is (a channel's logo); null for none. */
expect suspend fun fetchFromTv(endpoint: TvEndpoint, pinned: String, path: String): ByteArray?

/** A certificate's fingerprint as the TV's QR code carries it: SHA-256 of its DER encoding, base64url without padding. */
@OptIn(ExperimentalEncodingApi::class)
fun fingerprintOf(sha256: ByteArray): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(sha256)
