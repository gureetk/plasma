// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.JellyfinAccount
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.feature.home.ContinueKind
import ca.gureet.plasma.integrations.jellyfin.JellyfinException
import ca.gureet.plasma.integrations.jellyfin.JellyfinItem
import ca.gureet.plasma.integrations.jellyfin.JellyfinKind
import ca.gureet.plasma.integrations.jellyfin.JellyfinProblem
import ca.gureet.plasma.integrations.watchnext.PlayNextItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A Jellyfin server's items in Continue Watching (brief 5.19), from the boards' made-up library. */
class JellyfinItemsTest {
    private val words = ContinueWords(
        left = { "$it left" },
        episode = { season, number -> "S$season, E$number" },
        upNext = "Up next",
        episodeText = { season, number -> if (number == null) null else listOfNotNull(season?.let { "S$it" }, "E$number").joinToString(", ") },
        jellyfin = "Jellyfin",
    )
    private val at = Instant.parse("2026-10-21T20:41:00Z")

    private val kite = JellyfinItem(
        id = "11111111-1111-1111-1111-111111111111",
        kind = JellyfinKind.Movie,
        title = "Kite Season",
        image = "http://media.home.lan:8096/Items/1111/Images/Primary?tag=kite",
        wide = false,
        positionMs = Duration.ofMinutes(32).toMillis(),
        durationMs = Duration.ofMinutes(84).toMillis(),
        lastWatched = at.minusSeconds(600),
    )
    private val kingdoms = JellyfinItem(
        id = "22222222-2222-2222-2222-222222222222",
        kind = JellyfinKind.Episode,
        title = "Kingdoms of Ice",
        episodeTitle = "The Long Night",
        season = 1,
        episode = 3,
        image = "http://media.home.lan:8096/Items/3333/Images/Thumb?tag=th",
        wide = true,
        positionMs = Duration.ofMinutes(10).toMillis(),
        durationMs = Duration.ofMinutes(50).toMillis(),
        lastWatched = at.minusSeconds(86_400),
    )
    private val atlas = JellyfinItem(
        id = "44444444-4444-4444-4444-444444444444",
        kind = JellyfinKind.Episode,
        title = "Atlas Station",
        episodeTitle = "The Docking Bay",
        season = 2,
        episode = 5,
        upNext = true,
        lastWatched = null,
    )

    @Test fun aJellyfinMovieSaysJellyfinAndWhatsLeft() {
        val item = kite.continueItem(words)

        assertEquals("jellyfin:11111111-1111-1111-1111-111111111111", item.id)
        assertEquals("Kite Season", item.title)
        // As on board 02.
        assertEquals("Jellyfin · 52 min left", item.meta)
        assertEquals(ContinueKind.Poster, item.kind)
        assertEquals("JELLYFIN", item.sourceTag)
        assertFalse(item.offersHideAll)
        assertEquals(32f / 84f, item.progress)

        assertEquals("S1, E3 · 40 min left", kingdoms.continueItem(words).meta)
        assertEquals(ContinueKind.Art, kingdoms.continueItem(words).kind)
        assertEquals("S2, E5 · Up next", atlas.continueItem(words).meta)
        assertEquals("11111111-1111-1111-1111-111111111111", jellyfinIdOf("jellyfin:11111111-1111-1111-1111-111111111111"))
        assertEquals(null, jellyfinIdOf("playnext:tv.example.reelbox|ep-204"))
    }

    @Test fun hiddenItemsStayHiddenUntilWatchedAgain() {
        val items = listOf(kite, kingdoms, atlas)
        val hidden = mapOf(kite.id to kite.lastWatched!!.toEpochMilli(), kingdoms.id to kingdoms.lastWatched!!.minusSeconds(60).toEpochMilli())

        // Kingdoms of Ice has been watched since it was hidden.
        assertEquals(listOf(kingdoms, atlas), items.forContinue(hidden, since = null))
        // Too old for Hide Items Older Than; Next Up that the server can't date stays.
        assertEquals(listOf(kite, atlas), items.forContinue(emptyMap(), since = at.minusSeconds(3_600)))
    }

