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

    @Test fun version7GainsEachChannelsOwnSound() = runTest {
        helper.createDatabase(NAME, 7).use { db ->
            db.execSQL("INSERT INTO channel_playback (channelId, engine, engineReason, engineSetAt) VALUES ('home:105', 'Mpv', 'USER', 1)")
        }

        helper.runMigrationsAndValidate(NAME, 8, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(ChannelPlaybackEntity("home:105", "Mpv", "USER", 1, volumeOffsetDb = 0, audioOutput = null), db.channelPlayback().get("home:105"))
        } finally {
            db.close()
        }
    }

    @Test fun version8GainsAdvancedDetails() = runTest {
        helper.createDatabase(NAME, 8).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, catchupOffsetMinutes, guideGeneration, stbModel) " +
                    "VALUES ('portal', 'stalker', 'Portal', 'http://portal.example.net', 0, 1, 1, '', 'ts', 0, 0, 1, 'MAG254')",
            )
        }

        helper.runMigrationsAndValidate(NAME, 9, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val source = db.sources().get("portal")!!
            assertEquals("MAG254", source.stbModel)
            assertEquals(null, source.portalTimeZone)
            assertEquals(null, source.refreshHours)
        } finally {
            db.close()
        }
    }

    @Test fun version9GainsTheRestOfPlaybackForThisSource() = runTest {
        helper.createDatabase(NAME, 9).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, catchupOffsetMinutes, guideGeneration, userAgent) " +
                    "VALUES ('extras', 'm3u', 'Extras', 'http://lists.example.com', 0, 1, 1, '', 'ts', 0, 0, 1, 'Custom agent')",
            )
        }

        helper.runMigrationsAndValidate(NAME, 10, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val source = db.sources().get("extras")!!
            assertEquals("Custom agent", source.userAgent)
            assertEquals(null, source.engineChoice)
            assertEquals(null, source.useHub)
            assertEquals(null, source.udpxy)
        } finally {
            db.close()
        }
    }

    @Test fun version10GainsTheTracksAndPictureSizePicked() = runTest {
        helper.createDatabase(NAME, 10).use { db ->
            db.execSQL("INSERT INTO channel_playback (channelId, engine, engineReason, engineSetAt, volumeOffsetDb, audioOutput) VALUES ('home:105', NULL, NULL, NULL, 3, NULL)")
        }

        helper.runMigrationsAndValidate(NAME, 11, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val playback = db.channelPlayback().get("home:105")!!
            assertEquals(3, playback.volumeOffsetDb)
            assertEquals(null, playback.audioTrack)
            assertEquals(null, playback.subtitleTrack)
            assertEquals(null, playback.pictureSize)
        } finally {
            db.close()
        }
    }

    @Test fun version11GainsMoviesAndSeries() = runTest {
        helper.createDatabase(NAME, 11).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, catchupOffsetMinutes, guideGeneration) " +
                    "VALUES ('home', 'xtream', 'Home', 'http://line.example.com', 0, 1, 1, '', 'ts', 0, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 12, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(null, db.sources().get("home")!!.vodRefreshedAt)
            assertEquals(0, db.vod().movieCount("home"))
            assertEquals(emptyList<VodCategoryEntity>(), db.vod().observeCategories("movie").first())
        } finally {
            db.close()
        }
    }

    @Test fun version12GainsDetailsEpisodesWhereTitlesWereLeftAndMyList() = runTest {
        helper.createDatabase(NAME, 12).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, catchupOffsetMinutes, guideGeneration) " +
                    "VALUES ('home', 'xtream', 'Home', 'http://line.example.com', 0, 1, 1, '', 'ts', 0, 0, 1)",
            )
            db.execSQL(
                "INSERT INTO movies (id, sourceId, remoteId, categoryId, name, poster, addedAt, rating, year, extension, position, generation) " +
                    "VALUES ('home:7101', 'home', '7101', NULL, 'Neon Tide', NULL, NULL, NULL, 2026, 'mkv', 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 13, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Neon Tide", db.vod().movie("home:7101")!!.name)
            assertEquals(null, db.vod().movieDetails("home:7101"))
            assertEquals(emptyList<String>(), db.myList().observeIds("default", "movie").first())
            assertEquals(emptyList<ResumePointEntity>(), db.resumePoints().observeAll("default", "movie").first())
        } finally {
            db.close()
        }
    }

    @Test fun version13KeepsWhereTitlesWereLeftAndCanHideThem() = runTest {
        helper.createDatabase(NAME, 13).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, catchupOffsetMinutes, guideGeneration) " +
                    "VALUES ('home', 'xtream', 'Home', 'http://line.example.com', 0, 1, 1, '', 'ts', 0, 0, 1)",
            )
            db.execSQL(
                "INSERT INTO resume_points (profileId, kind, contentId, sourceId, seriesId, positionMs, durationMs, watched, updatedAt) " +
                    "VALUES ('default', 'movie', 'home:7101', 'home', NULL, 3360000, 7080000, 0, 5)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 14, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val point = db.resumePoints().observe("default", "movie", "home:7101").first()!!
            assertEquals(3_360_000L, point.positionMs)
            assertEquals(false, point.hidden)
        } finally {
            db.close()
        }
    }

    @Test fun version14KeepsMoviesAndSeriesAndTakesAPortalsCommands() = runTest {
        helper.createDatabase(NAME, 14).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, catchupOffsetMinutes, guideGeneration) " +
                    "VALUES ('home', 'xtream', 'Home', 'http://line.example.com', 0, 1, 1, '', 'ts', 0, 0, 1)",
            )
            db.execSQL(
                "INSERT INTO movies (id, sourceId, remoteId, categoryId, name, poster, addedAt, rating, year, extension, position, generation) " +
                    "VALUES ('home:7101', 'home', '7101', NULL, 'Neon Tide', NULL, NULL, NULL, 2026, 'mkv', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO series (id, sourceId, remoteId, categoryId, name, poster, backdrop, plot, cast, director, genre, year, rating, updatedAt, trailer, position, generation) " +
                    "VALUES ('home:8101', 'home', '8101', NULL, 'Atlas Station', NULL, NULL, NULL, NULL, NULL, NULL, 2024, NULL, NULL, NULL, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 15, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val movie = db.vod().movie("home:7101")!!
            assertEquals("mkv", movie.extension)
            assertEquals(null, movie.cmd)
            assertEquals(false, movie.fileApart)
            assertEquals(null, db.vod().series("home:8101")!!.layout)
        } finally {
            db.close()
        }
    }

    @Test fun version15KeepsSourcesAndGainsJellyfinSignIns() = runTest {
        helper.createDatabase(NAME, 15).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, catchupOffsetMinutes, guideGeneration) " +
                    "VALUES ('home', 'xtream', 'Home', 'http://line.example.com', 0, 1, 1, '', 'ts', 0, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 16, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Home", db.sources().get("home")?.name)
            assertEquals(null, db.jellyfinAccounts().get("default"))
            db.jellyfinAccounts().upsert(JellyfinAccountEntity("default", "http://media.home.lan:8096", "srv-1", "Media", "user-1", "Maya", "sealed", "device-1", 1))
            assertEquals("Maya", db.jellyfinAccounts().observe("default").first()?.userName)
        } finally {
            db.close()
        }
    }

    @Test fun version16KeepsSourcesAndGainsRecordings() = runTest {
        helper.createDatabase(NAME, 16).use { db ->
            db.execSQL(
                "INSERT INTO sources (id, type, name, server, priority, enabled, addedAt, outputFormats, streamFormat, epgOffsetMinutes, catchupOffsetMinutes, guideGeneration) " +
                    "VALUES ('home', 'xtream', 'Home', 'http://line.example.com', 0, 1, 1, '', 'ts', 0, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 17, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Home", db.sources().get("home")?.name)
            assertEquals(emptyList<RecordingEntity>(), db.recordings().pending())
        } finally {
            db.close()
        }
    }

    @Test fun version17KeepsRecordingsAndGainsSeriesRules() = runTest {
        helper.createDatabase(NAME, 17).use { db ->
            db.execSQL(
                "INSERT INTO recordings (id, channelId, channelName, title, kind, startAt, endAt, padBeforeMs, padAfterMs, status, sizeBytes, interruptions, keep, positionMs, watched, watchedAt, createdAt) " +
                    "VALUES ('home:104@1', 'home:104', 'Wildframe', 'Kingdoms of Ice', 'series', 1000, 2000, 60000, 300000, 'scheduled', 0, 0, 0, 0, 0, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 18, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            // A recording from before rules belongs to none.
            val recording = db.recordings().get("home:104@1")!!
            assertEquals("Kingdoms of Ice", recording.title)
            assertEquals(null, recording.ruleId)
            db.recordingRules().upsert(RecordingRuleEntity("rule-1", "Kingdoms of Ice", "home:104", "Wildframe", null, keepLast = 3, keepLastSince = 1, createdAt = 1))
            db.recordings().upsert(recording.copy(ruleId = "rule-1"))
            assertEquals(listOf("home:104@1"), db.recordings().ofRule("rule-1").map { it.id })
            assertEquals(3, db.recordingRules().get("rule-1")?.keepLast)
        } finally {
            db.close()
        }
    }

    @Test fun version18KeepsRecordingsAndGainsTheNetworkFolder() = runTest {
        helper.createDatabase(NAME, 18).use { db ->
            db.execSQL(
                "INSERT INTO recordings (id, channelId, channelName, title, kind, startAt, endAt, padBeforeMs, padAfterMs, status, sizeBytes, interruptions, keep, positionMs, watched, watchedAt, createdAt) " +
                    "VALUES ('home:104@1', 'home:104', 'Wildframe', 'Kingdoms of Ice', 'series', 1000, 2000, 60000, 300000, 'done', 188, 0, 0, 0, 0, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 19, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            // A recording from before has no copy, and none coming.
            val recording = db.recordings().get("home:104@1")!!
            assertEquals("none", recording.copyState)
            assertEquals(emptyList<RecordingEntity>(), db.recordings().toCopy())
            db.recordings().setCopy("home:104@1", "waiting", null, null)
            assertEquals(listOf("home:104@1"), db.recordings().toCopy().map { it.id })
            db.recordings().dropCopies()
            assertEquals(emptyList<RecordingEntity>(), db.recordings().toCopy())
            db.networkFolders().upsert(NetworkFolderEntity("main", "nas.home.lan", "Media", "Recordings", null, "maya", "sealed", 3_100_000_000_000, 1))
            assertEquals("nas.home.lan", db.networkFolders().get("main")?.host)
        } finally {
            db.close()
        }
    }

    @Test fun version19KeepsCopiesAndGainsWhenTheyGotThere() = runTest {
        helper.createDatabase(NAME, 19).use { db ->
            db.execSQL(
                "INSERT INTO recordings (id, channelId, channelName, title, kind, startAt, endAt, padBeforeMs, padAfterMs, status, filePath, sizeBytes, interruptions, keep, positionMs, watched, watchedAt, createdAt, copyState, networkPath) " +
                    "VALUES ('home:104@1', 'home:104', 'Wildframe', 'Kingdoms of Ice', 'series', 1000, 2000, 60000, 300000, 'done', '/storage/a.ts', 188, 0, 0, 0, 0, 0, 1, 'copied', 'smb://nas.home.lan/Media/a.ts')",
            )
        }

        helper.runMigrationsAndValidate(NAME, 20, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            // A copy from before doesn't say when it got there; it's on the drive still.
            assertEquals(null, db.recordings().get("home:104@1")?.copiedAt)
            assertEquals(listOf("home:104@1"), db.recordings().copiedOnDrive().map { it.id })
            db.recordings().copied("home:104@1", "smb://nas.home.lan/Media/a.ts", 5)
            assertEquals(5L, db.recordings().get("home:104@1")?.copiedAt)
            db.recordings().forgetFile("home:104@1")
            assertEquals(null, db.recordings().get("home:104@1")?.filePath)
            assertEquals(emptyList<RecordingEntity>(), db.recordings().copiedOnDrive())
        } finally {
            db.close()
        }
    }

    private companion object {
        const val NAME = "migration-test"
    }
}
