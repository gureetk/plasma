// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.channels

import org.junit.Assert.assertEquals
import org.junit.Test

/** Merge Duplicate Channels (brief 5.1): the same channel from several sources is one. */
class ChannelMergerTest {
    private fun entry(id: String, name: String, guideId: String? = null) = MergeEntry(id, id.substringBefore(':'), name, guideId)

    private val fhdFirst = ChannelMerger.qualityOrder(Quality.Fhd)

    @Test fun namesLoseTheirTagsButKeepWhatTellsChannelsApart() {
        assertEquals("arena sports 1", ChannelMerger.normalizedName("UK: Arena Sports 1 FHD"))
        assertEquals("arena sports 1", ChannelMerger.normalizedName("Arena Sports 1 [HD]"))
        assertEquals("arena sports 2", ChannelMerger.normalizedName("Arena Sports 2"))
        assertEquals("courtside plus", ChannelMerger.normalizedName("Courtside+"))
        assertEquals("cafe societa", ChannelMerger.normalizedName("Café Società HEVC"))
        assertEquals("news and weather", ChannelMerger.normalizedName("US | News & Weather"))
        assertEquals(Quality.Fhd, ChannelMerger.qualityOf("Arena Sports 1 FHD"))
        assertEquals(Quality.Uhd, ChannelMerger.qualityOf("Velocity Motor 4K"))
        assertEquals(Quality.Hd, ChannelMerger.qualityOf("Northline News (720p)"))
        assertEquals(Quality.Sd, ChannelMerger.qualityOf("Pulse SD"))
        assertEquals(Quality.Unknown, ChannelMerger.qualityOf("Pulse"))
    }

    @Test fun theSameGuideIdMergesWhateverTheNameSays() {
        val merged = ChannelMerger.merge(
            listOf(
                entry("home:105", "Arena Sports 1 HD", "arena1.example"),
                entry("backup:9105", "UK: ARENA 1 FHD", "Arena1.Example"),
                entry("extras:id:1", "Arena One", null),
            ),
            sourceOrder = listOf("home", "backup", "extras"),
            preferred = fhdFirst,
        )

        // The primary source's leads it; the FHD stream plays first.
        assertEquals(listOf(MergedChannel("home:105", listOf("backup:9105", "home:105"))), merged)
    }

    @Test fun withoutGuideIdsNameAndQualityMustBothMatch() {
        val merged = ChannelMerger.merge(
            listOf(
                entry("home:101", "Northline News HD"),
                entry("backup:9101", "UK: Northline News HD"),
                entry("home:107", "Velocity Motor HD"),
                entry("backup:9107", "Velocity Motor SD"),
            ),
            sourceOrder = listOf("home", "backup"),
            preferred = fhdFirst,
        )

        assertEquals(listOf(MergedChannel("home:101", listOf("home:101", "backup:9101"))), merged)
    }

    @Test fun eachSourceGivesOneCandidateAndSourcesKeepTheirOrder() {
        val merged = ChannelMerger.merge(
            listOf(
                entry("backup:1", "Arena Sports 1", "arena1"),
                entry("backup:2", "Arena Sports 1 again", "arena1"),
                entry("home:1", "Arena Sports 1", "arena1"),
                entry("home:9", "Only Here", "only"),
            ),
            sourceOrder = listOf("home", "backup"),
            preferred = fhdFirst,
        )

        assertEquals(listOf(MergedChannel("home:1", listOf("home:1", "backup:1"))), merged)
    }

    @Test fun preferredQualityOrdersTheStreams() {
        val entries = listOf(
            entry("a:1", "Arena 1 SD", "arena1"),
            entry("b:1", "Arena 1 HD", "arena1"),
            entry("c:1", "Arena 1 4K", "arena1"),
            entry("d:1", "Arena 1 FHD", "arena1"),
            entry("e:1", "Arena 1", "arena1"),
        )
        val order = listOf("a", "b", "c", "d", "e")

        assertEquals(listOf("d:1", "b:1", "c:1", "e:1", "a:1"), ChannelMerger.merge(entries, order, fhdFirst).single().candidateIds)
        assertEquals(listOf("c:1", "d:1", "b:1", "e:1", "a:1"), ChannelMerger.merge(entries, order, ChannelMerger.qualityOrder(Quality.Uhd)).single().candidateIds)
        assertEquals("a:1", ChannelMerger.merge(entries, order, fhdFirst).single().leaderId)
    }

    @Test fun anUnmergedChannelStaysApart() {
        val entries = listOf(
            entry("home:101", "Northline News HD"),
            entry("backup:9101", "UK: Northline News HD"),
            entry("home:105", "Arena Sports 1", "arena1"),
            entry("backup:9105", "Arena Sports 1", "arena1"),
        )
        val apart = setOf(ChannelMerger.identityOf(entries[2]))

        val merged = ChannelMerger.merge(entries, listOf("home", "backup"), fhdFirst, apart)

        assertEquals("id:arena1", apart.single())
        assertEquals(listOf("home:101"), merged.map { it.leaderId })
    }
}
