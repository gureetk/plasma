// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.ReminderEntity
import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A programme someone asked to be reminded of (brief 5.7). */
data class Reminder(
    /** "channelId@start in epoch seconds", the id the screens give the programme. */
    val id: String,
    val channelId: String,
    val title: String,
    val start: Instant,
    val end: Instant,
)

/** The reminders kept, as the [ReminderClock] needs them. */
interface ReminderStore {
    /** Every reminder still to come, soonest first. */
    val reminders: Flow<List<Reminder>>

    suspend fun cancel(id: String)

    /** Drops reminders whose programme has started: shown, or missed while Plasma was away. */
    suspend fun pruneStarted()
}

/** Reminders, kept in the database so they survive a restart (brief 5.7). */
class ReminderRepository(
    private val db: PlasmaDatabase,
    private val clock: PlasmaClock,
    private val profile: ProfileSource = ProfileSource.fixed(),
) : ReminderStore {
    override val reminders: Flow<List<Reminder>> = profile.watching { db.reminders().observe(it) }
        .map { list -> list.map { Reminder(it.id, it.channelId, it.title, Instant.ofEpochMilli(it.startAt), Instant.ofEpochMilli(it.endAt)) } }
        .distinctUntilChanged()

    /** The programme ids with a reminder, for the bells on the Guide, Live TV and Search. */
    val ids: Flow<Set<String>> = reminders.map { list -> list.mapTo(HashSet()) { it.id } }.distinctUntilChanged()

    suspend fun set(channelId: String, title: String, start: Instant, end: Instant) {
        db.reminders().upsert(
            ReminderEntity(
                id = idOf(channelId, start),
                profileId = profile.now(),
                channelId = channelId,
                title = title,
                startAt = start.toEpochMilli(),
                endAt = end.toEpochMilli(),
                createdAt = clock.now().toEpochMilli(),
            ),
        )
    }

    override suspend fun cancel(id: String) = db.reminders().remove(id)

    override suspend fun pruneStarted() = db.reminders().removeStartedBefore(clock.now().toEpochMilli())

    companion object {
        fun idOf(channelId: String, start: Instant): String = "$channelId@${start.epochSecond}"
    }
}
