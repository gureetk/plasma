// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Plasma's database (brief 6.3): what's here survives a reboot. The schema of each version is
 * exported to `core/database/schemas` for migrations.
 */
@Database(
    entities = [
        SourceEntity::class,
        GroupEntity::class,
        ChannelEntity::class,
        ProgrammeEntity::class,
        FavoriteEntity::class,
        RecentChannelEntity::class,
        ReminderEntity::class,
        NumberOverrideEntity::class,
        ProgrammeFtsEntity::class,
        SearchHistoryEntity::class,
        ChannelSourceMemoryEntity::class,
        ChannelPlaybackEntity::class,
        VodCategoryEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        MovieFtsEntity::class,
        SeriesFtsEntity::class,
        MovieDetailsEntity::class,
        SeriesDetailsEntity::class,
        EpisodeEntity::class,
        ResumePointEntity::class,
        MyListEntity::class,
        JellyfinAccountEntity::class,
        RecordingEntity::class,
        RecordingRuleEntity::class,
        NetworkFolderEntity::class,
        ProfileEntity::class,
    ],
    // 1: M1, sources and live TV. 2: M2, reminders and search. 3: M3, settings per source. 4: Stalker
    // portals. 5: Remember What Works per Channel. 6: each channel's own engine. 7: the clash a
    // number chosen on Duplicate Numbers settled. 8: each channel's own volume level and output.
    // 9: a source's Advanced details, a portal's time zone and a playlist's refresh interval.
    // 10: the rest of Playback for This Source: engine, stream hub, referrer, headers and udpxy.
    // 11: the tracks and Picture Size picked on a channel. 12: M4, movies and series. 13: their
    // details and episodes, where each was left, and My List. 14: what's removed from Continue Watching.
    // 15: Stalker portals' commands for movies and episodes, and how a portal gives a series' episodes.
    // 16: M5, each profile's Jellyfin sign-in. 17: M6, recordings. 18: Record Series rules.
    // 19–21: local catch-up, the network folder and recordings written straight to it. 22: M7,
    // locked groups and programmes' age ratings. 23: profiles.
    version = 23,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = FillSearchIndex::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 22, to = 23),
    ],
)
abstract class PlasmaDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao

    abstract fun channels(): ChannelDao

    abstract fun groups(): GroupDao

    abstract fun programmes(): ProgrammeDao

    abstract fun favorites(): FavoriteDao

    abstract fun recentChannels(): RecentChannelDao

    abstract fun reminders(): ReminderDao

    abstract fun numberOverrides(): NumberOverrideDao

    abstract fun search(): SearchDao

    abstract fun searchHistory(): SearchHistoryDao

    abstract fun channelSourceMemory(): ChannelSourceMemoryDao

    abstract fun channelPlayback(): ChannelPlaybackDao

    abstract fun vod(): VodDao

    abstract fun resumePoints(): ResumeDao

    abstract fun myList(): MyListDao

    abstract fun jellyfinAccounts(): JellyfinAccountDao

    abstract fun recordings(): RecordingDao

    abstract fun recordingRules(): RecordingRuleDao

    abstract fun networkFolders(): NetworkFolderDao

    abstract fun profiles(): ProfileDao

    companion object {
        const val NAME = "plasma.db"

        fun open(context: Context): PlasmaDatabase =
            Room.databaseBuilder(context.applicationContext, PlasmaDatabase::class.java, NAME).build()
    }
}

/**
 * The search index starts with the guide already there, from M1 (brief 5.10). Room adds the
 * triggers that keep it in step once the migration is done.
 */
class FillSearchIndex : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("INSERT INTO programmes_fts(programmes_fts) VALUES('rebuild')")
    }
}
