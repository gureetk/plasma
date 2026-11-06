// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** A channel hidden or locked on its own (brief 4.29 and 5.12), as a backup keeps it. */
data class ChannelFlags(val id: String, val hidden: Boolean, val locked: Boolean)

/**
 * What Restore from Backup puts in place of this TV's own (brief 5.17, #108), all at once. The
 * guide, channels, movies and series aren't here: the sources load them again.
 */
data class RestoredRows(
    val sources: List<SourceEntity> = emptyList(),
    val profiles: List<ProfileEntity> = emptyList(),
    val groups: List<GroupEntity> = emptyList(),
    val vodCategories: List<VodCategoryEntity> = emptyList(),
    val categoryMembers: List<CategoryMemberEntity> = emptyList(),
    val numberOverrides: List<NumberOverrideEntity> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val recents: List<RecentChannelEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val playback: List<ChannelPlaybackEntity> = emptyList(),
    val sourceMemory: List<ChannelSourceMemoryEntity> = emptyList(),
    val resumePoints: List<ResumePointEntity> = emptyList(),
    val myList: List<MyListEntity> = emptyList(),
    val recordingRules: List<RecordingRuleEntity> = emptyList(),
    val scheduledRecordings: List<RecordingEntity> = emptyList(),
    val jellyfin: List<JellyfinAccountEntity> = emptyList(),
    val networkFolders: List<NetworkFolderEntity> = emptyList(),
)

