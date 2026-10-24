// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlasmaDatabaseTest {
    private lateinit var db: PlasmaDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun close() = db.close()

    private val home = SourceEntity(id = "home", type = "xtream", name = "Home", server = "http://line.example.org/", username = "viewer", passwordEnc = "x", priority = 0, addedAt = 1)

    private fun channel(key: String, number: Int, generation: Long, sourceId: String = "home") = ChannelEntity(
        id = "$sourceId:$key", sourceId = sourceId, key = key, groupId = "$sourceId:4", name = key, position = number,
        playlistNumber = number, number = number, numberReason = "Playlist", wantedNumber = null, logo = null,
        tvgId = null, epgKey = null, catchupDays = null, isRadio = false, generation = generation,
    )

    private fun group(remoteId: String, generation: Long) =
        GroupEntity(id = "home:$remoteId", sourceId = "home", remoteId = remoteId, name = "Sports", position = 0, generation = generation)

    private fun programme(epgKey: String, start: Long, end: Long, generation: Long) = ProgrammeEntity(
        sourceId = "home", epgKey = epgKey, startAt = start, endAt = end, title = "P$start", subtitle = null,
        description = null, category = null, icon = null, season = null, episode = null, isNew = false, generation = generation,
    )

    @Test fun aNewImportReplacesTheOldOneWhole() = runTest {
        db.sources().upsert(home)
        db.channels().replaceSource("home", 1, listOf(group("4", 1)), listOf(channel("arena1", 105, 1), channel("atlas", 120, 1)))

        db.channels().replaceSource(
            "home", 2,
            listOf(group("4", 2)),
            listOf(channel("arena1", 105, 2), channel("velocity", 107, 2)),
        )

        assertEquals(listOf("home:arena1", "home:velocity"), db.channels().all().map { it.id })
        assertEquals(listOf("home:4"), db.groups().forSource("home").map { it.id })
    }

    @Test fun renumbersOtherSourcesChannelsInTheSameTransaction() = runTest {
        db.sources().upsert(home)
        db.sources().upsert(home.copy(id = "backup", priority = 1))
        db.channels().replaceSource("backup", 1, emptyList(), listOf(channel("ringside", 108, 1, sourceId = "backup")))

        db.channels().replaceSource(
            "home", 1, emptyList(), listOf(channel("courtside", 108, 1)),
            renumbered = listOf(NumberUpdate("backup:ringside", 1108, "DuplicateFix", 108)),
        )

        val ringside = db.channels().get("backup:ringside")!!
        assertEquals(1108, ringside.number)
        assertEquals("DuplicateFix", ringside.numberReason)
        assertEquals(108, ringside.wantedNumber)
    }

    @Test fun removingASourceTakesItsChannelsAndGroups() = runTest {
        db.sources().upsert(home)
        db.channels().replaceSource("home", 1, listOf(group("4", 1)), listOf(channel("arena1", 105, 1)))

        db.sources().delete("home")

        assertTrue(db.channels().all().isEmpty())
        assertTrue(db.groups().forSource("home").isEmpty())
    }

    @Test fun keepsFavoritesInTheUsersOrder() = runTest {
        val favorites = db.favorites()
        favorites.add("default", "home:wildframe", emptySet())
        favorites.add("default", "home:northline", emptySet())
        favorites.add("default", "home:velocity", emptySet())
        favorites.add("default", "home:northline", emptySet())
        assertEquals(listOf("home:wildframe", "home:northline", "home:velocity"), favorites.list("default").map { it.channelId })

        favorites.move("default", setOf("home:velocity"), after = null)
        assertEquals(listOf("home:velocity", "home:wildframe", "home:northline"), favorites.observe("default").first().map { it.channelId })
        favorites.move("default", setOf("home:velocity"), after = setOf("home:northline"))
        assertEquals(listOf("home:wildframe", "home:northline", "home:velocity"), favorites.list("default").map { it.channelId })

        favorites.remove("default", "home:wildframe")
        favorites.add("default", "backup:ringside", emptySet())
        favorites.removeForSource("backup")
        assertEquals(listOf("home:northline", "home:velocity"), favorites.list("default").map { it.channelId })
    }

    /** A merged channel may be kept as a favorite under any of its sources' ids (brief 5.1). */
    @Test fun aMergedChannelIsOneFavorite() = runTest {
        val favorites = db.favorites()
        val arena = setOf("home:arena1", "backup:arena1")
        favorites.add("default", "backup:arena1", emptySet())
        favorites.add("default", "home:velocity", emptySet())
        favorites.add("default", "home:northline", emptySet())
        // Adding it under its other source's id finds it there.
        favorites.add("default", "home:arena1", arena)
        assertEquals(listOf("backup:arena1", "home:velocity", "home:northline"), favorites.list("default").map { it.channelId })

        // It moves by any of its ids, and others move after it by any of them.
        favorites.move("default", arena, after = setOf("home:velocity"))
        assertEquals(listOf("home:velocity", "backup:arena1", "home:northline"), favorites.list("default").map { it.channelId })
        favorites.move("default", setOf("home:velocity"), after = arena)
        assertEquals(listOf("backup:arena1", "home:velocity", "home:northline"), favorites.list("default").map { it.channelId })
        // A channel that isn't a favorite, or a place that's gone, moves nothing.
        favorites.move("default", setOf("home:pulse"), after = null)
        favorites.move("default", setOf("home:northline"), after = setOf("home:pulse"))
        assertEquals(listOf("backup:arena1", "home:velocity", "home:northline"), favorites.list("default").map { it.channelId })

        favorites.removeAll("default", arena.toList())
        assertEquals(listOf("home:velocity", "home:northline"), favorites.list("default").map { it.channelId })
    }

    @Test fun remembersTheLastFiftyChannels() = runTest {
        val recents = db.recentChannels()
        repeat(55) { recents.watched("default", "home:c$it", at = it.toLong()) }
        recents.watched("default", "home:c0", at = 100)

        val list = recents.observe("default").first()
        assertEquals(50, list.size)
        assertEquals("home:c0", list.first().channelId)
        assertEquals("home:c54", list[1].channelId)
    }

    @Test fun keepsTheOldGuideUntilTheNewOneIsIn() = runTest {
        db.sources().upsert(home)
        val programmes = db.programmes()
        programmes.insertAll(listOf(programme("home:arena1.example", 100, 200, 1), programme("home:arena1.example", 200, 300, 1)))
        programmes.commit("home", generation = 1, at = 10)

        // A refresh writes its guide while the old one shows, untouched.
        programmes.insertAll(listOf(programme("home:arena1.example", 150, 250, 2)))
        assertEquals(3, programmes.countForSource("home"))
        assertEquals(listOf(100L, 200L), programmes.forKey("home:arena1.example", 0, 1_000).map { it.startAt })
        assertEquals(listOf(100L, 200L), programmes.observeWindow(0, 1_000).first().map { it.startAt })

        programmes.commit("home", generation = 2, at = 20)

        assertEquals(listOf(150L), programmes.forKey("home:arena1.example", 0, 1_000).map { it.startAt })
        assertEquals(1, programmes.countForSource("home"))
        assertEquals(20L, db.sources().get("home")?.guideRefreshedAt)
    }

    @Test fun findsWhatsOnInAWindowAndPrunesThePast() = runTest {
        db.sources().upsert(home.copy(guideGeneration = 1))
        val programmes = db.programmes()
        programmes.insertAll(
            listOf(
                programme("a", 0, 100, 1),
                programme("a", 100, 200, 1),
                programme("a", 200, 300, 1),
                programme("b", 50, 250, 1),
            ),
        )

        val window = programmes.observeWindow(from = 150, to = 210).first()
        assertEquals(listOf("a" to 100L, "a" to 200L, "b" to 50L), window.map { it.epgKey to it.startAt })

        programmes.prune(before = 150)
        assertEquals(3, programmes.countForSource("home"))
        assertNull(programmes.forKey("a", 0, 1_000).firstOrNull { it.startAt == 0L })
    }

    @Test fun aNewGuideOffsetMovesTheProgrammesAlreadyLoaded() = runTest {
        db.sources().upsert(home.copy(guideGeneration = 1, epgOffsetMinutes = 60))
        db.sources().upsert(home.copy(id = "backup", priority = 1, guideGeneration = 1))
        val programmes = db.programmes()
        programmes.insertAll(listOf(programme("a", 3_600_000, 7_200_000, 1), programme("b", 0, 3_600_000, 1).copy(sourceId = "backup")))

        // From +1 h to −1 h: two hours earlier.
        programmes.setGuideOffset("home", -60)

        assertEquals(-60, db.sources().get("home")?.epgOffsetMinutes)
        assertEquals(listOf(-3_600_000L to 0L), programmes.forKey("a", -10_000_000, 10_000_000).map { it.startAt to it.endAt })
        // Other sources' guides stay where they were.
        assertEquals(listOf(0L), programmes.forKey("b", -10_000_000, 10_000_000).map { it.startAt })

        // The same offset again moves nothing.
        programmes.setGuideOffset("home", -60)
        assertEquals(listOf(-3_600_000L), programmes.forKey("a", -10_000_000, 10_000_000).map { it.startAt })
    }

    @Test fun aSourceTurnedOffLeavesTheListsButKeepsItsChannels() = runTest {
        db.sources().upsert(home)
        db.sources().upsert(home.copy(id = "backup", priority = 1))
        db.channels().replaceSource("home", 1, listOf(group("4", 1)), listOf(channel("arena1", 105, 1), channel("atlas", 120, 1)))
        db.channels().replaceSource("backup", 1, emptyList(), listOf(channel("ringside", 108, 1, sourceId = "backup")))

        db.sources().setEnabled("home", false)

        assertEquals(listOf("backup:ringside"), db.channels().observeAll().first().map { it.id })
        assertEquals(emptyList<String>(), db.groups().observeAll().first().map { it.id })
        // Portals & Playlists still counts them, to show what turning it on brings back.
        assertEquals(mapOf("home" to 2, "backup" to 1), db.channels().observeCountsBySource().first().associate { it.sourceId to it.channels })

        db.sources().setEnabled("home", true)
        assertEquals(listOf("home:arena1", "backup:ringside", "home:atlas"), db.channels().observeAll().first().map { it.id })
    }

    @Test fun playbackForThisSourceIsKeptWithTheSource() = runTest {
        db.sources().upsert(home)

        db.sources().setPlayback(
            "home", streamFormat = "m3u8", bufferChoice = 3, audioOutputChoice = null, catchupOffsetMinutes = 120,
            engineChoice = 1, useHub = false, userAgent = "Custom/1.0", referrer = "http://lists.example.com/",
            extraHeadersEnc = "sealed", udpxy = "192.168.1.1:4022",
        )

        val source = db.sources().get("home")!!
        assertEquals("m3u8", source.streamFormat)
        assertEquals(3, source.bufferChoice)
        assertNull(source.audioOutputChoice)
        assertEquals(120, source.catchupOffsetMinutes)
        assertEquals(1, source.engineChoice)
        assertEquals(false, source.useHub)
        assertEquals("Custom/1.0", source.userAgent)
        assertEquals("http://lists.example.com/", source.referrer)
        assertEquals("sealed", source.extraHeadersEnc)
        assertEquals("192.168.1.1:4022", source.udpxy)
    }
}
