// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.ui.Alignment
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.core.data.Reminder
import ca.gureet.plasma.core.data.ReminderRepository
import ca.gureet.plasma.core.data.RememberedSource
import ca.gureet.plasma.core.data.rememberedBy
import ca.gureet.plasma.core.design.component.Art
import ca.gureet.plasma.core.design.component.ReminderInfo
import ca.gureet.plasma.core.design.component.SourceChoice
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.feature.catchup.CatchUpChannel
import ca.gureet.plasma.feature.catchup.CatchUpProgram
import ca.gureet.plasma.feature.catchup.CatchUpUiState
import ca.gureet.plasma.feature.guide.GuideChannel
import ca.gureet.plasma.feature.guide.GuideGroup
import ca.gureet.plasma.feature.guide.GuideProgram
import ca.gureet.plasma.feature.guide.GuideStale
import ca.gureet.plasma.feature.guide.GuideUiState
import ca.gureet.plasma.feature.home.ChannelThumb
import ca.gureet.plasma.feature.home.HeroBackdrop
import ca.gureet.plasma.feature.home.HeroItem
import ca.gureet.plasma.feature.home.HeroKind
import ca.gureet.plasma.feature.home.HomeUiState
import ca.gureet.plasma.feature.livetv.ChannelPreview
import ca.gureet.plasma.feature.livetv.LiveChannel
import ca.gureet.plasma.feature.livetv.LiveSource
import ca.gureet.plasma.feature.livetv.LiveGroup
import ca.gureet.plasma.feature.livetv.LiveRemembered
import ca.gureet.plasma.feature.livetv.LiveTvUiState
import ca.gureet.plasma.feature.livetv.UpcomingProgram
import ca.gureet.plasma.feature.player.PlaybackInfo
import ca.gureet.plasma.feature.player.PlayerChannel
import ca.gureet.plasma.feature.player.PlayerProgram
import ca.gureet.plasma.feature.player.PlayerStatus
import ca.gureet.plasma.feature.player.PlayerUiState
import ca.gureet.plasma.player.VideoFrame
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Names the screens need that the data doesn't carry. */
data class LiveNames(
    val profileName: String,
    val allChannels: String,
    val favorites: String,
    val recentlyWatched: String,
)

/**
 * The live screens' states from a [LiveWorld]: Live TV, the player, the Guide and Home's live
 * picture and favorites. Numbers follow each list's numbering (brief 5.2). A locked channel shows
 * no live picture, in previews or on Home, until [isUnlocked] says the PIN opened it (4.25).
 */
class LiveStates(private val names: LiveNames, private val zone: ZoneId, private val isUnlocked: (String) -> Boolean = { false }) {
    private val profileInitial = names.profileName.take(1).uppercase()

    // ---- Live TV (4.7) -----------------------------------------------------------------------

    /**
     * Live TV from [world]. [numberOwners] are every channel's numbers, those merged into another
     * too, for Change Number (5.2); without them, the numbers of the channels [world] shows.
     */
    fun liveTv(world: LiveWorld, selectedGroupId: String?, numberOwners: Map<Int, String>? = null): LiveTvUiState {
        val favorites = world.favoriteChannelIds.toSet()
        val groupIds = buildList {
            add(Lists.ALL to names.allChannels)
            add(Lists.FAVORITES to names.favorites)
            add(Lists.RECENT to names.recentlyWatched)
            world.groups.filter { !it.hidden }.forEach { add(it.id to it.name) }
        }
        val lists = groupIds.associate { (id, _) -> id to world.list(id) }
        val groups = groupIds.mapNotNull { (id, name) ->
            val smart = id == Lists.ALL || id == Lists.FAVORITES || id == Lists.RECENT
            val count = lists.getValue(id).count { !it.hidden }
            // An empty provider group has nothing to show; the smart ones always stay.
            if (!smart && count == 0) return@mapNotNull null
            LiveGroup(id, name, count, smart, world.countsFromOne(id))
        }
        val channels = groups.associate { group ->
            group.id to lists.getValue(group.id).mapIndexed { index, channel ->
                liveChannel(world, channel, number = if (group.countsFromOne) index + 1 else channel.number, favorite = channel.id in favorites)
            }
        }
        val current = world.currentChannel
        val listed = groups.map { it.id }
        val selected = selectedGroupId?.takeIf { it in listed }
            ?: world.lastWatched?.listId?.takeIf { it in listed && current != null && world.list(it).any { ch -> ch.id == current.id } }
            ?: current?.groupId?.takeIf { it in listed }
            ?: Lists.ALL
        return LiveTvUiState(
            profileName = names.profileName,
            profileInitial = profileInitial,
            groups = groups,
            channels = channels,
            previews = world.channels.mapNotNull { channel -> preview(world, channel)?.let { channel.id to it } }.toMap(),
            selectedGroupId = selected,
            currentChannelId = current?.id.orEmpty(),
            hidden = world.channels.filter { it.hidden }.mapTo(HashSet()) { it.id },
            // Hidden channels keep their numbers: Change Number can't take them (5.2).
            numberOwners = numberOwners ?: world.channels.associate { it.number to it.name },
        )
    }