/**
 * Back Up Settings and Restore from Backup (brief 5.17, #108): everything of the user's in the
 * database, read at once for a backup, and replaced at once by one.
 */
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM sources ORDER BY priority")
    abstract suspend fun sources(): List<SourceEntity>

    @Query("SELECT * FROM profiles ORDER BY position")
    abstract suspend fun profiles(): List<ProfileEntity>

    /** Groups the user has arranged (hidden, locked, kid-safe, renamed, moved) or made. */
    @Query("SELECT * FROM channel_groups WHERE custom = 1 OR hidden = 1 OR locked IS NOT NULL OR kidSafe IS NOT NULL OR customName IS NOT NULL OR userPosition IS NOT NULL")
    abstract suspend fun arrangedGroups(): List<GroupEntity>

    /** Movie and series categories the user has arranged (hidden, renamed, moved) or made. */
    @Query("SELECT * FROM vod_categories WHERE custom = 1 OR hidden = 1 OR customName IS NOT NULL OR userPosition IS NOT NULL")
    abstract suspend fun arrangedVodCategories(): List<VodCategoryEntity>

    @Query("SELECT * FROM category_members")
    abstract suspend fun categoryMembers(): List<CategoryMemberEntity>

    /** Channels hidden or locked on their own. */
    @Query("SELECT id, hidden, locked FROM channels WHERE hidden = 1 OR locked = 1")
    abstract suspend fun channelFlags(): List<ChannelFlags>

    @Query("SELECT * FROM number_overrides")
    abstract suspend fun numberOverrides(): List<NumberOverrideEntity>

    @Query("SELECT * FROM favorites ORDER BY profileId, position")
    abstract suspend fun favorites(): List<FavoriteEntity>

    @Query("SELECT * FROM recent_channels")
    abstract suspend fun recents(): List<RecentChannelEntity>

    @Query("SELECT * FROM reminders WHERE startAt > :now")
    abstract suspend fun reminders(now: Long): List<ReminderEntity>

    @Query("SELECT * FROM channel_playback")
    abstract suspend fun playback(): List<ChannelPlaybackEntity>

    /** The sources picked with Switch Source; what Plasma learned of the provider is learned again. */
    @Query("SELECT * FROM channel_source_memory WHERE reason = 'USER'")
    abstract suspend fun chosenSources(): List<ChannelSourceMemoryEntity>

    @Query("SELECT * FROM resume_points")
    abstract suspend fun resumePoints(): List<ResumePointEntity>

    @Query("SELECT * FROM my_list")
    abstract suspend fun myList(): List<MyListEntity>

    @Query("SELECT * FROM recording_rules")
    abstract suspend fun recordingRules(): List<RecordingRuleEntity>

    /** Recordings still to come; those made are files on this TV, or the network folder. */
    @Query("SELECT * FROM recordings WHERE status = 'scheduled' AND startAt > :now")
    abstract suspend fun scheduledRecordings(now: Long): List<RecordingEntity>

    @Query("SELECT * FROM jellyfin_accounts")
    abstract suspend fun jellyfin(): List<JellyfinAccountEntity>

    @Query("SELECT * FROM network_folders")
    abstract suspend fun networkFolders(): List<NetworkFolderEntity>

    @Query("SELECT id FROM channels WHERE sourceId = :sourceId")
    abstract suspend fun channelIds(sourceId: String): List<String>

    @Query("UPDATE channels SET hidden = 1 WHERE id IN (:ids)")
    abstract suspend fun hide(ids: List<String>)

    @Query("UPDATE channels SET locked = 1 WHERE id IN (:ids)")
    abstract suspend fun lock(ids: List<String>)

    /**
     * Puts [rows] in place of everything they replace: every source (and with it its channels,
     * groups, categories, movies and series), the guide, profiles and everything kept for them,
     * and the recordings still to come. Recordings already made stay.
     */
    @Transaction
    open suspend fun replace(rows: RestoredRows) {
        clearSources()
        clearProgrammes()
        clearFavorites()
        clearRecents()
        clearReminders()
        clearNumberOverrides()
        clearSourceMemory()
        clearPlayback()
        clearSearchHistory()
        clearProfiles()
        clearRecordingRules()
        clearScheduledRecordings()
        clearJellyfin()
        clearNetworkFolders()
        insertSources(rows.sources)
        insertProfiles(rows.profiles)
        insertGroups(rows.groups)
        insertVodCategories(rows.vodCategories)
        insertCategoryMembers(rows.categoryMembers)
        insertNumberOverrides(rows.numberOverrides)
        insertFavorites(rows.favorites)
        insertRecents(rows.recents)
        insertReminders(rows.reminders)
        insertPlayback(rows.playback)
        insertSourceMemory(rows.sourceMemory)
        insertResumePoints(rows.resumePoints)
        insertMyList(rows.myList)
        insertRecordingRules(rows.recordingRules)
        insertRecordings(rows.scheduledRecordings)
        insertJellyfin(rows.jellyfin)
        insertNetworkFolders(rows.networkFolders)
    }

    @Query("DELETE FROM sources")
    protected abstract suspend fun clearSources()

    @Query("DELETE FROM programmes")
    protected abstract suspend fun clearProgrammes()

    @Query("DELETE FROM favorites")
    protected abstract suspend fun clearFavorites()

    @Query("DELETE FROM recent_channels")
    protected abstract suspend fun clearRecents()

    @Query("DELETE FROM reminders")
    protected abstract suspend fun clearReminders()

    @Query("DELETE FROM number_overrides")
    protected abstract suspend fun clearNumberOverrides()

    @Query("DELETE FROM channel_source_memory")
    protected abstract suspend fun clearSourceMemory()

    @Query("DELETE FROM channel_playback")
    protected abstract suspend fun clearPlayback()

    @Query("DELETE FROM search_history")
    protected abstract suspend fun clearSearchHistory()

    @Query("DELETE FROM profiles")
    protected abstract suspend fun clearProfiles()

    @Query("DELETE FROM recording_rules")
    protected abstract suspend fun clearRecordingRules()

    @Query("DELETE FROM recordings WHERE status = 'scheduled'")
    protected abstract suspend fun clearScheduledRecordings()

    @Query("DELETE FROM jellyfin_accounts")
    protected abstract suspend fun clearJellyfin()

    @Query("DELETE FROM network_folders")
    protected abstract suspend fun clearNetworkFolders()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSources(rows: List<SourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertProfiles(rows: List<ProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertGroups(rows: List<GroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertVodCategories(rows: List<VodCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCategoryMembers(rows: List<CategoryMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertNumberOverrides(rows: List<NumberOverrideEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertFavorites(rows: List<FavoriteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRecents(rows: List<RecentChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertReminders(rows: List<ReminderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPlayback(rows: List<ChannelPlaybackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSourceMemory(rows: List<ChannelSourceMemoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertResumePoints(rows: List<ResumePointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertMyList(rows: List<MyListEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRecordingRules(rows: List<RecordingRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRecordings(rows: List<RecordingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertJellyfin(rows: List<JellyfinAccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertNetworkFolders(rows: List<NetworkFolderEntity>)
}
