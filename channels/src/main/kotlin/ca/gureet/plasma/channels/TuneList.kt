// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.channels

/** A channel in a list people tune from. */
data class ListChannel(
    val id: String,
    /** Its number in the full list. */
    val number: Int,
    /** Hidden channels keep their numbers but zapping skips them (brief 5.2). */
    val hidden: Boolean = false,
)

/**
 * A list people tune from (brief 5.2): All Channels, Favorites or a group. Its numbers are the
 * full list's, or count from 1 in the list's own order, as Favorites do by default.
 */
class TuneList(
    val id: String,
    val channels: List<ListChannel>,
    val countsFromOne: Boolean = false,
) {
    private val byNumber: Map<Int, ListChannel> by lazy {
        if (countsFromOne) {
            channels.withIndex().associate { (index, channel) -> index + 1 to channel }
        } else {
            // The first wins should two share a number, as numbering never allows.
            channels.reversed().associateBy { it.number }
        }
    }

    private val indexById: Map<String, Int> by lazy { channels.withIndex().associate { (index, channel) -> channel.id to index } }

    operator fun contains(channelId: String): Boolean = channelId in indexById

    /** The number this list shows for the channel. */
    fun numberOf(channelId: String): Int? {
        val index = indexById[channelId] ?: return null
        return if (countsFromOne) index + 1 else channels[index].number
    }

    /** The channel this list gives [number], hidden ones included, so typing one can offer Unhide. */
    fun find(number: Int): ListChannel? = byNumber[number]

    /**
     * The channel [step] places from [channelId] (CH + is +1), wrapping at the ends and skipping
     * hidden channels. From a channel that isn't in the list, +1 starts at the top and −1 at the end.
     */
    fun neighbor(channelId: String, step: Int): ListChannel? {
        if (channels.none { !it.hidden } || step == 0) return null
        val size = channels.size
        var index = indexById[channelId] ?: if (step > 0) -1 else size
        val direction = if (step > 0) 1 else -1
        var remaining = kotlin.math.abs(step)
        var guard = size * (remaining + 1)
        while (remaining > 0 && guard-- > 0) {
            index = Math.floorMod(index + direction, size)
            if (!channels[index].hidden) remaining--
        }
        return channels[index]
    }
}

/** How number keys pick a channel (Settings › Channels, 4.29). */
enum class NumberKeys {
    /** In a list that counts from 1, its own numbers first; the default. */
    CurrentListFirst,

    /** Always the full list's numbers. */
    FullListOnly,
}

/** What a typed number tunes, and the list zapping follows afterwards. */
data class TypedChannel(val channel: ListChannel, val list: TuneList)

object NumberEntry {
    /**
     * The channel a typed [number] tunes (brief 5.2): in a list that counts from 1, or while
     * watching a channel tuned from one, that list's channel if it has one; otherwise the full
     * list's. Tuning a full-list number switches zapping to All Channels.
     */
    fun find(number: Int, current: TuneList?, all: TuneList, keys: NumberKeys = NumberKeys.CurrentListFirst): TypedChannel? {
        if (keys == NumberKeys.CurrentListFirst && current != null && current.countsFromOne) {
            current.find(number)?.let { return TypedChannel(it, current) }
        }
        return all.find(number)?.let { TypedChannel(it, all) }
    }
}
