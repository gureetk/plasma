// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The phone remote's web page as Plasma serves it (brief 4.33 and 5.21): the page, its script,
 * fonts and home-screen icons from Plasma's own files, with [texts], the TV's words for it, put in
 * as JSON, in [language].
 */
class RemotePages(private val texts: Map<String, String>, private val language: String = "en") : RemotePage {
    private val template: String by lazy { resource("remote/remote.html").toString(Charsets.UTF_8) }
    private val script: ByteArray by lazy { resource("remote/remote.js") }

    override fun html(): String = template
        .replace("{{LANG}}", language.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "en" })
        .replace("{{TITLE}}", escapeHtml(texts["title"].orEmpty()))
        .replace("{{TEXTS}}", wordsJson())

    override fun file(name: String): RemoteFile? = when (name) {
        "remote.js" -> RemoteFile(script, SCRIPT)
        "manifest.webmanifest" -> RemoteFile(manifest().toByteArray(Charsets.UTF_8), MANIFEST)
        in FILES -> RemoteFile(resource("remote/$name"), typeOf(name), cache = true)
        else -> null
    }

    /** The words as JSON that can't end the script element it sits in. */
    private fun wordsJson(): String = JsonObject(texts.mapValues { JsonPrimitive(it.value) }).toString().replace("<", "\\u003c")

    /** Add to Home Screen gives the phone a Plasma icon that opens the page (5.21). */
    private fun manifest(): String = buildJsonObject {
        put("name", texts["appName"].orEmpty())
        put("short_name", texts["shortName"].orEmpty())
        put("start_url", "/remote")
        put("scope", "/remote")
        put("display", "standalone")
        put("background_color", "#0B0B0D")
        put("theme_color", "#0B0B0D")
        put(
            "icons",
            JsonArray(
                listOf(192, 512).map { size ->
                    buildJsonObject {
                        put("src", "/remote/icon-$size.png")
                        put("sizes", "${size}x$size")
                        put("type", "image/png")
                    }
                },
            ),
        )
    }.toString()

    private fun resource(path: String): ByteArray =
        requireNotNull(RemotePages::class.java.classLoader?.getResourceAsStream(path)) { "Missing $path" }.use { it.readBytes() }

    private fun typeOf(name: String): String = when (name.substringAfterLast('.')) {
        "png" -> "image/png"
        "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }

    private fun escapeHtml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        /** The words the page needs, by name: the app gives each one in its own language. */
        val KEYS: List<String> = listOf(
            "title", "appName", "shortName",
            "remote", "channels", "type", "back", "home", "menu", "numbers", "ok", "close",
            "up", "down", "left", "right", "rewind", "playPause", "fastForward", "channelUp", "channelDown",
            "red", "green", "yellow", "blue", "goLive", "behindLive",
            "pairTitle", "pairText", "codeLabel", "nameLabel", "pairButton", "wrongCode", "tooManyTries", "androidPhone", "computer",
            "connecting", "unreachable", "unreachableUnnamed",
            "searchChannels", "countsFromOne",
            "typeTitle", "typingInto", "typeText", "nothingToType", "typingElsewhere", "tapToType", "paste", "clear", "done", "resultsOnTv",
        )

        private const val SCRIPT = "text/javascript; charset=utf-8"
        private const val MANIFEST = "application/manifest+json; charset=utf-8"

        /** The page's own files, which don't change while Plasma's installed. */
        private val FILES = setOf(
            "icon-180.png", "icon-192.png", "icon-512.png",
            "fonts/instrument-sans-latin-400-normal.woff2",
            "fonts/instrument-sans-latin-500-normal.woff2",
            "fonts/instrument-sans-latin-600-normal.woff2",
        )
    }
}
