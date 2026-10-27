// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.m3u

/**
 * Keys for a playlist's channels that stay the same when it's refreshed, so favorites, history
 * and number choices keep their channels (brief 5.2 and 6.3). Addresses often carry a token that
 * changes, so the key is the stream id in an Xtream-style address, or else the guide id with the
 * name, or else the name. A key that repeats gets "#2", "#3"… in playlist order.
 */
object M3uKeys {
    fun of(entries: List<M3uEntry>): List<String> {
        val seen = HashMap<String, Int>()
        return entries.map { entry ->
            val base = base(entry)
            val count = (seen[base] ?: 0) + 1
            seen[base] = count
            if (count == 1) base else "$base#$count"
        }
    }

    internal fun base(entry: M3uEntry): String {
        streamId(entry.url)?.let { return "id:$it" }
        val name = entry.name.lowercase().replace(SPACES, " ").trim()
        val tvgId = entry.tvgId?.trim()?.lowercase()
        return if (tvgId.isNullOrEmpty()) "name:$name" else "tvg:$tvgId/$name"
    }

    /** The stream id at the end of an address such as `…/live/user/pass/1234.ts` or `…/user/pass/1234`. */
    internal fun streamId(url: String): String? {
        val path = url.substringAfter("://").substringBefore('?').substringBefore('#')
        if ('/' !in path) return null
        val last = path.substringAfterLast('/').substringBefore('.')
        return last.takeIf { it.isNotEmpty() && it.length <= MAX_ID_DIGITS && it.all { c -> c in '0'..'9' } }
    }

    private val SPACES = Regex("\\s+")
    private const val MAX_ID_DIGITS = 12
}
