// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only an APK signed with Plasma's own key goes over it (brief 5.18). */
class SignersTest {
    private val ours = "1".repeat(64)
    private val rotated = "2".repeat(64)
    private val theirs = "9".repeat(64)
    private val installed = ApkFacts("ca.gureet.plasma", versionCode = 480, signers = setOf(ours))

    @Test fun theSameAppSignedTheSameWayFits() {
        assertTrue(Signers.fits(ApkFacts("ca.gureet.plasma", 512, setOf(ours)), installed, versionCode = 512))
    }

    @Test fun anotherKeyDoesnt() {
        assertFalse(Signers.fits(ApkFacts("ca.gureet.plasma", 512, setOf(theirs)), installed, 512))
        assertFalse("Unsigned", Signers.fits(ApkFacts("ca.gureet.plasma", 512, emptySet()), installed, 512))
        assertFalse("Plasma unreadable", Signers.fits(ApkFacts("ca.gureet.plasma", 512, setOf(ours)), installed.let { ApkFacts(it.packageName, it.versionCode, emptySet()) }, 512))
    }

    @Test fun anotherAppOrVersionDoesnt() {
        assertFalse("Plasma Dev", Signers.fits(ApkFacts("ca.gureet.plasma.dev", 512, setOf(ours)), installed, 512))
        assertFalse("Not the version the file said", Signers.fits(ApkFacts("ca.gureet.plasma", 511, setOf(ours)), installed, 512))
    }

    @Test fun aKeyRotatedFromOursFits() {
        val next = ApkFacts("ca.gureet.plasma", 512, setOf(rotated), lineage = listOf(ours, rotated))
        assertTrue(Signers.fits(next, installed, 512))
        val notFromOurs = ApkFacts("ca.gureet.plasma", 512, setOf(rotated), lineage = listOf(theirs, rotated))
        assertFalse(Signers.fits(notFromOurs, installed, 512))
    }
}
