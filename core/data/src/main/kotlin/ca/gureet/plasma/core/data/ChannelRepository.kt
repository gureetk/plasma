// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.channels.ChannelMerger
import ca.gureet.plasma.channels.MergeEntry
import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.channels.Quality
import ca.gureet.plasma.core.database.ChannelEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.PreferredQuality
import ca.gureet.plasma.core.model.SettingsChoices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Profiles come with M7; until then everything belongs to this one. */
const val DEFAULT_PROFILE = "default"

/** A playlist channel's own stream: its address, opened, and the headers the playlist gives it. */
data class ChannelStream(
    val url: String,
    val userAgent: String?,
    val referrer: String?,
    /** The playlist's catch-up mode and template for the channel (brief 7.3), when it gives them. */
    val catchupType: String? = null,
    val catchupSource: String? = null,
)

/** Channels, groups, favorites and recent channels, for the screens (brief 5.2). */
class ChannelRepository(
    private val db: PlasmaDatabase,
    private val settings: SettingsStore,
    private val vault: Vault,
    private val clock: PlasmaClock,
    private val profileId: String = DEFAULT_PROFILE,
) {
    /**
     * Every channel, in number order, with its group's name. With Merge Duplicate Channels (on by
     * default), the same channel from several sources is one, under the primary source's (5.1).
     */
    val channels: Flow<List<Channel>> = combine(
        db.channels().observeAll(),
        db.groups().observeAll(),
        db.sources().observeAll(),
        settings.choices,
    ) { channels, groups, sources, chosen ->
        val names = groups.associate { it.id to it.name }
        val all = channels.map { it.toChannel(names[it.groupId]) }
        val choices = SettingsChoices(chosen)
        if (choices.mergeDuplicates) merged(all, sources.map { it.id }, choices.preferredQuality) else all
    }

    /** The groups, the primary source's first, each in the provider's order. */
    val groups: Flow<List<ChannelGroup>> = db.groups().observeAll().map { list ->
        list.map { ChannelGroup(it.id, it.sourceId, it.name, it.hidden) }
    }

    /** Favorite channel ids, in the user's order. */
    val favoriteIds: Flow<List<String>> = db.favorites().observe(profileId).map { list -> list.map { it.channelId } }

    /** The last 50 channels watched, the latest first. */
    val recentIds: Flow<List<String>> = db.recentChannels().observe(profileId).map { list -> list.map { it.channelId } }

    val hasChannels: Flow<Boolean> = db.channels().observeCount().map { it > 0 }

    /** How many channels each source has, by source id, sources turned off included. */
    val countsBySource: Flow<Map<String, Int>> = db.channels().observeCountsBySource().map { list -> list.associate { it.sourceId to it.channels } }

    val lastWatched: Flow<LastWatched?> = settings.lastWatched

    suspend fun get(channelId: String): Channel? = db.channels().get(channelId)?.toChannel(null)

    /** A playlist channel's stream, or null for a channel whose address is built when it plays (Xtream). */
    suspend fun streamOf(channelId: String): ChannelStream? {
        val channel = db.channels().get(channelId) ?: return null
        val sealed = channel.streamUrlEnc ?: return null
        val fields = vault.fields()
        return ChannelStream(fields.open(sealed), channel.userAgent, channel.referrer, channel.catchupType, channel.catchupSourceEnc?.let(fields::open))
    }

    /** Red, or Add to Favorites: returns whether the channel is a favorite now. */
    suspend fun toggleFavorite(channelId: String): Boolean {
        val isFavorite = db.favorites().list(profileId).any { it.channelId == channelId }
        if (isFavorite) db.favorites().remove(profileId, channelId) else db.favorites().add(profileId, channelId)
        return !isFavorite
    }

    suspend fun setFavorite(channelId: String, favorite: Boolean) {
        if (favorite) db.favorites().add(profileId, channelId) else db.favorites().remove(profileId, channelId)
    }

    suspend fun moveFavorite(channelId: String, index: Int) = db.favorites().move(profileId, channelId, index)

    suspend fun setHidden(channelId: String, hidden: Boolean) = db.channels().setHidden(channelId, hidden)

    suspend fun setLocked(channelId: String, locked: Boolean) = db.channels().setLocked(channelId, locked)

    /** Notes a channel as watched, from [listId], for Recently Watched and Last Channel (brief 5.2). */
    suspend fun watched(channelId: String, listId: String?) {
        db.recentChannels().watched(profileId, channelId, clock.now().toEpochMilli())
        settings.setLastWatched(channelId, listId)
    }
}

/**
 * [channels] with each that's the same across sources made one (brief 5.1): the primary source's
 * stands for it, with the others' as candidates in Preferred Quality's order. It shows the guide
 * and archive of whichever candidate has them.
 */
internal fun merged(channels: List<Channel>, sourceOrder: List<String>, quality: PreferredQuality): List<Channel> {
    val first = when (quality) {
        PreferredQuality.Fhd -> Quality.Fhd
        PreferredQuality.Uhd -> Quality.Uhd
        PreferredQuality.Hd -> Quality.Hd
    }
    val merges = ChannelMerger.merge(channels.map { MergeEntry(it.id, it.sourceId, it.name, it.tvgId) }, sourceOrder, ChannelMerger.qualityOrder(first))
    if (merges.isEmpty()) return channels
    val byId = channels.associateBy { it.id }
    val leaders = merges.associateBy { it.leaderId }
    val absorbed = merges.flatMapTo(HashSet()) { merge -> merge.candidateIds.filter { it != merge.leaderId } }
    return channels.mapNotNull { channel ->
        if (channel.id in absorbed) return@mapNotNull null
        val merge = leaders[channel.id] ?: return@mapNotNull channel
        val members = merge.candidateIds.mapNotNull(byId::get)
        channel.copy(
            candidates = merge.candidateIds,
            epgKey = channel.epgKey ?: members.firstNotNullOfOrNull { it.epgKey },
            catchupDays = members.mapNotNull { it.catchupDays }.maxOrNull(),
        )
    }
}

internal fun ChannelEntity.toChannel(groupName: String?) = Channel(
    id = id,
    sourceId = sourceId,
    key = key,
    name = name,
    number = number,
    numberReason = runCatching { NumberReason.valueOf(numberReason) }.getOrDefault(NumberReason.Playlist),
    logo = logo,
    groupId = groupId,
    groupName = groupName,
    epgKey = epgKey,
    catchupDays = catchupDays,
    isRadio = isRadio,
    hidden = hidden,
    locked = locked,
    tvgId = tvgId,
)
