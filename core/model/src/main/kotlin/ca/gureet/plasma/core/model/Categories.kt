// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import java.text.Collator
import java.util.Locale

/**
 * The kinds of category a source has (the owner's ask, 1 November): Live TV's groups, and its
 * movie and series categories. Each source's of each kind can be shown or hidden, put in order,
 * renamed, and joined by categories of the user's own, in Settings › Portals & Playlists › the
 * source › Categories.
 */
enum class CategoryKind(
    /** How it's kept, in the database and in Settings' routes: never changes. */
    val id: String,
) {
    Live("live"),
    Movies("movie"),
    Series("series"),
    ;

    companion object {
        fun of(id: String?): CategoryKind? = entries.firstOrNull { it.id == id }
    }
}

/** The order a source's categories of one kind show in. */
enum class CategoryOrder(
    /** How it's kept with the source: never changes. Null is the provider's. */
    val id: String?,
) {
    /** As the provider lists them, the user's own categories first. */
    Provider(null),

    /** By name, A to Z, numbers in a name counting as numbers. */
    AToZ("az"),

    /** As the user put them; those the provider adds later go last, in its order. */
    Mine("mine"),
    ;

    companion object {
        /** The order kept as [id]; one Plasma doesn't know is the provider's. */
        fun of(id: String?): CategoryOrder = entries.firstOrNull { it.id == id && it.id != null } ?: Provider
    }
}

/** A category as its source's order sees it. */
data class CategoryPlace(
    /** The name it shows under: the user's, or the provider's. */
    val name: String,
    /** Made by the user rather than given by the provider. */
    val custom: Boolean,
    /** Its place in the provider's list; the user's own count in the order they were made. */
    val position: Int,
    /** Its place in the user's order, or null where it has none yet. */
    val userPosition: Int?,
)

object CategoryOrdering {
    /**
     * [items] of one source and kind in [order]. In the provider's order the user's own categories
     * come first, as they were made; A to Z sorts every one by its name; the user's order keeps the
     * places the user gave, and puts any without one last, in the provider's order.
     */
    fun <T> sorted(items: List<T>, order: CategoryOrder, place: (T) -> CategoryPlace): List<T> {
        val places = items.associateWith(place)
        val provider = compareBy<T>({ !places.getValue(it).custom }, { places.getValue(it).position })
        val comparator = when (order) {
            CategoryOrder.Provider -> provider
            CategoryOrder.AToZ -> Comparator<T> { a, b -> NaturalNames.compare(places.getValue(a).name, places.getValue(b).name) }.then(provider)
            CategoryOrder.Mine -> compareBy<T, Int?>(nullsLast()) { places.getValue(it).userPosition }.then(provider)
        }
        return items.sortedWith(comparator)
    }
}

/**
 * Names compared as people read them: letters by the TV's language, whatever their case or
 * accents, and a run of digits by its value, so "Sports 2" comes before "Sports 10".
 */
object NaturalNames : Comparator<String> {
    private val collator: Collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }

    override fun compare(a: String, b: String): Int {
        val left = chunks(a)
        val right = chunks(b)
        for (i in 0 until minOf(left.size, right.size)) {
            val x = left[i]
            val y = right[i]
            val byChunk = if (x.first().isDigit() && y.first().isDigit()) compareNumbers(x, y) else synchronized(collator) { collator.compare(x, y) }
            if (byChunk != 0) return byChunk
        }
        return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
    }

    /** Runs of digits and runs of anything else. */
    private fun chunks(text: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        for (i in 1..text.length) {
            if (i == text.length || text[i].isDigit() != text[i - 1].isDigit()) {
                out.add(text.substring(start, i))
                start = i
            }
        }
        return out
    }

    /** Two runs of digits by their value, however long: fewer digits, once leading zeros go, is less. */
    private fun compareNumbers(x: String, y: String): Int {
        val a = x.trimStart('0')
        val b = y.trimStart('0')
        return a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
    }
}

/** One of a source's categories, as its Categories page lists it. */
data class SourceCategory(
    val id: String,
    /** The name it shows under: the user's, or the provider's. */
    val name: String,
    /** The provider's name for it, where the user gave it another; null otherwise. */
    val providerName: String?,
    val hidden: Boolean,
    /** Made by the user, with what the user put in it. */
    val custom: Boolean,
    /** How many channels, movies or series it has. */
    val count: Int,
)

/** A source's categories of one kind, in the order they show in, and which order that is. */
data class SourceCategories(val order: CategoryOrder, val categories: List<SourceCategory>)

/** A channel, movie or series, as a category of the user's own lists it, to put in or take out. */
data class CategoryItem(
    val id: String,
    val name: String,
    /** What goes before its name: a channel's number. */
    val lead: String? = null,
)
