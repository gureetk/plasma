// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.editor

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Edit in a Browser's page as Plasma serves it: the page, its script, styles, fonts and icon
 * from Plasma's own files, with [texts], the TV's words for it, put in as JSON, in [language].
 */
class EditorPages(private val texts: Map<String, String>, private val language: String = "en") : EditorPage {
    private val template: String by lazy { resource("editor/editor.html").toString(Charsets.UTF_8) }

    override fun html(): String = template
        .replace("{{LANG}}", language.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "en" })
        .replace("{{TITLE}}", escapeHtml(texts["title"].orEmpty()))
        .replace("{{TEXTS}}", wordsJson())

    override fun file(name: String): EditorFile? = when (name) {
        "editor.js" -> EditorFile(resource("editor/editor.js"), SCRIPT)
        "editor.css" -> EditorFile(resource("editor/editor.css"), STYLE)
        in FILES -> EditorFile(resource("editor/$name"), typeOf(name), cache = true)
        else -> null
    }

    /** The words as JSON that can't end the script element it sits in. */
    private fun wordsJson(): String = JsonObject(texts.mapValues { JsonPrimitive(it.value) }).toString().replace("<", "\\u003c")

    private fun resource(path: String): ByteArray =
        requireNotNull(EditorPages::class.java.classLoader?.getResourceAsStream(path)) { "Missing $path" }.use { it.readBytes() }

    private fun typeOf(name: String): String = when (name.substringAfterLast('.')) {
        "png" -> "image/png"
        "svg" -> "image/svg+xml"
        "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    companion object {
        /** The words the page needs, by name: the app gives each one in its own language. */
        val KEYS: List<String> = listOf(
            "title", "heading", "tv", "browser", "end",
            "joinText", "codeLabel", "join", "wrongCode", "tooManyTries", "notOpen", "full",
            "endedTitle", "endedIdle", "endedTv", "endedYou", "endedText", "unreachable", "saving", "saveFailed",
            "favorites", "channels", "categories", "liveTv", "movies", "series", "numbering", "locks", "sourceOff",
            "editingAs", "favoritesText", "addChannels", "searchChannels", "allGroups", "noFavorites", "noMatches",
            "moveUp", "moveDown", "moveTop", "moveBottom", "removeFavorite", "addFavorite", "addAllShown", "dragToMove",
            "countsFromOne", "keepsNumbers", "change", "close",
            "channelsText", "filterAll", "filterFavorites", "filterHidden", "filterLocked",
            "hide", "show", "lock", "unlock", "hiddenWithGroup", "lockedWithGroup", "selected", "selectAll", "clearSelection",
            "addToCategory", "noOwnCategories",
            "categoriesText", "order", "providerOrder", "aToZ", "myOrder", "showAll", "hideAll", "newCategory",
            "categoryName", "create", "cancel", "save", "rename", "resetName", "providerName", "yours", "adult",
            "shown", "hidden", "hiddenCount", "deleteCategory", "deleteText", "delete", "editChannels", "editMovies", "editSeries",
            "inThisCategory", "searchMovies", "searchSeries", "nothingInCategory", "add", "remove", "movedToMyOrder",
            "numberingText", "numbersInFavorites", "numbersInFavoritesText", "numbersInGroups", "numbersInGroupsText",
            "countFromOne", "keepNumbers",
            "locksText", "locksOff", "pinTitle", "pinText", "pinLabel", "unlockLocks", "wrongPin", "pinBlocked", "pinTooMany",
            "locksUnlocked", "lockedGroups", "lockedGroupsText", "lockedChannels", "lockedChannelsText", "lockChannels", "noLockedChannels",
            "channelCount", "categoryCount", "titleCount",
        )

        private const val SCRIPT = "text/javascript; charset=utf-8"
        private const val STYLE = "text/css; charset=utf-8"

        /** The page's own files, which don't change while Plasma's installed. */
        private val FILES = setOf(
            "icon.svg",
            "fonts/instrument-sans-latin-400-normal.woff2",
            "fonts/instrument-sans-latin-500-normal.woff2",
            "fonts/instrument-sans-latin-600-normal.woff2",
        )
    }
}
