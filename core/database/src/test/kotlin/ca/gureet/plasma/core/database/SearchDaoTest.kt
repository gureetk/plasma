// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The guide's search index (brief 5.10): words by their start, the guide in use, nearest first. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SearchDaoTest {
    private lateinit var db: PlasmaDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun close() = db.close()

    private val home = SourceEntity(id = "home", type = "xtream", name = "Home", server = "http://line.example.org/", username = "viewer", passwordEnc = "x", priority = 0, addedAt = 1)

    private fun programme(title: String, start: Long, end: Long = start + 60, subtitle: String? = null, generation: Long = 1) = ProgrammeEntity(
        sourceId = "home", epgKey = "home:arena1", startAt = start, endAt = end, title = title, subtitle = subtitle,
        description = null, category = null, icon = null, season = null, episode = null, isNew = false, generation = generation,
    )

    private suspend fun guide(vararg programmes: ProgrammeEntity) {
        db.sources().upsert(home)
        db.programmes().insertAll(programmes.toList())
        db.programmes().commit("home", 1, at = 1)
    }

    private suspend fun titles(match: String, from: Long = 0, to: Long = Long.MAX_VALUE, now: Long = 1_000) =
        db.search().programmes(match, from, to, now, limit = 10).map { it.title }

    @Test fun wordsMatchByTheirStart() = runTest {
        guide(
            programme("Endurance 24: Night Stint", 1_000),
            programme("Late Night Sessions", 2_000),
            programme("Midnight Movie", 3_000),
            programme("Football Tonight", 4_000, subtitle = "Harbour City at night"),
            programme("Café Società", 5_000),
        )

        assertEquals(listOf("Endurance 24: Night Stint", "Late Night Sessions", "Football Tonight"), titles("night*"))
        assertEquals(listOf("Endurance 24: Night Stint"), titles("night* stint*"))
        // Accents don't matter.
        assertEquals(listOf("Café Società"), titles("cafe* societa*"))
    }

    @Test fun onlyTheGuideInUseInTheWindowIsSearchedWhatsOnFirst() = runTest {
        guide(
            programme("Night Stint", 100, 200),
            programme("Night Ride", 900, 1_100),
            programme("Night Owls", 1_200, 1_300),
            programme("Night Life", 5_000, 5_100),
        )
        // A refresh being written isn't searched until it's committed.
        db.programmes().insertAll(listOf(programme("Night Shift", 1_000, generation = 2)))

        assertEquals(listOf("Night Ride", "Night Owls", "Night Life"), titles("night*", from = 500, to = 6_000))
        assertEquals(listOf("Night Ride", "Night Owls"), titles("night*", from = 500, to = 2_000))
    }

    @Test fun searchHistoryKeepsTheLatestTen() = runTest {
        for (i in 1..12) db.searchHistory().add(SearchHistoryEntity("default", "search $i", at = i.toLong()), keep = 10)
        db.searchHistory().add(SearchHistoryEntity("default", "search 5", at = 13), keep = 10)

        val recent = db.searchHistory().observe("default", 10).first()
        assertEquals("search 5", recent.first())
        assertEquals(10, recent.size)
        assertEquals(listOf("search 5", "search 12", "search 11"), recent.take(3))
    }
}
