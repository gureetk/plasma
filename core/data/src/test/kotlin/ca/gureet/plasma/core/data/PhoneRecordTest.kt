// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** How the TV keeps its paired phones: a line each, and whether one is the Plasma Remote app. */
class PhoneRecordTest {
    private val browser = PhoneRecord("p-1", "Daniel’s Pixel", "hash-1", 1_000, 2_000)
    private val app = PhoneRecord("p-2", "Maya’s iPhone", "hash-2", 3_000, 4_000, app = true)

    @Test fun phonesReadBackAsTheyWereKept() {
        assertEquals(listOf(browser, app), PhoneRecord.parse(PhoneRecord.format(listOf(browser, app))))
    }

    /** A browser's line is as a Plasma from before writes it, so that one keeps it; an app's has one more field. */
    @Test fun anAppsLineHasOneFieldMore() {
        val lines = PhoneRecord.format(listOf(browser, app)).lines()
        assertEquals("p-1\tDaniel’s Pixel\thash-1\t1000\t2000", lines[0])
        assertEquals("p-2\tMaya’s iPhone\thash-2\t3000\t4000\tapp", lines[1])
    }

    @Test fun aPhoneKeptBeforeIsABrowser() {
        assertEquals(listOf(browser), PhoneRecord.parse("p-1\tDaniel’s Pixel\thash-1\t1000\t2000"))
    }

    @Test fun aLineThatIsntAPhoneIsLeftOut() {
        assertEquals(emptyList<PhoneRecord>(), PhoneRecord.parse("p-1\tDaniel’s Pixel\thash-1\t1000\nnothing"))
    }
}
