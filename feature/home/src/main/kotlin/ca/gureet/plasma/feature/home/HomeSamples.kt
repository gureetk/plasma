// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.TransformOrigin
import ca.gureet.plasma.core.design.component.focal
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.model.HomeRow
import ca.gureet.plasma.core.model.UpcomingCandidate
import ca.gureet.plasma.core.model.UpcomingEvents
import ca.gureet.plasma.core.model.UpcomingReason
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.fixtures.fx
import ca.gureet.plasma.player.VideoFrame
import java.time.Duration
import java.time.ZonedDateTime

/** Home as drawn on boards 01 and 02, from the sample data (brief appendix A). */
object HomeSamples {
    private fun thumb(channel: Sample.Channel): ChannelThumb {
        val now = Sample.nowOn(channel)
        return ChannelThumb(
            channelId = channel.id,
            name = channel.name,
            logo = channel.logo,
            still = channel.still,
            stillAlignment = channel.stillAlignment,
            progress = now?.progress ?: 0f,
            description = listOfNotNull(channel.name, now?.title).joinToString(", "),
        )
    }

    /** The board's order: the user's favorites, without Arena Sports 1, which is in the hero. */
    private val favoriteRow = with(Sample) { listOf(wildframe, northline, velocity, pulse, cinema, meridian, atlas, courtside) }

    val state: HomeUiState
        get() {
            val live = Sample.harbourCity
            val reminder = Sample.schedule.values.flatten()
                .filter { it.reminder && it.start.toInstant().isAfter(Sample.now) }
                .minBy { it.start }
            val neon = Sample.movies.first { it.id == "neon" }
            return HomeUiState(
                profileName = Sample.maya.name,
                profileInitial = Sample.maya.initial,
                hero = listOf(
                    HeroItem(
                        id = "live:${live.channel.id}",
                        kind = HeroKind.Live,
                        title = live.title,
                        backdrop = HeroBackdrop.Video(VideoFrame(live.channel.still, live.channel.stillAlignment)),
                        logo = live.channel.logo,
                        logoName = live.channel.name,
                        channelNumber = live.channel.number.toString(),
                        score = "2–1",
                        minute = "64′",
                        subtitle = live.subtitle,
                        progress = live.progress,
                        endsAt = Formats.time(live.end),
                        next = Sample.nextOn(live.channel)?.title,
                    ),
                    HeroItem(
                        id = "continue:atlas",
                        kind = HeroKind.ContinueWatching,
                        title = "Atlas Station",
                        backdrop = HeroBackdrop.Art(fx("orbit2"), focal(40f, 60f)),
                        subtitle = "Season 2, Episode 4",
                        progress = 0.7f,
                        note = "18 min left",
                    ),
                    HeroItem(
                        id = "movie:${neon.id}",
                        kind = HeroKind.NewMovie,
                        title = neon.title,
                        backdrop = HeroBackdrop.Art(neon.poster, blurred = true),
                        subtitle = "${neon.year} · ${neon.genre} · ${Formats.duration(java.time.Duration.ofMinutes(neon.minutes.toLong()))}",
                    ),
                    HeroItem(
                        id = "reminder:${reminder.channel.id}",
                        kind = HeroKind.Reminder,
                        title = reminder.title,
                        backdrop = HeroBackdrop.Art(reminder.channel.still, reminder.channel.stillAlignment),
                        logo = reminder.channel.logo,
                        logoName = reminder.channel.name,
                        subtitle = "${reminder.channel.name} · ${Formats.range(reminder.start, reminder.end)}",
                        note = "Starts in ${Formats.duration(java.time.Duration.between(Sample.now, reminder.start.toInstant()))}",
                    ),
                ),
                favorites = favoriteRow.map(::thumb),
                continueWatching = listOf(
                    ContinueItem(
                        id = "series:atlas:s2e4",
                        title = "Atlas Station",
                        meta = "S2, E4 · 18 min left",
                        image = fx("orbit2"),
                        kind = ContinueKind.Art,
                        progress = 0.70f,
                        imageZoom = 1.6f,
                        zoomPivot = TransformOrigin(0.375f, 1f),
                    ),
                    ContinueItem(
                        id = "jellyfin:kite",
                        title = "Kite Season",
                        meta = "Jellyfin · 52 min left",
                        image = fx("poster_kite"),
                        kind = ContinueKind.Poster,
                        progress = 0.38f,
                        sourceTag = "JELLYFIN",
                        offersHideAll = false,
                    ),
                    ContinueItem(
                        id = "movie:lighthouse",
                        title = "The Last Lighthouse",
                        meta = "1 h 02 min left",
                        image = fx("lighthouse3"),
                        kind = ContinueKind.Art,
                        progress = 0.54f,
                        imageAlignment = focal(76f, 35f),
                    ),
                    ContinueItem(
                        id = "catchup:ch180:kingdoms-of-ice-2",
                        title = "Kingdoms of Ice, Episode 2",
                        meta = "Catch-up · Yesterday, 20:00",
                        image = fx("aurora2"),
                        kind = ContinueKind.CatchUp,
                        progress = 0.30f,
                        imageAlignment = focal(60f, 40f),
                        logo = Sample.wildframe.logo,
                    ),
                    ContinueItem(
                        id = "catchup:ch200:late-night-sessions",
                        title = "Late Night Sessions",
                        meta = "Catch-up · Monday, 22:00",
                        image = fx("concert2"),
                        kind = ContinueKind.CatchUp,
                        progress = 0.80f,
                        logo = Sample.pulse.logo,
                    ),
                ),
                newMovies = Sample.movies.take(10).map { PosterItem(it.id, it.title, it.poster) },
                apps = Sample.apps.filter { it.onHome }.map { AppItem(it.packageName, it.label, it.banner) },
                newSeries = newSeries,
                upcoming = upcoming,
                recorded = recorded,
            )
        }

