// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.channels

import java.text.Normalizer
import java.util.Locale

/** A stream's picture quality, as a channel's name gives it away (brief 5.1 and 5.2). */
enum class Quality {
    Uhd,
    Fhd,
    Hd,
    Sd,

    /** The name doesn't say. */
    Unknown,
}

/** A channel as merging sees it. */
data class MergeEntry(
    val id: String,
    val sourceId: String,
    val name: String,
    /** The provider's guide id: `tvg-id`, Xtream's `epg_channel_id` or a portal's `xmltv_id`. */
    val guideId: String?,
)

/**
 * The same channel from several sources (Merge Duplicate Channels, brief 5.1): the [leaderId] is
 * the one from the highest-priority source, which the merged channel shows as, and
 * [candidateIds] are every member in the order to try them.
 */
data class MergedChannel(val leaderId: String, val candidateIds: List<String>)

/**
 * Finds channels that are the same across sources (brief 5.1): the same guide id first, otherwise
 * the same name, without its quality and country tags, and the same quality.
 */
object ChannelMerger {
    /**
     * Groups the [entries] that are the same channel across sources, at most one from each
     * source: the first in [entries]' order. Only groups that span two or more sources come back.
     *
     * @param sourceOrder the source ids, the primary first.
     * @param preferred the qualities, the preferred first (Preferred Quality, 5.2).
     * @param apart channels the user unmerged on Duplicate Numbers (4.30), by [identityOf].
     */
    fun merge(
        entries: List<MergeEntry>,
        sourceOrder: List<String>,
        preferred: List<Quality>,
        apart: Set<String> = emptySet(),
    ): List<MergedChannel> {
        val priority = sourceOrder.withIndex().associate { (index, id) -> id to index }
        val rank = preferred.withIndex().associate { (index, quality) -> quality to index }
        val groups = LinkedHashMap<String, LinkedHashMap<String, MergeEntry>>()
        for (entry in entries) {
            groups.getOrPut(identityOf(entry)) { LinkedHashMap() }.putIfAbsent(entry.sourceId, entry)
        }
        return groups.filter { (identity, bySource) -> bySource.size > 1 && identity !in apart }.values.map { bySource ->
            val members = bySource.values
            val leader = members.minBy { priority[it.sourceId] ?: Int.MAX_VALUE }
            val ordered = members.sortedWith(
                compareBy<MergeEntry>({ rank[qualityOf(it.name)] ?: preferred.size }, { priority[it.sourceId] ?: Int.MAX_VALUE }),
            )
            MergedChannel(leader.id, ordered.map { it.id })
        }
    }

    /**
     * What two sources' channels share when they're the same channel. A name that's nothing but a
     * number once its tags are gone ("UK: 1") says too little: such a channel merges by guide id only.
     */
    fun identityOf(entry: MergeEntry): String {
        val guideId = entry.guideId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "null" && it != "none" }
        if (guideId != null) return "id:$guideId"
        val name = normalizedName(entry.name)
        if (name.none(Char::isLetter)) return "own:${entry.id}"
        return "name:$name|${qualityOf(entry.name)}"
    }

    /** The quality a channel's name gives: "Arena Sports 1 FHD", "Northline News [720p]", "Velocity 4K". */
    fun qualityOf(name: String): Quality {
        val tokens = tokensOf(name)
        return when {
            tokens.any { it in UHD } -> Quality.Uhd
            tokens.any { it in FHD } -> Quality.Fhd
            tokens.any { it in HD } -> Quality.Hd
            tokens.any { it in SD } -> Quality.Sd
            else -> Quality.Unknown
        }
    }

    /**
     * A channel's name without accents, case, a country tag ("UK: ", "US | "), quality tags or
     * punctuation: "UK: Arena Sports 1 FHD" and "Arena Sports 1 HD" are both "arena sports 1". Other
     * prefixes stay: "NFL | Game 01" and "NBA | Game 01" are different channels.
     */
    fun normalizedName(name: String): String {
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()
        val tag = COUNTRY_TAG.find(plain)?.takeIf { match -> match.groupValues.drop(1).any { it in COUNTRY_CODES } }
        val untagged = (if (tag != null) plain.substring(tag.range.last + 1) else plain).replace("&", " and ").replace("+", " plus ")
        return tokensOf(untagged).filterNot { it in TAGS }.joinToString(" ")
    }

    /** The order a Preferred Quality choice puts streams in (Settings › Channels, 4.29). */
    fun qualityOrder(first: Quality): List<Quality> = when (first) {
        Quality.Uhd -> listOf(Quality.Uhd, Quality.Fhd, Quality.Hd, Quality.Unknown, Quality.Sd)
        Quality.Hd -> listOf(Quality.Hd, Quality.Fhd, Quality.Uhd, Quality.Unknown, Quality.Sd)
        // FHD, then HD, then 4K: the default (5.2).
        else -> listOf(Quality.Fhd, Quality.Hd, Quality.Uhd, Quality.Unknown, Quality.Sd)
    }

    private fun tokensOf(text: String): List<String> = text.lowercase().split(NON_WORD).filter { it.isNotEmpty() }

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** "uk: ", "us | ", "ca - " at the start: maybe a provider's country prefix, if it's a country's code. */
    private val COUNTRY_TAG = Regex("^\\s*([a-z]{2,3})\\s*[:|]\\s*|^\\s*([a-z]{2,3})\\s+-\\s+")

    /**
     * Countries' two- and three-letter codes, and the tags providers use besides: UK, and regions
     * such as LAT (Latin America) and ARA (Arabic).
     */
    private val COUNTRY_CODES: Set<String> = buildSet {
        for (code in Locale.getISOCountries()) {
            add(code.lowercase())
            runCatching { Locale.Builder().setRegion(code).build().isO3Country }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { add(it.lowercase()) }
        }
        addAll(listOf("uk", "ger", "ara", "lat", "afr", "eu", "eur", "asi", "int"))
    }

    private val UHD = setOf("uhd", "4k", "2160p", "2160")
    private val FHD = setOf("fhd", "1080p", "1080i", "1080")
    private val HD = setOf("hd", "720p", "720")
    private val SD = setOf("sd", "576p", "576i", "480p", "480i")

    /** Words that say how a stream is sent, not which channel it is. */
    private val TAGS = UHD + FHD + HD + SD + setOf("hevc", "h264", "h265", "50fps", "60fps", "raw")
}
