// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    ],
    // 1: M1, sources and live TV. 2: M2, reminders.
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
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

    companion object {
        const val NAME = "plasma.db"

        fun open(context: Context): PlasmaDatabase =
            Room.databaseBuilder(context.applicationContext, PlasmaDatabase::class.java, NAME).build()
    }
}
