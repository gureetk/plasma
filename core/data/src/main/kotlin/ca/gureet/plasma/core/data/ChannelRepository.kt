// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.channels.ChannelMerger
import ca.gureet.plasma.channels.MergeEntry
import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.channels.Quality
import ca.gureet.plasma.core.database.CategoryMemberEntity
import ca.gureet.plasma.core.database.ChannelEntity
import ca.gureet.plasma.core.database.GroupEntity
import ca.gureet.plasma.core.database.LogoCounts
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.CategoryOrder
import ca.gureet.plasma.core.model.CategoryOrdering
import ca.gureet.plasma.core.model.CategoryPlace
import ca.gureet.plasma.core.model.ChannelLogos
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.PreferredQuality
import ca.gureet.plasma.core.model.SettingsChoices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

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

/** The channels of the sources turned on, in number order, with their groups' names. */
data class ChannelLineup(
    /** Each channel as its source lists it. */
    val listed: List<Channel>,
    /**
     * As the screens show them: with Merge Duplicate Channels (on by default), the same channel
     * from several sources is one, under the primary source's (5.1).
     */
    val shown: List<Channel>,
)

/** Channels, groups, favorites and recent channels, for the screens (brief 5.2). */
class ChannelRepository(
    private val db: PlasmaDatabase,
    private val settings: SettingsStore,
    private val vault: Vault,
    private val clock: PlasmaClock,
    private val profile: ProfileSource = ProfileSource.fixed(),
    /** The pictures known not to load on this TV (#228): with Replace Placeholder Logos, a logo among them gives way to the other, or the name. */
    unloadable: Flow<UnloadablePictures.Known> = flowOf(UnloadablePictures.Known.NONE),
) {
    /**
     * The channels' logos, their sources' and the guide's, known not to load (#228). What's known
     * changes with each picture that doesn't load, posters among them; the channels change only
     * when this does. None to begin with, so the channels needn't wait for it.
     */
    private val logosNotLoading: Flow<Set<String>> = combine(db.channels().observeLogos(), unloadable) { logos, known ->
        val notLoading: Set<String> = logos.filterTo(HashSet()) { it in known }
        notLoading
    }.onStart { emit(emptySet()) }.distinctUntilChanged().flowOn(Dispatchers.Default)

    /** Every channel as listed and as shown, for the screens and Duplicate Numbers (4.30). */
    val lineup: Flow<ChannelLineup> = combine(
        db.channels().observeAll(),
        db.groups().observeAll(),
        db.sources().observeAll(),
        combine(settings.choices, logosNotLoading, ::Pair),
        settings.unmerged,
    ) { channels, groups, sources, (chosen, notLoading), unmerged ->
        val names = groups.associate { it.id to (it.customName ?: it.name) }
        val choices = SettingsChoices(chosen)
        val replacing = choices.replacePlaceholderLogos
        val logos = LogoChoice(choices.channelLogos, replacing, if (replacing) placeholderLogos(channels) else emptySet(), notLoading)
        val all = channels.map { it.toChannel(names[it.groupId], logos) }
        ChannelLineup(all, if (choices.mergeDuplicates) merged(all, sources.map { it.id }, choices.preferredQuality, unmerged) else all)
    }

    /** How the channels' logos stand, for Export Diagnostics (#228): placeholders are as [placeholderLogos] finds them. */
    suspend fun logoCounts(): LogoCounts = db.pictures().logoCounts(StandIns.TITLES)

    /**
     * Every channel, in number order, with its group's name. With Merge Duplicate Channels (on by
     * default), the same channel from several sources is one, under the primary source's (5.1).
     */
    val channels: Flow<List<Channel>> = lineup.map { it.shown }

    /**
     * The groups, the primary source's first, each source's in the order its Categories page says
     * (the provider's unless the user chose another) and under the names the user gave them, with
     * the user's own groups among them (the owner's ask, 1 November).
     */
    val groups: Flow<List<ChannelGroup>> = combine(
        db.groups().observeAll(),
        // Only what orders the groups: a refresh writes to its source as it goes.
        db.sources().observeAll().map { list -> list.map { SourceOrder(it.id, it.enabled, CategoryOrder.of(it.liveOrder)) } }.distinctUntilChanged(),
        db.categories().observeMembers(),
        ::arrangedGroups,
    ).distinctUntilChanged()

    /** Favorite channel ids, in the user's order. */
    val favoriteIds: Flow<List<String>> = profile.watching { db.favorites().observe(it) }.map { list -> list.map { it.channelId } }

    /** The last 50 channels watched, the latest first. */
    val recentIds: Flow<List<String>> = profile.watching { db.recentChannels().observe(it) }.map { list -> list.map { it.channelId } }

    val hasChannels: Flow<Boolean> = db.channels().observeCount().map { it > 0 }

    /** How many channels each source has, by source id, sources turned off included. */
    val countsBySource: Flow<Map<String, Int>> = db.channels().observeCountsBySource().map { list -> list.associate { it.sourceId to it.channels } }

    /** The most days of catch-up any of each source's channels has, by source id (#189). */
    val catchUpDaysBySource: Flow<Map<String, Int>> = db.channels().observeCatchUpDaysBySource().map { list -> list.associate { it.sourceId to it.count } }

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
        val profileId = profile.now()
        val isFavorite = db.favorites().list(profileId).any { it.channelId == channelId }
        if (isFavorite) db.favorites().remove(profileId, channelId) else db.favorites().add(profileId, channelId, emptySet())
        return !isFavorite
    }

    /**
     * Adds or removes the favorite [channelId] (brief 5.2). A merged channel may be kept under the
     * id of any of its sources, [sameChannel] (5.1): adding finds it there, and removing takes it
     * from there.
     */
    suspend fun setFavorite(channelId: String, favorite: Boolean, sameChannel: Set<String> = emptySet()) {
        val profileId = profile.now()
        if (favorite) db.favorites().add(profileId, channelId, sameChannel) else db.favorites().removeAll(profileId, (sameChannel + channelId).toList())
    }

    /**
     * Menu › Move (brief 5.2): the favorite kept under any of [channel] comes right after the one
     * kept under any of [after], or first when that's null. Each is a channel's ids: its own, and a
     * merged channel's on each of its sources (5.1).
     */
    suspend fun moveFavorite(channel: Set<String>, after: Set<String>?) = db.favorites().move(profile.now(), channel, after)

    /**
     * Live TV's Favorites: [channelId], taken out and back in before it went from the list, is a
     * favorite again right after the one kept under any of [after] (first when that's null), the
     * place it had. [sameChannel] is as for [setFavorite].
     */
    suspend fun putBackFavorite(channelId: String, sameChannel: Set<String>, after: Set<String>?) =
        db.favorites().putBack(profile.now(), channelId, sameChannel, after)

    suspend fun setHidden(channelId: String, hidden: Boolean) = db.channels().setHidden(channelId, hidden)

    suspend fun setLocked(channelId: String, locked: Boolean) = db.channels().setLocked(channelId, locked)

    /** Locks a group with the parental PIN, or not; null leaves it to Lock Adult Groups (brief 5.12). */
    suspend fun setGroupLocked(groupId: String, locked: Boolean?) = db.groups().setLocked(groupId, locked)

    /** Makes a group kid-safe, or not; null leaves it to Kids Words (brief 5.11). */
    suspend fun setGroupKidSafe(groupId: String, kidSafe: Boolean?) = db.groups().setKidSafe(groupId, kidSafe)

    /** Hides a group, and with it its channels, or shows it again (Settings › Channels › Hidden Groups, 4.29). */
    suspend fun setGroupHidden(groupId: String, hidden: Boolean) = db.groups().setHidden(groupId, hidden)

    /** Notes a channel as watched, from [listId], for Recently Watched and Last Channel (brief 5.2). */
    suspend fun watched(channelId: String, listId: String?) {
        // A guest keeps no history (5.11).
        if (profile.keepsHistory()) db.recentChannels().watched(profile.now(), channelId, clock.now().toEpochMilli())
        settings.setLastWatched(channelId, listId)
    }

    /**
     * A channel OK played in Live TV's or the Guide's picture, from [listId] (the owner's ask, 29
     * October): the last channel now, so Home, the Guide and When the TV Turns On carry on with it.
     * Recently Watched stays what's been watched full screen, so the list doesn't reorder under you.
     */
    suspend fun previewed(channelId: String, listId: String?) = settings.setLastWatched(channelId, listId)
}

