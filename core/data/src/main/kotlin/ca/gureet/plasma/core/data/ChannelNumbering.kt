// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.channels.AssignedNumber
import ca.gureet.plasma.channels.ChannelMerger
import ca.gureet.plasma.channels.ChannelNumberer
import ca.gureet.plasma.channels.ChannelRef
import ca.gureet.plasma.channels.MergeEntry
import ca.gureet.plasma.channels.NumberChoice
import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.channels.PlaylistEntry
import ca.gureet.plasma.channels.SourceEntries
import ca.gureet.plasma.core.database.ChannelEntity
import ca.gureet.plasma.core.database.NumberOverrideEntity
import ca.gureet.plasma.core.database.NumberUpdate
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.DuplicateRule
import ca.gureet.plasma.core.model.PlasmaClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Channel numbers after an import (brief 5.2 and 4.30): the choices on Duplicate Numbers, its fix
 * rule and Unmerge, and numbering every channel afresh when Settings › Channels › Channel Numbers
 * changes. It takes turns with channel imports through [lock], since they number too.
 */
class ChannelNumbering(
    private val db: PlasmaDatabase,
    private val settings: SettingsStore,
    private val clock: PlasmaClock,
    private val lock: Mutex,
) {
    /** Keep: the channel keeps the number it was moved to, whatever the fix rule. */
    suspend fun keep(channelId: String) = numbering {
        val channel = db.channels().get(channelId) ?: return@numbering
        val wanted = channel.wantedNumber ?: return@numbering
        give(listOf(channel to channel.number), wanted)
    }

    /**
     * Choose Another Number: the channel gets [number], if no channel of a source that's on has
     * it. Returns whether it did.
     */
    suspend fun choose(channelId: String, number: Int): Boolean = numbering {
        val channel = db.channels().get(channelId) ?: return@numbering false
        if (db.channels().withNumber(number).any { it.id != channelId }) return@numbering false
        give(listOf(channel to number), channel.wantedNumber)
        true
    }

    /**
     * Swap: the channel that was moved gets the number it wanted, and the channel that had it
     * takes the moved one's number.
     */
    suspend fun swap(channelId: String) = numbering {
        val moved = db.channels().get(channelId) ?: return@numbering
        val wanted = moved.wantedNumber ?: return@numbering
        val keeper = db.channels().withNumber(wanted).firstOrNull { it.id != moved.id }
        give(listOfNotNull(moved to wanted, keeper?.let { it to moved.number }), wanted)
    }

    /**
     * The fix rule (Menu on Duplicate Numbers): from now on, and for every channel numbering moved
     * so far. Numbers the user chose stay.
     */
    suspend fun setRule(rule: DuplicateRule) = numbering {
        settings.setDuplicateRule(rule)
        val channels = db.channels().inNumberingOrder()
        val byRef = channels.associateBy { ChannelRef(it.sourceId, it.key) }
        val moved = ChannelNumberer.moveAgain(channels.map { ChannelRef(it.sourceId, it.key) to it.assigned() }, rule)
        db.channels().renumber(moved.map { (ref, number) -> byRef.getValue(ref).update(number) })
    }

    /** Unmerge: the merged channel [channelId] stands for shows as one channel per source again. */
    suspend fun unmerge(channelId: String) {
        val channel = db.channels().get(channelId) ?: return
        settings.unmerge(ChannelMerger.identityOf(MergeEntry(channel.id, channel.sourceId, channel.name, channel.tvgId)))
    }

    /**
     * Numbers every channel afresh, the way Channel Numbers now says (brief 5.2). Numbers chosen
     * on Duplicate Numbers go with the clashes they settled; other numbers the user chose stay.
     */
    suspend fun numberAfresh() = numbering {
        val channels = db.channels().inNumberingOrder()
        val sources = channels.groupBy { it.sourceId }.map { (sourceId, list) ->
            SourceEntries(sourceId, list.map { PlaylistEntry(it.key, it.playlistNumber) })
        }
        val overrides = db.numberOverrides().all()
            .filter { it.wantedNumber == null }
            .associate { ChannelRef(it.sourceId, it.channelKey) to NumberChoice(it.number) }
        val setting = settings.numberingNow()
        val numbering = ChannelNumberer.assign(sources, overrides = overrides, source = setting.source, rule = setting.duplicateRule)
        val changed = channels.mapNotNull { channel ->
            val number = numbering.numbers[ChannelRef(channel.sourceId, channel.key)] ?: return@mapNotNull null
            channel.update(number).takeIf { number != channel.assigned() }
        }
        db.channels().renumber(changed, forgetDuplicateChoices = true)
    }

    /** Gives each channel its number as the user's choice, settling the clash over [wanted]. */
    private suspend fun give(numbers: List<Pair<ChannelEntity, Int>>, wanted: Int?) {
        val now = clock.now().toEpochMilli()
        db.channels().choose(
            overrides = numbers.map { (channel, number) -> NumberOverrideEntity(channel.sourceId, channel.key, number, now, wanted) },
            numbers = numbers.map { (channel, number) -> NumberUpdate(channel.id, number, NumberReason.User.name, wanted) },
        )
    }

    // Numbering reads tens of thousands of channels, off the main thread whoever calls.
    private suspend fun <T> numbering(block: suspend () -> T): T = withContext(Dispatchers.Default) { lock.withLock { block() } }
}

/** A channel's number as numbering sees it. */
internal fun ChannelEntity.assigned() = AssignedNumber(
    number,
    runCatching { NumberReason.valueOf(numberReason) }.getOrDefault(NumberReason.Playlist),
    wantedNumber,
)

private fun ChannelEntity.update(number: AssignedNumber) = NumberUpdate(id, number.number, number.reason.name, number.wanted)
