// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.editor

import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.core.model.CategoryOrder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What Edit in a Browser reads and changes on the TV: the channels, favorites, categories, hidden
 * channels, numbering and locks, as the app keeps them. The server calls it from its own threads.
 * Ids are the ones the TV's screens show: a channel from several sources is one, under the id it
 * shows under (brief 5.1), and the app finds it under any of its sources' ids.
 */
interface EditorBackend {
    /** Grows whenever anything the page shows changes on the TV, from a browser or not. */
    val revision: Long

    /** The profile watching now: a browser that joins edits its favorites. */
    suspend fun profile(): EditorProfile

    /** The channels, groups and favorites of [profileId], and what's set for them. */
    suspend fun library(profileId: String): EditorLibrary

    /** [sourceId]'s categories of [kind] as its Categories page has them; null for a source it doesn't have. */
    suspend fun categories(sourceId: String, kind: CategoryKind): EditorCategories?

    /** What the user put in one of the user's own movie or series categories. */
    suspend fun titles(kind: CategoryKind, categoryId: String): List<EditorTitle>

    /** [sourceId]'s movies or series whose names have words starting with [query]'s. */
    suspend fun searchTitles(kind: CategoryKind, sourceId: String, query: String): List<EditorTitle>

    suspend fun favorites(profileId: String, change: FavoritesChange)

    /** Hides channels on their own, or shows them again (brief 5.2, 4.29). */
    suspend fun hide(channelIds: List<String>, hidden: Boolean)

    /** Locks channels with the parental PIN, or unlocks them (5.12): only once the browser typed it. */
    suspend fun lock(channelIds: List<String>, locked: Boolean)

    /** Locks a group's channels, or unlocks them, whatever Lock Adult Groups says (5.12). */
    suspend fun lockGroup(groupId: String, locked: Boolean)

    /** Applies a change to categories; for a new one, returns its id. */
    suspend fun category(change: CategoryChange): String?

    /** Numbers in Favorites and Numbers in Groups (4.29): count from 1, or keep channel numbers; null leaves one as it is. */
    suspend fun numbering(favoritesFromOne: Boolean?, groupsFromOne: Boolean?)

    /** Whether [pin] is the parental PIN, counted with the TV's own tries (5.12). */
    suspend fun checkPin(pin: String): PinCheck

    /** A channel's logo, which the TV fetches so the browser never asks the provider; null for none. */
    fun logo(channelId: String): EditorLogo?
}

/** The profile a browser edits the favorites of. */
data class EditorProfile(val id: String, val name: String)

/** A logo's bytes and type. */
class EditorLogo(val bytes: ByteArray, val type: String)

/** What a PIN typed in the browser was. */
sealed interface PinCheck {
    data object Right : PinCheck

    /** [left] more tries before entry stops for a minute. */
    data class Wrong(val left: Int) : PinCheck

    /** Too many wrong tries, on the TV or here: entry opens again in [seconds]. */
    data class Blocked(val seconds: Int) : PinCheck

    /** No PIN is set: parental controls are off. */
    data object Off : PinCheck
}

/** A change to the profile's favorites, by the ids channels show under. */
sealed interface FavoritesChange {
    /** Added at the end, in this order; those already there stay where they are. */
    data class Add(val ids: List<String>) : FavoritesChange

    data class Remove(val ids: List<String>) : FavoritesChange

    /** The favorites in this order; any not named keep their places among them. */
    data class Order(val ids: List<String>) : FavoritesChange
}

/** A change to a source's categories, as its Categories page makes them (#103). */
sealed interface CategoryChange {
    val kind: CategoryKind

    data class Hide(override val kind: CategoryKind, val id: String, val hidden: Boolean) : CategoryChange

    /** Show All or Hide All. */
    data class HideAll(override val kind: CategoryKind, val sourceId: String, val hidden: Boolean) : CategoryChange

    data class Order(override val kind: CategoryKind, val sourceId: String, val order: CategoryOrder) : CategoryChange

    /** The categories in the user's own order, [ids] as they are. */
    data class Move(override val kind: CategoryKind, val sourceId: String, val ids: List<String>) : CategoryChange

