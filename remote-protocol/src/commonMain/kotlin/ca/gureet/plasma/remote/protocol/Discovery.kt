// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.protocol

import kotlinx.serialization.Serializable

/**
 * What the phone remote's QR code holds (brief 5.21 and 5.22):
 * `http://192.168.1.42:8484/remote?code=7KQ4MZ&fp=…`. The web page opens it and reads the code.
 * The apps read the TV's address, the code and [fingerprint], the TV's certificate, which they
 * pin before they send the code.
 *
 * [fingerprint] is the SHA-256 of the certificate's DER encoding, in base64url without padding
 * (43 characters). It's null where the TV has no TLS listener running: an app then trusts the
 * certificate the TV shows the first time, as it does for a code typed in.
 */
data class RemoteInvite(val host: String, val code: String, val fingerprint: String? = null) {
    /** The address the QR code holds. */
    val url: String
        get() = buildString {
            append("http://").append(host).append(PAGE_PATH).append("?code=").append(code)
            if (fingerprint != null) append("&fp=").append(fingerprint)
        }

    companion object {
        const val PAGE_PATH = "/remote"

        /** Whether [text] is a fingerprint as the QR code carries it. */
        fun isFingerprint(text: String): Boolean =
            text.length == FINGERPRINT_LENGTH && text.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }

        /**
         * The invite in a scanned QR code; null for anything that isn't the phone remote's code
         * (another app's code, Add a Source's, or a fingerprint that isn't one).
         */
        fun parse(text: String): RemoteInvite? {
            val trimmed = text.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true)) return null
            val rest = trimmed.substring("http://".length)
            val host = rest.substringBefore('/')
            if (host.isEmpty() || host.any { it.isWhitespace() || it == '?' || it == '#' || it == '@' }) return null
            val path = rest.substring(host.length).substringBefore('#')
            if (path.substringBefore('?').trimEnd('/') != PAGE_PATH) return null
            val query = path.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to it.substringAfter('=', "") }
            val code = query["code"]?.uppercase()?.takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) } ?: return null
            val fingerprint = query["fp"]
            if (fingerprint != null && !isFingerprint(fingerprint)) return null
            return RemoteInvite(host, code, fingerprint)
        }

        private const val FINGERPRINT_LENGTH = 43
    }
}

/**
 * How a TV running Plasma tells the apps where it is (brief 5.22). While the phone remote's
 * server runs, the TV advertises [SERVICE_TYPE] over mDNS, its SRV record giving the apps' TLS
 * port, with these as its TXT entries: [id], the TV's id, made when Plasma was installed; [name];
 * [v], the protocol's version; and [tls], the TLS port again. The same comes as JSON from
 * `http://<tv>:<port>/remote/app.json` ([PATH]), for an app that has the TV's address but can't
 * browse the network.
 */
@Serializable
data class RemoteAdvert(val id: String, val name: String, val v: Int, val tls: Int) {
    /** The mDNS TXT entries. */
    fun txt(): Map<String, String> = mapOf("id" to id, "name" to name, "v" to v.toString(), "tls" to tls.toString())

    fun json(): String = RemoteProtocol.json.encodeToString(serializer(), this)

    companion object {
        const val SERVICE_TYPE = "_plasma-remote._tcp"
        const val PATH = "/remote/app.json"

        /** The advert in a service's TXT entries; null where one is missing or isn't a number. */
        fun fromTxt(entries: Map<String, String?>): RemoteAdvert? {
            val id = entries["id"]?.takeIf { it.isNotEmpty() } ?: return null
            val name = entries["name"]?.takeIf { it.isNotEmpty() } ?: return null
            val version = entries["v"]?.toIntOrNull() ?: return null
            val tls = entries["tls"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return RemoteAdvert(id, name, version, tls)
        }

        fun fromJson(text: String): RemoteAdvert? = runCatching { RemoteProtocol.json.decodeFromString(serializer(), text) }.getOrNull()
    }
}