    @Test fun anItemInBothPlaysOnceAsJellyfins() {
        val fromApp = PlayNextItem(id = 1, packageName = "org.jellyfin.androidtv", contentId = kite.id, title = "Kite Season", lastWatched = at)
        val sameEpisode = PlayNextItem(id = 2, packageName = "tv.example.reelbox", contentId = "ep-3", title = "kingdoms of ice", season = "1", episode = "03", lastWatched = at)
        val other = PlayNextItem(id = 3, packageName = "tv.example.reelbox", contentId = "ep-4", title = "Kingdoms of Ice", season = "1", episode = "4", lastWatched = at)

        assertEquals(listOf(other), listOf(fromApp, sameEpisode, other).besides(listOf(kite, kingdoms)))
        assertEquals(listOf(fromApp), listOf(fromApp).besides(emptyList()))
    }

    @Test fun theRowMergesEveryoneByWhenItWasWatched() {
        val other = PlayNextItem(id = 3, packageName = "tv.example.reelbox", contentId = "ep-4", title = "Night Stint", lastWatched = at.minusSeconds(3_600))

        val row = continueRow(emptyList(), listOf(other), words, { "Reelbox" }, listOf(atlas, kingdoms, kite))

        assertEquals(listOf("Kite Season", "Night Stint", "Kingdoms of Ice", "Atlas Station"), row.map { it.title })
    }

    private val maya = JellyfinAccount("http://media.home.lan:8096", "srv-1", "Media", "user-maya", "Maya", "tok-maya", "device-1")

    private fun TestScope.clock() = object : PlasmaClock {
        override val zone: ZoneId = ZoneOffset.UTC

        override fun now(): Instant = at.plusMillis(testScheduler.currentTime)
    }

    @Test fun itemsAreReadAtOnceThenEveryFifteenMinutes() = runTest {
        var reads = 0
        val feed = JellyfinFeed(clock(), read = { _, _, _ -> reads++; listOf(kite) }, onSignedOut = {})
        val seen = mutableListOf<List<JellyfinItem>>()
        val job = launch { feed.items(maya, nextUp = true, since = null).collect { seen += it } }

        runCurrent()
        assertEquals(1, reads)
        advanceTimeBy(Duration.ofMinutes(15).toMillis() + 1)
        assertEquals(2, reads)
        assertEquals(listOf(listOf(kite), listOf(kite)), seen)
        job.cancel()
    }

    @Test fun aServerThatsOffKeepsItsItemsForAnHour() = runTest {
        var up = true
        val feed = JellyfinFeed(
            clock(),
            read = { _, _, _ -> if (up) listOf(kite) else throw JellyfinException(JellyfinProblem.CantReach) },
            onSignedOut = {},
        )
        val seen = mutableListOf<List<JellyfinItem>>()
        val job = launch { feed.items(maya, nextUp = true, since = null).collect { seen += it } }
        runCurrent()

        up = false
        // 15 and 30 minutes on, the last items stay; an hour on, they go.
        advanceTimeBy(Duration.ofMinutes(45).toMillis() + 1)
        assertEquals(listOf(listOf(kite), listOf(kite), listOf(kite), listOf(kite)), seen)
        advanceTimeBy(Duration.ofMinutes(15).toMillis())
        assertEquals(emptyList<JellyfinItem>(), seen.last())
        job.cancel()
    }

    @Test fun signedOutByTheServerTheItemsGo() = runTest {
        val gone = mutableListOf<JellyfinAccount>()
        val feed = JellyfinFeed(clock(), read = { _, _, _ -> throw JellyfinException(JellyfinProblem.SignedOut) }, onSignedOut = { gone += it })

        val seen = feed.items(maya, nextUp = true, since = null).take(1).toList()

        assertEquals(listOf(emptyList<JellyfinItem>()), seen)
        runCurrent()
        assertEquals(listOf(maya), gone)
        // Not signed in: nothing to read.
        assertEquals(listOf(emptyList<JellyfinItem>()), feed.items(null, nextUp = true, since = null).toList())
    }
}
