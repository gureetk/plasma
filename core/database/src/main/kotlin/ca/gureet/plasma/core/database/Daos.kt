// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY priority, addedAt")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY priority, addedAt")
    suspend fun all(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun get(id: String): SourceEntity?

    @Upsert
    suspend fun upsert(source: SourceEntity)

    // Imports change a source a field or two at a time, so a channel refresh and a guide import
    // running together never write back each other's old copy.

    @Query("UPDATE sources SET channelsRefreshedAt = :at, lastError = NULL WHERE id = :id")
    suspend fun markChannelsRefreshed(id: String, at: Long)

    @Query("UPDATE sources SET lastError = :error WHERE id = :id")
    suspend fun markError(id: String, error: String)

    @Query(
        """
        UPDATE sources SET status = :status, expiresAt = :expiresAt, maxConnections = :maxConnections,
            activeConnections = :activeConnections, outputFormats = :outputFormats, serverTimeZone = :serverTimeZone
        WHERE id = :id
        """,
    )
    suspend fun markAccount(
        id: String,
        status: String?,
        expiresAt: Long?,
        maxConnections: Int?,
        activeConnections: Int?,
        outputFormats: String,
        serverTimeZone: String?,
    )

    @Query("UPDATE sources SET playlistGuideUrlsEnc = :sealed WHERE id = :id")
    suspend fun markPlaylistGuides(id: String, sealed: String?)

    /** Its groups and channels go with it (foreign keys); the guide and favorites are removed by the caller. */
    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: String)
}

/** A channel's new number, after numbering ran again for every source. */
data class NumberUpdate(val id: String, val number: Int, val numberReason: String, val wantedNumber: Int?)

@Dao
abstract class ChannelDao {
    /** Every channel, in number order. */
    @Query("SELECT * FROM channels ORDER BY number")
    abstract fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY number")
    abstract suspend fun all(): List<ChannelEntity>

    /** A source's channels in the provider's order, as numbering reads them. */
    @Query("SELECT * FROM channels WHERE sourceId = :sourceId ORDER BY position")
    abstract suspend fun forSource(sourceId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id")
    abstract suspend fun get(id: String): ChannelEntity?

    @Query("SELECT COUNT(*) FROM channels")
    abstract fun observeCount(): Flow<Int>

    @Upsert
    protected abstract suspend fun upsertGroups(groups: List<GroupEntity>)

    @Upsert
    protected abstract suspend fun upsertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channel_groups WHERE sourceId = :sourceId AND generation != :generation")
    protected abstract suspend fun deleteOlderGroups(sourceId: String, generation: Long)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId AND generation != :generation")
    protected abstract suspend fun deleteOlderChannels(sourceId: String, generation: Long)

    @Query("UPDATE channels SET number = :number, numberReason = :numberReason, wantedNumber = :wantedNumber WHERE id = :id")
    protected abstract suspend fun setNumber(id: String, number: Int, numberReason: String, wantedNumber: Int?)

    /**
     * Swaps in a source's new channels and groups in one transaction, so a failed import never
     * leaves a list half-written (brief 5.1), and renumbers other sources' channels where needed.
     * Hidden and locked flags are the caller's to carry over.
     */
    @Transaction
    open suspend fun replaceSource(
        sourceId: String,
        generation: Long,
        groups: List<GroupEntity>,
        channels: List<ChannelEntity>,
        renumbered: List<NumberUpdate> = emptyList(),
    ) {
        upsertGroups(groups)
        upsertChannels(channels)
        deleteOlderChannels(sourceId, generation)
        deleteOlderGroups(sourceId, generation)
        renumbered.forEach { setNumber(it.id, it.number, it.numberReason, it.wantedNumber) }
    }

    @Query("UPDATE channels SET epgKey = NULL WHERE sourceId = :sourceId")
    protected abstract suspend fun clearEpgKeys(sourceId: String)

    @Query("UPDATE channels SET epgKey = :epgKey WHERE id = :id")
    protected abstract suspend fun setEpgKey(id: String, epgKey: String)

    /** Points a source's channels at the guide channels they were matched to (brief 5.3). */
    @Transaction
    open suspend fun setEpgKeys(sourceId: String, keys: Map<String, String>) {
        clearEpgKeys(sourceId)
        keys.forEach { (channelId, epgKey) -> setEpgKey(channelId, epgKey) }
    }

    @Query("UPDATE channels SET hidden = :hidden WHERE id = :id")
    abstract suspend fun setHidden(id: String, hidden: Boolean)

    @Query("UPDATE channels SET locked = :locked WHERE id = :id")
    abstract suspend fun setLocked(id: String, locked: Boolean)
}

@Dao
interface GroupDao {
    /** Every group, the primary source's first, each in the provider's order. */
    @Query("SELECT g.* FROM channel_groups g JOIN sources s ON s.id = g.sourceId ORDER BY s.priority, g.position")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM channel_groups WHERE sourceId = :sourceId")
    suspend fun forSource(sourceId: String): List<GroupEntity>

    @Query("UPDATE channel_groups SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)
}

@Dao
abstract class ProgrammeDao {
    @Insert
    abstract suspend fun insertAll(programmes: List<ProgrammeEntity>)

    /** For the guide importer, which reads a stream on a background thread and writes as it goes. */
    @Insert
    abstract fun insertAllBlocking(programmes: List<ProgrammeEntity>)

    @Query("SELECT MAX(generation) FROM programmes WHERE sourceId = :sourceId")
    abstract suspend fun maxGeneration(sourceId: String): Long?

