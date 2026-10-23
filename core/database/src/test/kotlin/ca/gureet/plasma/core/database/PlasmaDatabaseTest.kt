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
        favorites.add("default", "home:wildframe")
        favorites.add("default", "home:northline")
        favorites.add("default", "home:velocity")
        favorites.add("default", "home:northline")
        assertEquals(listOf("home:wildframe", "home:northline", "home:velocity"), favorites.list("default").map { it.channelId })

        favorites.move("default", "home:velocity", 0)
        assertEquals(listOf("home:velocity", "home:wildframe", "home:northline"), favorites.observe("default").first().map { it.channelId })

        favorites.remove("default", "home:wildframe")
        favorites.add("default", "backup:ringside")
        favorites.removeForSource("backup")
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
}
