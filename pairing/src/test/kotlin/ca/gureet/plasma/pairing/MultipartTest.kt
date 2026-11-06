// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The restore page's form, as a phone's browser sends it (#108). */
class MultipartTest {
    private fun body(vararg lines: String) = lines.joinToString("\r\n").toByteArray(Charsets.UTF_8)

    @Test fun fieldsAndAFileComeApart() {
        val parts = Multipart.parse(
            "multipart/form-data; boundary=----WebKitFormBoundaryX3",
            body(
                "------WebKitFormBoundaryX3",
                "Content-Disposition: form-data; name=\"code\"",
                "",
                "7KQ4MZ",
                "------WebKitFormBoundaryX3",
                "Content-Disposition: form-data; name=\"backup\"; filename=\"plasma-backup.json\"",
                "Content-Type: application/json",
                "",
                "{\"format\":\"plasma-backup\"}",
                "line two",
                "------WebKitFormBoundaryX3",
                "Content-Disposition: form-data; name=\"passphrase\"",
                "",
                "",
                "------WebKitFormBoundaryX3--",
                "",
            ),
        )!!
        assertEquals(listOf("code", "backup", "passphrase"), parts.map { it.name })
        assertEquals("7KQ4MZ", parts[0].text)
        assertNull(parts[0].filename)
        assertEquals("plasma-backup.json", parts[1].filename)
        assertEquals("{\"format\":\"plasma-backup\"}\r\nline two", parts[1].text)
        assertEquals("", parts[2].text)
    }

    @Test fun aQuotedBoundaryWorksToo() {
        val parts = Multipart.parse(
            "multipart/form-data; boundary=\"b1\"",
            body("--b1", "Content-Disposition: form-data; name=\"a\"", "", "1", "--b1--"),
        )
        assertEquals("1", parts!!.single().text)
    }

    @Test fun whatIsntMultipartOrIsCutShortIsRefused() {
        assertNull(Multipart.parse("application/x-www-form-urlencoded", "a=1".toByteArray()))
        assertNull(Multipart.parse("multipart/form-data", body("--b1", "", "1", "--b1--")))
        // The last part never ends.
        assertNull(Multipart.parse("multipart/form-data; boundary=b1", body("--b1", "Content-Disposition: form-data; name=\"a\"", "", "1")))
        // A part with no name.
        assertNull(Multipart.parse("multipart/form-data; boundary=b1", body("--b1", "Content-Type: text/plain", "", "1", "--b1--")))
    }
}
