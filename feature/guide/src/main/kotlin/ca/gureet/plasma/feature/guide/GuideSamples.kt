// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.player.VideoFrame

/** The Guide as drawn on board 08, from the sample data (brief appendix A). */
object GuideSamples {
    private fun channel(channel: Sample.Channel) = GuideChannel(
        id = channel.id,
        number = channel.number.toString(),
        name = channel.name,
        logo = channel.logo,
        frame = VideoFrame(channel.still, channel.stillAlignment),
        catchUp = channel.catchupDays > 0,
        programs = Sample.schedule[channel].orEmpty().map {
            GuideProgram(
                id = "${channel.id}@${it.start.toEpochSecond()}",
                title = it.title,
                start = it.start.toInstant(),
                end = it.end.toInstant(),
                subtitle = it.subtitle,
                description = it.description,
                badges = it.badges,
                recording = it.recording,
                recordingScheduled = it.recordingScheduled,
                reminder = it.reminder,
            )
        },
    )

    /** The rows board 08 shows, then the rest of the sample channels. */
    private val lineup = with(Sample) {
        listOf(northline, meridian, arena1, velocity, atlas, cinema, wildframe, pulse, arenaNews, arenaExtra, arena2, courtside, fairway, combat)
    }

    val state: GuideUiState
        get() = GuideUiState(
            profileName = Sample.maya.name,
            profileInitial = Sample.maya.initial,
            groupName = "All Channels",
            channels = lineup.map(::channel),
            currentChannelId = Sample.lastChannel.id,
        )
}
