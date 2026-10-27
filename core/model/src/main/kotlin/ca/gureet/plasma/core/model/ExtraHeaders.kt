// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * A source's extra headers as typed in Playback for This Source (brief 5.1): "Name: value", one
 * after another, separated by semicolons or lines. A line without a name is left out.
 */
object ExtraHeaders {
    fun parse(typed: String): List<Pair<String, String>> = typed.split(';', '\n').mapNotNull { line ->
        val name = line.substringBefore(':', "").trim()
        val value = line.substringAfter(':', "").trim()
        if (name.isEmpty() || name.any { it.isWhitespace() || it.code < ' '.code }) null else name to value
    }
}
