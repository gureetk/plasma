// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import java.util.Locale

object Titles {
    private val articles = listOf("the ", "a ", "an ")

    /** Titles sort by name without a leading article, as libraries do: The Last Lighthouse under L. */
    fun sortKey(title: String): String {
        val lower = title.lowercase(Locale.US)
        val article = articles.firstOrNull { lower.startsWith(it) }
        return if (article != null) lower.removePrefix(article) else lower
    }
}
