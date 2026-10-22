// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import java.util.Locale

/**
 * How well [title] matches [query] (brief 4.3): 0 when the title starts with it, 1 when a word in it
 * does, 2 when it appears anywhere, or null for no match. Lower ranks first.
 */
fun matchBand(title: String, query: String): Int? {
    val q = query.trim().lowercase(Locale.US)
    if (q.isEmpty()) return null
    val t = title.lowercase(Locale.US)
    return when {
        t.startsWith(q) -> 0
        wordStarts(t).any { t.startsWith(q, it) } -> 1
        t.contains(q) -> 2
        else -> null
    }
}

/** Where the match is in [title], for highlighting it: the first word-start match, else the first match. */
fun matchRange(title: String, query: String): IntRange? {
    val q = query.trim().lowercase(Locale.US)
    if (q.isEmpty()) return null
    val t = title.lowercase(Locale.US)
    val start = wordStarts(t).firstOrNull { t.startsWith(q, it) } ?: t.indexOf(q).takeIf { it >= 0 } ?: return null
    return start until start + q.length
}

private fun wordStarts(text: String): List<Int> =
    text.indices.filter { i -> text[i].isLetterOrDigit() && (i == 0 || !text[i - 1].isLetterOrDigit()) }

/**
 * Sorts results by match band, keeping what's live now first within each band (brief 4.3), and
 * otherwise the order they came in.
 */
fun <T> rankResults(items: List<T>, query: String, title: (T) -> String, live: (T) -> Boolean = { false }): List<T> =
    items.mapNotNull { item -> matchBand(title(item), query)?.let { band -> item to band } }
        .sortedWith(compareBy<Pair<T, Int>> { it.second }.thenBy { if (live(it.first)) 0 else 1 })
        .map { it.first }

/**
 * Query completions (brief 4.3): the part of each title around the match, up to punctuation,
 * lower-cased ("Endurance 24: Night Stint" gives "night stint" for "night"), best matches first.
 */
fun completions(titles: List<String>, query: String, limit: Int = 3): List<String> {
    val q = query.trim().lowercase(Locale.US)
    if (q.isEmpty()) return emptyList()
    return titles.asSequence()
        .flatMap { it.split(':', ',', '(', ')').asSequence() }
        .map { it.trim().lowercase(Locale.US) }
        .filter { it.isNotEmpty() && it != q }
        .distinct()
        .mapNotNull { phrase -> matchBand(phrase, q)?.let { phrase to it } }
        .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.length })
        .map { it.first }
        .take(limit)
        .toList()
}
