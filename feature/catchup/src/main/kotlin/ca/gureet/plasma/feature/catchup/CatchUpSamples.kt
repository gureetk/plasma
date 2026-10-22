// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.catchup

import ca.gureet.plasma.core.design.component.Art
import ca.gureet.plasma.core.design.component.focal
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.fixtures.fx
import java.time.LocalDate
import java.time.LocalTime

/**
 * Catch-up as board 18 draws it: Arena Sports 1's archive yesterday, among the channels that keep
 * one (appendix A: 214 of them). Every channel gets a plausible day of programmes for each day of
 * its archive, so moving around shows something.
 */
object CatchUpSamples {
    /** A programme in a channel's usual day: when it starts, how long it runs, what it is. */
    private class Slot(val time: String, val minutes: Long, val title: String, val subtitle: String? = null)

    private val weekday = mapOf(
        Sample.arena1 to listOf(
            Slot("06:00", 120, "Sports Desk", "The morning’s news from every league"),
            Slot("08:00", 120, "Classic Matches", "Harbour City’s title-winning season"),
            Slot("10:00", 60, "City League Review", "Every goal from the weekend"),
            Slot("11:00", 120, "Live: Women’s Cup", "Kingsway v Ashford Town"),
            Slot("13:00", 60, "Training Ground", "Inside the clubs this week"),
            Slot("14:00", 180, "Matchday Live", "Build-up and analysis from the grounds"),
            Slot("17:00", 90, "City League Preview", "Team news and predictions for Matchweek 9"),
            Slot("18:30", 60, "Football Tonight", "The big stories from the week, with guests"),
            Slot("19:30", 130, "Live: City League", "Northport v Kingsway · City League"),
            Slot("21:40", 50, "The Goals Show", "Every goal from tonight"),
            Slot("22:30", 60, "Overseas Cup: Live"),
            Slot("23:30", 30, "Sports Desk Late", "The day’s results"),
        ),
        Sample.arena2 to listOf(
            Slot("08:00", 60, "Tennis Today", "The day ahead at the Grand Slam"),
            Slot("09:00", 240, "Grand Slam Tennis: Round 3", "Live from Court One"),
            Slot("13:00", 300, "Grand Slam Tennis: Round 3", "Live from Centre Court"),
            Slot("18:00", 60, "Tennis Today", "Highlights from the day"),
            Slot("19:00", 210, "Grand Slam Tennis: Night Session", "Live from Centre Court"),
            Slot("22:30", 60, "Tennis Tonight", "Every point that mattered"),
        ),
        Sample.arenaExtra to listOf(
            Slot("10:00", 60, "Transfer Talk", "Who’s moving in January"),
            Slot("11:00", 120, "Classic Matches", "Redmoor’s cup run"),
            Slot("13:00", 120, "Lower Leagues Live", "Ashford Town v Westbury"),
            Slot("15:00", 90, "Fan Zone", "Your calls and messages"),
            Slot("16:30", 60, "Transfer Talk", "The deals that nearly happened"),
            Slot("20:30", 60, "Transfer Talk", "Tonight’s rumours, checked"),
        ),
        Sample.velocity to listOf(
            Slot("10:00", 30, "Pit Lane", "The week in motorsport"),
            Slot("10:30", 180, "Endurance 24: Qualifying", "Every lap from the shoot-out"),
            Slot("14:00", 120, "Rally Highlights", "Stage by stage"),
            Slot("16:00", 60, "Garage Talk", "With the teams before the start"),
            Slot("20:00", 90, "Endurance 24: Night Stint", "Hours 6 to 8"),
            Slot("21:30", 30, "Pit Lane", "The night so far"),
        ),
        Sample.northline to listOf(
            Slot("06:00", 180, "Morning Edition", "News, weather and travel"),
            Slot("09:00", 180, "Northline Today", "The stories that matter this morning"),
            Slot("12:00", 60, "News at Noon"),
            Slot("13:00", 120, "The Briefing", "Politics and policy, explained"),
            Slot("15:00", 180, "Afternoon Live", "Breaking news as it happens"),
            Slot("18:00", 60, "The Six O’Clock News"),
            Slot("19:00", 60, "Northline Tonight", "The day’s big story in depth"),
            Slot("20:00", 30, "Evening Bulletin"),
            Slot("20:30", 30, "Business Wrap", "Markets, money and the day’s deals"),
            Slot("21:00", 60, "The World Tonight", "Reports from our correspondents"),
            Slot("22:00", 60, "Late Edition", "Tomorrow’s papers"),
        ),
        Sample.meridian to listOf(
            Slot("07:00", 120, "Market Open", "Futures, currencies and the day ahead"),
            Slot("09:00", 180, "Meridian Morning"),
            Slot("12:00", 120, "Midday Markets"),
            Slot("14:00", 120, "Global Business", "Companies, trade and technology"),
            Slot("16:00", 60, "Closing Bell"),
            Slot("17:00", 120, "Meridian Reports", "Stories from around the world"),
            Slot("19:00", 120, "Markets Close", "How the day ended in every market"),
            Slot("21:00", 30, "Late Edition"),
            Slot("21:30", 60, "Meridian Debate", "Four voices, one question"),
        ),
        Sample.atlas to listOf(
            Slot("09:00", 120, "Breakfast Club", "Guests, news and the day’s recipes"),
            Slot("11:00", 60, "Home Truths", "Fixing up a farmhouse"),
            Slot("12:00", 60, "The Quiz Hour", "Daytime special"),
            Slot("13:00", 60, "Baker’s Dozen", "Pastry week"),
            Slot("14:00", 120, "Afternoon Film: Salt and Stone"),
            Slot("16:00", 60, "Small Claims", "The neighbour’s fence"),
            Slot("17:00", 90, "Kitchen Live", "Supper in thirty minutes"),
            Slot("18:30", 90, "Atlas Tonight", "Chat and music"),
            Slot("20:00", 60, "The Quiz Hour", "Tonight’s champions return"),
            Slot("21:00", 60, "Baker’s Dozen", "The semi-final"),
            Slot("22:00", 60, "Last Orders", "Closing time at the Crown"),
        ),
        Sample.wildframe to listOf(
            Slot("10:00", 60, "Wild Coasts", "Seabirds of the north"),
            Slot("11:00", 60, "Rivers of the North", "The salmon run"),
            Slot("12:00", 60, "Kingdoms of Ice", "The long night"),
            Slot("13:00", 60, "Small Worlds", "Life under a leaf"),
            Slot("14:00", 120, "Great Migrations", "Across the plains"),
            Slot("20:00", 60, "Kingdoms of Ice", "Thin ice"),
            Slot("21:00", 60, "Rivers of the North", "Into the delta"),
        ),
        Sample.pulse to listOf(
            Slot("12:00", 120, "Pulse Top 40", "The week’s biggest songs"),
            Slot("14:00", 120, "Live Sessions", "Recorded at the Pulse studio"),
            Slot("16:00", 120, "Rewind: The 90s", "Videos from the vault"),
            Slot("18:00", 120, "Pulse Top 40", "The countdown, again"),
            Slot("20:00", 120, "Late Night Sessions", "Acoustic sets after dark"),
            Slot("22:00", 120, "Club Pulse", "Mixes until midnight"),
        ),
        Sample.orbit to listOf(
            Slot("10:00", 60, "Deep Field", "The oldest light"),
            Slot("11:00", 60, "How Things Fly", "Wings, rotors and rockets"),
            Slot("12:00", 60, "Night Sky Guide", "What to look for this month"),
            Slot("13:00", 60, "The Engineers", "Building a bridge in winter"),
            Slot("14:00", 60, "Mission Logs", "Ten days on the station"),
            Slot("20:00", 60, "Deep Field", "Dark matter"),
        ),
        Sample.courtside to listOf(
            Slot("14:00", 120, "Courtside Classics", "Finals from the archive"),
            Slot("16:00", 90, "Hoops Weekly", "Every game, every highlight"),
            Slot("17:30", 150, "City Lights v Kingsway", "Regular season"),
            Slot("20:30", 150, "City Lights v Northport", "Regular season"),
        ),
    )

