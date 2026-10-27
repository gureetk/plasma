// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.epg

/** What matching needs to know about one of Plasma's channels. */
data class GuideKey(
    /** Plasma's id for the channel. */
    val channelId: String,
    /** The guide id the playlist gives it: Xtream `epg_channel_id`, M3U `tvg-id`. */
    val tvgId: String?,
    val name: String,
)

/**
 * Which channels each guide channel feeds (brief 5.3): a channel matches by its guide id,
 * ignoring case; when that's missing or the guide doesn't know it, by the guide channel with the
 * same display name. One guide channel can feed several channels, such as an HD and an SD copy.
 */
object GuideMatcher {
    /** Channel ids for each guide channel id, lowercased. */
    fun match(channels: List<GuideKey>, guide: List<XmltvChannel>): Map<String, List<String>> {
        val guideIds = guide.mapTo(HashSet()) { it.id.lowercase() }
        val idByName = HashMap<String, String>()
        guide.forEach { channel ->
            channel.names.forEach { idByName.putIfAbsent(normalizeName(it), channel.id.lowercase()) }
        }
        val result = HashMap<String, MutableList<String>>()
        for (channel in channels) {
            val tvgId = channel.tvgId?.lowercase()
            val guideId = when {
                // A guide that lists no channels at all is trusted to use the playlist's ids.
                tvgId != null && (tvgId in guideIds || guideIds.isEmpty()) -> tvgId
                else -> idByName[normalizeName(channel.name)]
            } ?: continue
            result.getOrPut(guideId) { mutableListOf() } += channel.channelId
        }
        return result
    }

    private val PREFIX = Regex("""^\s*[A-Za-z]{2,3}\s*[:|]\s*""")
    private val QUALITY = listOf("uhd", "fhd", "hd", "sd", "4k")

    /**
     * Names as the guide and the playlist might both write them: "Arena Sports 1 HD",
     * "arena-sports-1" and "UK: ARENA SPORTS 1" all become "arenasports1".
     */
    fun normalizeName(name: String): String {
        val compact = name.replace(PREFIX, "").lowercase().filter { it.isLetterOrDigit() }
        return QUALITY.firstOrNull { compact.endsWith(it) && compact.length > it.length }
            ?.let { compact.removeSuffix(it) }
            ?: compact
    }
}
