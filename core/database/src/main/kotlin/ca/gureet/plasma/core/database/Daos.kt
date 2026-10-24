// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
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

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    /** Playback for This Source (brief 4.20); the guide's time offset moves its programmes too, so it's set with them. */
    @Query(
        """
        UPDATE sources SET streamFormat = :streamFormat, bufferChoice = :bufferChoice, audioOutputChoice = :audioOutputChoice,
            catchupOffsetMinutes = :catchupOffsetMinutes, engineChoice = :engineChoice, useHub = :useHub, userAgent = :userAgent,
            referrer = :referrer, extraHeadersEnc = :extraHeadersEnc, udpxy = :udpxy
        WHERE id = :id
        """,
    )
    suspend fun setPlayback(
        id: String,
        streamFormat: String,
        bufferChoice: Int?,
        audioOutputChoice: Int?,
        catchupOffsetMinutes: Int,
        engineChoice: Int?,
        useHub: Boolean?,
        userAgent: String?,
        referrer: String?,
        extraHeadersEnc: String?,
        udpxy: String?,
    )

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

/** A source's channel count. */
data class SourceChannelCount(val sourceId: String, val channels: Int)

@Dao
abstract class ChannelDao {
    /** Every channel of the sources turned on, in number order. */
    @Query("SELECT c.* FROM channels c JOIN sources s ON s.id = c.sourceId WHERE s.enabled = 1 ORDER BY c.number")
    abstract fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY number")
    abstract suspend fun all(): List<ChannelEntity>

