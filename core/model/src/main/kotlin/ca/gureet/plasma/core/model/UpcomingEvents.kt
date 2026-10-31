// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import java.time.Duration
import java.time.Instant

/**
 * The kinds of program Home's Upcoming Events brings in (the owner's #80), each with its switch in
 * Settings › Launcher & Home › Home Rows › Upcoming Events, all on by default.
 */
enum class UpcomingKind(val key: String) {
    /** Programs with Remind Me or Record set. */
    RemindersAndRecordings(SettingKeys.UPCOMING_MARKED),

    /** Programs about to start on favorite channels, from the guide. */
    StartingSoonOnFavorites(SettingKeys.UPCOMING_FAVORITES),

    /** Programs the guide lists as sport, on now or about to start. */
    LiveSportsAndEvents(SettingKeys.UPCOMING_SPORTS),
}

/** Why a program is in Upcoming Events: a program can be there for more than one reason. */
enum class UpcomingReason(val kind: UpcomingKind) {
    Reminder(UpcomingKind.RemindersAndRecordings),
    Recording(UpcomingKind.RemindersAndRecordings),
    Favorite(UpcomingKind.StartingSoonOnFavorites),
    Sports(UpcomingKind.LiveSportsAndEvents),
}

/** A program Upcoming Events might show, for one [reason]. */
data class UpcomingCandidate(
    /** "channelId@start in epoch seconds", the id the screens give a program, with the channel's id as it shows. */
    val id: String,
    val channelId: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val reason: UpcomingReason,
    /** The channel's number, which orders programs that start together. */
    val channelNumber: Int = 0,
)

/** A program in Upcoming Events, with every reason it's there. */
data class UpcomingEvent(
    val id: String,
    val channelId: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val reasons: Set<UpcomingReason>,
    val channelNumber: Int = 0,
) {
    val reminder: Boolean get() = UpcomingReason.Reminder in reasons
    val recording: Boolean get() = UpcomingReason.Recording in reasons

    /** Whether it's on at [at]. */
    fun isOn(at: Instant): Boolean = !start.isAfter(at) && end.isAfter(at)
}

/**
 * Home's Upcoming Events (the owner's #80): programs with a reminder or a recording set, starting
 * in the next day (and a recording being made now); programs starting within the hour on favorite
 * channels; and sport on now or starting within two hours. Each program once, whatever brought it
 * in, soonest first, at most [LIMIT].
 */
object UpcomingEvents {
    /** Reminders and recordings: those starting within a day. */
    val MARKED_AHEAD: Duration = Duration.ofHours(24)

    /** Starting Soon on Favorites: within the hour. */
    val FAVORITES_AHEAD: Duration = Duration.ofHours(1)

    /** Live Sports and Events: on now, or starting within two hours. */
    val SPORTS_AHEAD: Duration = Duration.ofHours(2)

    /** The row's length. */
    const val LIMIT = 20

    /**
     * What Upcoming Events shows at [now] from [candidates], with only [kinds] brought in: each
     * program once with all its reasons, soonest first. Programs starting together go in the
     * order of their reminders and recordings first, then their channels' numbers.
     */
    fun pick(
        candidates: Iterable<UpcomingCandidate>,
        now: Instant,
        kinds: Set<UpcomingKind> = UpcomingKind.entries.toSet(),
        limit: Int = LIMIT,
    ): List<UpcomingEvent> {
        val byId = LinkedHashMap<String, UpcomingEvent>()
        for (candidate in candidates) {
            if (candidate.reason.kind !in kinds || !fits(candidate, now)) continue
            val had = byId[candidate.id]
            byId[candidate.id] = had?.copy(reasons = had.reasons + candidate.reason)
                ?: with(candidate) { UpcomingEvent(id, channelId, title, start, end, setOf(reason), channelNumber) }
        }
        return byId.values
            .sortedWith(compareBy<UpcomingEvent>({ it.start }, { !it.reminder && !it.recording }, { it.channelNumber }, { it.title }))
            .take(limit)
    }

    /** Whether [candidate] is within its reason's window at [now]; nothing that's over is. */
    private fun fits(candidate: UpcomingCandidate, now: Instant): Boolean {
        if (!candidate.end.isAfter(now)) return false
        val start = candidate.start
        return when (candidate.reason) {
            UpcomingReason.Reminder -> start.isAfter(now) && !start.isAfter(now.plus(MARKED_AHEAD))
            // Being recorded now, or starting within the day.
            UpcomingReason.Recording -> !start.isAfter(now.plus(MARKED_AHEAD))
            UpcomingReason.Favorite -> start.isAfter(now) && !start.isAfter(now.plus(FAVORITES_AHEAD))
            // On now, or starting soon.
            UpcomingReason.Sports -> !start.isAfter(now.plus(SPORTS_AHEAD))
        }
    }

    /**
     * Whether a guide category means sport: a word in it starting with "sport" ("Sports", "Sports
     * event"), with a word for sport in another form or language ("Motorsport", "Deportes",
     * "Desporto"), or with a sport's own name ("Football", "Tennis"). "Transport" isn't one.
     */
    fun isSports(category: String?): Boolean {
        val words = category?.lowercase()?.split(NOT_A_WORD)?.filter { it.isNotEmpty() } ?: return false
        return words.any { word -> SPORTS.any(word::startsWith) }
    }

    private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** The starts of words that mean sport. */
    private val SPORTS = listOf(
        "sport", "motorsport", "esport", "desport", "deporte", "wintersport", "watersport",
        "football", "soccer", "futbol", "fútbol", "tennis", "golf", "rugby", "cricket", "hockey", "basketball", "baseball",
        "boxing", "wrestling", "racing", "athletics", "cycling", "olympic", "formula", "nfl", "nba", "nhl", "mlb", "ufc", "mma",
    )
}
