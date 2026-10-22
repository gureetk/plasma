// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.recordings

import androidx.compose.ui.Alignment
import ca.gureet.plasma.core.design.component.Art
import ca.gureet.plasma.core.design.component.focal
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.fixtures.fx
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Recordings as board 17 draws them (appendix A): twelve recordings, three scheduled, two series
 * rules, and a 500 GB USB drive with 212 GB free. Kingdoms of Ice airs on Mondays and Thursdays.
 */
object RecordingsSamples {
    private val kingdomsOfIce = SeriesRef("kingdoms", "Kingdoms of Ice")

    private fun recording(
        id: String,
        title: String,
        channel: Sample.Channel,
        art: Art,
        kind: RecordingKind,
        start: ZonedDateTime,
        minutes: Long,
        inProgress: Boolean = false,
        unwatched: Boolean = false,
        watched: Boolean = false,
        resume: Float? = null,
        deletesInDays: Long? = null,
        keepLast: Int? = null,
        series: SeriesRef? = null,
    ) = RecordingItem(
        id = id,
        title = title,
        channelName = channel.name,
        logo = channel.logo,
        art = art,
        kind = kind,
        start = start,
        end = start.plusMinutes(minutes),
        inProgress = inProgress,
        unwatched = unwatched,
        watched = watched,
        resume = resume,
        deletesOn = deletesInDays?.let { Sample.today.plusDays(it) },
        keepLast = keepLast,
        series = series,
        byRule = series != null,
    )

    private val match = Art(fx("stadium2"), focal(50f, 71f), zoom = 2f)
    private val aurora = Art(fx("aurora2"), focal(60f, 40f))

    val recordings: List<RecordingItem> = listOf(
        recording("harbour", "Harbour City v Redmoor", Sample.arena1, match, RecordingKind.Sports, Sample.at(19, 45), 135, inProgress = true),
        recording("kingdoms1", "Kingdoms of Ice, Episode 1", Sample.wildframe, aurora, RecordingKind.Series, Sample.at(20, 0, -9), 60, watched = true, series = kingdomsOfIce),
        recording("kingdoms2", "Kingdoms of Ice, Episode 2", Sample.wildframe, aurora, RecordingKind.Series, Sample.at(20, 0, -6), 60, unwatched = true, series = kingdomsOfIce),
        recording("kingdoms3", "Kingdoms of Ice, Episode 3", Sample.wildframe, aurora, RecordingKind.Series, Sample.at(20, 0, -2), 60, unwatched = true, series = kingdomsOfIce),
        recording("lighthouse", "The Last Lighthouse", Sample.cinema, Art(fx("lighthouse3"), focal(62f, 40f)), RecordingKind.Movie, Sample.at(21, 0, -3), 135),
        recording("sessions", "Late Night Sessions", Sample.pulse, Art(fx("concert2")), RecordingKind.Series, Sample.at(22, 0, -2), 58, unwatched = true),
        recording("business", "Business Wrap", Sample.northline, Art(fx("city2"), focal(50f, 60f)), RecordingKind.News, Sample.at(20, 30, -1), 30, unwatched = true),
        recording("endurance", "Endurance 24: Hours 1–6", Sample.velocity, Art(fx("motor2")), RecordingKind.Sports, Sample.at(14, 0, -4), 360, resume = 0.18f),
        recording("deepfield", "Deep Field", Sample.orbit, Art(fx("orbit2")), RecordingKind.Series, Sample.at(20, 0, -5), 60, deletesInDays = 2, keepLast = 3),
        recording("football", "Tuesday Night Football", Sample.arena1, Art(fx("stadium2"), focal(50f, 78f)), RecordingKind.Sports, Sample.at(19, 30, -1), 130, watched = true),
        // The two further down the list (appendix A).
        recording("stormwatch", "Storm Watch: Live", Sample.northline, Art(fx("city2"), focal(55f, 40f)), RecordingKind.News, Sample.at(22, 0, -7), 600, unwatched = true),
        recording("bakers", "Baker’s Dozen: Series Marathon", Sample.atlas, Art(fx("city2"), Alignment.Center), RecordingKind.Series, Sample.at(9, 0, -8), 250, unwatched = true),
    )

    val scheduled: List<ScheduledItem> = listOf(
        ScheduledItem("fightnight", "Fight Night: Main Card", Sample.arena1.name, Sample.arena1.logo, Sample.at(0, 30, 1), Sample.at(3, 0, 1)),
        ScheduledItem(
            "kingdoms4", "Kingdoms of Ice, Ep. 4", Sample.wildframe.name, Sample.wildframe.logo, Sample.at(20, 0, 1), Sample.at(21, 0, 1),
            byRule = true,
        ),
        ScheduledItem(
            "northport", "Harbour City v Northport", Sample.arena1.name, Sample.arena1.logo, Sample.at(15, 0, 3), Sample.at(17, 0, 3),
            byRule = true, endsLate = Duration.ofMinutes(20),
        ),
    )

    val reminders: List<ReminderItem> = listOf(
        ReminderItem("bakers-reminder", "Baker’s Dozen", Sample.atlas.name, Sample.atlas.logo, Sample.at(21, 0)),
        ReminderItem("extra-reminder", "Match of the Week Extra", Sample.arena1.name, Sample.arena1.logo, Sample.at(22, 0)),
    )

    val rules: List<SeriesRuleItem> = listOf(
        SeriesRuleItem("rule-kingdoms", "Kingdoms of Ice", Sample.wildframe.name, Sample.wildframe.logo, keepLast = null),
        // Sports get 15 more minutes at the end (brief 5.6).
        SeriesRuleItem("rule-harbour", "Harbour City", channelName = null, logo = null, keepLast = 3, endsLate = Duration.ofMinutes(20)),
    )

    val state: RecordingsUiState
        get() = RecordingsUiState(
            recordings = recordings,
            scheduled = scheduled,
            reminders = reminders,
            rules = rules,
            storage = StorageInfo("USB drive", freeBytes = 212_000_000_000, totalBytes = 500_000_000_000),
            connectionsInUse = 1,
            connectionLimit = 2,
        )
}
