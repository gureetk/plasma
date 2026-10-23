// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.ui.Alignment
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.feature.guide.GuideChannel
import ca.gureet.plasma.feature.guide.GuideProgram
import ca.gureet.plasma.feature.guide.GuideUiState
import ca.gureet.plasma.feature.home.ChannelThumb
import ca.gureet.plasma.feature.home.HeroBackdrop
import ca.gureet.plasma.feature.home.HeroItem
import ca.gureet.plasma.feature.home.HeroKind
import ca.gureet.plasma.feature.home.HomeUiState
import ca.gureet.plasma.feature.livetv.ChannelPreview
import ca.gureet.plasma.feature.livetv.LiveChannel
import ca.gureet.plasma.feature.livetv.LiveGroup
import ca.gureet.plasma.feature.livetv.LiveTvUiState
import ca.gureet.plasma.feature.livetv.UpcomingProgram
import ca.gureet.plasma.feature.player.PlaybackInfo
import ca.gureet.plasma.feature.player.PlayerChannel
import ca.gureet.plasma.feature.player.PlayerProgram
import ca.gureet.plasma.feature.player.PlayerStatus
import ca.gureet.plasma.feature.player.PlayerUiState
import ca.gureet.plasma.feature.player.StreamHealth
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
 * picture and favorites. Numbers follow each list's numbering (brief 5.2).
 */
class LiveStates(private val names: LiveNames, private val zone: ZoneId) {
    private val profileInitial = names.profileName.take(1).uppercase()

    // ---- Live TV (4.7) -----------------------------------------------------------------------

    fun liveTv(world: LiveWorld, selectedGroupId: String?): LiveTvUiState {
        val favorites = world.favoriteIds.toSet()
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
        )
    }

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
                UpcomingProgram(id = programmeId(channel, it), time = Formats.time(it.start.atZone(zone)), title = it.title)
            },
        )
    }

    // ---- The player (4.9 and 4.11) -------------------------------------------------------------

    /** The player on [channelId], zapping through [listId] (5.2: zapping follows the list). */
    fun player(world: LiveWorld, listId: String, channelId: String, status: PlayerStatus, sourceName: String?): PlayerUiState? {
        // Zapping skips hidden channels; a channel that isn't in the list zaps through All Channels.
        val list = world.list(listId).filter { !it.hidden || it.id == channelId }
        val inList = list.any { it.id == channelId }
        val zapList = if (inList) list else world.channels.filter { !it.hidden || it.id == channelId }
        if (zapList.none { it.id == channelId }) return null
        val countsFromOne = inList && world.countsFromOne(listId)
        val favorites = world.favoriteIds
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
                    programs = world.programmesOf(channel).map { PlayerProgram(programmeId(channel, it), it.title, it.start, it.end, it.subtitle) },
                )
            },
            currentChannelId = channelId,
            playback = PlaybackInfo(
                behindLiveMs = 0,
                paused = false,
                audio = "",
                subtitles = null,
                engine = ENGINE,
                nextEngine = NEXT_ENGINE,
                format = "",
                bitrate = "",
                buffer = "",
                source = sourceName.orEmpty(),
                nextSource = null,
                pictureSize = PICTURE_SIZE,
                sleepTimer = null,
                health = when (status) {
                    PlayerStatus.Playing -> StreamHealth.Good
                    PlayerStatus.Opening, PlayerStatus.Reconnecting -> StreamHealth.Warning
                    is PlayerStatus.Failed -> StreamHealth.Error
                },
            ),
            favoritesCount = favorites.size,
            status = status,
        )
    }

    // ---- The Guide (4.8) -----------------------------------------------------------------------

    fun guide(world: LiveWorld): GuideUiState {
        val channels = world.channels.filter { !it.hidden }
        return GuideUiState(
            profileName = names.profileName,
            profileInitial = profileInitial,
            groupName = names.allChannels,
            channels = channels.map { channel ->
                GuideChannel(
                    id = channel.id,
                    number = channel.number.toString(),
                    name = channel.name,
                    logo = channel.logo,
                    frame = frameOf(channel),
                    catchUp = (channel.catchupDays ?: 0) > 0,
                    programs = world.programmesOf(channel).map {
                        GuideProgram(id = programmeId(channel, it), title = it.title, start = it.start, end = it.end, subtitle = it.subtitle, description = it.description)
                    },
                )
            },
            currentChannelId = world.currentChannel?.id ?: channels.firstOrNull()?.id.orEmpty(),
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
        val favorites = world.favoriteIds.mapNotNull(world.byId::get).filter { !it.hidden }.map { channel ->
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

    /** The live picture of [channel]: no still, so the video layer plays it (M1). */
    private fun frameOf(channel: Channel, muted: Boolean = false) = VideoFrame(still = null, channelId = channel.id, muted = muted)

    private fun range(programme: Programme) = Formats.range(programme.start.atZone(zone), programme.end.atZone(zone))

    private fun progress(programme: Programme, now: Instant): Float {
        val length = Duration.between(programme.start, programme.end).toMillis().coerceAtLeast(1)
        return (Duration.between(programme.start, now).toMillis().toFloat() / length).coerceIn(0f, 1f)
    }

    private fun programmeId(channel: Channel, programme: Programme) = "${channel.id}@${programme.start.epochSecond}"

    private fun isUhd(channel: Channel): Boolean = UHD.containsMatchIn(channel.name)

    private companion object {
        const val UPCOMING = 3
        const val ENGINE = "VLC"
        const val NEXT_ENGINE = "mpv"
        const val PICTURE_SIZE = "Fit"
        val UHD = Regex("""(?i)\b(4K|UHD)\b""")
    }
}
