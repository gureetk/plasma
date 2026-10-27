// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * The age a program's rating is for (brief 5.12), from the guide's `<rating>` as North America
 * and Europe write them: "TV-MA", "PG-13", "12A", "FSK 16", "18+". Age Limit locks a program
 * rated for its age or older.
 */
object ContentRatings {
    private val AGES: Map<String, Int> = mapOf(
        // US television.
        "TV-Y" to 0, "TV-Y7" to 7, "TV-Y7-FV" to 7, "TV-G" to 0, "TV-PG" to 10, "TV-14" to 14, "TV-MA" to 17,
        // US films.
        "G" to 0, "PG" to 10, "PG-13" to 13, "R" to 17, "NC-17" to 18, "X" to 18,
        // UK films.
        "U" to 0, "UC" to 0, "12A" to 12, "R18" to 18,
        // Canadian television and films, and Australia's M.
        "C" to 0, "C8" to 8, "E" to 0, "14A" to 14, "18A" to 18, "M" to 15,
    )

    /** A number of years, with whatever's around it: "18", "18+", "+16", "FSK 16", "-12", "MA15+". */
    private val YEARS = Regex("""(?<!\d)(\d{1,2})(?!\d)""")

    /** The age [rating] is for, or null when it doesn't say. */
    fun age(rating: String): Int? {
        val text = rating.trim().uppercase().replace(Regex("""\s+"""), " ")
        if (text.isEmpty()) return null
        AGES[text]?.let { return it }
        // "TV MA", "TVMA", "PG13".
        AGES[text.replace(' ', '-')]?.let { return it }
        AGES[text.replace(Regex("""^TV-?"""), "TV-")]?.let { return it }
        AGES[text.replace(Regex("""^(PG|NC)-?(\d+)$"""), "$1-$2")]?.let { return it }
        return YEARS.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it <= MAX_AGE }
    }

    /** Whether a program rated [rating] needs the PIN under the age [limit] (Off is null). */
    fun locks(rating: String?, limit: Int?): Boolean {
        if (limit == null || rating == null) return false
        val age = age(rating) ?: return false
        return age >= limit
    }

    /** The badge the PIN screen shows: "18+" for a number of years, the rating as written otherwise. */
    fun badge(rating: String): String {
        val text = rating.trim()
        return if (text.all(Char::isDigit) && text.isNotEmpty()) "$text+" else text
    }

    private const val MAX_AGE = 21
}

/**
 * Lock Adult Groups (brief 5.12): a group whose name has one of [words] in it is adult. A word
 * matches whole and in any case: with "xxx", "XXX Movies" and "Adults | XXX" are adult, but
 * "Mixxx Radio" isn't.
 */
class AdultWords(val words: List<String>) {
    private val pattern: Regex? = wholeWords(words)

    fun matches(name: String): Boolean = pattern?.containsMatchIn(name) == true

    /** The words as Settings shows and takes them: "adult, xxx, 18+". */
    override fun toString(): String = words.joinToString(", ")

    companion object {
        /** What's adult until the list is changed. */
        const val DEFAULT = "adult, adults, xxx, 18+, porn, erotic"

        /** The words in [text], separated by commas. */
        fun parse(text: String): AdultWords = AdultWords(text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() })
    }
}

/**
 * Kids Words (brief 5.11): a group whose name has one of [words] in it is kid-safe, so kids
 * profiles see its channels, unless Kid-Safe Groups takes it out. A word matches whole and in any
 * case: with "kids", "Kids | EN" and "KIDS TV" are kid-safe, but "Kidnapped Classics" isn't.
 */
class KidWords(val words: List<String>) {
    private val pattern: Regex? = wholeWords(words)

    fun matches(name: String): Boolean = pattern?.containsMatchIn(name) == true

    /** The words as Settings shows and takes them: "kids, family, cartoons". */
    override fun toString(): String = words.joinToString(", ")

    companion object {
        /** What's kid-safe until the list is changed. */
        const val DEFAULT = "kids, kid, children, family, cartoon, cartoons, junior, toons"

        /** The words in [text], separated by commas. */
        fun parse(text: String): KidWords = KidWords(text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() })
    }
}

/** Any of [words], each matched whole (not inside another word) and in any case; null for none. */
private fun wholeWords(words: List<String>): Regex? = words.takeIf { it.isNotEmpty() }?.let { list ->
    Regex(list.joinToString("|", prefix = """(?<![\p{L}\p{N}])(?:""", postfix = """)(?![\p{L}\p{N}])""") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
}
