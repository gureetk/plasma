// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.network.PlasmaHttp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

/** Who's watching (brief 5.11): the profiles, and each one's own favorites and history. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProfileRepositoryTest {
    private val clock = FixedClock(Instant.parse("2026-10-21T20:41:00Z"), ZoneOffset.UTC)
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
        settingsDir.deleteRecursively()
    }

    @Test fun beforeAnyIsMadeThereIsTheFirstWatching() = runTest {
        val profiles = data.profiles.all.first()

        assertEquals(listOf(DEFAULT_PROFILE), profiles.map { it.id })
        assertEquals("", profiles.single().name)
        assertEquals(DEFAULT_PROFILE, data.profiles.current.first().id)
    }

    @Test fun eachProfileKeepsItsOwnFavoritesAndHistory() = runTest {
        data.channels.setFavorite("home:105", true)
        data.channels.watched("home:105", null)
        val leo = data.profiles.add("Leo", avatar = "aurora", kind = ProfileKind.Standard, maxAge = null)!!

        data.profiles.choose(leo)

        assertEquals("Leo", data.profiles.current.first().name)
        assertEquals(emptyList<String>(), data.channels.favoriteIds.first())
        data.channels.setFavorite("home:108", true)
        data.channels.watched("home:108", null)
        assertEquals(listOf("home:108"), data.channels.favoriteIds.first())

        // Who's Watching says what each watched last, the first one still first.
        val all = data.profiles.all.first()
        assertEquals(listOf(DEFAULT_PROFILE, leo), all.map { it.id })
        assertEquals(listOf("home:105", "home:108"), all.map { it.lastChannelId })

        data.profiles.choose(DEFAULT_PROFILE)
        assertEquals(listOf("home:105"), data.channels.favoriteIds.first())
    }

    @Test fun aProfilesOwnPinIsKeptAsAHash() = runTest {
        val dan = data.profiles.add("Dan", null, ProfileKind.Standard, null)!!
        assertTrue(data.profiles.isPin(dan, "0000"))

        data.profiles.setPin(dan, "2580")

        assertTrue(data.profiles.all.first().first { it.id == dan }.hasPin)
        assertTrue(data.profiles.isPin(dan, "2580"))
        assertFalse(data.profiles.isPin(dan, "1234"))
        assertFalse("2580" in db.profiles().get(dan)!!.pinHash!!)
    }

    @Test fun deletingOneTakesWhatItKeptAndLeavesTheFirstWatching() = runTest {
        val leo = data.profiles.add("Leo", null, ProfileKind.Standard, null)!!
        data.profiles.choose(leo)
        data.channels.setFavorite("home:105", true)
        data.search.remember("night stint")
        data.settings.setDefaultProfile(leo)

        data.profiles.delete(leo)

        assertEquals(listOf(DEFAULT_PROFILE), data.profiles.all.first().map { it.id })
        assertEquals(DEFAULT_PROFILE, data.settings.profileNow())
        assertNull(data.settings.defaultProfile())
        assertEquals(0, db.favorites().list(leo).size)
        // The first one can't go.
        data.profiles.delete(DEFAULT_PROFILE)
        assertEquals(1, data.profiles.all.first().size)
    }

    @Test fun aGuestKeepsNoHistoryFromBefore() = runTest {
        val sam = data.profiles.add("Sam", null, ProfileKind.Standard, null)!!
        data.profiles.choose(sam)
        data.channels.watched("home:105", null)
        data.search.remember("night stint")

        data.profiles.update(sam, "Guest", null, ProfileKind.Guest, null)

        assertTrue(data.profiles.isGuest(sam))
        assertEquals(emptyList<String>(), data.channels.recentIds.first())
        assertEquals(emptyList<String>(), data.search.recent.first())
    }

    @Test fun thereIsRoomForSix() = runTest {
        repeat(5) { assertTrue(data.profiles.add("P$it", null, ProfileKind.Standard, null) != null) }

        assertNull(data.profiles.add("Seventh", null, ProfileKind.Standard, null))
        assertEquals(ProfileRepository.MAX, data.profiles.all.first().size)
        // A kids profile keeps its age; the first one stays a standard one.
        val kids = data.profiles.all.first()[1].id
        data.profiles.update(kids, "Kids", null, ProfileKind.Kids, 7)
        data.profiles.update(DEFAULT_PROFILE, "Maya", null, ProfileKind.Kids, 7)
        val all = data.profiles.all.first()
        assertEquals(7, all.first { it.id == kids }.maxAge)
        assertEquals(ProfileKind.Standard, all.first().kind)
        assertEquals("Maya", all.first().name)
    }
}
