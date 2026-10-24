// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone remote's page as the TV serves it (brief 4.33 and 5.21): its words, manifest and files. */
class RemotePagesTest {
    private val texts = RemotePages.KEYS.associateWith { "‹$it›" } + mapOf("title" to "Plasma </script> Remote", "appName" to "Plasma Remote", "shortName" to "Plasma")
    private val pages = RemotePages(texts, "en-US")

    private fun resource(path: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "Missing $path" }.use { it.readBytes().toString(Charsets.UTF_8) }

    @Test fun everyWordThePageUsesComesFromTheTv() {
        val html = resource("remote/remote.html")
        val script = resource("remote/remote.js")
        val used = Regex("""data-(?:text|label)="(\w+)"""").findAll(html).map { it.groupValues[1] }.toSet() +
            Regex("""\bT\.(\w+)""").findAll(script).map { it.groupValues[1] }.toSet() +
            // The colour keys' own names, when the screen gives them no label.
            setOf("red", "green", "yellow", "blue")

        assertEquals(emptySet<String>(), used - RemotePages.KEYS.toSet())
    }

    @Test fun theWordsCantEndTheScriptTheySitIn() {
        val html = pages.html()

        assertFalse("Plasma </script> Remote" in html)
        assertTrue("Plasma \\u003c/script> Remote" in html)
        assertTrue("<title>Plasma &lt;/script&gt; Remote</title>" in html)
        assertTrue("<html lang=\"en-US\">" in html)
    }

    @Test fun addToHomeScreenOpensThePageWithItsIcon() {
        val manifest = pages.file("manifest.webmanifest")!!
        val json = Json.parseToJsonElement(manifest.bytes.toString(Charsets.UTF_8)).jsonObject

        assertEquals("Plasma Remote", json["name"]!!.jsonPrimitive.content)
        assertEquals("/remote", json["start_url"]!!.jsonPrimitive.content)
        json["icons"]!!.jsonArray.forEach { icon ->
            val name = icon.jsonObject["src"]!!.jsonPrimitive.content.removePrefix("/remote/")
            assertEquals("image/png", pages.file(name)?.type)
        }
    }

    @Test fun onlyThePagesOwnFilesAreServed() {
        assertEquals("text/javascript; charset=utf-8", pages.file("remote.js")?.type)
        val font = pages.file("fonts/instrument-sans-latin-500-normal.woff2")
        assertNotNull(font)
        assertTrue(font!!.cache)
        assertNull(pages.file("remote.html"))
        assertNull(pages.file("../remote/remote.html"))
        assertNull(pages.file("fonts/OFL-instrumentsans.txt"))
    }
}
