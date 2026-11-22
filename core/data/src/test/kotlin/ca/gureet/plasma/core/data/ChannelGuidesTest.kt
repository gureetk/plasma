// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.epg.GuideKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which of a source's guides each channel takes its programmes and its logo from (brief 5.3). */
class ChannelGuidesTest {
    private val arena = GuideKey("home:105", "arena1.example", "Arena Sports 1")
    private val northline = GuideKey("home:101", "northline.example", "Northline News")
    private val velocity = GuideKey("home:107", "velocity.example", "Velocity Motor")
    private val keys = listOf(arena, northline, velocity)

    private fun read(
        matched: Map<String, List<String>> = emptyMap(),
        wrote: Set<String> = emptySet(),
        logoMatched: Map<String, List<String>> = matched,
        icons: List<Pair<String, String>> = emptyList(),
    ) = GuideRead(matched, wrote, logoMatched, icons)

    @Test fun aChannelTakesItsProgrammesFromTheFirstGuideThatHasSome() {
        val guides = ChannelGuides("home", placeholders = emptySet())
        guides.add(read(matched = mapOf("arena1.example" to listOf(arena.channelId)), wrote = setOf("arena1.example")))
        assertEquals(listOf(northline, velocity), guides.wantingProgrammes(keys))

        guides.add(read(matched = mapOf("arena.extra" to listOf(arena.channelId), "northline.extra" to listOf(northline.channelId)), wrote = setOf("arena.extra", "northline.extra")))

        assertEquals(mapOf(arena.channelId to "home:arena1.example", northline.channelId to "home:northline.extra"), guides.epgKeys)
    }

    @Test fun aChannelAGuideListsWithoutProgrammesIsLookedForInTheNext() {
        val guides = ChannelGuides("home", placeholders = emptySet())
        // The first guide lists Velocity Motor and Northline News, with programmes for neither.
        guides.add(read(matched = mapOf("velocity.example" to listOf(velocity.channelId), "northline.example" to listOf(northline.channelId))))
        assertEquals(keys, guides.wantingProgrammes(keys))

        // The next has Velocity Motor's, under another id.
        guides.add(read(matched = mapOf("velocity.extra" to listOf(velocity.channelId)), wrote = setOf("velocity.extra")))

        assertEquals("home:velocity.extra", guides.epgKeys[velocity.channelId])
        // Northline News has none anywhere: it keeps the first guide that listed it.
        assertEquals("home:northline.example", guides.epgKeys[northline.channelId])
        assertEquals(listOf(arena, northline), guides.wantingProgrammes(keys))
    }

    @Test fun programmesAnEarlierGuideWroteCountForAChannelMatchedToTheirIdLater() {
        val guides = ChannelGuides("home", placeholders = emptySet())
        guides.add(read(matched = mapOf("arena1.example" to listOf(arena.channelId)), wrote = setOf("arena1.example")))

        // A later guide pairs another channel with the same id: its programmes are already there.
        guides.add(read(matched = mapOf("arena1.example" to listOf(velocity.channelId))))

        assertEquals("home:arena1.example", guides.epgKeys[velocity.channelId])
        assertEquals(setOf("arena1.example"), guides.written)
    }

    @Test fun aChannelsLogoIsTheFirstRealOneAnyGuideHas() {
        val guides = ChannelGuides("home", placeholders = emptySet())
        guides.add(
            read(
                matched = mapOf("arena1.example" to listOf(arena.channelId), "northline.example" to listOf(northline.channelId)),
                wrote = setOf("arena1.example", "northline.example"),
                icons = listOf("northline.example" to "https://guide.home.lan/northline.png"),
            ),
        )
        assertEquals(listOf(arena, velocity), guides.wantingLogos(keys))

        // Arena Sports 1's programmes stay with the first guide; its logo comes from the next.
        guides.add(
            read(
                matched = emptyMap(),
                logoMatched = mapOf("arena.extra" to listOf(arena.channelId), "northline.extra" to listOf(northline.channelId)),
                icons = listOf("arena.extra" to "https://extra.home.lan/arena1.png", "northline.extra" to "https://extra.home.lan/northline.png"),
            ),
        )

        assertEquals(
            mapOf(northline.channelId to "https://guide.home.lan/northline.png", arena.channelId to "https://extra.home.lan/arena1.png"),
            guides.guideLogos,
        )
        assertEquals("home:arena1.example", guides.epgKeys[arena.channelId])
    }

    @Test fun aPlaceholderIsNoGuidesLogoForAChannel() {
        val sourcesOwn = "https://img.home.lan/no-logo.png"
        val guides = ChannelGuides("home", placeholders = sharedPictures(List(StandIns.TITLES) { sourcesOwn }))
        // The first guide gives Arena Sports 1 the source's placeholder, and twelve of its channels
        // its own: neither is a logo for a channel.
        val guidesOwn = "https://guide.home.lan/blank.png"
        guides.add(
            read(
                matched = mapOf("arena1.example" to listOf(arena.channelId), "northline.example" to listOf(northline.channelId)),
                icons = listOf("arena1.example" to sourcesOwn, "northline.example" to guidesOwn) + List(11) { "other$it.example" to guidesOwn },
            ),
        )
        assertEquals(keys, guides.wantingLogos(keys))

        guides.add(read(matched = mapOf("northline.extra" to listOf(northline.channelId)), icons = listOf("northline.extra" to "https://extra.home.lan/northline.png")))

        assertEquals(mapOf(northline.channelId to "https://extra.home.lan/northline.png"), guides.guideLogos)
    }

    @Test fun picturesTenOrMoreShareAreShared() {
        val shared = "https://img.home.lan/no-logo.png"
        val few = "https://img.home.lan/news.png"
        assertEquals(setOf(shared), sharedPictures(List(StandIns.TITLES) { shared } + List(StandIns.TITLES - 1) { few } + null))
    }
}
