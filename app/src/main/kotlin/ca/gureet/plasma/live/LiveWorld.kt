// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import android.os.Trace
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.ChannelGroup
import ca.gureet.plasma.core.data.GuideRepository
import ca.gureet.plasma.core.data.LastWatched
import ca.gureet.plasma.core.data.NowNext
import ca.gureet.plasma.core.data.NumberingSettings
import ca.gureet.plasma.core.data.PlasmaData
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.core.model.ContentRatings
import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The lists people tune from (brief 5.2), as Live TV names its smart groups. */
object Lists {
    const val ALL = "all"
    const val FAVORITES = "favorites"
    const val RECENT = "recent"
}

/**
 * Everything the live screens draw from at one moment: the channels, the lists, the guide around
 * now, and the settings that number them.
 */
data class LiveWorld(
    /** Every channel in number order, hidden ones included: they keep their numbers (5.2). */
    val channels: List<Channel>,
    val groups: List<ChannelGroup>,
    val favoriteIds: List<String>,
    val recentIds: List<String>,
    /** The guide around now, by guide channel, each in time order. */
    val programmes: Map<String, List<Programme>>,
    val numbering: NumberingSettings,
    val lastWatched: LastWatched?,
    val now: Instant,
    /** Programmes with a reminder, by the id the screens give them (brief 5.7). */
    val reminderIds: Set<String> = emptySet(),
    /** Each source's name by its id: where a channel plays from (5.1). */
    val sourceNames: Map<String, String> = emptyMap(),
    /** Programmes being recorded, or set to be, by the same ids (brief 5.6). */
    val recordingIds: Set<String> = emptySet(),
    /** The Record Series rules' titles, each with its channel or any ([seriesKey]). */
    val seriesKeys: Set<String> = emptySet(),
    /** Local catch-up (brief 5.5): the channels chosen for it, and what it holds of each. */
    val local: LocalCatchUpNow = LocalCatchUpNow(),
    /** Age Limit (5.12): programs rated for this age or older need the PIN; null when it's off. */
    val ratingLimit: Int? = null,
    /**
     * Now and next from an Xtream server's `get_short_epg`, by channel id, for channels the full
     * guide doesn't cover (5.3): fetched as they're looked at.
     */
    val shortGuide: Map<String, List<Programme>> = emptyMap(),
    /**
     * Settings › Appearance › Clock: 12-Hour. It's here so the states made from this world write
     * their times as chosen, and are made again at once when the choice changes (#118).
     */
    val twelveHour: Boolean = false,
    /**
     * Whether [programmes] is the whole guide window, as for every screen; false for Home's first
     * look as Plasma starts, which has only the guide of the channels Home shows (#127).
     */
    val guideWhole: Boolean = true,
) {
    /** Whether [programme] needs the PIN under Age Limit (5.12). */
    fun overLimit(programme: Programme?): Boolean = ContentRatings.locks(programme?.rating, ratingLimit)

    /**
     * Whether [channel] needs the PIN now (5.12): it's locked, or what's on is rated at Age Limit
     * or older. Until the PIN opens it, it shows no picture.
     */
    fun lockedNow(channel: Channel): Boolean = channel.locked || (ratingLimit != null && overLimit(nowNext(channel).now))

    /** Whether [channel]'s past plays: from the provider's archive, or from local catch-up (5.5). */
    fun hasCatchUp(channel: Channel): Boolean = (channel.catchupDays ?: 0) > 0 || channel.id in local.windows

    /** From when [channel]'s past plays: as far as the provider's archive goes, or local catch-up holds. */
    fun catchUpFrom(channel: Channel): Instant? {
        val archive = channel.catchupDays?.takeIf { it > 0 }?.let { now.minus(Duration.ofDays(it.toLong())) }
        val held = local.windows[channel.id]?.let { Instant.ofEpochMilli(it.first) }
        return listOfNotNull(archive, held).minOrNull()
    }

    /**
     * Whether a Record Series rule records [title] on [channelId], or on any channel. Asked for every
     * program in the Guide and the player, so with no rules it makes no keys.
     */
    fun hasSeriesRule(title: String, channelId: String): Boolean =
        seriesKeys.isNotEmpty() && (seriesKey(title, channelId) in seriesKeys || seriesKey(title, null) in seriesKeys)

    /**
     * Channels by id, a merged channel under each of its candidates' ids too (brief 5.1): a
     * favorite or a recent channel from either source finds it.
     */
    val byId: Map<String, Channel> = HashMap<String, Channel>(channels.size * 2).apply {
        for (channel in channels) for (candidate in channel.candidates) putIfAbsent(candidate, channel)
        for (channel in channels) put(channel.id, channel)
    }

    /** The favorites by the id their channel shows under, in the user's order. */
    val favoriteChannelIds: List<String> by lazy { favoriteIds.mapNotNull { byId[it]?.id }.distinct() }

    /**
     * [channel]'s programmes around now: from the guide, unless it has nothing on now or ahead and
     * the channel's server gave its own now and next (5.3).
     */
    fun programmesOf(channel: Channel): List<Programme> {
        val guide = channel.epgKey?.let { programmes[it] }.orEmpty()
        if (guide.lastOrNull()?.end?.isAfter(now) == true) return guide
        return shortGuide[channel.id]?.takeIf { it.isNotEmpty() } ?: guide
    }

    fun nowNext(channel: Channel): NowNext = GuideRepository.nowNext(programmesOf(channel), now)

    /** Each group's channels in number order, made once: a source can have thousands of channels in hundreds of groups. */
    private val byGroup: Map<String?, List<Channel>> by lazy { channels.groupBy { it.groupId } }

    /**
     * The channels the user put in each group of the user's own (the owner's ask, 1 November), in
     * number order, as provider's groups are: a channel from several sources once, under the id it
     * shows under.
     */
    private val picked: Map<String, List<Channel>> by lazy {
        groups.filter { it.custom }.associate { group -> group.id to group.members.mapNotNull(byId::get).distinctBy { it.id }.sortedBy { it.number } }
    }

    /** A list's channels in its own order, hidden ones included. */
    fun list(listId: String): List<Channel> = when (listId) {
        Lists.ALL -> channels
        Lists.FAVORITES -> favoriteChannelIds.mapNotNull(byId::get)
        Lists.RECENT -> recentIds.mapNotNull(byId::get).distinctBy { it.id }
        else -> picked[listId] ?: byGroup[listId].orEmpty()
    }

    /**
     * The list [channelId] was last played in, while it's the last channel watched and the list
     * still shows it: Favorites, Recently Watched or a group that isn't hidden. All Channels
     * otherwise. Home's banner opens the channel there, as Live TV does (#100).
     */
    fun lastListOf(channelId: String): String {
        val last = lastWatched?.takeIf { it.channelId == channelId }?.listId ?: return Lists.ALL
        val shown = last == Lists.ALL || last == Lists.FAVORITES || last == Lists.RECENT || groups.any { it.id == last }
        return last.takeIf { shown && list(it).any { channel -> channel.id == channelId } } ?: Lists.ALL
    }

    /** Favorites count from 1 by default; groups keep full-list numbers (5.2, Settings › Channels). */
    fun countsFromOne(listId: String): Boolean = when (listId) {
        Lists.FAVORITES -> numbering.favoritesCountFromOne
        Lists.ALL, Lists.RECENT -> false
        else -> numbering.groupsCountFromOne
    }

    /**
     * [channel]'s number in [listId], as the player shows it there: its place among the channels
     * that aren't hidden, in a list that counts from 1, or else its own number. Home's banner shows
     * it for the list the channel was last played in (the owner on #145, 4 November).
     */
    fun numberIn(listId: String, channel: Channel): Int {
        if (!countsFromOne(listId)) return channel.number
        val place = list(listId).filter { !it.hidden || it.id == channel.id }.indexOfFirst { it.id == channel.id }
        return if (place >= 0) place + 1 else channel.number
    }

    /** The channel to start on: the last one watched, or the first in the list. */
    val currentChannel: Channel?
        get() = lastWatched?.channelId?.let(byId::get) ?: channels.firstOrNull { !it.hidden }
}

