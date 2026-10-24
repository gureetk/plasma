// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.catchup

import androidx.compose.runtime.Immutable
import ca.gureet.plasma.core.design.component.Art
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** A channel whose past programmes can be played: from the provider's archive, or Plasma's own (brief 5.5). */
@Immutable
data class CatchUpChannel(
    val id: String,
    val number: Int,
    val name: String,
    val logo: String?,
    /** How many days the provider keeps, or null when Plasma keeps a local window instead. */
    val archiveDays: Int?,
    /** Local catch-up: how many hours Plasma keeps. */
    val localHours: Int? = null,
) {
    /** The days with programmes to play: today, yesterday and back as far as the archive goes. */
    fun days(today: LocalDate): List<LocalDate> {
        val count = archiveDays ?: localHours?.let { (it + 23) / 24 + 1 } ?: 1
        return (0 until count).map { today.minusDays(it.toLong()) }
    }
}

@Immutable
data class CatchUpProgram(
    val id: String,
    val title: String,
    val subtitle: String?,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    /** The guide's picture for it; without one, the channel's logo stands in. */
    val art: Art? = null,
    /** How much has been watched. */
    val progress: Float? = null,
    /** Some programmes aren't in the archive, such as events the provider can't offer in your region. */
    val available: Boolean = true,
) {
    val duration: Duration get() = Duration.between(start, end)
}

@Immutable
data class CatchUpUiState(
    val channels: List<CatchUpChannel>,
    /** How many channels have catch-up in all ("214"), not just those listed so far. */
    val channelCount: Int,
    val selectedChannelId: String,
    /** Each channel's archived programmes, oldest first. */
    val programs: Map<String, List<CatchUpProgram>>,
)

class CatchUpActions(
    /** OK: plays the programme from its start. */
    val onPlay: (channelId: String, program: CatchUpProgram) -> Unit = { _, _ -> },
    /** Green: the Guide, where any channel's past programmes can be played. */
    val onGuide: () -> Unit = {},
    val onShowInGuide: (channelId: String, start: ZonedDateTime) -> Unit = { _, _ -> },
    /** A channel's programmes are showing: the app loads them, if it hasn't yet. */
    val onChannel: (channelId: String) -> Unit = {},
    /** Keep as Recording, from local catch-up (brief 5.5): the programme copied into Recordings. */
    val onKeep: (channelId: String, program: CatchUpProgram) -> Unit = { _, _ -> },
)

/** The programmes that started on [day] (in [zone]) and have started by [now], oldest first. */
internal fun programsOn(programs: List<CatchUpProgram>, day: LocalDate, zone: ZoneId, now: Instant): List<CatchUpProgram> =
    programs.filter { it.start.withZoneSameInstant(zone).toLocalDate() == day && it.start.toInstant().isBefore(now) }

/**
 * The programme a day opens on: the one on at this time of day, as the Guide opens other days at
 * the same time (4.8). Before the day's first programme, the first; after its last, the last.
 */
internal fun openingProgram(programs: List<CatchUpProgram>, day: LocalDate, now: ZonedDateTime): CatchUpProgram? {
    val moment = day.atTime(now.toLocalTime()).atZone(now.zone).toInstant()
    return programs.firstOrNull { !it.start.toInstant().isAfter(moment) && it.end.toInstant().isAfter(moment) }
        ?: programs.lastOrNull { !it.start.toInstant().isAfter(moment) }
        ?: programs.firstOrNull()
}

/** Yesterday when the archive has it (board 18), otherwise today. */
internal fun openingDay(channel: CatchUpChannel, today: LocalDate): LocalDate =
    channel.days(today).getOrNull(1) ?: today
