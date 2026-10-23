// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import ca.gureet.plasma.core.design.component.ReminderInfo
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.player.VideoFrame

/** The player as drawn on boards 09–11, from the sample data (brief appendix A). */
object PlayerSamples {
    private fun channel(channel: Sample.Channel) = PlayerChannel(
        id = channel.id,
        number = channel.number.toString(),
        name = channel.name,
        logo = channel.logo,
        frame = VideoFrame(channel.still, channel.stillAlignment),
        catchUp = channel.catchupDays > 0,
        uhd = channel.uhd,
        hdr = channel.hdr,
        favoriteIndex = Sample.favorites.indexOf(channel).takeIf { it >= 0 }?.plus(1),
        programs = Sample.schedule[channel].orEmpty().map {
            PlayerProgram(
                id = "${channel.id}@${it.start.toEpochSecond()}",
                title = it.title,
                start = it.start.toInstant(),
                end = it.end.toInstant(),
                subtitle = it.subtitle,
                // The stream adds what the guide can't know: 50 FPS after deinterlacing (appendix A).
                badges = if (it === Sample.harbourCity) listOf("HD", "50 FPS", "5.1", "CC") else it.badges,
                recording = it.recording,
                series = if (it === Sample.harbourCity) "City League Match" else null,
            )
        },
    )

    /** Arena Sports 1 in VLC, 2:14 behind live, from Home Portal (appendix A, playback). */
    private val playback = PlaybackInfo(
        behindLiveMs = (2 * 60 + 14) * 1_000L,
        paused = false,
        audio = "English 5.1",
        subtitles = null,
        engine = "VLC",
        nextEngine = "mpv",
        format = "1080i · H.264 · AC-3 5.1 passthrough",
        bitrate = "8.2 Mb/s",
        buffer = "2.1 s",
        source = "Home Portal",
        nextSource = "Backup Line",
        pictureSize = "Fit · 16:9",
        sleepTimer = null,
        health = StreamHealth.Good,
    )

    private val channels = Sample.channels.map(::channel)

    /** Watching Arena Sports 1 at 20:41 (boards 09 and 10). */
    val state: PlayerUiState
        get() = PlayerUiState(
            channels = channels,
            currentChannelId = Sample.lastChannel.id,
            playback = playback,
            favoritesCount = 24,
        )

    /** At 21:59, just zapped to Velocity Motor, with Match of the Week Extra about to start (board 11). */
    val zapState: PlayerUiState
        get() {
            val extra = Sample.schedule.getValue(Sample.arena1).first { it.title == "Match of the Week Extra" }
            return PlayerUiState(
                channels = channels,
                currentChannelId = Sample.velocity.id,
                playback = playback.copy(behindLiveMs = 0),
                favoritesCount = 24,
                reminder = ReminderInfo(
                    channelId = Sample.arena1.id,
                    channelNumber = Sample.arena1.number.toString(),
                    logo = Sample.arena1.logo,
                    channelName = Sample.arena1.name,
                    title = extra.title,
                    start = extra.start.toInstant(),
                    end = extra.end.toInstant(),
                    autoSwitch = true,
                ),
            )
        }

    private fun tile(channel: Sample.Channel, subtitles: Boolean = false) = MultiviewTile(
        channelId = channel.id,
        channelName = channel.name,
        logo = channel.logo,
        title = Sample.nowOn(channel)?.title,
        frame = VideoFrame(channel.still, channel.stillAlignment),
        subtitles = subtitles,
    )

    /** Four channels at 20:41, Arena Sports 1 with the sound, Northline News with subtitles (board 12). */
    val multiview: MultiviewUiState
        get() = with(Sample) {
            MultiviewUiState(
                tiles = listOf(tile(arena1), tile(velocity), tile(northline, subtitles = true), tile(wildframe)),
                more = favorites.map { tile(it) },
            )
        }
}