    /**
     * The source each merged channel starts on, from what Remember What Works per Channel keeps
     * ([all], by the candidate id each is kept under): Live TV's Switch Source shows it (4.35).
     */
    fun remembered(world: LiveWorld, all: Map<String, RememberedSource>): Map<String, LiveRemembered> =
        all.keys.mapNotNull { id -> world.byId[id] }.distinctBy { it.id }.mapNotNull { channel ->
            all.rememberedBy(channel.candidates)?.let { channel.id to LiveRemembered(it.candidateId, it.learnedAt) }
        }.toMap()

    private fun liveChannel(world: LiveWorld, channel: Channel, number: Int, favorite: Boolean): LiveChannel {
        val now = world.nowNext(channel).now
        return LiveChannel(
            id = channel.id,
            number = number.toString(),
            name = channel.name,
            logo = channel.logo,
            frame = frameOf(channel),
            nowTitle = now?.title,
            nowRange = now?.let { range(it) },
            progress = now?.let { progress(it, world.now) },
            catchUp = (channel.catchupDays ?: 0) > 0,
            favorite = favorite,
            uhd = isUhd(channel),
            locked = channel.locked,
            fullNumber = channel.number,
            sources = channel.candidates.map { candidate ->
                LiveSource(candidate, world.sourceNames[candidate.substringBefore(':')] ?: candidate.substringBefore(':'))
            },
            groupName = channel.groupName,
            guideId = channel.tvgId,
            catchUpDays = channel.catchupDays ?: 0,
        )
    }

    private fun preview(world: LiveWorld, channel: Channel): ChannelPreview? {
        val programmes = world.programmesOf(channel)
        val now = world.nowNext(channel).now ?: return null
        return ChannelPreview(
            title = now.title,
            range = range(now),
            left = Formats.left(Duration.between(world.now, now.end).coerceAtLeast(Duration.ZERO)),
            progress = progress(now, world.now),
            description = now.description,
            upcoming = programmes.filter { it.start.isAfter(world.now) }.take(UPCOMING).map {
                val id = programmeId(channel, it)
                UpcomingProgram(
                    id = id, time = Formats.time(it.start.atZone(zone)), title = it.title, reminder = id in world.reminderIds,
                    recordingScheduled = id in world.recordingIds, seriesRule = world.hasSeriesRule(it.title, channel.id),
                )
            },
        )
    }

    // ---- The player (4.9 and 4.11) -------------------------------------------------------------

    /** The player on [channelId], zapping through [listId] (5.2: zapping follows the list). */
    fun player(
        world: LiveWorld,
        listId: String,
        channelId: String,
        status: PlayerStatus,
        sourceName: String?,
        nextSource: String? = null,
        sources: List<SourceChoice> = emptyList(),
        /** The engine playing, and the one Switch Player goes to ("" for none). */
        engine: String = ENGINE,
        nextEngine: String = NEXT_ENGINE,
    ): PlayerUiState? {
        // Zapping skips hidden channels; a channel that isn't in the list zaps through All Channels.
        val list = world.list(listId).filter { !it.hidden || it.id == channelId }
        val inList = list.any { it.id == channelId }
        val zapList = if (inList) list else world.channels.filter { !it.hidden || it.id == channelId }
        if (zapList.none { it.id == channelId }) return null
        val countsFromOne = inList && world.countsFromOne(listId)
        val favorites = world.favoriteChannelIds
        return PlayerUiState(
            channels = zapList.mapIndexed { index, channel ->
                PlayerChannel(
                    id = channel.id,
                    number = (if (countsFromOne) index + 1 else channel.number).toString(),
                    name = channel.name,
                    logo = channel.logo,
                    frame = frameOf(channel),
                    catchUp = (channel.catchupDays ?: 0) > 0,
                    uhd = isUhd(channel),
                    hdr = false,
                    favoriteIndex = favorites.indexOf(channel.id).takeIf { it >= 0 }?.plus(1),
                    programs = world.programmesOf(channel).map {
                        val id = programmeId(channel, it)
                        PlayerProgram(
                            id, it.title, it.start, it.end, it.subtitle,
                            recording = id in world.recordingIds,
                            // Record Every…: an episode of something, by its title (brief 5.6).
                            series = it.title.takeIf { _ -> it.season != null || it.episode != null || it.subtitle != null },
                            seriesRule = world.hasSeriesRule(it.title, channel.id),
                        )
                    },
                )
            },
            currentChannelId = channelId,
            playback = PlaybackInfo(
                behindLiveMs = 0,
                paused = false,
                engine = engine,
                nextEngine = nextEngine,
                format = "",
                bitrate = "",
                buffer = "",
                source = sourceName.orEmpty(),
                nextSource = nextSource,
                sources = sources,
                health = streamHealth(status, afterFallback = false),
            ),
            favoritesCount = favorites.size,
            status = status,
        )
    }