    /** Yesterday on Arena Sports 1, from 17:00, as the board shows it. */
    private val arenaYesterday = listOf(
        Slot("17:00", 90, "City League Preview", "Team news and predictions for Matchweek 9"),
        Slot("18:30", 60, "Football Tonight", "The big stories from the week, with guests"),
        Slot("19:30", 130, "Tuesday Night Football", "Redmoor v Ashford Town · City League"),
        Slot("21:40", 50, "The Goals Show", "Every goal from Tuesday night"),
        Slot("22:30", 60, "Overseas Cup: Live"),
        Slot("23:30", 30, "Sports Desk Late", "The day’s results"),
    )

    /** The board's channels in its order, then Courtside+, recorded locally (appendix A). */
    val channels: List<CatchUpChannel> = listOf(
        Sample.arena1, Sample.arena2, Sample.arenaExtra, Sample.velocity, Sample.northline,
        Sample.meridian, Sample.atlas, Sample.wildframe, Sample.pulse, Sample.orbit,
    ).map { CatchUpChannel(it.id, it.number, it.name, it.logo, archiveDays = it.catchupDays) } +
        CatchUpChannel(Sample.courtside.id, Sample.courtside.number, Sample.courtside.name, Sample.courtside.logo, archiveDays = null, localHours = 24)

