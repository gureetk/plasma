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
    ],
    // 1: M1, sources and live TV. 2: M2, reminders and search. 3: M3, settings per source. 4: Stalker
    // portals. 5: Remember What Works per Channel. 6: each channel's own engine. 7: the clash a
    // number chosen on Duplicate Numbers settled.
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = FillSearchIndex::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
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
