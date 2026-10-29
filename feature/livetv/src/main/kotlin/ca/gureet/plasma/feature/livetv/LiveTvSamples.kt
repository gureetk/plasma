// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.player.VideoFrame

/** Live TV as drawn on board 07, from the sample data (brief appendix A). */
object LiveTvSamples {
    private fun channel(channel: Sample.Channel, number: String): LiveChannel {
        val now = Sample.nowOn(channel)
        return LiveChannel(
            id = channel.id,
            number = number,
            name = channel.name,
            logo = channel.logo,
            frame = VideoFrame(channel.still, channel.stillAlignment),
            nowTitle = now?.title,
            nowRange = now?.let { Formats.range(it.start, it.end) },
            progress = now?.progress,
            catchUp = channel.catchupDays > 0,
            favorite = channel.favorite,
            uhd = channel.uhd,
            locked = channel.locked,
            fullNumber = channel.number,
            groupName = channel.group,
            catchUpDays = channel.catchupDays,
        )
    }

    private fun preview(channel: Sample.Channel): ChannelPreview? {
        val now = Sample.nowOn(channel) ?: return null
        val upcoming = Sample.schedule[channel].orEmpty().filter { it.start.toInstant().isAfter(Sample.now) }
        return ChannelPreview(
            now = NowProgram(
                title = now.title,
                range = Formats.range(now.start, now.end),
                left = Formats.left(now.left),
                progress = now.progress,
                description = now.description,
            ),
            upcoming = upcoming.map {
                UpcomingProgram(
                    id = "${channel.id}@${it.start.toEpochSecond()}",
                    time = Formats.time(it.start),
                    title = it.title,
                    reminder = it.reminder,
                    recordingScheduled = it.recordingScheduled,
                )
            },
        )
    }

    private val previews: Map<String, ChannelPreview> by lazy { Sample.channels.mapNotNull { ch -> preview(ch)?.let { ch.id to it } }.toMap() }

    /**
     * Board 07 with a guide that runs on through the night on Arena Sports 1: its schedule is
     * longer than fits under the preview.
     */
    val withLongSchedule: LiveTvUiState
        get() = state.let { s ->
            s.copy(
                preview = { id ->
                    val preview = s.preview(id)
                    if (id != Sample.arena1.id || preview == null) {
                        preview
                    } else {
                        val night = (1..9).map { hour ->
                            UpcomingProgram(id = "${Sample.arena1.id}@night$hour", time = "${(hour + 2).toString().padStart(2, '0')}:00", title = "Overnight Highlights $hour")
                        }
                        preview.copy(upcoming = preview.upcoming + night)
                    }
                },
            )
        }

    val state: LiveTvUiState
        get() = LiveTvUiState(
            profileName = Sample.maya.name,
            profileInitial = Sample.maya.initial,
            groups = Sample.groups.map { LiveGroup(it.id, it.name, it.count, it.smart, it.countsFromOne) },
            channels = Sample.groups.associate { group ->
                group.id to group.channels.mapIndexed { i, ch ->
                    channel(ch, if (group.countsFromOne) (i + 1).toString() else ch.number.toString())
                }
            },
            preview = previews::get,
            selectedGroupId = Sample.groups.first { Sample.lastChannel in it.channels && !it.smart }.id,
            currentChannelId = Sample.lastChannel.id,
        )
}