/**
 * The [LiveWorld], kept current: it changes with the database, moves its guide window each hour,
 * and re-reads the time each minute so progress bars and "now" stay true.
 *
 * With [firstLook], the first world can come before the guide window has loaded, which can take
 * seconds on a TV with a big guide (#127): the channels [firstLook] names from a world without a
 * guide have theirs read on their own, and that world, its [LiveWorld.guideWhole] false, goes
 * first, unless the whole one is in by then.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun liveWorld(
    data: PlasmaData,
    clock: PlasmaClock,
    channels: Flow<List<Channel>> = data.channels.channels,
    local: Flow<LocalCatchUpNow> = flowOf(LocalCatchUpNow()),
    ratingLimit: Flow<Int?> = flowOf(null),
    groups: Flow<List<ChannelGroup>> = data.channels.groups,
    shortGuide: Flow<Map<String, List<Programme>>> = data.shortGuide.programmes,
    twelveHour: Flow<Boolean> = flowOf(false),
    firstLook: (suspend (LiveWorld) -> Collection<Channel>)? = null,
): Flow<LiveWorld> {
    val minutes = flow {
        while (true) {
            val now = clock.now()
            emit(now)
            delay(Duration.between(now, now.truncatedTo(ChronoUnit.MINUTES).plusSeconds(60)).toMillis() + TICK_SLACK_MS)
        }
    }
    val guide = minutes
        .map { it.truncatedTo(ChronoUnit.HOURS) }
        .distinctUntilChanged()
        .flatMapLatest { hour -> data.guide.window(hour.minus(GUIDE_BEHIND), hour.plus(GUIDE_AHEAD)) }
    val lists = combine(
        channels,
        groups,
        data.channels.favoriteIds,
        data.channels.recentIds,
        data.reminders.ids,
    ) { channels, groups, favorites, recents, reminders -> ListsAndMarks(channels, groups, favorites, recents, reminders) }
    val named = combine(lists, data.sources.sources, data.recordings.ids, data.recordings.rules, combine(local, ratingLimit, ::Pair)) { l, sources, recordings, rules, (held, limit) ->
        l.copy(
            sourceNames = sources.associate { it.id to it.name },
            recordings = recordings,
            seriesKeys = rules.mapTo(HashSet()) { seriesKey(it.title, it.channelId) },
            local = held,
            ratingLimit = limit,
        )
    }
    val numberingAndClock = combine(data.settings.numbering, twelveHour.distinctUntilChanged(), ::Pair)
    // Everything but the guide window.
    val bare = combine(named, shortGuide, numberingAndClock, data.channels.lastWatched, minutes) { l, short, (numbering, twelve), last, now ->
        LiveWorld(
            l.channels, l.groups, l.favorites, l.recents, emptyMap(), numbering, last, now, l.reminders, l.sourceNames, l.recordings, l.seriesKeys,
            l.local, l.ratingLimit, short, twelve, guideWhole = false,
        )
    }
    val whole = { bares: Flow<LiveWorld> -> combine(bares, guide) { world, programmes -> made { world.copy(programmes = programmes, guideWhole = true) } } }
    val worlds = if (firstLook == null) {
        whole(bare)
    } else {
        firstLookThenWhole(bare, whole) { world ->
            val hour = world.now.truncatedTo(ChronoUnit.HOURS)
            val keys = firstLook(world).mapNotNull { it.epgKey }
            val programmes = data.guide.windowFor(keys, hour.minus(GUIDE_BEHIND), hour.plus(GUIDE_AHEAD))
            made { world.copy(programmes = programmes) }
        }
    }
    return worlds.flowOn(Dispatchers.Default)
}

/**
 * [whole]'s worlds, made from [bare]'s, after a first look: [look] makes it from [bare]'s first
 * world, and it goes as soon as it's made, unless [whole]'s first is in by then; nothing of it
 * comes after that. [bare] is collected once for both.
 */
