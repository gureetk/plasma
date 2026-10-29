// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

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

    /** Whether a Record Series rule records [title] on [channelId], or on any channel. */
    fun hasSeriesRule(title: String, channelId: String): Boolean =
        seriesKey(title, channelId) in seriesKeys || seriesKey(title, null) in seriesKeys

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

    /** [channel]'s programmes around now, from the guide, or else from its server's short guide (5.3). */
    fun programmesOf(channel: Channel): List<Programme> =
        channel.epgKey?.let { programmes[it] }?.takeIf { it.isNotEmpty() } ?: shortGuide[channel.id].orEmpty()

    fun nowNext(channel: Channel): NowNext = GuideRepository.nowNext(programmesOf(channel), now)

    /** Each group's channels in number order, made once: a source can have thousands of channels in hundreds of groups. */
    private val byGroup: Map<String?, List<Channel>> by lazy { channels.groupBy { it.groupId } }

    /** A list's channels in its own order, hidden ones included. */
    fun list(listId: String): List<Channel> = when (listId) {
        Lists.ALL -> channels
        Lists.FAVORITES -> favoriteChannelIds.mapNotNull(byId::get)
        Lists.RECENT -> recentIds.mapNotNull(byId::get).distinctBy { it.id }
        else -> byGroup[listId].orEmpty()
    }

    /** Favorites count from 1 by default; groups keep full-list numbers (5.2, Settings › Channels). */
    fun countsFromOne(listId: String): Boolean = when (listId) {
        Lists.FAVORITES -> numbering.favoritesCountFromOne
        Lists.ALL, Lists.RECENT -> false
        else -> numbering.groupsCountFromOne
    }

    /** The channel to start on: the last one watched, or the first in the list. */
    val currentChannel: Channel?
        get() = lastWatched?.channelId?.let(byId::get) ?: channels.firstOrNull { !it.hidden }
}

/**
 * The [LiveWorld], kept current: it changes with the database, moves its guide window each hour,
 * and re-reads the time each minute so progress bars and "now" stay true.
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
    return combine(named, combine(guide, shortGuide, ::Pair), data.settings.numbering, data.channels.lastWatched, minutes) { l, (programmes, short), numbering, last, now ->
        LiveWorld(
            l.channels, l.groups, l.favorites, l.recents, programmes, numbering, last, now, l.reminders, l.sourceNames, l.recordings, l.seriesKeys,
            l.local, l.ratingLimit, short,
        )
    }.flowOn(Dispatchers.Default)
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

/** The guide kept in memory: the last few hours, for programmes still on, and the evening ahead. */
private val GUIDE_BEHIND: Duration = Duration.ofHours(3)
private val GUIDE_AHEAD: Duration = Duration.ofHours(15)
private const val TICK_SLACK_MS = 50L
