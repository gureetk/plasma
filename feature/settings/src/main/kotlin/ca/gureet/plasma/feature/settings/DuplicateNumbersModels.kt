// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.Immutable
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.model.ChannelNumbers
import ca.gureet.plasma.core.model.DuplicateRule

/** A channel as one source lists it. */
@Immutable
data class ListedChannel(val name: String, val logo: String?, val source: String, val sourceType: SourceType)

/** Why a conflict's channel kept the number (brief 5.2). */
enum class KeepReason {
    /** It's on the primary source. */
    Primary,

    /** Its source comes first in Portals & Playlists. */
    SourceOrder,

    /** Both are on one source, and it comes first in the playlist. */
    PlaylistOrder,

    /** It had the number before the other channel came, or before the sources' order changed. */
    HadItFirst,

    /** The user swapped the two. */
    Swapped,
}

/**
 * Two different channels the playlists gave the same [number] (brief 4.30): [keeper] keeps it,
 * and [mover] has [newNumber] instead. [id] is the mover's channel and [keeperId] the keeper's. A
 * [chosen] number came from the user (Keep, Choose Another Number or Swap) and stays when the rule
 * changes.
 */
@Immutable
data class NumberConflict(
    val id: String,
    val keeperId: String,
    val number: Int,
    val keeper: ListedChannel,
    val mover: ListedChannel,
    val newNumber: Int,
    val reason: KeepReason,
    val chosen: Boolean = false,
    /** How many channels wanted [number]: two, unless more clash over it. */
    val uses: Int = 2,
)

/** A channel that several sources carry, merged into one: its number and its sources in play order. */
@Immutable
data class MergedChannel(val id: String, val number: Int, val name: String, val logo: String?, val sources: List<String>)

/** How the lists on Duplicate Numbers are sorted (yellow). */
enum class DuplicateSort { Number, Name }

@Immutable
data class DuplicateNumbersState(
    val conflicts: List<NumberConflict>,
    val merged: List<MergedChannel>,
    /** How many channels are merged; the sample lists only some of appendix A's 38. */
    val mergedCount: Int = merged.size,
    val rule: DuplicateRule = DuplicateRule.AddThousands,
    /** Every number in use apart from the new numbers of the movers the rule placed, and whose it is. */
    val numbers: Map<Int, String>,
) {
    /** Whose number [number] is, if anyone's. */
    fun owner(number: Int): String? = numbers[number] ?: conflicts.firstOrNull { it.newNumber == number }?.mover?.name

    /** The new numbers under [rule]; numbers the user chose stay. */
    fun withRule(rule: DuplicateRule): DuplicateNumbersState {
        val free = conflicts.filterNot { it.chosen }
        val taken = numbers.keys + conflicts.filter { it.chosen }.map { it.newNumber }
        val moved = free.map { it.id }.zip(ChannelNumbers.moveAll(free.map { it.number }, rule, taken)).toMap()
        return copy(rule = rule, conflicts = conflicts.map { c -> moved[c.id]?.let { c.copy(newNumber = it) } ?: c })
    }

    /** Keeps the conflict [id] as it is, even if the rule changes. */
    fun keep(id: String): DuplicateNumbersState = update(id) { it.copy(chosen = true) }

    /** Gives the channel that moved in conflict [id] the number [newNumber]. */
    fun choose(id: String, newNumber: Int): DuplicateNumbersState = update(id) { it.copy(newNumber = newNumber, chosen = true) }

    /**
     * Gives the contested number to the channel that moved, and moves the other one to its new
     * number: the conflict is then the other channel's, under its id.
     */
    fun swap(id: String): DuplicateNumbersState {
        val conflict = conflicts.first { it.id == id }
        return copy(
            conflicts = conflicts.map {
                if (it.id != id) {
                    it
                } else {
                    it.copy(id = it.keeperId, keeperId = it.id, keeper = it.mover, mover = it.keeper, reason = KeepReason.Swapped, chosen = true)
                }
            },
            numbers = numbers + (conflict.number to conflict.mover.name),
        )
    }

    /** Splits a merged channel back into one channel per source. */
    fun unmerge(channel: MergedChannel): DuplicateNumbersState = copy(merged = merged - channel, mergedCount = mergedCount - 1)

    private fun update(id: String, change: (NumberConflict) -> NumberConflict) =
        copy(conflicts = conflicts.map { if (it.id == id) change(it) else it })
}

/** What the choices on Duplicate Numbers do beyond the screen: the app keeps them (brief 4.30). */
@Immutable
class DuplicateNumbersActions(
    val onKeep: (NumberConflict) -> Unit = {},
    val onChoose: (NumberConflict, number: Int) -> Unit = { _, _ -> },
    val onSwap: (NumberConflict) -> Unit = {},
    val onRule: (DuplicateRule) -> Unit = {},
    val onUnmerge: (MergedChannel) -> Unit = {},
)

/** Board 30's conflicts (appendix A): Sports Extras' channels that clash with Home Portal's. */
object DuplicateNumbersSamples {
    private const val HOME = "Home Portal"
    private const val BACKUP = "Backup Line"
    private const val EXTRAS = "Sports Extras"

    private fun onHome(channel: Sample.Channel) = ListedChannel(channel.name, channel.logo, HOME, SourceType.Stalker)

    private fun onExtras(name: String, logo: String?) = ListedChannel(name, logo, EXTRAS, SourceType.M3U)

    val state: DuplicateNumbersState = DuplicateNumbersState(
        conflicts = listOf(
            NumberConflict(
                "extras:arena-extra-2", "home:104", 104, onHome(Sample.arenaExtra), onExtras("Arena Sports Extra 2", Sample.arenaExtra.logo), 1104,
                KeepReason.Primary,
            ),
            NumberConflict("extras:courtside-classics", "home:108", 108, onHome(Sample.courtside), onExtras("Courtside Classics", null), 1108, KeepReason.Primary),
            NumberConflict("extras:fairway-2", "home:109", 109, onHome(Sample.fairway), onExtras("Fairway Golf 2", Sample.fairway.logo), 1109, KeepReason.Primary),
            NumberConflict("extras:ringside-2", "extras:ringside-1", 212, onExtras("Ringside 1", null), onExtras("Ringside 2", null), 1212, KeepReason.PlaylistOrder),
        ),
        // Both portals carry the sample channels; Fairway Golf plays from Backup Line first (appendix A).
        merged = Sample.channels.map { channel ->
            val sources = if (channel == Sample.fairway) listOf(BACKUP, HOME) else listOf(HOME, BACKUP)
            MergedChannel("home:${channel.number}", channel.number, channel.name, channel.logo, sources)
        },
        mergedCount = SettingsSamples.facts.mergedChannels,
        numbers = Sample.channels.associate { it.number to it.name } + (212 to "Ringside 1"),
    )
}
