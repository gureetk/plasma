// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Remind Me and Cancel Reminder (brief 5.7), kept by programme. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReminderRepositoryTest {
    private val now = Instant.parse("2026-10-21T20:41:00Z")
    private lateinit var db: PlasmaDatabase
    private lateinit var reminders: ReminderRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        reminders = ReminderRepository(db, FixedClock(now, ZoneOffset.UTC))
    }

    @After fun tearDown() = db.close()

    private val extra = Instant.parse("2026-10-21T22:00:00Z")
    private val football = Instant.parse("2026-10-21T23:00:00Z")

    @Test fun aReminderIsKeptUnderItsProgrammesId() = runTest {
        reminders.set("home:105", "Football Tonight", football, football.plusSeconds(5_400))
        reminders.set("home:105", "Match of the Week Extra", extra, football)
        // Remind Me twice on one programme keeps one reminder.
        reminders.set("home:105", "Match of the Week Extra", extra, football)

        val kept = reminders.reminders.first()
        assertEquals(listOf("Match of the Week Extra", "Football Tonight"), kept.map { it.title })
        assertEquals("home:105@${extra.epochSecond}", kept.first().id)
        assertEquals(extra, kept.first().start)
        assertEquals(setOf("home:105@${extra.epochSecond}", "home:105@${football.epochSecond}"), reminders.ids.first())
    }

    @Test fun cancelAndPruneDropReminders() = runTest {
        reminders.set("home:105", "Match of the Week Extra", extra, football)
        reminders.set("home:101", "Evening News", now.minusSeconds(60), now.plusSeconds(1_740))

        reminders.cancel(ReminderRepository.idOf("home:105", extra))
        assertEquals(listOf("Evening News"), reminders.reminders.first().map { it.title })

        // Started before now: shown already, or missed.
        reminders.pruneStarted()
        assertEquals(emptyList<Reminder>(), reminders.reminders.first())
    }
}
