// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.core.model.CategoryOrder
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.network.PlasmaHttp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * A source's categories as the user arranges them, in Settings › Portals & Playlists › the source
 * › Categories (the owner's ask, 1 November), against a fake Xtream server.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CategoryRepositoryTest {
    private val xtream = FakeXtreamServer()
    private val clock = FixedClock(Instant.parse("2026-11-02T20:41:00Z"), ZoneOffset.UTC)
    private lateinit var db: PlasmaDatabase
    private lateinit var settingsDir: File
    private lateinit var data: PlasmaData

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsDir = Files.createTempDirectory("settings").toFile()
        val settings = SettingsStore(PreferenceDataStoreFactory.create { File(settingsDir, "settings.preferences_pb") })
        data = PlasmaData(db, PlasmaHttp.client("Plasma test"), settings, FakeSecretBox(), clock)
    }

    @After fun tearDown() {
        db.close()
        xtream.close()
        settingsDir.deleteRecursively()
    }

    private suspend fun groupNames() = data.channels.groups.first().map { it.name }

    @Test fun groupsShowInTheOrderChosenUnderTheNamesGiven() = runTest {
        val id = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source.id
        data.importer.importChannels(id)
        assertEquals(listOf("Sports", "News", "Music"), groupNames())

        data.categories.setOrder(id, CategoryKind.Live, CategoryOrder.AToZ)
        assertEquals(listOf("Music", "News", "Sports"), groupNames())

        data.categories.rename(CategoryKind.Live, "$id:7", "  Headlines ")
        assertEquals(listOf("Headlines", "Music", "Sports"), groupNames())
        // Its channels go by its new name, and the words that lock a group by the provider's.
        assertEquals("Headlines", data.channels.channels.first().first { it.name == "Northline News" }.groupName)
        assertEquals("News", data.channels.groups.first().first { it.id == "$id:7" }.providerName)

        // Moving one puts them in the user's order.
        data.categories.move(id, CategoryKind.Live, listOf("$id:12", "$id:4", "$id:7"))
        assertEquals(CategoryOrder.Mine, data.categories.categories(id, CategoryKind.Live).first().order)
        assertEquals(listOf("Music", "Sports", "Headlines"), groupNames())

        // A refresh keeps the order and the name; a group the provider adds goes last.
        xtream.categories = xtream.categories + ("20" to "Kids")
        xtream.streams = xtream.streams + FakeStream("9300", 300, "Toon Time", category = "20")
        data.importer.importChannels(id)
        assertEquals(listOf("Music", "Sports", "Headlines", "Kids"), groupNames())

        // Nothing typed is the provider's name again.
        data.categories.rename(CategoryKind.Live, "$id:7", "")
        assertEquals(listOf("Music", "Sports", "News", "Kids"), groupNames())

        // The provider's order, then the user's again, as it was left.
        data.categories.setOrder(id, CategoryKind.Live, CategoryOrder.Provider)
        assertEquals(listOf("Sports", "News", "Music", "Kids"), groupNames())
        data.categories.setOrder(id, CategoryKind.Live, CategoryOrder.Mine)
        assertEquals(listOf("Music", "Sports", "News", "Kids"), groupNames())
    }

    @Test fun theUsersOrderStartsFromTheOrderShowing() = runTest {
        val id = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source.id
        data.importer.importChannels(id)

        data.categories.setOrder(id, CategoryKind.Live, CategoryOrder.AToZ)
        data.categories.setOrder(id, CategoryKind.Live, CategoryOrder.Mine)

        assertEquals(listOf("Music", "News", "Sports"), groupNames())
        assertEquals(listOf("Music", "News", "Sports"), data.categories.categories(id, CategoryKind.Live).first().categories.map { it.name })
    }

    @Test fun aGroupOfTheUsersOwnHasTheChannelsPutInIt() = runTest {
        val id = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source.id
        data.importer.importChannels(id)

        val mine = data.categories.create(id, CategoryKind.Live, "My Sports")
        data.categories.add(mine, "$id:9105")
        data.categories.add(mine, "$id:9101")
        // Twice is once.
        data.categories.add(mine, "$id:9105")

        val own = data.channels.groups.first().first { it.custom }
        assertEquals("My Sports", own.name)
        assertEquals(listOf("$id:9105", "$id:9101"), own.members)
        // The user's own come first in the provider's order.
        assertEquals(listOf("My Sports", "Sports", "News", "Music"), groupNames())
        val page = data.categories.categories(id, CategoryKind.Live).first()
        assertEquals(listOf(2, 3, 1, 1), page.categories.map { it.count })
        assertEquals(listOf(true, false, false, false), page.categories.map { it.custom })

        // A refresh leaves it, and what's in it, be.
        data.importer.importChannels(id)
        assertEquals(listOf("$id:9105", "$id:9101"), data.channels.groups.first().first { it.custom }.members)

        data.categories.rename(CategoryKind.Live, mine, "Game Day")
        data.categories.remove(mine, listOf("$id:9101"))
        assertEquals(listOf("$id:9105"), data.categories.members(mine).first())
        assertEquals("Game Day", data.channels.groups.first().first { it.custom }.name)

        // Deleting it takes what's in it; the channels stay.
        data.categories.delete(CategoryKind.Live, mine)
        assertTrue(data.channels.groups.first().none { it.custom })
        assertEquals(emptyMap<String, List<String>>(), data.categories.allMembers.first())
        assertEquals(5, data.channels.channels.first().size)
    }

    @Test fun hiddenAndRenamedMovieCategoriesStaySoThroughARefresh() = runTest {
        val id = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source.id
        data.importer.importVod(id)

        data.categories.setHidden(CategoryKind.Movies, "$id:movie:30", true)
        data.categories.rename(CategoryKind.Movies, "$id:movie:31", "Sci-Fi")
        data.importer.importVod(id)

        val movies = data.vod.movieCategories.first()
        assertEquals(listOf("Thrillers" to true, "Sci-Fi" to false), movies.map { it.name to it.hidden })
        assertEquals("Science Fiction", movies.last().providerName)

        // Show All brings every one back.
        data.categories.setAllHidden(id, CategoryKind.Movies, false)
        assertEquals(listOf(false, false), data.vod.movieCategories.first().map { it.hidden })
        // Series' are their own.
        data.categories.setAllHidden(id, CategoryKind.Series, true)
        assertEquals(listOf(true, true), data.vod.seriesCategories.first().map { it.hidden })
        assertEquals(listOf(false, false), data.vod.movieCategories.first().map { it.hidden })
    }

    @Test fun aMovieCategoryOfTheUsersOwnListsTheMoviesPutInIt() = runTest {
        val id = data.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source.id
        data.importer.importVod(id)

        val mine = data.categories.create(id, CategoryKind.Movies, "Weekend")
        // There to put movies in, empty as it is.
        assertEquals(listOf("Weekend" to 0, "Thrillers" to 2, "Science Fiction" to 1), data.vod.movieCategories.first().map { it.name to it.count })

        data.categories.add(mine, "$id:7103")
        data.categories.add(mine, "$id:7101")

        assertEquals(listOf("Low Orbit", "Neon Tide"), data.vod.moviesIn(mine).first().map { it.name })
        assertEquals(2, data.vod.movieCategories.first().first { it.custom }.count)
        // The provider's categories are as they were.
        assertEquals(listOf("Neon Tide", "Glass Orchard"), data.vod.moviesIn("$id:movie:30").first().map { it.name })
        // A refresh leaves it be.
        data.importer.importVod(id)
        assertEquals(listOf("Low Orbit", "Neon Tide"), data.vod.moviesIn(mine).first().map { it.name })
    }
}