/**
 * [channels] with each that's the same across sources made one (brief 5.1): the primary source's
 * stands for it, with the others' as candidates in Preferred Quality's order. It shows the guide
 * and archive of whichever candidate has them.
 */
internal fun merged(channels: List<Channel>, sourceOrder: List<String>, quality: PreferredQuality, unmerged: Set<String> = emptySet()): List<Channel> {
    val first = when (quality) {
        PreferredQuality.Fhd -> Quality.Fhd
        PreferredQuality.Uhd -> Quality.Uhd
        PreferredQuality.Hd -> Quality.Hd
    }
    val merges = ChannelMerger.merge(channels.map(Channel::mergeEntry), sourceOrder, ChannelMerger.qualityOrder(first), unmerged)
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

/** The channel as merging sees it. */
internal fun Channel.mergeEntry() = MergeEntry(id, sourceId, name, tvgId)

/**
 * The channel as the screens have it. Its logo is the one Portals & Playlists › Channel Logos
 * says, the source's or the guide's, or the other one when that's missing.
 */
/**
 * [groups] as Live TV lists them: the sources turned on in their order, each one's groups in the
 * order its Categories page says, under the names the user gave them, the user's own with the
 * channels put in them ([members]).
 */
internal fun arrangedGroups(groups: List<GroupEntity>, sources: List<SourceOrder>, members: List<CategoryMemberEntity>): List<ChannelGroup> {
    val bySource = groups.groupBy { it.sourceId }
    val picked = members.groupBy({ it.categoryId }, { it.itemId })
    return sources.filter { it.enabled }.flatMap { source ->
        CategoryOrdering.sorted(bySource[source.id].orEmpty(), source.order) { it.place() }.map { group ->
            ChannelGroup(
                id = group.id,
                sourceId = group.sourceId,
                name = group.customName ?: group.name,
                hidden = group.hidden,
                locked = group.locked,
                kidSafe = group.kidSafe,
                providerName = group.name,
                custom = group.custom,
                members = picked[group.id].orEmpty(),
                adult = group.adult,
            )
        }
    }
}

/** A source, in its order, as far as ordering its categories of one kind goes. */
internal data class SourceOrder(val id: String, val enabled: Boolean, val order: CategoryOrder)

/** Where a group goes in its source's order. */
internal fun GroupEntity.place() = CategoryPlace(customName ?: name, custom, position, userPosition)

/**
 * How a channel's logo is chosen (#228): Channel Logos' order, the sources' or the guide's first;
 * with Replace Placeholder Logos ([replacing]), a source's [placeholders] give way to the guide's,
 * and a logo that's [notLoading] to the other, or the name. Off, every logo is the source's or the
 * guide's as it comes, as though nothing were known.
 */
internal class LogoChoice(
    val logos: ChannelLogos = ChannelLogos.FromPortal,
    val replacing: Boolean = false,
    val placeholders: Set<String> = emptySet(),
    val notLoading: Set<String> = emptySet(),
)

/**
 * The logos [StandIns.TITLES] or more of [channels] have from their sources (#228): a source's
 * placeholder for a channel it has no logo for, not the channel's. A logo many channels share for
 * their own, as a network's local stations' can, gives way only to each one's from the guide.
 */
internal fun placeholderLogos(channels: List<ChannelEntity>): Set<String> {
    val counts = HashMap<String, Int>()
    for (channel in channels) channel.logo?.let { counts.merge(it, 1, Int::plus) }
    return counts.filterValues { it >= StandIns.TITLES }.keys
}

/** The logo [choice] says this channel shows: one of its source's and the guide's, or none, for its name. */
internal fun ChannelEntity.shownLogo(choice: LogoChoice): String? {
    val notLoading = if (choice.replacing) choice.notLoading else emptySet()
    val own = logo?.takeUnless { it in notLoading }
    val guide = guideLogo?.takeUnless { it in notLoading }
    return when (choice.logos) {
        ChannelLogos.FromPortal -> if (choice.replacing && own != null && own in choice.placeholders) guide ?: own else own ?: guide
        ChannelLogos.FromGuide -> guide ?: own
    }
}

internal fun ChannelEntity.toChannel(groupName: String?, logos: LogoChoice = LogoChoice()) = Channel(
    id = id,
    sourceId = sourceId,
    key = key,
    name = name,
    number = number,
    numberReason = runCatching { NumberReason.valueOf(numberReason) }.getOrDefault(NumberReason.Playlist),
    logo = shownLogo(logos),
    groupId = groupId,
    groupName = groupName,
    epgKey = epgKey,
    catchupDays = catchupDays,
    isRadio = isRadio,
    hidden = hidden,
    locked = locked,
    tvgId = tvgId,
    wantedNumber = wantedNumber,
    position = position,
)
