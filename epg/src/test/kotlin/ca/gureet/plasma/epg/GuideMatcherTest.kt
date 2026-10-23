// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideMatcherTest {
    private val guide = listOf(
        XmltvChannel("arena1.example", listOf("Arena Sports 1"), null),
        XmltvChannel("Velocity.example", listOf("Velocity Motor"), null),
        XmltvChannel("northline.example", listOf("Northline News", "Northline"), null),
    )

    @Test fun matchesByGuideIdIgnoringCase() {
        val channels = listOf(
            GuideKey("ch105", "arena1.example", "Arena Sports 1"),
            GuideKey("ch107", "velocity.example", "Velocity"),
        )
        assertEquals(
            mapOf("arena1.example" to listOf("ch105"), "velocity.example" to listOf("ch107")),
            GuideMatcher.match(channels, guide),
        )
    }

    @Test fun fallsBackToTheNameAndLetsOneGuideChannelFeedSeveral() {
        val channels = listOf(
            GuideKey("ch101", null, "Northline News"),
            GuideKey("ch101hd", "unknown.id", "UK: NORTHLINE NEWS HD"),
            GuideKey("ch999", null, "Orbit Science"),
        )
        assertEquals(mapOf("northline.example" to listOf("ch101", "ch101hd")), GuideMatcher.match(channels, guide))
    }

    @Test fun trustsPlaylistIdsWhenTheGuideListsNoChannels() {
        val channels = listOf(GuideKey("ch105", "Arena1.example", "Arena Sports 1"))
        assertEquals(mapOf("arena1.example" to listOf("ch105")), GuideMatcher.match(channels, emptyList()))
    }

    @Test fun normalisesNames() {
        assertEquals("arenasports1", GuideMatcher.normalizeName("Arena Sports 1 HD"))
        assertEquals("arenasports1", GuideMatcher.normalizeName("arena-sports-1"))
        assertEquals("arenasports1", GuideMatcher.normalizeName("UK: ARENA SPORTS 1"))
        assertEquals("velocitymotor", GuideMatcher.normalizeName("Velocity Motor 4K"))
        assertEquals("channel4", GuideMatcher.normalizeName("Channel 4"))
    }
}
