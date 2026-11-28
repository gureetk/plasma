// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import org.junit.Assert.assertEquals
import org.junit.Test

/** A poster made small for a phone's row (#230): read at a power of two, then sized, keeping its shape. */
class PhonePosterTest {
    @Test fun aLargePosterIsReadSmallerButNeverNarrowerThanTheRow() {
        assertEquals(4, posterSample(1000, 180))
        assertEquals(2, posterSample(500, 180))
        assertEquals(1, posterSample(300, 180))
        assertEquals(1, posterSample(120, 180))
    }

    @Test fun itKeepsItsShape() {
        assertEquals(270, heightAt(250, 375, 180))
        assertEquals(1, heightAt(4000, 10, 180))
    }
}
