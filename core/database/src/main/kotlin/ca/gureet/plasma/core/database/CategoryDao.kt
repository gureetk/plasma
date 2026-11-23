// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A source's categories as the user arranges them, in Settings › Portals & Playlists › the source
 * › Categories (the owner's ask, 1 November): Live TV's groups and the movie and series categories
 * shown or hidden, renamed and put in an order, and the user's own categories with what's in them.
 * A kind is "live", or [VodKinds.MOVIE] or [VodKinds.SERIES].
 */
@Dao
abstract class CategoryDao {
    // ---- What there is ----------------------------------------------------------------------

    @Query("SELECT * FROM channel_groups WHERE sourceId = :sourceId")
    abstract fun observeGroups(sourceId: String): Flow<List<GroupEntity>>

    @Query("SELECT * FROM vod_categories WHERE sourceId = :sourceId AND kind = :kind")
    abstract fun observeVodCategories(sourceId: String, kind: String): Flow<List<VodCategoryEntity>>

    /** How many channels each of [sourceId]'s groups has. */
    @Query("SELECT groupId AS categoryId, COUNT(*) AS count FROM channels WHERE sourceId = :sourceId GROUP BY groupId")
    abstract fun observeChannelCounts(sourceId: String): Flow<List<CategoryCount>>

    /** How many movies each of [sourceId]'s movie categories has. */
    @Query("SELECT categoryId, COUNT(*) AS count FROM movies WHERE sourceId = :sourceId GROUP BY categoryId")
    abstract fun observeMovieCounts(sourceId: String): Flow<List<CategoryCount>>

    /** How many series each of [sourceId]'s series categories has. */
    @Query("SELECT categoryId, COUNT(*) AS count FROM series WHERE sourceId = :sourceId GROUP BY categoryId")
    abstract fun observeSeriesCounts(sourceId: String): Flow<List<CategoryCount>>

    /** How many of their channels, movies or series each of the user's own categories still has. */
    @Query(
        """
        SELECT categoryId, COUNT(*) AS count FROM category_members
        WHERE itemId IN (SELECT id FROM channels) OR itemId IN (SELECT id FROM movies) OR itemId IN (SELECT id FROM series)
        GROUP BY categoryId
        """,
    )
    abstract fun observeMemberCounts(): Flow<List<CategoryCount>>

    /** Everything in the user's own categories, in the order it was put in. */
    @Query("SELECT * FROM category_members ORDER BY addedAt, rowid")
    abstract fun observeMembers(): Flow<List<CategoryMemberEntity>>

    /** What's in the user's own category [categoryId], in the order it was put in. */
    @Query("SELECT * FROM category_members WHERE categoryId = :categoryId ORDER BY addedAt, rowid")
    abstract fun observeMembersOf(categoryId: String): Flow<List<CategoryMemberEntity>>

    // ---- Shown or hidden --------------------------------------------------------------------

    @Query("UPDATE channel_groups SET hidden = :hidden WHERE id = :id")
    abstract suspend fun setGroupHidden(id: String, hidden: Boolean)

    @Query("UPDATE channel_groups SET hidden = :hidden WHERE sourceId = :sourceId")
    abstract suspend fun setGroupsHidden(sourceId: String, hidden: Boolean)

    @Query("UPDATE vod_categories SET hidden = :hidden WHERE id = :id")
    abstract suspend fun setVodHidden(id: String, hidden: Boolean)

    @Query("UPDATE vod_categories SET hidden = :hidden WHERE sourceId = :sourceId AND kind = :kind")
    abstract suspend fun setVodAllHidden(sourceId: String, kind: String, hidden: Boolean)

    // ---- Names ------------------------------------------------------------------------------

    /** The user's [name] for the group [id]; null for the provider's again. */
    @Query("UPDATE channel_groups SET customName = :name WHERE id = :id")
    abstract suspend fun renameGroup(id: String, name: String?)

    @Query("UPDATE vod_categories SET customName = :name WHERE id = :id")
    abstract suspend fun renameVod(id: String, name: String?)

    /** The user's own group's name: it has no provider's to go back to. */
    @Query("UPDATE channel_groups SET name = :name WHERE id = :id AND custom = 1")
    abstract suspend fun renameOwnGroup(id: String, name: String)

    @Query("UPDATE vod_categories SET name = :name WHERE id = :id AND custom = 1")
    abstract suspend fun renameOwnVod(id: String, name: String)

    // ---- The order --------------------------------------------------------------------------

    @Query("UPDATE channel_groups SET userPosition = :position WHERE sourceId = :sourceId AND id = :id")
    protected abstract suspend fun placeGroup(sourceId: String, id: String, position: Int)

    @Query("UPDATE vod_categories SET userPosition = :position WHERE sourceId = :sourceId AND id = :id")
    protected abstract suspend fun placeVod(sourceId: String, id: String, position: Int)

    @Query("UPDATE sources SET liveOrder = :order WHERE id = :sourceId")
    protected abstract suspend fun setLiveOrder(sourceId: String, order: String?)

    @Query("UPDATE sources SET movieOrder = :order WHERE id = :sourceId")
    protected abstract suspend fun setMovieOrder(sourceId: String, order: String?)

    @Query("UPDATE sources SET seriesOrder = :order WHERE id = :sourceId")
    protected abstract suspend fun setSeriesOrder(sourceId: String, order: String?)

    /**
     * [sourceId]'s categories of [kind] show in [order] now (`CategoryOrder`'s id, null for the
     * provider's). [places], when given, are their ids in the user's order, kept with them in the
     * same transaction.
     */
    @Transaction
    open suspend fun order(sourceId: String, kind: String, order: String?, places: List<String>? = null) {
        places?.forEachIndexed { i, id -> if (kind == LIVE) placeGroup(sourceId, id, i) else placeVod(sourceId, id, i) }
        when (kind) {
            LIVE -> setLiveOrder(sourceId, order)
            VodKinds.MOVIE -> setMovieOrder(sourceId, order)
            else -> setSeriesOrder(sourceId, order)
        }
    }

    // ---- The user's own ---------------------------------------------------------------------

    /** The number of [sourceId]'s last group of the user's own; their positions count up from 0. */
    @Query("SELECT MAX(position) FROM channel_groups WHERE sourceId = :sourceId AND custom = 1")
    abstract suspend fun lastOwnGroup(sourceId: String): Int?

    @Query("SELECT MAX(position) FROM vod_categories WHERE sourceId = :sourceId AND kind = :kind AND custom = 1")
    abstract suspend fun lastOwnVod(sourceId: String, kind: String): Int?

    @Insert
    abstract suspend fun insertGroup(group: GroupEntity)

    @Insert
    abstract suspend fun insertVod(category: VodCategoryEntity)

    @Query("DELETE FROM channel_groups WHERE id = :id AND custom = 1")
    protected abstract suspend fun deleteOwnGroup(id: String)

    @Query("DELETE FROM vod_categories WHERE id = :id AND custom = 1")
    protected abstract suspend fun deleteOwnVod(id: String)

    @Query("DELETE FROM category_members WHERE categoryId = :categoryId")
    protected abstract suspend fun deleteMembers(categoryId: String)

    /** The user's own category [id] goes, with what's in it; a provider's can't. */
    @Transaction
    open suspend fun deleteOwn(id: String, kind: String) {
        deleteMembers(id)
        if (kind == LIVE) deleteOwnGroup(id) else deleteOwnVod(id)
    }

    /** Puts [member] in its category; one already there keeps when it went in. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addMember(member: CategoryMemberEntity)

    /** Takes [itemIds] out of [categoryId]: a channel from several sources may be in it under any of their ids. */
    @Query("DELETE FROM category_members WHERE categoryId = :categoryId AND itemId IN (:itemIds)")
    abstract suspend fun removeMembers(categoryId: String, itemIds: List<String>)

    /** Puts [members] in their categories, many at once (Edit in a Browser); one already there keeps when it went in. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addMembers(members: List<CategoryMemberEntity>)

    /** Takes [itemIds] out of [categoryId], many at once (Edit in a Browser), as one change. */
    @Transaction
    open suspend fun removeManyMembers(categoryId: String, itemIds: Collection<String>) =
        itemIds.chunked(MAX_ARGUMENTS).forEach { removeMembers(categoryId, it) }

    companion object {
        /** Live TV's groups, as a kind. */
        const val LIVE = "live"
    }
}
