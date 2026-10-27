// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.network

/**
 * Takes credentials out of text before it reaches a log, an error message or a report
 * (brief 6.10). Xtream puts the username and password in every URL, Stalker portals use tokens
 * and MAC addresses, and some playlists carry keys in their query strings.
 */
object Redact {
    private const val HIDDEN = "***"

    /** Path segments that Xtream follows with the username and the password. */
    private val XTREAM_PATHS = setOf("live", "movie", "series", "timeshift")

    private val SECRET_PARAMS = setOf("username", "password", "token", "mac", "auth", "key", "sn", "sign", "signature", "play_token")

    private val MAC = Regex("""(?i)\b([0-9a-f]{2}[:-][0-9a-f]{2}[:-][0-9a-f]{2})[:-][0-9a-f]{2}[:-][0-9a-f]{2}[:-][0-9a-f]{2}\b""")
    private val URL = Regex("""(?i)\b[a-z][a-z0-9+.-]*://[^\s"'<>]+""")

    /** Every URL and MAC address in [text], redacted. */
    fun text(text: String): String = MAC.replace(URL.replace(text) { url(it.value) }) { "${it.groupValues[1]}:**:**:**" }

    /** [url] with its user info, Xtream credentials and secret query values hidden. */
    fun url(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return url
        val start = schemeEnd + 3
        val pathStart = url.indexOfAny(charArrayOf('/', '?', '#'), start).let { if (it < 0) url.length else it }
        val authority = url.substring(start, pathStart).let { if ('@' in it) "$HIDDEN@" + it.substringAfterLast('@') else it }
        val rest = url.substring(pathStart)
        val fragmentAt = rest.indexOf('#').let { if (it < 0) rest.length else it }
        val queryAt = rest.indexOf('?').let { if (it < 0 || it > fragmentAt) fragmentAt else it }
        val path = redactPath(rest.substring(0, queryAt))
        val query = if (queryAt < fragmentAt) "?" + redactQuery(rest.substring(queryAt + 1, fragmentAt)) else ""
        return url.substring(0, start) + authority + path + query + rest.substring(fragmentAt)
    }

    private fun redactPath(path: String): String {
        val segments = path.split('/').toMutableList()
        var i = 0
        while (i < segments.size) {
            if (segments[i].lowercase() in XTREAM_PATHS && i + 2 < segments.size) {
                segments[i + 1] = HIDDEN
                segments[i + 2] = HIDDEN
                i += 3
            } else {
                i++
            }
        }
        return segments.joinToString("/")
    }

    private fun redactQuery(query: String): String = query.split('&').joinToString("&") { pair ->
        val name = pair.substringBefore('=')
        if ('=' in pair && name.lowercase() in SECRET_PARAMS) "$name=$HIDDEN" else pair
    }
}
