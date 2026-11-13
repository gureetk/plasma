// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.editor.EditorPages
import org.junit.Assert.assertEquals
import org.junit.Test

/** Edit in a Browser's page gets a word for every name it uses, and the TV sends no others. */
class BrowserEditorWordsTest {
    @Test fun everyWordThePageUsesComesFromTheTv() {
        val sent = BrowserEditorHost.WORDS.keys + BrowserEditorHost.PLURALS.keys + "tv"
        assertEquals(EditorPages.KEYS.toSet() - sent, emptySet<String>())
        assertEquals(sent - EditorPages.KEYS.toSet(), emptySet<String>())
    }
}