    private fun day(channel: Sample.Channel, date: LocalDate): List<CatchUpProgram> {
        val slots = when {
            channel == Sample.arena1 && date == Sample.today.minusDays(1) ->
                weekday.getValue(channel).filter { LocalTime.parse(it.time) < LocalTime.of(17, 0) } + arenaYesterday
            channel == Sample.arena1 && date == Sample.today ->
                weekday.getValue(channel).filter { LocalTime.parse(it.time) < LocalTime.of(19, 30) }
            else -> weekday.getValue(channel)
        }
        val programs = slots.map { slot ->
            val start = date.atTime(LocalTime.parse(slot.time)).atZone(Sample.zone)
            CatchUpProgram(
                id = "${channel.id}-$date-${slot.time}",
                title = slot.title,
                subtitle = slot.subtitle,
                start = start,
                end = start.plusMinutes(slot.minutes),
                art = when (slot.title) {
                    "Tuesday Night Football" -> Art(fx("stadium2"), focal(50f, 71f), zoom = 2f)
                    "The Goals Show" -> if (date == Sample.today.minusDays(1)) Art(fx("stadium2"), focal(50f, 82f)) else null
                    else -> null
                },
                progress = if (slot.title == "The Goals Show" && date == Sample.today.minusDays(1)) 0.35f else null,
                available = slot.title != "Overseas Cup: Live",
            )
        }
        // Today, Harbour City v Redmoor is on now: Start Over plays it from 19:45.
        val live = if (channel == Sample.arena1 && date == Sample.today) {
            listOf(CatchUpProgram("${channel.id}-$date-19:45", Sample.harbourCity.title, Sample.harbourCity.subtitle, Sample.harbourCity.start, Sample.harbourCity.end))
        } else {
            emptyList()
        }
        return programs + live
    }

    val state: CatchUpUiState
        get() = CatchUpUiState(
            channels = channels,
            channelCount = 214,
            selectedChannelId = Sample.arena1.id,
            programs = channels.associate { channel ->
                val sample = Sample.channels.first { it.id == channel.id }
                channel.id to channel.days(Sample.today).sorted().flatMap { day(sample, it) }
            },
        )
}