    /**
     * The player's channels while [channelId] plays [key] from its archive: its picture is the
     * archive's, and its programs include [archived], which may be older than the guide around now.
     */
    fun withArchive(channels: List<PlayerChannel>, channelId: String, key: String, archived: Programme?): List<PlayerChannel> = channels.map { channel ->
        if (channel.id != channelId) return@map channel
        val program = archived?.let { PlayerProgram(ReminderRepository.idOf(channelId, it.start), it.title, it.start, it.end, it.subtitle) }
        channel.copy(
            frame = channel.frame.copy(channelId = channel.frame.channelId?.let { key }),
            programs = if (program == null || channel.programs.any { it.id == program.id }) channel.programs else (channel.programs + program).sortedBy { it.start },
        )
    }

    // ---- Catch-up (4.18) -------------------------------------------------------------------------

    /**
     * Catch-up on the channels whose provider keeps an archive, in number order, opening on
     * [channelId] or the last channel watched; null when none does. [programmes] are the past
     * programs of the channels loaded so far.
     */
    fun catchUp(world: LiveWorld, programmes: Map<String, List<Programme>>, channelId: String?): CatchUpUiState? {
        val channels = world.channels.filter { !it.hidden && !it.locked && (it.catchupDays ?: 0) > 0 }
        if (channels.isEmpty()) return null
        val ids = channels.mapTo(HashSet()) { it.id }
        return CatchUpUiState(
            channels = channels.map { CatchUpChannel(it.id, it.number, it.name, it.logo, archiveDays = it.catchupDays) },
            channelCount = channels.size,
            selectedChannelId = channelId?.takeIf { it in ids } ?: world.lastWatched?.channelId?.takeIf { it in ids } ?: channels.first().id,
            programs = programmes.mapValues { (channelId, list) ->
                // One per start: the screen keys its rows by the programme's id.
                list.distinctBy { it.start }.map {
                    CatchUpProgram(
                        id = ReminderRepository.idOf(channelId, it.start),
                        title = it.title,
                        subtitle = it.subtitle,
                        start = it.start.atZone(zone),
                        end = it.end.atZone(zone),
                        art = it.icon?.let(::Art),
                    )
                }
            },
        )
    }

    // ---- Reminders (4.11 and 5.7) --------------------------------------------------------------

    /**
     * The toast for [due]. In the player the channel has the number it has in the list being
     * zapped through ([numbers]), as on the zap banner.
     */
    fun reminder(world: LiveWorld, due: Reminder, numbers: List<PlayerChannel> = emptyList()): ReminderInfo? = reminderInfo(world, due, numbers)

    // ---- The Guide (4.8) -----------------------------------------------------------------------

    /**
     * The Guide on [listId]'s channels, with [programmes], the day showing; [loading] until they're
     * in. Its groups are All Channels, Favorites and the provider's groups that have channels.
     */
    fun guide(
        world: LiveWorld,
        programmes: Map<String, List<Programme>>,
        loading: Boolean,
        stale: GuideStale?,
        listId: String,
    ): GuideUiState {
        val groups = buildList {
            add(GuideGroup(Lists.ALL, names.allChannels))
            if (world.favoriteChannelIds.isNotEmpty()) add(GuideGroup(Lists.FAVORITES, names.favorites))
            world.groups.filter { group -> !group.hidden && world.list(group.id).any { !it.hidden } }.forEach { add(GuideGroup(it.id, it.name)) }
        }
        val showing = groups.firstOrNull { it.id == listId } ?: groups.first()
        val countsFromOne = world.countsFromOne(showing.id)
        val channels = world.list(showing.id).filter { !it.hidden }
        return GuideUiState(
            profileName = names.profileName,
            profileInitial = profileInitial,
            groupName = showing.name,
            channels = channels.mapIndexed { index, channel ->
                GuideChannel(
                    id = channel.id,
                    number = (if (countsFromOne) index + 1 else channel.number).toString(),
                    name = channel.name,
                    logo = channel.logo,
                    frame = frameOf(channel),
                    catchUp = (channel.catchupDays ?: 0) > 0,
                    archiveFrom = channel.catchupDays?.takeIf { it > 0 }?.let { world.now.minus(Duration.ofDays(it.toLong())) },
                    programs = channel.epgKey?.let { programmes[it] }.orEmpty().map {
                        val id = programmeId(channel, it)
                        GuideProgram(
                            id = id, title = it.title, start = it.start, end = it.end, subtitle = it.subtitle, description = it.description,
                            reminder = id in world.reminderIds, recordingScheduled = id in world.recordingIds,
                            seriesRule = world.hasSeriesRule(it.title, channel.id),
                        )
                    },
                )
            },
            currentChannelId = world.currentChannel?.id?.takeIf { id -> channels.any { it.id == id } } ?: channels.firstOrNull()?.id.orEmpty(),
            loading = loading,
            stale = stale,
            groups = groups,
            groupId = showing.id,
        )
    }

