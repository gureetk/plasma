// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.network

/**
 * Makes text safe to put in a report people share, such as Export Diagnostics' bundle (brief 5.17
 * and 6.10). Credentials go as [Redact.text] takes them out; each of [secrets] (usernames,
 * passwords, MAC addresses) goes wherever it shows, even outside a URL; and every server's name or
 * address becomes `server-1`, `server-2`…, the same name for the same server throughout, so lines
 * about one server can still be told apart from another's. [hosts] are servers known by name,
 * which are found in any text, not only in URLs. The TV's own loopback addresses stay, since
 * they're the stream hub's.
 *
 * One scrubber is used for every file of one report, and isn't shared between threads.
 */
class ReportScrubber(secrets: Collection<String>, hosts: Collection<String>) {
    private val secrets = secrets.map { it.trim() }.filter { it.length >= MIN_SECRET }.distinct().sortedByDescending { it.length }

    /** The known servers in one pattern, the longest first, so they're named in the order they show. */
    private val hostPattern: Regex? = hosts.map { it.trim().lowercase().removeSurrounding("[", "]") }
        .filter { it.isNotEmpty() && !isLocal(it) }
        .distinct()
        .sortedByDescending { it.length }
        .takeIf { it.isNotEmpty() }
        // A name inside a longer one (another server's) isn't it; a subdomain's end is, and goes too.
        ?.let { known -> Regex("(?i)(?<![a-z0-9-])(?:" + known.joinToString("|") { Regex.escape(it) } + ")(?![a-z0-9-])") }
    private val names = LinkedHashMap<String, String>()

    /** [text] with credentials, [secrets] and servers taken out. */
    fun scrub(text: String): String {
        var out = Redact.text(text)
        for (secret in secrets) out = out.replace(secret, HIDDEN)
        out = URL.replace(out) { maskHostIn(it.value) }
        hostPattern?.let { pattern -> out = pattern.replace(out) { nameOf(it.value) } }
        return IPV4.replace(out) { match -> if (isLocal(match.value)) match.value else nameOf(match.value) }
    }

    /** How many servers have been given a name so far. */
    val serverCount: Int get() = names.size

    private fun maskHostIn(url: String): String {
        val start = url.indexOf("://") + 3
        val end = url.indexOfAny(charArrayOf('/', '?', '#'), start).let { if (it < 0) url.length else it }
        val authority = url.substring(start, end)
        val at = authority.lastIndexOf('@')
        val userInfo = if (at >= 0) authority.substring(0, at + 1) else ""
        val hostPort = authority.substring(at + 1)
        val hostEnd = if (hostPort.startsWith("[")) {
            hostPort.indexOf(']').let { if (it < 0) hostPort.length else it + 1 }
        } else {
            hostPort.lastIndexOf(':').let { if (it < 0) hostPort.length else it }
        }
        val host = hostPort.substring(0, hostEnd)
        if (host.isEmpty() || isLocal(host)) return url
        return url.substring(0, start) + userInfo + nameOf(host) + hostPort.substring(hostEnd) + url.substring(end)
    }

    private fun nameOf(host: String): String = names.getOrPut(host.lowercase().removeSurrounding("[", "]")) { "server-${names.size + 1}" }

    private companion object {
        const val HIDDEN = "***"

        /** Shorter values are left alone: hiding every "tv" or "123" would leave a report unreadable. */
        const val MIN_SECRET = 4

        val URL = Regex("""(?i)\b[a-z][a-z0-9+.-]*://[^\s"'<>]+""")
        val IPV4 = Regex("""(?<![\d.])(?:(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(?![\d.])""")

        /** The TV itself: the stream hub's addresses, and "any address" in a server's own messages. */
        fun isLocal(host: String): Boolean {
            val bare = host.lowercase().removeSurrounding("[", "]")
            return bare == "localhost" || bare == "::1" || bare == "0.0.0.0" || bare.startsWith("127.")
        }
    }
}
