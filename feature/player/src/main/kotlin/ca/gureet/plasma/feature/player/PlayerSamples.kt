// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import ca.gureet.plasma.core.design.component.ReminderInfo
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.player.MediaTrack
import ca.gureet.plasma.player.MediaTracks
import ca.gureet.plasma.player.PictureSize
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
        // English in 5.1 and stereo, and Spanish; English and German subtitles (board 10's "English 5.1 · Off").
        tracks = MediaTracks(
            audio = listOf(
                MediaTrack("1", language = "eng", codec = "a52", channels = 6),
                MediaTrack("2", language = "eng", codec = "mp4a", channels = 2),
                MediaTrack("3", language = "spa", codec = "mp4a", channels = 2),
                MediaTrack("4", language = "eng", title = "Audio Description", codec = "mp4a", channels = 2),
            ),
            subtitles = listOf(
                MediaTrack("5", language = "eng", codec = "dvbs"),
                MediaTrack("6", language = "ger", codec = "telx"),
            ),
            audioId = "1",
        ),
        engine = "VLC",
        nextEngine = "mpv",
        format = "1080i · H.264 · AC-3 5.1 passthrough",
        bitrate = "8.2 Mb/s",
        buffer = "2.1 s",
        source = "Home Portal",
        nextSource = "Backup Line",
        pictureSize = PictureSize.Fit,
        pictureAspect = 16f / 9f,
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

    /** A movie or episode from a file: in VLC, with its own picture and sound. */
    private val file = playback.copy(behindLiveMs = 0, format = "3840×2160 · 23.98 fps · HEVC · E-AC-3 5.1 passthrough", bitrate = "", buffer = "", source = "", nextSource = null)

    /** The Last Lighthouse, 1 h 12 min into its 2 h 14 min (brief 4.9's VOD variant). */
    val movie: VodPlayerUiState
        get() = VodPlayerUiState(
            title = Sample.LastLighthouse.movie.title,
            meta = listOf(Sample.LastLighthouse.movie.year.toString()) + Sample.LastLighthouse.genres,
            badges = listOf("4K", "HDR", "5.1"),
            art = Sample.LastLighthouse.BACKDROP,
            positionMs = 72 * MINUTE_MS,
            lengthMs = 134 * MINUTE_MS,
            playback = file,
        )

    /** Atlas Station's Dead Reckoning in its last 30 s, with The Quiet Side up next. */
    val episode: VodPlayerUiState
        get() {
            val atlas = Sample.AtlasStation
            val reckoning = atlas.season2.first { it.number == 4 }
            val quiet = atlas.season2.first { it.number == 5 }
            return VodPlayerUiState(
                title = Sample.series.first { it.id == "atlas" }.title,
                episode = VodEpisode(atlas.CURRENT_SEASON, reckoning.number, reckoning.title),
                badges = listOf("4K", "HDR", "5.1"),
                art = atlas.BACKDROP,
                positionMs = reckoning.minutes * MINUTE_MS - 20_000,
                lengthMs = reckoning.minutes * MINUTE_MS,
                playback = file,
                upNext = UpNext(VodEpisode(atlas.CURRENT_SEASON, quiet.number, quiet.title), quiet.still),
            )
        }

    private const val MINUTE_MS = 60_000L

    private fun tile(channel: Sample.Channel, subtitles: Boolean = false, number: Int = channel.number) = MultiviewTile(
        channelId = channel.id,
        channelName = channel.name,
        logo = channel.logo,
        title = Sample.nowOn(channel)?.title,
        frame = VideoFrame(channel.still, channel.stillAlignment),
        subtitles = subtitles,
        number = number.toString(),
    )

    /**
     * Four channels at 20:41, Arena Sports 1 with the sound, Northline News with subtitles (board
     * 12), and Change Channel's lists.
     */
    val multiview: MultiviewUiState
        get() = with(Sample) {
            MultiviewUiState(
                tiles = listOf(tile(arena1), tile(velocity), tile(northline, subtitles = true), tile(wildframe)),
                // Favorites first, then the groups, then All Channels, as Change Channel lists them.
                lists = (groups.filter { it.id == "favorites" } + groups.filter { !it.smart && it.channels.isNotEmpty() } + groups.filter { it.id == "all" }).map { group ->
                    MultiviewList(
                        id = group.id,
                        name = group.name,
                        channels = group.channels.mapIndexed { i, channel -> tile(channel, number = if (group.countsFromOne) i + 1 else channel.number) },
                        smart = group.smart,
                    )
                },
            )
        }
}