    // ---- Home (4.1) ----------------------------------------------------------------------------

    /**
     * Home with the last channel live behind it, muted, and the real favorites. Continue
     * Watching, movies and apps keep [samples] until VOD (M4) and the launcher (M5).
     */
    fun home(world: LiveWorld, samples: HomeUiState): HomeUiState {
        val hero = world.currentChannel?.let { channel ->
            val (now, next) = world.nowNext(channel)
            HeroItem(
                id = "live:${channel.id}",
                kind = HeroKind.Live,
                title = now?.title ?: channel.name,
                backdrop = HeroBackdrop.Video(frameOf(channel, muted = true)),
                logo = channel.logo,
                logoName = channel.name,
                channelNumber = channel.number.toString(),
                subtitle = now?.subtitle,
                progress = now?.let { progress(it, world.now) },
                endsAt = now?.let { Formats.time(it.end.atZone(zone)) },
                next = next?.title,
            )
        }
        val favorites = world.favoriteChannelIds.mapNotNull(world.byId::get).filter { !it.hidden }.map { channel ->
            val now = world.nowNext(channel).now
            ChannelThumb(
                channelId = channel.id,
                name = channel.name,
                logo = channel.logo,
                still = null,
                stillAlignment = Alignment.Center,
                progress = now?.let { progress(it, world.now) } ?: 0f,
                description = listOfNotNull(channel.name, now?.title).joinToString(", "),
            )
        }
        return samples.copy(
            profileName = names.profileName,
            profileInitial = profileInitial,
            hero = listOfNotNull(hero),
            favorites = favorites,
        )
    }

    // ---- Shared ----------------------------------------------------------------------------------

    /** The live picture of [channel]: no still, so the video layer plays it; a locked one plays nothing. */
    private fun frameOf(channel: Channel, muted: Boolean = false) =
        VideoFrame(still = null, channelId = channel.id.takeIf { !channel.locked || isUnlocked(it) }, muted = muted)

    private fun range(programme: Programme) = Formats.range(programme.start.atZone(zone), programme.end.atZone(zone))

    private fun progress(programme: Programme, now: Instant): Float {
        val length = Duration.between(programme.start, programme.end).toMillis().coerceAtLeast(1)
        return (Duration.between(programme.start, now).toMillis().toFloat() / length).coerceIn(0f, 1f)
    }

    /** "channelId@start in epoch seconds": the id a reminder is kept under too. */
    private fun programmeId(channel: Channel, programme: Programme) = ReminderRepository.idOf(channel.id, programme.start)

    private fun isUhd(channel: Channel): Boolean = UHD.containsMatchIn(channel.name)

    private companion object {
        const val UPCOMING = 3
        const val ENGINE = "VLC"
        const val NEXT_ENGINE = "mpv"
        val UHD = Regex("""(?i)\b(4K|UHD)\b""")
    }
}

/**
 * The toast for [due] (brief 4.11 and 5.7), in Plasma or over another app: its channel by the number
 * it has in [numbers], the list being zapped through, or else its own.
 */
internal fun reminderInfo(world: LiveWorld, due: Reminder, numbers: List<PlayerChannel> = emptyList()): ReminderInfo? {
    val channel = world.byId[due.channelId] ?: return null
    return ReminderInfo(
        channelId = channel.id,
        channelNumber = numbers.firstOrNull { it.id == channel.id }?.number ?: channel.number.toString(),
        logo = channel.logo,
        channelName = channel.name,
        title = due.title,
        start = due.start,
        end = due.end,
    )
}
