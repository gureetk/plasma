// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.model.ReadsForBenchmarks
import ca.gureet.plasma.core.network.PlasmaHttp
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * What a screen reads of movies, series and Continue Watching is worked out off the thread that
 * collects it, which for a screen is the main thread, once the first world is in; a read that
 * begins while Plasma starts is worked out where it's collected (#127). A list's rows are turned
 * into what shows where Replace Placeholder Posters is read, and Continue Watching's entries are
 * found where the profile watching is read: each test hands the repository one of those two and
 * looks at the thread it was read on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VodReadsTest {
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
        ReadsForBenchmarks.worldIn = false
        db.close()
        settingsDir.deleteRecursively()
    }

    /** Runs [block] as a screen would collect: on one thread of its own, which it's handed. */
    private fun collecting(block: suspend (collector: Thread) -> Unit) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            runBlocking(executor.asCoroutineDispatcher()) { withTimeout(TEST_ENDS_MS) { block(Thread.currentThread()) } }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun listsOfMoviesAndSeriesAreMadeOffTheThreadThatCollects() = collecting { collector ->
        ReadsForBenchmarks.worldIn = true
        val readOn = CopyOnWriteArrayList<Thread>()
        val replacePlaceholders = flow {
            readOn += Thread.currentThread()
            emit(true)
        }
        val vod = VodRepository(db, data.sources, clock, replacePlaceholders = replacePlaceholders)

        assertTrue(vod.recentMovies().first().isEmpty())
        assertTrue(vod.newEpisodes().first().isEmpty())

        assertTrue("Replace Placeholder Posters wasn't read for both lists", readOn.size >= 2)
        assertFalse("A list's rows were turned into what shows on the collector's thread", readOn.any { it === collector })
    }

    @Test fun whilePlasmaStartsListsAreMadeWhereTheyreCollected() = collecting { collector ->
        val readOn = CopyOnWriteArrayList<Thread>()
        val replacePlaceholders = flow {
            readOn += Thread.currentThread()
            emit(true)
        }
        val vod = VodRepository(db, data.sources, clock, replacePlaceholders = replacePlaceholders)

        assertTrue(vod.recentMovies().first().isEmpty())
        assertTrue(vod.newEpisodes().first().isEmpty())

        assertTrue("Replace Placeholder Posters was read for neither list", readOn.size >= 2)
        assertTrue("A list's rows were turned into what shows off the collector's thread while Plasma started", readOn.all { it === collector })
    }

    @Test fun continueWatchingIsWorkedOutOffTheThreadThatCollects() = collecting { collector ->
        ReadsForBenchmarks.worldIn = true
        val readOn = CopyOnWriteArrayList<Thread>()
        val watching = object : ProfileSource {
            override val id: Flow<String> = flow {
                readOn += Thread.currentThread()
                emit(DEFAULT_PROFILE)
            }

            override suspend fun now(): String = DEFAULT_PROFILE
        }
        val vod = VodRepository(db, data.sources, clock, watching)

        assertTrue(vod.continueWatching(since = null, nextEpisodes = true).first().isEmpty())

        assertTrue("The profile watching wasn't read", readOn.isNotEmpty())
        assertFalse("Continue Watching was worked out on the collector's thread", readOn.any { it === collector })
    }

    private companion object {
        const val TEST_ENDS_MS = 30_000L
    }
}
