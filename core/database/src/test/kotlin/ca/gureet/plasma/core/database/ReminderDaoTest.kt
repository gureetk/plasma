// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Reminders as kept (brief 5.7): per profile, soonest first, and gone with their source. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReminderDaoTest {
    private lateinit var db: PlasmaDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun close() = db.close()

    private fun reminder(channelId: String, startAt: Long, profileId: String = "default") =
        ReminderEntity("$channelId@$startAt", profileId, channelId, "P$startAt", startAt, startAt + 3_600, createdAt = 1)

    @Test fun remindersComeSoonestFirstForTheirProfile() = runTest {
        db.reminders().upsert(reminder("home:105", 3_000))
        db.reminders().upsert(reminder("home:101", 2_000))
        db.reminders().upsert(reminder("home:108", 1_000, profileId = "kids"))
        // Setting the same reminder again keeps one.
        db.reminders().upsert(reminder("home:101", 2_000))

        assertEquals(listOf("home:101@2000", "home:105@3000"), db.reminders().observe("default").first().map { it.id })
        assertEquals(listOf("home:108@1000"), db.reminders().observe("kids").first().map { it.id })
    }

    @Test fun startedRemindersAreDropped() = runTest {
        db.reminders().upsert(reminder("home:105", 1_000))
        db.reminders().upsert(reminder("home:105", 5_000))

        db.reminders().removeStartedBefore(2_000)

        assertEquals(listOf("home:105@5000"), db.reminders().observe("default").first().map { it.id })
    }

    @Test fun aSourceTakesOnlyItsOwnRemindersWithIt() = runTest {
        db.reminders().upsert(reminder("home:105", 1_000))
        db.reminders().upsert(reminder("home2:105", 1_000))
        // A source id with LIKE's wildcards in it matches only itself.
        db.reminders().upsert(reminder("a_b:105", 1_000))
        db.reminders().upsert(reminder("axb:105", 1_000))

        db.reminders().removeForSource("home")
        db.reminders().removeForSource("a_b")

        assertEquals(setOf("home2:105@1000", "axb:105@1000"), db.reminders().observe("default").first().map { it.id }.toSet())
    }
}
