// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.network.PlasmaHttp
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Edit in a Browser's favorites: a profile's, whoever is watching, added, taken out and put in
 * another order many at once, a channel from two sources (5.1) kept under either source's id.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FavoritesEditingTest {
    private val clock = FixedClock(Instant.parse("2026-11-13T20:41:00Z"), ZoneOffset.UTC)
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

    private suspend fun favorites(profile: String = AVERY) = data.channels.favoriteIdsOf(profile).first()

    @Test fun addingManyKeepsTheirOrderAndLeavesThoseAlreadyThere() = runTest {
        data.channels.addFavorites(AVERY, listOf(NORTHLINE to setOf(NORTHLINE), ARENA to setOf(ARENA)))
        // Arena Sports 1 is a favorite already, kept under its Backup Line id too.
        data.channels.addFavorites(AVERY, listOf(VELOCITY to setOf(VELOCITY), ARENA_BACKUP to setOf(ARENA_BACKUP, ARENA), PULSE to setOf(PULSE)))
        assertEquals(listOf(NORTHLINE, ARENA, VELOCITY, PULSE), favorites())
        // Another profile's stay its own.
        assertEquals(emptyList<String>(), favorites(JORDAN))
    }

    @Test fun anOrderPutsThoseItNamesInTheirPlacesAndLeavesTheRest() = runTest {
        data.channels.addFavorites(AVERY, listOf(NORTHLINE, ARENA, OFF_SOURCE, VELOCITY, PULSE).map { it to setOf(it) })
        // The page lists what shows: a channel of a source that's off isn't in its order.
        data.channels.arrangeFavorites(AVERY, listOf(PULSE, NORTHLINE, VELOCITY, ARENA).map { setOf(it) })
        assertEquals(listOf(PULSE, NORTHLINE, OFF_SOURCE, VELOCITY, ARENA), favorites())
    }

    @Test fun aMergedChannelMovesUnderEitherOfItsIds() = runTest {
        data.channels.addFavorites(AVERY, listOf(NORTHLINE to setOf(NORTHLINE), ARENA_BACKUP to setOf(ARENA_BACKUP), PULSE to setOf(PULSE)))
        // The screens show Arena Sports 1 under the Home Portal's id; the favorite is kept under the Backup Line's.
        data.channels.arrangeFavorites(AVERY, listOf(setOf(ARENA, ARENA_BACKUP), setOf(PULSE), setOf(NORTHLINE)))
        assertEquals(listOf(ARENA_BACKUP, PULSE, NORTHLINE), favorites())
        data.channels.removeFavorites(AVERY, setOf(ARENA, ARENA_BACKUP))
        assertEquals(listOf(PULSE, NORTHLINE), favorites())
    }

    @Test fun manyAtOnceGoInOneChange() = runTest {
        val many = (1..1200).map { "home:$it" }
        data.channels.addFavorites(AVERY, many.map { it to setOf(it) })
        assertEquals(1200, favorites().size)
        data.channels.arrangeFavorites(AVERY, many.reversed().map { setOf(it) })
        assertEquals(many.reversed(), favorites())
        data.channels.removeFavorites(AVERY, many.drop(1))
        assertEquals(listOf("home:1"), favorites())
    }

    private companion object {
        const val AVERY = "avery"
        const val JORDAN = "jordan"
        const val NORTHLINE = "home:101"
        const val ARENA = "home:105"
        const val ARENA_BACKUP = "backup:1105"
        const val VELOCITY = "home:107"
        const val PULSE = "home:200"
        const val OFF_SOURCE = "spare:900"
    }
}