    /** A new name; nothing, or the provider's, puts the provider's back. */
    data class Rename(override val kind: CategoryKind, val id: String, val name: String) : CategoryChange

    /** One of the user's own, named [name]. */
    data class Create(override val kind: CategoryKind, val sourceId: String, val name: String) : CategoryChange

    /** One of the user's own goes; what's in it stays where it was. */
    data class Delete(override val kind: CategoryKind, val id: String) : CategoryChange

    /** Channels, movies or series put in one of the user's own, or taken out. */
    data class Members(override val kind: CategoryKind, val id: String, val add: List<String>, val remove: List<String>) : CategoryChange
}

/** Everything the page lists: the TV, the profile, the sources, the groups, the channels and the favorites. */
@Serializable
data class EditorLibrary(
    val rev: Long,
    val tv: String,
    val profile: String,
    /** Parental controls are on: a PIN is set. */
    val pin: Boolean,
    /** This browser typed the PIN, so it can change locks. Filled in by the server. */
    val unlocked: Boolean = false,
    val favoritesFromOne: Boolean,
    val groupsFromOne: Boolean,
    val sources: List<EditorSource>,
    /** Live TV's groups of the sources turned on, in Live TV's order, hidden ones too. */
    val groups: List<EditorGroup>,
    /** Every channel in number order, hidden ones too. */
    val channels: List<EditorChannel>,
    /** The profile's favorites, in its order. */
    val favorites: List<String>,
)

@Serializable
data class EditorSource(
    val id: String,
    val name: String,
    /** "stalker", "xtream" or "m3u". */
    val type: String,
    val enabled: Boolean,
    val channels: Int,
    val movies: Int,
    val series: Int,
)

@Serializable
data class EditorGroup(
    val id: String,
    val source: String,
    val name: String,
    /** The provider's name, where the user gave the group another. */
    val provider: String? = null,
    /** Made by the user: its channels are [members]. */
    val custom: Boolean = false,
    val hidden: Boolean = false,
    /** Its channels need the PIN, because it's locked or, with Lock Adult Groups on, adult. */
    val locked: Boolean = false,
    /** It has an adult word in its name, or the provider marks it adult. */
    val adult: Boolean = false,
    /** A group of the user's own's channels, by the ids they show under. */
    val members: List<String> = emptyList(),
)

@Serializable
data class EditorChannel(
    val id: String,
    @SerialName("n") val number: Int,
    val name: String,
    val group: String? = null,
    val source: String,
    /** Hidden on its own. */
    val hidden: Boolean = false,
    /** Hidden because its group is. */
    val groupHidden: Boolean = false,
    /** Locked on its own. */
    val locked: Boolean = false,
    /** Locked because its group is. */
    val groupLocked: Boolean = false,
    /** It has a logo the TV can fetch. */
    val logo: Boolean = false,
)

/** A source's categories of one kind, as its Categories page has them (#103). */
@Serializable
data class EditorCategories(
    val rev: Long,
    /** "provider", "az" or "mine". */
    val order: String,
    val categories: List<EditorCategory>,
)

@Serializable
data class EditorCategory(
    val id: String,
    val name: String,
    /** The provider's name, where the user gave it another. */
    val provider: String? = null,
    val hidden: Boolean = false,
    val custom: Boolean = false,
    val count: Int = 0,
    /** Live TV's only: its channels need the PIN. */
    val locked: Boolean = false,
    val adult: Boolean = false,
)

/** A movie or a series, as a category of the user's own lists it. */
@Serializable
data class EditorTitle(val id: String, val name: String, val year: Int? = null)

/** How an order is written on the page. */
fun CategoryOrder.wire(): String = when (this) {
    CategoryOrder.Provider -> "provider"
    CategoryOrder.AToZ -> "az"
    CategoryOrder.Mine -> "mine"
}

internal fun orderOf(wire: String?): CategoryOrder? = when (wire) {
    "provider" -> CategoryOrder.Provider
    "az" -> CategoryOrder.AToZ
    "mine" -> CategoryOrder.Mine
    else -> null
}

/** A kind of category as the page writes it: "live", "movie" or "series", as it's kept. */
internal fun kindOf(wire: String?): CategoryKind? = CategoryKind.of(wire)
