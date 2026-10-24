// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Age ratings and adult groups for parental controls (brief 5.12). */
class ParentalTest {
    @Test fun ratingsAreReadAsAges() {
        assertEquals(17, ContentRatings.age("TV-MA"))
        assertEquals(17, ContentRatings.age("tv ma"))
        assertEquals(17, ContentRatings.age("TVMA"))
        assertEquals(14, ContentRatings.age("TV-14"))
        assertEquals(13, ContentRatings.age("PG-13"))
        assertEquals(13, ContentRatings.age("PG13"))
        assertEquals(18, ContentRatings.age("NC-17"))
        assertEquals(12, ContentRatings.age("12A"))
        assertEquals(16, ContentRatings.age("FSK 16"))
        assertEquals(18, ContentRatings.age("18+"))
        assertEquals(16, ContentRatings.age("+16"))
        assertEquals(15, ContentRatings.age("MA15+"))
        assertEquals(0, ContentRatings.age("U"))
        assertNull(ContentRatings.age("Not Rated"))
        assertNull(ContentRatings.age("2006"))
        assertNull(ContentRatings.age(""))
    }

    @Test fun aProgramRatedForTheLimitOrOlderIsLocked() {
        assertTrue(ContentRatings.locks("18", limit = 18))
        assertTrue(ContentRatings.locks("TV-MA", limit = 16))
        assertFalse(ContentRatings.locks("TV-MA", limit = 18))
        assertFalse(ContentRatings.locks("TV-14", limit = 16))
        // Off, or no rating the limit can go by.
        assertFalse(ContentRatings.locks("18", limit = null))
        assertFalse(ContentRatings.locks(null, limit = 12))
        assertFalse(ContentRatings.locks("Unrated", limit = 12))
    }

    @Test fun theBadgeSaysAPlainNumberAsAnAge() {
        assertEquals("18+", ContentRatings.badge("18"))
        assertEquals("TV-MA", ContentRatings.badge("TV-MA"))
        assertEquals("16+", ContentRatings.badge("16+"))
    }

    @Test fun adultGroupsAreFoundByWholeWords() {
        val adult = AdultWords.parse(AdultWords.DEFAULT)

        assertTrue(adult.matches("XXX Movies"))
        assertTrue(adult.matches("Adults | VOD"))
        assertTrue(adult.matches("For Adults"))
        assertTrue(adult.matches("Late Night 18+"))
        assertFalse(adult.matches("Mixxx Radio"))
        assertFalse(adult.matches("Sports"))
        assertFalse(adult.matches("Channel 118+"))
    }

    @Test fun theWordsAreTypedWithCommas() {
        val words = AdultWords.parse(" Adult,  nightlife ;XXX,, adult ")

        assertEquals(listOf("Adult", "nightlife", "XXX"), words.words)
        assertEquals("Adult, nightlife, XXX", words.toString())
        assertTrue(words.matches("Nightlife TV"))
        assertFalse(AdultWords.parse("").matches("Adult"))
    }

    @Test fun kidSafeGroupsAreFoundByWholeWords() {
        val kids = KidWords.parse(KidWords.DEFAULT)

        assertTrue(kids.matches("Kids | EN"))
        assertTrue(kids.matches("KIDS TV"))
        assertTrue(kids.matches("Family Movies"))
        assertTrue(kids.matches("Cartoons"))
        assertFalse(kids.matches("Kidnapped Classics"))
        assertFalse(kids.matches("Sports"))
        assertEquals("kids, family", KidWords.parse(" kids;family ,, Kids").toString())
    }
}
