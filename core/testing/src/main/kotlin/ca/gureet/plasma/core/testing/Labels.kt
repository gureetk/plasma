// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.testing

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import kotlin.math.roundToInt

// What TalkBack reads (#128), worked out from the unmerged semantics tree as Compose hands it to
// TalkBack: a node read as one (merging its descendants) says its own words first, then those of
// its parts, in order, leaving out parts hidden from accessibility and parts that are stops of
// their own. Nodes here come from `useUnmergedTree = true`, so `config` is each node's own.

/** The words TalkBack reads for this focusable node, in order; empty when it would say none. */
fun SemanticsNode.spokenParts(): List<String> =
    if (config.isMergingSemanticsOfDescendants) ownWords() + children.flatMap { it.partWords() } else ownWords()

/**
 * Everything on screen that can take focus and that TalkBack would read without saying what it
 * is (#128), each described by its place on the screen, in board pixels, and the nearest words
 * around it.
 */
fun SemanticsNodeInteractionsProvider.unlabeledFocusables(): List<String> =
    focusables().filter { it.spokenParts().isEmpty() }.map { it.whereAndWhat() }

/**
 * Everything on screen that can take focus and that TalkBack would read with words twice over
 * (#128): a description of its own followed by words in it that TalkBack still reads, or the
 * same words twice among its parts, such as a channel's logo, described by its name, beside its
 * name. A `PlasmaSurface` given a `description` keeps the words in it out of TalkBack's way.
 */
fun SemanticsNodeInteractionsProvider.focusablesReadTwice(): List<String> = focusables().mapNotNull { node ->
    val inside = if (node.config.isMergingSemanticsOfDescendants) node.children.flatMap { it.partWords() }.filter(::isWords) else emptyList()
    val described = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.isNotBlank() }
    val repeated = node.spokenParts().filter(::isWords).groupingBy { it.trim().lowercase() }.eachCount().filterValues { it > 1 }.keys
    when {
        described && inside.isNotEmpty() -> "${node.whereAndWhat()}: its description, then ${inside.joinToString { "\"$it\"" }}"
        repeated.isNotEmpty() -> "${node.whereAndWhat()}: says ${repeated.joinToString { "\"$it\"" }} twice"
        else -> null
    }
}

/** Whether [part] has words in it: a mark alone, such as a dot between two parts of a line, hasn't. */
private fun isWords(part: String): Boolean = part.any { it.isLetterOrDigit() }

private fun SemanticsNodeInteractionsProvider.focusables(): List<SemanticsNode> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused), useUnmergedTree = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filterNot { node -> generateSequence(node) { it.parent }.any { it.isHiddenHere() } }

private fun SemanticsNode.ownWords(): List<String> = (
    config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
        listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text)
    ).filter { it.isNotBlank() }

/** A part's words as TalkBack reads them with the node it's in: none if it's hidden, or a stop of its own. */
private fun SemanticsNode.partWords(): List<String> =
    if (isHiddenHere() || config.isMergingSemanticsOfDescendants) emptyList() else ownWords() + children.flatMap { it.partWords() }

private fun SemanticsNode.isHiddenHere(): Boolean = SemanticsProperties.HideFromAccessibility in config

private fun SemanticsNode.whereAndWhat(): String {
    val bounds = boundsInRoot
    val role = config.getOrNull(SemanticsProperties.Role)?.toString()?.lowercase() ?: "focusable"
    val tag = config.getOrNull(SemanticsProperties.TestTag)
    val own = spokenParts().joinToString(", ").ifEmpty { null }
    val around = generateSequence(parent) { it.parent }.firstNotNullOfOrNull { it.ownWords().joinToString(", ").ifEmpty { null } }
    return buildString {
        append("a $role")
        if (tag != null) append(" tagged \"$tag\"")
        if (own != null) append(" \"${own.take(80)}\"")
        append(" at ${bounds.left.roundToInt()}, ${bounds.top.roundToInt()} (${bounds.width.roundToInt()} × ${bounds.height.roundToInt()} px)")
        if (own == null && around != null) append(", in \"${around.take(80)}\"")
    }
}
