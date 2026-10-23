// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.channels

import ca.gureet.plasma.core.model.ChannelNumbers
import ca.gureet.plasma.core.model.DuplicateRule

/** One of a source's channels, in playlist order. */
data class PlaylistEntry(
    /**
     * Stable within the source across refreshes: the Xtream stream id, or for a playlist the
     * `tvg-id`, else the name (brief 5.2).
     */
    val key: String,
    /** The playlist's own number: Xtream `num`, M3U `tvg-chno`, Stalker `number`. */
    val playlistNumber: Int?,
)

/** A source's channels, as numbering sees them. */
data class SourceEntries(val sourceId: String, val entries: List<PlaylistEntry>)

/** A channel, across every source. */
data class ChannelRef(val sourceId: String, val key: String)

/** Where a channel's number comes from (brief 6.3, `numberReason`). */
enum class NumberReason {
    /** The playlist's own number. */
    Playlist,

    /** Given by Plasma: the channel had no number, or numbers follow playlist order. */
    Auto,

    /** Moved off a number another channel kept (brief 5.2 and 4.30). */
    DuplicateFix,

    /** Chosen by the user: Keep, Choose Another Number, Swap or Change Number. */
    User,
}

data class AssignedNumber(
    val number: Int,
    val reason: NumberReason,
    /** For a [NumberReason.DuplicateFix], the number the channel wanted. */
    val wanted: Int? = null,
)

/** Where numbers come from (Settings › Channels, 4.29). */
enum class NumberSource {
    /** The playlists' numbers, the default. */
    Playlists,

    /** 1, 2, 3… across sources in priority order, ignoring the playlists' numbers. */
    PlaylistOrder,
}

/** Every channel's number after a refresh, and what changed. */
data class Numbering(
    val numbers: Map<ChannelRef, AssignedNumber>,
    /** Channels that lost a contested number in this refresh, for the review screen and the toast (4.30, 4.35). */
    val newDuplicates: List<ChannelRef>,
    /** Channels numbered for the first time in this refresh. */
    val newlyNumbered: List<ChannelRef>,
)

/**
 * Gives every channel a number (brief 5.2):
 * 1. the user's own choices;
 * 2. the numbers channels already had, because numbers are sticky and a refresh never moves them;
 * 3. the playlists' numbers, where free: the higher-priority source keeps a contested number, and
 *    within a source the first in playlist order;
 * 4. channels that lost a contested number move by [DuplicateRule] (108 → 1108);
 * 5. channels without a number get the next free one after the highest in their source.
 *
 * Pass no previous numbers to number everything afresh, as when Settings change the [NumberSource].
 */
object ChannelNumberer {
    fun assign(
        /** In priority order: the primary source first, then the order in Portals & Playlists. */
        sources: List<SourceEntries>,
        previous: Map<ChannelRef, AssignedNumber> = emptyMap(),
        overrides: Map<ChannelRef, Int> = emptyMap(),
        source: NumberSource = NumberSource.Playlists,
        rule: DuplicateRule = DuplicateRule.AddThousands,
    ): Numbering {
        val all = sources.flatMap { s -> s.entries.map { ChannelRef(s.sourceId, it.key) to it } }
        val result = LinkedHashMap<ChannelRef, AssignedNumber>(all.size * 2)
        val taken = HashSet<Int>(all.size * 2)
        val duplicates = mutableListOf<ChannelRef>()
        val fresh = mutableListOf<ChannelRef>()

        fun give(ref: ChannelRef, number: AssignedNumber) {
            taken += number.number
            result[ref] = number
        }

        // 1. The user's choices.
        for ((ref, _) in all) {
            val number = overrides[ref] ?: continue
            if (number !in taken) give(ref, AssignedNumber(number, NumberReason.User))
        }

        // 2. Numbers channels already had.
        for ((ref, _) in all) {
            if (ref in result) continue
            val kept = previous[ref] ?: continue
            if (kept.number !in taken) give(ref, kept)
        }

        // 3. The playlists' numbers, where free. A channel that lost its old number to a user's
        //    choice wants that one back, and is moved like any other duplicate.
        val contested = mutableListOf<Pair<ChannelRef, Int>>()
        val unnumbered = mutableListOf<ChannelRef>()
        for ((ref, entry) in all) {
            if (ref in result) continue
            val wanted = previous[ref]?.number ?: entry.playlistNumber.takeIf { source == NumberSource.Playlists }
            when {
                wanted == null -> unnumbered += ref
                wanted !in taken -> {
                    give(ref, AssignedNumber(wanted, NumberReason.Playlist))
                    fresh += ref
                }
                else -> contested += ref to wanted
            }
        }

        // 4. Duplicates move by the rule.
        for ((ref, wanted) in contested) {
            give(ref, AssignedNumber(ChannelNumbers.moveTo(wanted, rule, taken), NumberReason.DuplicateFix, wanted))
            duplicates += ref
            if (ref !in previous) fresh += ref
        }

        // 5. The rest follow the highest number in their source, or in the whole list when
        //    numbers follow playlist order. Moved duplicates don't count, or numbers would jump.
        fun highest(sourceId: String?): Int = result.entries
            .filter { (ref, number) -> (sourceId == null || ref.sourceId == sourceId) && number.reason != NumberReason.DuplicateFix }
            .maxOfOrNull { it.value.number } ?: 0
        val bySource = unnumbered.groupBy { it.sourceId }
        var next = if (source == NumberSource.PlaylistOrder) highest(null) else 0
        for (s in sources) {
            val refs = bySource[s.sourceId] ?: continue
            if (source == NumberSource.Playlists) next = highest(s.sourceId)
            for (ref in refs) {
                do next++ while (next in taken)
                give(ref, AssignedNumber(next, NumberReason.Auto))
                fresh += ref
            }
        }

        return Numbering(result, duplicates, fresh)
    }
}
