// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.ui.graphics.TransformOrigin
import ca.gureet.plasma.core.design.component.focal
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.fixtures.fx
import ca.gureet.plasma.player.VideoFrame

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
                        id = "continue:atlas-station",
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
                        note = "Starts in ${Formats.durationLoose(java.time.Duration.between(Sample.now, reminder.start.toInstant()))}",
                    ),
                ),
                favorites = favoriteRow.map(::thumb),
                continueWatching = listOf(
                    ContinueItem(
                        id = "series:atlas-station:s2e4",
                        title = "Atlas Station",
                        meta = "S2, E4 · 18 min left",
                        image = fx("orbit2"),
                        kind = ContinueKind.Art,
                        progress = 0.70f,
                        imageZoom = 1.6f,
                        zoomPivot = TransformOrigin(0.375f, 1f),
                    ),
                    ContinueItem(
                        id = "jellyfin:kite-season",
                        title = "Kite Season",
                        meta = "Jellyfin · 52 min left",
                        image = fx("poster_kite"),
                        kind = ContinueKind.Poster,
                        progress = 0.38f,
                        sourceTag = "JELLYFIN",
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
            )
        }
}
