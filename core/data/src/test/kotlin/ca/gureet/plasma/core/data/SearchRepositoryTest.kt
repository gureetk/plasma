// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.ProgrammeEntity
import ca.gureet.plasma.core.database.SourceEntity
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.model.PlasmaClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Search over the guide (brief 4.3 and 5.10) and the recent searches. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SearchRepositoryTest {
    private val now = Instant.parse("2026-10-21T20:41:00Z")
    private lateinit var db: PlasmaDatabase
    private lateinit var search: SearchRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        search = SearchRepository(db, FixedClock(now, ZoneOffset.UTC))
    }

    @After fun tearDown() = db.close()

    @Test fun eachWordFindsTheStartOfAWord() {
        assertEquals("night* st*", SearchRepository.matchOf(" Night  st"))
        // FTS's own syntax is just more separators.
        assertEquals("fight* night*", SearchRepository.matchOf("\"fight\" -night*"))
        assertEquals("ação*", SearchRepository.matchOf("Ação"))
        assertNull(SearchRepository.matchOf(" :- "))
    }

    @Test fun programsComeFromTheGuide() = runTest {
        db.sources().upsert(SourceEntity(id = "home", type = "xtream", name = "Home", server = "http://line.example.org/", username = "viewer", passwordEnc = "x", priority = 0, addedAt = 1))
        fun programme(title: String, start: String, end: String) = ProgrammeEntity(
            sourceId = "home", epgKey = "home:velocity", startAt = Instant.parse(start).toEpochMilli(), endAt = Instant.parse(end).toEpochMilli(),
            title = title, subtitle = null, description = null, category = null, icon = null, season = null, episode = null, isNew = false, generation = 1,
        )
        db.programmes().insertAll(
            listOf(
                programme("Endurance 24: Night Stint", "2026-10-21T19:00:00Z", "2026-10-21T23:00:00Z"),
                programme("Fight Night: Main Card", "2026-10-21T23:30:00Z", "2026-10-22T01:30:00Z"),
            ),
        )
        db.programmes().commit("home", 1, at = 1)

        val found = search.programmes("night", now.minusSeconds(86_400), now.plusSeconds(7 * 86_400))
        assertEquals(listOf("Endurance 24: Night Stint", "Fight Night: Main Card"), found.map { it.title })
        assertEquals("home:velocity", found.first().epgKey)
        assertEquals(emptyList<Programme>(), search.programmes("  ", now, now.plusSeconds(60)))
    }

    @Test fun recentSearchesKeepTheLatestTen() = runTest {
        // A second passes between searches.
        val ticking = object : PlasmaClock {
            var at = now
            override val zone: ZoneId = ZoneOffset.UTC
            override fun now(): Instant = at.also { at = at.plusSeconds(1) }
        }
        val search = SearchRepository(db, ticking)
        for (i in 1..11) search.remember("Search $i")
        search.remember("  Arena   Sports 1 ")
        search.remember("")

        val recent = search.recent.first()
        assertEquals(10, recent.size)
        assertEquals(listOf("arena sports 1", "search 11"), recent.take(2))
    }
}