internal fun firstLookThenWhole(
    bare: Flow<LiveWorld>,
    whole: (Flow<LiveWorld>) -> Flow<LiveWorld>,
    look: suspend (LiveWorld) -> LiveWorld,
): Flow<LiveWorld> = channelFlow {
    val bares = bare.shareIn(this, SharingStarted.Eagerly, replay = 1)
    val sending = Mutex()
    var wholeIn = false
    val first = launch {
        // A first look that fails leaves the whole world to come as it would have.
        val world = try {
            look(bares.first())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            return@launch
        }
        sending.withLock { if (!wholeIn) send(world) }
    }
    whole(bares).collect { world ->
        sending.withLock {
            wholeIn = true
            first.cancel()
            send(world)
        }
    }
}

/** A live world made, in the trace section the benchmarks time a cold start's first world by (#127). */
private inline fun made(world: () -> LiveWorld): LiveWorld {
    Trace.beginSection(WORLD_SECTION)
    try {
        return world()
    } finally {
        Trace.endSection()
    }
}

private data class ListsAndMarks(
    val channels: List<Channel>,
    val groups: List<ChannelGroup>,
    val favorites: List<String>,
    val recents: List<String>,
    val reminders: Set<String>,
    val sourceNames: Map<String, String> = emptyMap(),
    val recordings: Set<String> = emptySet(),
    val seriesKeys: Set<String> = emptySet(),
    val local: LocalCatchUpNow = LocalCatchUpNow(),
    val ratingLimit: Int? = null,
)

/**
 * Local catch-up now (brief 5.5): the [channels] chosen for it, and what it holds of each
 * ([windows]): from when to when, in Unix ms.
 */
data class LocalCatchUpNow(val channels: Set<String> = emptySet(), val windows: Map<String, LongRange> = emptyMap())

/** A Record Series rule's key: its title, whatever its case, with its channel or "*" for any. */
internal fun seriesKey(title: String, channelId: String?): String = "${title.lowercase()}|${channelId ?: "*"}"

/** The trace section for making a [LiveWorld], which the benchmark module looks for. */
private const val WORLD_SECTION = "Plasma.liveWorld"

/** The guide kept in memory: the last few hours, for programmes still on, and the evening ahead. */
private val GUIDE_BEHIND: Duration = Duration.ofHours(3)
private val GUIDE_AHEAD: Duration = Duration.ofHours(15)
private const val TICK_SLACK_MS = 50L
