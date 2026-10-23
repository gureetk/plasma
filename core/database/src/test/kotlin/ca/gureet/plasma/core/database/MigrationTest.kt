// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Each released schema moves to the next without losing what's kept (brief 6.3). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlasmaDatabase::class.java)

    @Test fun version1KeepsItsDataAndGainsRemindersAndSearch() = runTest {
        helper.createDatabase(NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, guideGeneration) " +
                    "VALUES ('home', 'xtream', 'Home', 'http://line.example.org', 0, 1, 1, 'ts', 'ts', 0, 1)",
            )
            db.execSQL("INSERT INTO favorites (profileId, channelId, position) VALUES ('default', 'home:105', 0)")
            db.execSQL(
                "INSERT INTO channels (id, sourceId, key, name, position, number, numberReason, epgKey, isRadio, hidden, locked, generation) " +
                    "VALUES ('home:105', 'home', '105', 'Arena Sports 1', 0, 105, 'Playlist', 'home:arena1', 0, 0, 0, 1)",
            )
            db.execSQL(
                "INSERT INTO programmes (sourceId, epgKey, startAt, endAt, title, isNew, generation) " +
                    "VALUES ('home', 'home:arena1', 1000, 2000, 'Match of the Week Extra', 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 2, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Home", db.sources().get("home")?.name)
            db.reminders().upsert(ReminderEntity("home:105@1", "default", "home:105", "Match of the Week Extra", 1_000, 2_000, 1))
            assertEquals(listOf("home:105@1"), db.reminders().observe("default").first().map { it.id })
            // The guide that was there is in the search index.
            assertEquals(listOf("Match of the Week Extra"), db.search().programmes("week*", 0, 3_000, 1_500, 10).map { it.title })
        } finally {
            db.close()
        }
    }

    @Test fun version2GainsSettingsPerSource() = runTest {
        helper.createDatabase(NAME, 2).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, guideGeneration) " +
                    "VALUES ('home', 'xtream', 'Home', 'http://line.example.org', 0, 1, 1, 'm3u8,ts', 'm3u8', 60, 1)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 3, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val source = db.sources().get("home")!!
            // What was set stays; the new settings start as "Same as Playback settings".
            assertEquals("m3u8", source.streamFormat)
            assertEquals(60, source.epgOffsetMinutes)
            assertEquals(null, source.bufferChoice)
            assertEquals(null, source.audioOutputChoice)
            assertEquals(0, source.catchupOffsetMinutes)
        } finally {
            db.close()
        }
    }

    @Test fun version3GainsStalkerBoxes() = runTest {
        helper.createDatabase(NAME, 3).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, catchupOffsetMinutes, guideGeneration) " +
                    "VALUES ('home', 'xtream', 'Home', 'http://line.example.org', 0, 1, 1, 'ts', 'ts', 0, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 4, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val source = db.sources().get("home")!!
            assertEquals("Home", source.name)
            assertEquals(null, source.macEnc)
            assertEquals(null, source.stbModel)
        } finally {
            db.close()
        }
    }

    @Test fun version4GainsWhatWorksPerChannel() = runTest {
        helper.createDatabase(NAME, 4).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, catchupOffsetMinutes, guideGeneration) " +
                    "VALUES ('home', 'xtream', 'Home', 'http://line.example.org', 0, 1, 1, 'ts', 'ts', 0, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 5, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Home", db.sources().get("home")!!.name)
            assertEquals(emptyList<ChannelSourceMemoryEntity>(), db.channelSourceMemory().forAny(listOf("home:105")))
        } finally {
            db.close()
        }
    }

    @Test fun version5GainsEachChannelsOwnEngine() = runTest {
        helper.createDatabase(NAME, 5).use { db ->
            db.execSQL(
                "INSERT INTO channel_source_memory (mergedChannelId, preferredChannelId, reason, learnedAt, recheckAfter, recentResults) " +
                    "VALUES ('home:105', 'backup:105', 'USER', 1, 2, '')",
            )
        }

        helper.runMigrationsAndValidate(NAME, 6, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(1, db.channelSourceMemory().forAny(listOf("home:105")).size)
            assertEquals(null, db.channelPlayback().get("home:105"))
        } finally {
            db.close()
        }
    }

    @Test fun version6GainsTheClashAChoiceSettled() = runTest {
        helper.createDatabase(NAME, 6).use { db ->
            db.execSQL("INSERT INTO number_overrides (sourceId, channelKey, number, setAt) VALUES ('home', '108', 208, 1)")
        }

        helper.runMigrationsAndValidate(NAME, 7, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(listOf(NumberOverrideEntity("home", "108", 208, 1, wantedNumber = null)), db.numberOverrides().all())
        } finally {
            db.close()
        }
    }

    private companion object {
        const val NAME = "migration-test"
    }
}