    /** Removes a source's guide except the import [generation] just wrote. */
    @Query("DELETE FROM programmes WHERE sourceId = :sourceId AND generation != :generation")
    abstract suspend fun deleteOtherGenerations(sourceId: String, generation: Long)

    /** Removes what a failed import wrote, leaving the guide as it was. */
    @Query("DELETE FROM programmes WHERE sourceId = :sourceId AND generation = :generation")
    abstract suspend fun deleteGeneration(sourceId: String, generation: Long)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId")
    abstract suspend fun deleteForSource(sourceId: String)

    /** Drops what ended before [before] (pruning, brief 5.3). */
    @Query("DELETE FROM programmes WHERE endAt < :before")
    abstract suspend fun prune(before: Long)

    /**
     * Everything on between [from] and [to], for every channel: each source's complete guide only,
     * never one that's still being written.
     */
    @Query(
        """
        SELECT p.* FROM programmes p JOIN sources s ON p.sourceId = s.id
        WHERE p.generation = s.guideGeneration AND p.startAt < :to AND p.endAt > :from
        ORDER BY p.epgKey, p.startAt
        """,
    )
    abstract fun observeWindow(from: Long, to: Long): Flow<List<ProgrammeEntity>>

    @Query(
        """
        SELECT p.* FROM programmes p JOIN sources s ON p.sourceId = s.id
        WHERE p.generation = s.guideGeneration AND p.epgKey = :epgKey AND p.startAt < :to AND p.endAt > :from
        ORDER BY p.startAt
        """,
    )
    abstract suspend fun forKey(epgKey: String, from: Long, to: Long): List<ProgrammeEntity>

    /** When the last programme of every complete guide ends: how far ahead the guide goes. */
    @Query("SELECT MAX(p.endAt) FROM programmes p JOIN sources s ON p.sourceId = s.id WHERE p.generation = s.guideGeneration")
    abstract fun observeLatestEnd(): Flow<Long?>

    @Query("UPDATE sources SET guideGeneration = :generation, guideRefreshedAt = :at, lastError = NULL WHERE id = :sourceId")
    protected abstract suspend fun markGuide(sourceId: String, generation: Long, at: Long)

    /** Shows the guide import [generation] just finished, in place of the one before, in one step. */
    @Transaction
    open suspend fun commit(sourceId: String, generation: Long, at: Long) {
        markGuide(sourceId, generation, at)
        deleteOtherGenerations(sourceId, generation)
    }

    @Query("SELECT COUNT(*) FROM programmes WHERE sourceId = :sourceId")
    abstract suspend fun countForSource(sourceId: String): Int
}

@Dao
abstract class FavoriteDao {
    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY position")
    abstract fun observe(profileId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY position")
    abstract suspend fun list(profileId: String): List<FavoriteEntity>

    @Upsert
    protected abstract suspend fun upsertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND channelId = :channelId")
    abstract suspend fun remove(profileId: String, channelId: String)

    /** Removes the favorites of a source's channels, whose ids start with "sourceId:". */
    @Query("DELETE FROM favorites WHERE channelId LIKE :sourceId || ':%'")
    abstract suspend fun removeForSource(sourceId: String)

    /** Adds [channelId] at the end of the profile's favorites. */
    @Transaction
    open suspend fun add(profileId: String, channelId: String) {
        val current = list(profileId)
        if (current.any { it.channelId == channelId }) return
        upsertAll(listOf(FavoriteEntity(profileId, channelId, (current.maxOfOrNull { it.position } ?: 0) + 1)))
    }

    /** Moves [channelId] to [index] in the profile's order (Menu › Move, brief 5.2). */
    @Transaction
    open suspend fun move(profileId: String, channelId: String, index: Int) {
        val ids = list(profileId).map { it.channelId }.toMutableList()
        if (!ids.remove(channelId)) return
        ids.add(index.coerceIn(0, ids.size), channelId)
        upsertAll(ids.mapIndexed { i, id -> FavoriteEntity(profileId, id, i + 1) })
    }
}

@Dao
abstract class RecentChannelDao {
    @Query("SELECT * FROM recent_channels WHERE profileId = :profileId ORDER BY watchedAt DESC LIMIT :limit")
    abstract fun observe(profileId: String, limit: Int = RECENT_LIMIT): Flow<List<RecentChannelEntity>>

    @Upsert
    protected abstract suspend fun upsert(recent: RecentChannelEntity)

    @Query(
        """
        DELETE FROM recent_channels WHERE profileId = :profileId AND channelId NOT IN
            (SELECT channelId FROM recent_channels WHERE profileId = :profileId ORDER BY watchedAt DESC LIMIT :keep)
        """,
    )
    protected abstract suspend fun trim(profileId: String, keep: Int)

    @Query("DELETE FROM recent_channels WHERE channelId LIKE :sourceId || ':%'")
    abstract suspend fun removeForSource(sourceId: String)

    /** Records a channel as watched, keeping the last [RECENT_LIMIT] (brief 5.2). */
    @Transaction
    open suspend fun watched(profileId: String, channelId: String, at: Long) {
        upsert(RecentChannelEntity(profileId, channelId, at))
        trim(profileId, RECENT_LIMIT)
    }

    companion object {
        const val RECENT_LIMIT = 50
    }
}

@Dao
interface NumberOverrideDao {
    @Query("SELECT * FROM number_overrides")
    suspend fun all(): List<NumberOverrideEntity>

    @Upsert
    suspend fun upsert(override: NumberOverrideEntity)

    @Query("DELETE FROM number_overrides WHERE sourceId = :sourceId AND channelKey = :channelKey")
    suspend fun delete(sourceId: String, channelKey: String)

    @Query("DELETE FROM number_overrides WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: String)
}