    /** New in Series: the series with new episodes and seasons first (board 15's badges). */
    private val newSeries: List<PosterItem>
        get() = listOf("kingsway", "heatindex", "panel", "atlas", "northof", "harbour", "lastorders", "nightstint", "smallclaims", "backstage", "quiethours", "tumble")
            .map { id -> Sample.series.first { it.id == id } }
            .map { PosterItem(it.id, it.title, it.poster) }

    /** The programs the sample guide lists as sport tonight: its programs have no categories. */
    private val sport = setOf("Harbour City v Redmoor", "Grand Slam Tennis: Quarter-final", "City Lights v Northport", "Endurance 24: Night Stint, Part 2")

    /**
     * Upcoming Events at 20:41 (the owner's #80), picked as the app picks them: the reminders and
     * recordings set in the sample guide, what starts within the hour on Maya's favorites, and
     * tonight's sport.
     */
    private val upcoming: List<UpcomingItem>
        get() {
            val favorites = Sample.favorites.mapTo(HashSet()) { it.id }
            val candidates = Sample.schedule.values.flatten().flatMap { program ->
                fun because(reason: UpcomingReason) = UpcomingCandidate(
                    "${program.channel.id}@${program.start.toEpochSecond()}", program.channel.id, program.title,
                    program.start.toInstant(), program.end.toInstant(), reason, program.channel.number,
                )
                listOfNotNull(
                    because(UpcomingReason.Reminder).takeIf { program.reminder },
                    because(UpcomingReason.Recording).takeIf { program.recording || program.recordingScheduled },
                    because(UpcomingReason.Favorite).takeIf { program.channel.id in favorites },
                    because(UpcomingReason.Sports).takeIf { program.title in sport },
                )
            }
            val channels = Sample.channels.associateBy { it.id }
            return UpcomingEvents.pick(candidates, Sample.now).map { event ->
                val channel = channels.getValue(event.channelId)
                UpcomingItem(
                    id = event.id,
                    channelId = channel.id,
                    channelName = channel.name,
                    logo = channel.logo,
                    title = event.title,
                    start = event.start.atZone(Sample.zone),
                    end = event.end.atZone(Sample.zone),
                    reminder = event.reminder,
                    recording = event.recording,
                    fromGuide = UpcomingReason.Favorite in event.reasons || UpcomingReason.Sports in event.reasons,
                )
            }
        }

    private fun recorded(
        id: String,
        title: String,
        channel: Sample.Channel,
        image: String,
        alignment: Alignment,
        aired: ZonedDateTime,
        minutes: Long,
        unwatched: Boolean = false,
        progress: Float? = null,
        news: Boolean = false,
    ) = RecordedItem(id, title, channel.name, channel.logo, image, aired, Duration.ofMinutes(minutes), alignment, progress, unwatched, news)

    /** Recently Recorded: board 17's recordings that are done, the newest first. */
    private val recorded: List<RecordedItem>
        get() = listOf(
            recorded("business", "Business Wrap", Sample.northline, fx("city2"), focal(50f, 60f), Sample.at(20, 30, -1), 30, unwatched = true, news = true),
            recorded("football", "Tuesday Night Football", Sample.arena1, fx("stadium2"), focal(50f, 78f), Sample.at(19, 30, -1), 130),
            recorded("sessions", "Late Night Sessions", Sample.pulse, fx("concert2"), Alignment.Center, Sample.at(22, 0, -2), 58, unwatched = true),
            recorded("kingdoms3", "Kingdoms of Ice, Episode 3", Sample.wildframe, fx("aurora2"), focal(60f, 40f), Sample.at(20, 0, -2), 60, unwatched = true),
            recorded("lighthouse", "The Last Lighthouse", Sample.cinema, fx("lighthouse3"), focal(62f, 40f), Sample.at(21, 0, -3), 135),
            recorded("endurance", "Endurance 24: Hours 1–6", Sample.velocity, fx("motor2"), Alignment.Center, Sample.at(14, 0, -4), 360, progress = 0.18f),
            recorded("deepfield", "Deep Field", Sample.orbit, fx("orbit2"), Alignment.Center, Sample.at(20, 0, -5), 60),
        )

    /**
     * Every row on, the new ones up top: Upcoming Events and Recently Recorded under Favorite
     * Channels (the owner's #80). No board draws it.
     */
    val allRows: HomeUiState
        get() = state.copy(
            rows = listOf(
                HomeRow.Favorites, HomeRow.UpcomingEvents, HomeRow.RecentlyRecorded, HomeRow.NewSeries,
                HomeRow.ContinueWatching, HomeRow.NewMovies, HomeRow.Apps,
            ),
        )

    /**
     * Home as a source's channels and catalog show it: channels have no pictures, so the favorites
     * show their logos with what's on, and movies without a poster their names (the owner's #77).
     */
    val withoutPictures: HomeUiState
        get() = state.let { s ->
            s.copy(
                favorites = favoriteRow.map { channel -> thumb(channel).copy(still = null, nowTitle = Sample.nowOn(channel)?.title) },
                newMovies = s.newMovies.mapIndexed { i, movie -> if (i % 2 == 0) movie.copy(poster = null) else movie },
            )
        }

    /** The screensaver at 20:41 while Harbour City v Redmoor records (board 06). */
    val screensaver: ScreensaverUiState
        get() = PlasmaPictures.copy(
            recording = Sample.schedule.values.flatten().firstOrNull { it.recording && it.isLive }?.title,
        )
}
