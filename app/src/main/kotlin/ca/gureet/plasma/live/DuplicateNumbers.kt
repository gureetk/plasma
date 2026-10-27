// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.ChannelLineup
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.core.model.DuplicateRule
import ca.gureet.plasma.feature.settings.DuplicateNumbersState
import ca.gureet.plasma.feature.settings.KeepReason
import ca.gureet.plasma.feature.settings.ListedChannel
import ca.gureet.plasma.feature.settings.MergedChannel
import ca.gureet.plasma.feature.settings.NumberConflict
import ca.gureet.plasma.feature.settings.SourceType

/** Settings › Channels' counts (brief 4.29). */
data class ChannelSummary(
    val merged: Int = 0,
    val duplicatesFixed: Int = 0,
    val hiddenChannels: Int = 0,
    val hiddenGroups: Int = 0,
)

/**
 * Duplicate Numbers (brief 4.30) for the channels of the sources that are on: each channel the
 * lists show that numbering moved off a number another channel kept, or that the user gave a
 * number on the screen, with the channel that has the number; and the merged channels, with their
 * sources in play order. A clash whose number no other channel has any more isn't one.
 */
fun duplicateNumbersState(lineup: ChannelLineup, sources: List<Source>, rule: DuplicateRule): DuplicateNumbersState {
    val byId = sources.associateBy { it.id }
    val rank = sources.withIndex().associate { (index, source) -> source.id to index }
    // Whose each number is: a channel the lists show before one merging hides.
    val holders = HashMap<Int, Channel>(lineup.listed.size * 2)
    for (channel in lineup.listed) holders.putIfAbsent(channel.number, channel)
    for (channel in lineup.shown) holders[channel.number] = channel

    val movers = lineup.shown.filter { channel ->
        val wanted = channel.wantedNumber
        wanted != null && wanted != channel.number && channel.numberReason in MOVED
    }
    val clashes = movers.groupingBy { it.wantedNumber }.eachCount()
    val conflicts = movers.mapNotNull { mover ->
        val wanted = mover.wantedNumber ?: return@mapNotNull null
        val keeper = holders[wanted]?.takeIf { it.id != mover.id } ?: return@mapNotNull null
        NumberConflict(
            id = mover.id,
            keeperId = keeper.id,
            number = wanted,
            keeper = keeper.listed(byId),
            mover = mover.listed(byId),
            newNumber = mover.number,
            reason = keepReason(keeper, mover, rank, byId),
            chosen = mover.numberReason == NumberReason.User,
            uses = 1 + (clashes[wanted] ?: 1),
        )
    }

    val listedById = lineup.listed.associateBy { it.id }
    val merged = lineup.shown.filter { it.isMerged }.map { channel ->
        val names = channel.candidates.mapNotNull { id -> listedById[id]?.let { byId[it.sourceId]?.name } }
        MergedChannel(channel.id, channel.number, channel.name, channel.logo, names)
    }

    // The numbers the rule gave stay out: another rule moves those channels again.
    val placed = conflicts.filterNot { it.chosen }.mapTo(HashSet()) { it.id }
    val numbers = HashMap<Int, String>(holders.size * 2)
    for ((number, channel) in holders) if (channel.id !in placed) numbers[number] = channel.name
    return DuplicateNumbersState(conflicts = conflicts, merged = merged, rule = rule, numbers = numbers)
}

/** How many channels Settings › Channels counts. */
fun channelSummary(lineup: ChannelLineup, duplicates: DuplicateNumbersState, hiddenGroups: Int) = ChannelSummary(
    merged = duplicates.mergedCount,
    duplicatesFixed = duplicates.conflicts.size,
    hiddenChannels = lineup.shown.count { it.hidden },
    hiddenGroups = hiddenGroups,
)

/** Numbers numbering moved, or that the user chose instead. */
private val MOVED = setOf(NumberReason.DuplicateFix, NumberReason.User)

private fun Channel.listed(sources: Map<String, Source>): ListedChannel {
    val source = sources[sourceId]
    return ListedChannel(name, logo, source?.name.orEmpty(), source?.let { typeOf(it.type) } ?: SourceType.M3U)
}

/**
 * Why [keeper] has the number [mover] wanted (brief 5.2): the user gave it, it's first in their
 * shared source's playlist, or its source comes first. Otherwise it had the number first.
 */
private fun keepReason(keeper: Channel, mover: Channel, rank: Map<String, Int>, sources: Map<String, Source>): KeepReason {
    if (keeper.numberReason == NumberReason.User) return KeepReason.Swapped
    if (keeper.sourceId == mover.sourceId) return if (keeper.position < mover.position) KeepReason.PlaylistOrder else KeepReason.HadItFirst
    val keeperRank = rank[keeper.sourceId] ?: Int.MAX_VALUE
    val moverRank = rank[mover.sourceId] ?: Int.MAX_VALUE
    return when {
        keeperRank > moverRank -> KeepReason.HadItFirst
        sources[keeper.sourceId]?.isPrimary == true -> KeepReason.Primary
        else -> KeepReason.SourceOrder
    }
}