    /** A source's channels in the provider's order, as numbering reads them. */
    @Query("SELECT * FROM channels WHERE sourceId = :sourceId ORDER BY position")
    abstract suspend fun forSource(sourceId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id")
    abstract suspend fun get(id: String): ChannelEntity?

    /**
     * Every channel of the sources turned on, in the order numbering goes through them: the
     * primary source first, each source in the provider's order (brief 5.2).
     */
    @Query("SELECT c.* FROM channels c JOIN sources s ON s.id = c.sourceId WHERE s.enabled = 1 ORDER BY s.priority, s.addedAt, c.position")
    abstract suspend fun inNumberingOrder(): List<ChannelEntity>

    /** The channels of the sources turned on that have [number]. */
    @Query("SELECT c.* FROM channels c JOIN sources s ON s.id = c.sourceId WHERE s.enabled = 1 AND c.number = :number")
    abstract suspend fun withNumber(number: Int): List<ChannelEntity>

    @Query("SELECT COUNT(*) FROM channels")
    abstract fun observeCount(): Flow<Int>

    /** How many channels each source has, turned on or not (Portals & Playlists, brief 4.20). */
    @Query("SELECT sourceId, COUNT(*) AS channels FROM channels GROUP BY sourceId")
    abstract fun observeCountsBySource(): Flow<List<SourceChannelCount>>

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

    @Upsert
    protected abstract suspend fun upsertOverrides(overrides: List<NumberOverrideEntity>)

    @Query("DELETE FROM number_overrides")
    protected abstract suspend fun deleteChoices()

    /**
     * The user's numbers (brief 4.30): kept as overrides, so they survive refreshes, and given to
     * the channels now, in one transaction.
     */
    @Transaction
    open suspend fun choose(overrides: List<NumberOverrideEntity>, numbers: List<NumberUpdate>) {
        upsertOverrides(overrides)
        numbers.forEach { setNumber(it.id, it.number, it.numberReason, it.wantedNumber) }
    }

    /**
     * Gives channels new numbers in one transaction. [forgetChoices] drops the numbers the user
     * chose, when every channel is numbered afresh (brief 5.2).
     */
    @Transaction
    open suspend fun renumber(numbers: List<NumberUpdate>, forgetChoices: Boolean = false) {
        if (forgetChoices) deleteChoices()
        numbers.forEach { setNumber(it.id, it.number, it.numberReason, it.wantedNumber) }
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
    /** Every group of the sources turned on, the primary source's first, each in the provider's order. */
    @Query("SELECT g.* FROM channel_groups g JOIN sources s ON s.id = g.sourceId WHERE s.enabled = 1 ORDER BY s.priority, g.position")
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

    /**
     * A new time offset for a source's guide (brief 5.16): its programmes move by the difference
     * at once, so the guide needn't be downloaded again. Imports read the new offset from then on.
     */
    @Transaction
    open suspend fun setGuideOffset(sourceId: String, minutes: Int) {
        val before = guideOffsetOf(sourceId) ?: return
        if (before == minutes) return
        writeGuideOffset(sourceId, minutes)
        shift(sourceId, (minutes - before) * MINUTE_MS)
    }

    @Query("SELECT epgOffsetMinutes FROM sources WHERE id = :sourceId")
    protected abstract suspend fun guideOffsetOf(sourceId: String): Int?

    @Query("UPDATE sources SET epgOffsetMinutes = :minutes WHERE id = :sourceId")
    protected abstract suspend fun writeGuideOffset(sourceId: String, minutes: Int)

    @Query("UPDATE programmes SET startAt = startAt + :deltaMs, endAt = endAt + :deltaMs WHERE sourceId = :sourceId")
    protected abstract suspend fun shift(sourceId: String, deltaMs: Long)

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

    /** A guide that lists a programme twice keeps the first: one programme per channel and start. */
    @Query(
        """
        DELETE FROM programmes WHERE sourceId = :sourceId AND generation = :generation AND rowId NOT IN
        (SELECT MIN(rowId) FROM programmes WHERE sourceId = :sourceId AND generation = :generation GROUP BY epgKey, startAt)
        """,
    )
    protected abstract suspend fun deleteDuplicates(sourceId: String, generation: Long)

    /** Shows the guide import [generation] just finished, in place of the one before, in one step. */
    @Transaction
    open suspend fun commit(sourceId: String, generation: Long, at: Long) {
        deleteDuplicates(sourceId, generation)
        markGuide(sourceId, generation, at)
        deleteOtherGenerations(sourceId, generation)
    }

    @Query("SELECT COUNT(*) FROM programmes WHERE sourceId = :sourceId")
    abstract suspend fun countForSource(sourceId: String): Int
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE profileId = :profileId ORDER BY startAt")
    fun observe(profileId: String): Flow<List<ReminderEntity>>

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun remove(id: String)

    /** Reminders whose programme has started: done, or missed while Plasma was away. */
    @Query("DELETE FROM reminders WHERE startAt < :before")
    suspend fun removeStartedBefore(before: Long)

    @Query("DELETE FROM reminders WHERE substr(channelId, 1, length(:sourceId) + 1) = :sourceId || ':'")
    suspend fun removeForSource(sourceId: String)
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

    /** Removes a channel's favorite kept under any of [channelIds]: a merged channel's ids on each of its sources (5.1). */
    @Query("DELETE FROM favorites WHERE profileId = :profileId AND channelId IN (:channelIds)")
    abstract suspend fun removeAll(profileId: String, channelIds: List<String>)

    /** Removes the favorites of a source's channels, whose ids start with "sourceId:". */
    @Query("DELETE FROM favorites WHERE channelId LIKE :sourceId || ':%'")
    abstract suspend fun removeForSource(sourceId: String)

    /**
     * Adds [channelId] at the end of the profile's favorites, unless the channel is one already:
     * kept under its own id or one of [sameChannel], a merged channel's ids on its other sources (5.1).
     */
    @Transaction
    open suspend fun add(profileId: String, channelId: String, sameChannel: Set<String>) {
        val current = list(profileId)
        if (current.any { it.channelId == channelId || it.channelId in sameChannel }) return
        upsertAll(listOf(FavoriteEntity(profileId, channelId, (current.maxOfOrNull { it.position } ?: 0) + 1)))
    }

    /**
     * Menu › Move (brief 5.2): the favorite kept under any of [channel] comes right after the first
     * one kept under any of [after], or first when that's null. A merged channel may be kept under
     * the ids of several of its sources (5.1), and they move together.
     */
    @Transaction
    open suspend fun move(profileId: String, channel: Set<String>, after: Set<String>?) {
        val ids = list(profileId).map { it.channelId }
        val carried = ids.filter { it in channel }
        val rest = ids.filter { it !in channel }
        val at = if (after == null) 0 else rest.indexOfFirst { it in after } + 1
        if (carried.isEmpty() || at == 0 && after != null) return
        upsertAll((rest.take(at) + carried + rest.drop(at)).mapIndexed { i, id -> FavoriteEntity(profileId, id, i + 1) })
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

@Dao
interface SearchDao {
    /**
     * Programs in the guides in use that match [match], an FTS4 query, ending after [from] and
     * starting before [to]: what's on at [now] first, then the nearest to it. Only programs a
     * channel can show count: one that's neither hidden nor locked, and for a program that's over,
     * one whose archive still has it.
     */
    @Query(
        """
        SELECT p.* FROM programmes_fts
        JOIN programmes p ON p.rowId = programmes_fts.rowid
        JOIN sources s ON p.sourceId = s.id
        WHERE programmes_fts MATCH :match AND p.generation = s.guideGeneration AND p.endAt > :from AND p.startAt < :to
          AND EXISTS (
            SELECT 1 FROM channels c JOIN sources cs ON cs.id = c.sourceId
            WHERE c.epgKey = p.epgKey AND c.hidden = 0 AND c.locked = 0 AND cs.enabled = 1
              AND (p.endAt > :now OR (c.catchupDays > 0 AND p.startAt >= :now - c.catchupDays * 86400000))
          )
        ORDER BY (p.startAt <= :now AND p.endAt > :now) DESC, abs(p.startAt - :now)
        LIMIT :limit
        """,
    )
    suspend fun programmes(match: String, from: Long, to: Long, now: Long, limit: Int): List<ProgrammeEntity>
}

@Dao
abstract class SearchHistoryDao {
    /** The latest searches first. */
    @Query("SELECT text FROM search_history WHERE profileId = :profileId ORDER BY at DESC LIMIT :limit")
    abstract fun observe(profileId: String, limit: Int): Flow<List<String>>

    @Upsert
    abstract suspend fun upsert(entry: SearchHistoryEntity)

    @Query(
        """
        DELETE FROM search_history WHERE profileId = :profileId AND text NOT IN
        (SELECT text FROM search_history WHERE profileId = :profileId ORDER BY at DESC LIMIT :keep)
        """,
    )
    abstract suspend fun trim(profileId: String, keep: Int)

    /** Adds [entry], keeping the latest [keep] of its profile's searches. */
    @Transaction
    open suspend fun add(entry: SearchHistoryEntity, keep: Int) {
        upsert(entry)
        trim(entry.profileId, keep)
    }
}

/** Remember What Works per Channel (brief 5.1). */
@Dao
interface ChannelSourceMemoryDao {
    /** What's kept for a merged channel, under any of its candidates' ids. */
    @Query("SELECT * FROM channel_source_memory WHERE mergedChannelId IN (:ids)")
    suspend fun forAny(ids: List<String>): List<ChannelSourceMemoryEntity>

    @Query("SELECT * FROM channel_source_memory WHERE mergedChannelId IN (:ids)")
    fun observeAny(ids: List<String>): Flow<List<ChannelSourceMemoryEntity>>

    /** Every channel that remembers a source (Live TV's Switch Source, brief 4.35). */
    @Query("SELECT * FROM channel_source_memory WHERE preferredChannelId IS NOT NULL")
    fun observeRemembered(): Flow<List<ChannelSourceMemoryEntity>>

    @Upsert
    suspend fun upsert(memory: ChannelSourceMemoryEntity)

    @Query("DELETE FROM channel_source_memory WHERE mergedChannelId IN (:ids)")
    suspend fun delete(ids: List<String>)

    /** A source removed: what's kept about any channel it was part of goes too (brief 5.1). */
    @Query("DELETE FROM channel_source_memory WHERE instr(mergedChannelId, :prefix) = 1 OR instr(preferredChannelId, :prefix) = 1")
    suspend fun deleteForSource(prefix: String)
}

/** How each channel plays (brief 6.3): for now, the engine it has of its own (5.4.3). */
@Dao
interface ChannelPlaybackDao {
    @Query("SELECT * FROM channel_playback WHERE channelId = :channelId")
    suspend fun get(channelId: String): ChannelPlaybackEntity?

    @Upsert
    suspend fun upsert(playback: ChannelPlaybackEntity)

    /** Forget Players Chosen per Channel (5.4.3). Returns how many channels had one. */
    @Query("UPDATE channel_playback SET engine = NULL, engineReason = NULL, engineSetAt = NULL WHERE engine IS NOT NULL")
    suspend fun forgetEngines(): Int

    /** The channels with a sound of their own: a volume level, or decoded (5.4.6). */
    @Query("SELECT * FROM channel_playback WHERE volumeOffsetDb != 0 OR audioOutput IS NOT NULL ORDER BY channelId")
    fun observeSounds(): Flow<List<ChannelPlaybackEntity>>

    /** [channelId] sounds as the others do again: 0 dB, and its source's Audio Output. */
    @Query("UPDATE channel_playback SET volumeOffsetDb = 0, audioOutput = NULL WHERE channelId = :channelId")
    suspend fun resetSound(channelId: String)

    /** Reset All: returns how many channels had a sound of their own. */
    @Query("UPDATE channel_playback SET volumeOffsetDb = 0, audioOutput = NULL WHERE volumeOffsetDb != 0 OR audioOutput IS NOT NULL")
    suspend fun resetSounds(): Int

    /** A source removed: its channels' settings go with it. */
    @Query("DELETE FROM channel_playback WHERE instr(channelId, :prefix) = 1")
    suspend fun deleteForSource(prefix: String)

    /** The channels with a Picture Size of their own (4.10). */
    @Query("SELECT * FROM channel_playback WHERE pictureSize IS NOT NULL")
    fun observePictureSizes(): Flow<List<ChannelPlaybackEntity>>
}

private const val MINUTE_MS = 60_000L

/**
 * Each source's movies and series (brief 5.9). An import upserts what it reads, a thousand at a
 * time, then [commit] removes what it didn't see: titles never go missing while one runs, and a
 * failed import leaves the catalog as it was (6.4). Only sources that are turned on are read.
 */
@Dao
abstract class VodDao {
    @Upsert
    abstract suspend fun upsertCategories(categories: List<VodCategoryEntity>)

    @Upsert
    abstract suspend fun upsertMovies(movies: List<MovieEntity>)

    @Upsert
    abstract suspend fun upsertSeries(series: List<SeriesEntity>)

    @Query("SELECT MAX(generation) FROM vod_categories WHERE sourceId = :sourceId")
    abstract suspend fun maxGeneration(sourceId: String): Long?

    @Query("DELETE FROM vod_categories WHERE sourceId = :sourceId AND generation != :generation")
    protected abstract suspend fun deleteOldCategories(sourceId: String, generation: Long)

    @Query("DELETE FROM movies WHERE sourceId = :sourceId AND generation != :generation")
    protected abstract suspend fun deleteOldMovies(sourceId: String, generation: Long)

    @Query("DELETE FROM series WHERE sourceId = :sourceId AND generation != :generation")
    protected abstract suspend fun deleteOldSeries(sourceId: String, generation: Long)

    /** The catalog was read at [at], whether or not it changed anything. */
    @Query("UPDATE sources SET vodRefreshedAt = :at, lastError = NULL WHERE id = :sourceId")
    abstract suspend fun markRefreshed(sourceId: String, at: Long)

    /** The import [generation] read the whole catalog: what it didn't see goes. */
    @Transaction
    open suspend fun commit(sourceId: String, generation: Long, at: Long) {
        deleteOldCategories(sourceId, generation)
        deleteOldMovies(sourceId, generation)
        deleteOldSeries(sourceId, generation)
        markRefreshed(sourceId, at)
    }

    @Query(
        """
        SELECT c.* FROM vod_categories c JOIN sources s ON s.id = c.sourceId
        WHERE s.enabled = 1 AND c.kind = :kind ORDER BY s.priority, s.addedAt, c.position
        """,
    )
    abstract fun observeCategories(kind: String): Flow<List<VodCategoryEntity>>

    @Query("SELECT m.categoryId AS categoryId, COUNT(*) AS count FROM movies m JOIN sources s ON s.id = m.sourceId WHERE s.enabled = 1 GROUP BY m.categoryId")
    abstract fun observeMovieCounts(): Flow<List<CategoryCount>>

    @Query("SELECT t.categoryId AS categoryId, COUNT(*) AS count FROM series t JOIN sources s ON s.id = t.sourceId WHERE s.enabled = 1 GROUP BY t.categoryId")
    abstract fun observeSeriesCounts(): Flow<List<CategoryCount>>

    @Query("SELECT m.* FROM movies m JOIN sources s ON s.id = m.sourceId WHERE s.enabled = 1 AND m.categoryId = :categoryId ORDER BY m.position")
    abstract fun observeMoviesIn(categoryId: String): Flow<List<MovieEntity>>

    @Query("SELECT t.* FROM series t JOIN sources s ON s.id = t.sourceId WHERE s.enabled = 1 AND t.categoryId = :categoryId ORDER BY t.position")
    abstract fun observeSeriesIn(categoryId: String): Flow<List<SeriesEntity>>

    /** Movies added since [since], newest first, at most [limit]: Recently Added (4.13). */
    @Query(
        """
        SELECT m.* FROM movies m JOIN sources s ON s.id = m.sourceId
        WHERE s.enabled = 1 AND m.addedAt >= :since ORDER BY m.addedAt DESC LIMIT :limit
        """,
    )
    abstract fun observeRecentMovies(since: Long, limit: Int): Flow<List<MovieEntity>>

    /** Series with an episode added since [since], latest first, at most [limit]: New Episodes (4.15). */
    @Query(
        """
        SELECT t.* FROM series t JOIN sources s ON s.id = t.sourceId
        WHERE s.enabled = 1 AND t.updatedAt >= :since ORDER BY t.updatedAt DESC LIMIT :limit
        """,
    )
    abstract fun observeRecentSeries(since: Long, limit: Int): Flow<List<SeriesEntity>>

    /** Movies whose name matches [match], an FTS4 query (Search, brief 5.10). */
    @Query(
        """
        SELECT m.* FROM movies_fts JOIN movies m ON m.rowId = movies_fts.rowid JOIN sources s ON s.id = m.sourceId
        WHERE movies_fts MATCH :match AND s.enabled = 1 LIMIT :limit
        """,
    )
    abstract suspend fun searchMovies(match: String, limit: Int): List<MovieEntity>

    /** Series whose name matches [match], an FTS4 query. */
    @Query(
        """
        SELECT t.* FROM series_fts JOIN series t ON t.rowId = series_fts.rowid JOIN sources s ON s.id = t.sourceId
        WHERE series_fts MATCH :match AND s.enabled = 1 LIMIT :limit
        """,
    )
    abstract suspend fun searchSeries(match: String, limit: Int): List<SeriesEntity>

    @Query("SELECT * FROM movies WHERE id = :id")
    abstract suspend fun movie(id: String): MovieEntity?

    @Query("SELECT * FROM series WHERE id = :id")
    abstract suspend fun series(id: String): SeriesEntity?

    @Query("SELECT COUNT(*) FROM movies WHERE sourceId = :sourceId")
    abstract suspend fun movieCount(sourceId: String): Int

    @Query("SELECT COUNT(*) FROM series WHERE sourceId = :sourceId")
    abstract suspend fun seriesCount(sourceId: String): Int

    @Query("SELECT * FROM vod_categories WHERE id = :id")
    abstract suspend fun category(id: String): VodCategoryEntity?

    @Query("SELECT * FROM movies WHERE id = :id")
    abstract fun observeMovie(id: String): Flow<MovieEntity?>

    @Query("SELECT * FROM series WHERE id = :id")
    abstract fun observeSeries(id: String): Flow<SeriesEntity?>

    /** Up to [limit] other movies of [categoryId], in the provider's order: More Like This (4.14). */
    @Query("SELECT * FROM movies WHERE categoryId = :categoryId AND id != :movieId ORDER BY position LIMIT :limit")
    abstract fun observeMoviesBeside(categoryId: String, movieId: String, limit: Int): Flow<List<MovieEntity>>

    @Upsert
    abstract suspend fun upsertMovieDetails(details: MovieDetailsEntity)

    /** A movie's file, as its details name it; only that, so an import running meanwhile keeps its own. */
    @Query("UPDATE movies SET extension = :extension WHERE id = :id")
    abstract suspend fun setExtension(id: String, extension: String)

    @Query("SELECT * FROM movie_details WHERE movieId = :movieId")
    abstract suspend fun movieDetails(movieId: String): MovieDetailsEntity?

    @Query("SELECT * FROM movie_details WHERE movieId = :movieId")
    abstract fun observeMovieDetails(movieId: String): Flow<MovieDetailsEntity?>

    @Query("SELECT * FROM series_details WHERE seriesId = :seriesId")
    abstract suspend fun seriesDetails(seriesId: String): SeriesDetailsEntity?

    /** A series' details with its episodes, read together, so one never shows without the other. */
    @Transaction
    @Query("SELECT * FROM series_details WHERE seriesId = :seriesId")
    abstract fun observeSeriesRead(seriesId: String): Flow<SeriesRead?>

    @Upsert
    protected abstract suspend fun upsertSeriesDetails(details: SeriesDetailsEntity)

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    protected abstract suspend fun deleteEpisodes(seriesId: String)

    @Insert
    protected abstract suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    /** A series as its provider just described it: its episodes are these now. */
    @Transaction
    open suspend fun replaceEpisodes(details: SeriesDetailsEntity, episodes: List<EpisodeEntity>) {
        deleteEpisodes(details.seriesId)
        insertEpisodes(episodes)
        upsertSeriesDetails(details)
    }

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY position")
    abstract suspend fun episodes(seriesId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :id")
    abstract suspend fun episode(id: String): EpisodeEntity?
}

/** A series as its provider last described it: what it said, and the episodes. */
data class SeriesRead(
    @Embedded val details: SeriesDetailsEntity,
    @Relation(parentColumn = "seriesId", entityColumn = "seriesId") val episodes: List<EpisodeEntity>,
)

/** Where each profile left movies and episodes, and which are watched (brief 5.9). */
@Dao
abstract class ResumeDao {
    @Upsert
    abstract suspend fun upsert(points: List<ResumePointEntity>)

    @Query("DELETE FROM resume_points WHERE profileId = :profileId AND kind = :kind AND contentId IN (:contentIds)")
    abstract suspend fun remove(profileId: String, kind: String, contentIds: List<String>)

    @Query("SELECT * FROM resume_points WHERE profileId = :profileId AND kind = :kind AND contentId IN (:contentIds)")
    abstract suspend fun points(profileId: String, kind: String, contentIds: List<String>): List<ResumePointEntity>

    @Query("SELECT * FROM resume_points WHERE profileId = :profileId AND kind = :kind AND contentId = :contentId")
    abstract fun observe(profileId: String, kind: String, contentId: String): Flow<ResumePointEntity?>

    /** Every movie, or every episode, the profile has started or marked. */
    @Query("SELECT * FROM resume_points WHERE profileId = :profileId AND kind = :kind")
    abstract fun observeAll(profileId: String, kind: String): Flow<List<ResumePointEntity>>

    @Query("SELECT * FROM resume_points WHERE profileId = :profileId AND kind = 'episode' AND seriesId = :seriesId")
    abstract fun observeSeries(profileId: String, seriesId: String): Flow<List<ResumePointEntity>>

    /** The series whose every episode, as last read, the profile has watched. */
    @Query(
        """
        SELECT e.seriesId FROM episodes e
        LEFT JOIN resume_points r ON r.profileId = :profileId AND r.kind = 'episode' AND r.contentId = e.id
        GROUP BY e.seriesId HAVING SUM(CASE WHEN r.watched = 1 THEN 1 ELSE 0 END) = COUNT(*)
        """,
    )
    abstract fun observeWatchedSeries(profileId: String): Flow<List<String>>
}

/** Each profile's My List (brief 5.9). */
@Dao
abstract class MyListDao {
    @Upsert
    abstract suspend fun add(entry: MyListEntity)

    @Query("DELETE FROM my_list WHERE profileId = :profileId AND kind = :kind AND contentId = :contentId")
    abstract suspend fun remove(profileId: String, kind: String, contentId: String)

    @Query("SELECT contentId FROM my_list WHERE profileId = :profileId AND kind = :kind")
    abstract fun observeIds(profileId: String, kind: String): Flow<List<String>>

    /** The profile's movies on My List, the latest added first, from the sources that are on. */
    @Query(
        """
        SELECT m.* FROM my_list l JOIN movies m ON m.id = l.contentId JOIN sources s ON s.id = m.sourceId
        WHERE l.profileId = :profileId AND l.kind = 'movie' AND s.enabled = 1 ORDER BY l.addedAt DESC
        """,
    )
    abstract fun observeMovies(profileId: String): Flow<List<MovieEntity>>

    /** The profile's series on My List, the latest added first. */
    @Query(
        """
        SELECT t.* FROM my_list l JOIN series t ON t.id = l.contentId JOIN sources s ON s.id = t.sourceId
        WHERE l.profileId = :profileId AND l.kind = 'series' AND s.enabled = 1 ORDER BY l.addedAt DESC
        """,
    )
    abstract fun observeSeries(profileId: String): Flow<List<SeriesEntity>>
}
