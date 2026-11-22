// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.epg.GuideKey

/**
 * Which guide each of a source's channels takes its programmes and its logo from (brief 5.3), as
 * the source's guides are read in order: a login's own, then the extra ones typed in for it (4.21);
 * a playlist's, as typed in or as its header names them.
 *
 * A channel takes its programmes from the first guide that has some for it, in the days the guide
 * keeps. One a guide lists but has none for is looked for in the next. Listed in none that has
 * any, it keeps the first guide that listed it, for when that guide has programmes again.
 *
 * Its logo is the first real one a guide has for it, whichever guide its programmes come from: not
 * a picture [StandIns.TITLES] or more of the source's channels share ([placeholders], the source's
 * placeholder), nor one a guide gives that many of its own channels (the guide's).
 */
internal class ChannelGuides(private val sourceId: String, private val placeholders: Set<String>) {
    /** Channel id to guide key, of a guide that has programmes for the channel. */
    private val withProgrammes = LinkedHashMap<String, String>()

    /** Channel id to guide key, of the first guide that listed the channel without programmes. */
    private val listed = LinkedHashMap<String, String>()

    /** Channel id to its logo from a guide. */
    private val logos = LinkedHashMap<String, String>()

    /** Guide ids some guide has written programmes for: a later one doesn't write them again. */
    val written: Set<String> get() = writtenIds
    private val writtenIds = HashSet<String>()

    /** Of [keys], the channels no guide has had programmes for yet, which the next guide is matched for. */
    fun wantingProgrammes(keys: List<GuideKey>): List<GuideKey> = keys.filter { it.channelId !in withProgrammes }

    /** Of [keys], the channels no guide has had a real logo for yet. */
    fun wantingLogos(keys: List<GuideKey>): List<GuideKey> = keys.filter { it.channelId !in logos }

    /** Takes in what the next guide gave. */
    fun add(read: GuideRead) {
        writtenIds += read.wrote
        for ((guideId, channelIds) in read.matched) {
            val key = "$sourceId:$guideId"
            for (channelId in channelIds) {
                if (channelId in withProgrammes) continue
                if (guideId in writtenIds) withProgrammes[channelId] = key else listed.putIfAbsent(channelId, key)
            }
        }
        val shared = sharedPictures(read.icons.map { it.second })
        val icons = HashMap<String, String>()
        for ((guideId, icon) in read.icons) icons.putIfAbsent(guideId, icon)
        for ((guideId, channelIds) in read.logoMatched) {
            val icon = icons[guideId]?.takeUnless { it in shared || it in placeholders } ?: continue
            for (channelId in channelIds) logos.putIfAbsent(channelId, icon)
        }
    }

    /** Each channel's guide key: the first guide with programmes for it, or else the first that listed it. */
    val epgKeys: Map<String, String> get() = LinkedHashMap(listed).apply { putAll(withProgrammes) }

    /** Each channel's logo from its guides, for those a guide has a real one for. */
    val guideLogos: Map<String, String> get() = logos.toMap()
}

/**
 * What one guide gave ([ChannelGuides.add]): the channels [matched] to its guide ids, for their
 * programmes; the guide ids it [wrote] programmes for; the channels [logoMatched] to its guide ids,
 * for their logos; and the [icons] of the channels it lists, by guide id, one for each it lists.
 */
internal class GuideRead(
    val matched: Map<String, List<String>>,
    val wrote: Set<String>,
    val logoMatched: Map<String, List<String>>,
    val icons: List<Pair<String, String>>,
)

/**
 * The pictures [StandIns.TITLES] or more of [pictures] share: a placeholder for many, not any one's
 * own, as a source's for channels it has no logo for.
 */
internal fun sharedPictures(pictures: Iterable<String?>): Set<String> {
    val counts = HashMap<String, Int>()
    for (picture in pictures) picture?.let { counts.merge(it, 1, Int::plus) }
    return counts.filterValues { it >= StandIns.TITLES }.keys
}
