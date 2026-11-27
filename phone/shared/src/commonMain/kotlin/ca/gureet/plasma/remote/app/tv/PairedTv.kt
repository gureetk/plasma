// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.tv

/**
 * A TV this phone is paired with (brief 5.22), as the app keeps it: the TV's id (made when
 * Plasma was installed), its name, where it was last found, the fingerprint of its certificate,
 * pinned, and the phone's own token, which the app keeps in the phone's secure storage.
 */
data class PairedTv(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val fingerprint: String,
    val token: String,
) {
    val endpoint: TvEndpoint get() = TvEndpoint(host, port)

    /** The TV over the network: its WebSocket and its logos, both pinned to its certificate. */
    fun link(app: PhoneApp, onForgotten: (reason: String) -> Unit = {}): NetworkTv = NetworkTv(
        name = name,
        token = token,
        app = app,
        dialer = TvDialer { openTvSocket(endpoint, fingerprint) },
        onForgotten = onForgotten,
        fetchLogo = { channelId -> fetchFromTv(endpoint, fingerprint, "/remote/logo/" + pathSegment(channelId)) },
    )
}

/** [text] as one segment of an address's path: letters, digits and `-._~` as they are, the rest percent-encoded from UTF-8. */
internal fun pathSegment(text: String): String = buildString {
    text.encodeToByteArray().forEach { byte ->
        val c = byte.toInt().toChar()
        if (byte >= 0 && (c.isLetterOrDigit() || c in "-._~")) append(c) else append('%').append(HEX[(byte.toInt() shr 4) and 15]).append(HEX[byte.toInt() and 15])
    }
}

private const val HEX = "0123456789ABCDEF"
