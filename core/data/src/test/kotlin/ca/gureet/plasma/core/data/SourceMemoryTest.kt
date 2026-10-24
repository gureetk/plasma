// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.ChannelSourceMemoryEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.PlasmaClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Remember What Works per Channel (brief 5.1): what's learned, and when it's checked again. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SourceMemoryTest {
    private class MovingClock(var at: Instant) : PlasmaClock {
        override val zone: ZoneId = ZoneOffset.UTC

        override fun now(): Instant = at
    }

    private val clock = MovingClock(Instant.parse("2026-10-21T20:41:00Z"))
    private lateinit var db: PlasmaDatabase
    private lateinit var memory: SourceMemory

    /** Arena Sports 1 on the primary source, the backup and a third. */
    private val candidates = listOf("home:105", "backup:105", "spare:105")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        memory = SourceMemory(db, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun tune(): TuneMemory = TuneMemory(memory, memory.plan("home:105", candidates))

    /** A tune whose first candidate fails and the backup takes over and plays. */
    private suspend fun failingOver(long: Boolean = false) {
        val tune = tune()
        tune.failed("home:105", next = "backup:105")
        tune.playing("backup:105")
        if (long) tune.playedLong("backup:105")
    }

    @Test fun withNothingLearnedTheChannelKeepsItsOwnOrder() = runTest {
        val plan = memory.plan("home:105", candidates)

        assertEquals(candidates, plan.order)
        assertNull(plan.remembered)
        assertFalse(plan.recheck)
    }

    @Test fun aFailoverThatPlaysTenMinutesIsRemembered() = runTest {
        failingOver(long = true)

        val plan = memory.plan("home:105", candidates)
        assertEquals(listOf("backup:105", "home:105", "spare:105"), plan.order)
        assertEquals(RememberedSource("backup:105", RememberedBecause.Failover, clock.at), plan.remembered)
        assertEquals(plan.remembered, memory.observe(candidates).first())
    }

    @Test fun aFailoverWatchedBrieflyIsntRememberedOnItsOwn() = runTest {
        failingOver()
        // Ten minutes on the first candidate, with no failover, teaches nothing either.
        tune().playedLong("home:105")

        assertNull(memory.plan("home:105", candidates).remembered)
    }

    @Test fun theFirstFailingOnThreeOfFiveTunesRemembersTheOneThatPlayed() = runTest {
        failingOver()
        tune().playing("home:105")
        failingOver()
        assertNull(memory.plan("home:105", candidates).remembered)

        failingOver()

        assertEquals(RememberedSource("backup:105", RememberedBecause.RepeatedFailures, clock.at), memory.plan("home:105", candidates).remembered)
    }

    @Test fun onlyTheLastFiveTunesCount() = runTest {
        failingOver()
        failingOver()
        repeat(4) { tune().playing("home:105") }

        failingOver()

        // Two failures have dropped out of the last five.
        assertNull(memory.plan("home:105", candidates).remembered)
    }

    @Test fun aSourcePickedInSwitchSourceIsRememberedAtOnceAndForgetClearsIt() = runTest {
        val plan = memory.plan("home:105", candidates)

        memory.choose(plan, "spare:105")

        val remembered = memory.plan("home:105", candidates)
        assertEquals(listOf("spare:105", "home:105", "backup:105"), remembered.order)
        assertEquals(RememberedBecause.User, remembered.remembered?.because)

        memory.forget(candidates)
        assertEquals(candidates, memory.plan("home:105", candidates).order)
    }

    @Test fun everythingRememberedFindsEachChannelsOwn() = runTest {
        memory.choose(memory.plan("home:105", candidates), "spare:105")
        // A tune counted without anything learned yet isn't listed.
        TuneMemory(memory, memory.plan("home:108", listOf("home:108", "backup:108"))).playing("home:108")

        val all = memory.observeAll().first()

        assertEquals(setOf("home:105"), all.keys)
        assertEquals(RememberedSource("spare:105", RememberedBecause.User, clock.at), all.rememberedBy(candidates))
        // Led by another source's copy, the channel still finds it; without the spare, it's gone.
        assertEquals("spare:105", all.rememberedBy(listOf("backup:105", "home:105", "spare:105"))?.candidateId)
        assertNull(all.rememberedBy(listOf("home:105", "backup:105")))
        assertNull(all.rememberedBy(listOf("home:108", "backup:108")))
    }

    @Test fun pickingTheChannelsOwnFirstChoiceForgets() = runTest {
        memory.choose(memory.plan("home:105", candidates), "backup:105")

        memory.choose(memory.plan("home:105", candidates), "home:105")

        assertNull(memory.plan("home:105", candidates).remembered)
    }

    @Test fun aWeekLaterOneTuneTriesTheOriginalOrderAndGoesQuietlyBack() = runTest {
        failingOver(long = true)
        clock.at = clock.at.plus(Duration.ofDays(7))

        val check = memory.plan("home:105", candidates)

        assertTrue(check.recheck)
        assertEquals(listOf("home:105", "backup:105", "spare:105"), check.order)
        // The first failing again goes straight back to the backup, without a notice.
        assertTrue(check.isQuietSwitch("home:105", "backup:105"))
        assertFalse(check.isQuietSwitch("backup:105", "spare:105"))
        val checking = TuneMemory(memory, check)
        checking.failed("home:105", next = "backup:105")
        checking.playing("backup:105")

        // Only that one tune: the next ones start on the backup again, for another week.
        val after = memory.plan("home:105", candidates)
        assertFalse(after.recheck)
        assertEquals("backup:105", after.order.first())
        clock.at = clock.at.plus(Duration.ofDays(7))
        assertTrue(memory.plan("home:105", candidates).recheck)
    }

    @Test fun aWeekLaterTheFirstPlayingTenMinutesClearsTheMemory() = runTest {
        failingOver(long = true)
        clock.at = clock.at.plus(Duration.ofDays(8))
        val checking = TuneMemory(memory, memory.plan("home:105", candidates))

        checking.playing("home:105")
        checking.playedLong("home:105")

        val plan = memory.plan("home:105", candidates)
        assertEquals(candidates, plan.order)
        assertNull(plan.remembered)
    }

    @Test fun aRememberedSourceThatsOffOrGoneDoesntCount() = runTest {
        failingOver(long = true)

        // The backup turned off: its candidate isn't there.
        assertEquals(listOf("home:105", "spare:105"), memory.plan("home:105", listOf("home:105", "spare:105")).order)
        // A merged channel led by another source's copy still finds what it learned.
        assertEquals("backup:105", memory.plan("backup:105", listOf("backup:105", "home:105")).remembered?.candidateId)
    }

    @Test fun removingEitherSourceForgets() = runTest {
        failingOver(long = true)
        db.channelSourceMemory().upsert(ChannelSourceMemoryEntity("home:108", "spare:108", "USER", 1, 1, ""))

        db.channelSourceMemory().deleteForSource("backup:")

        assertTrue(db.channelSourceMemory().forAny(listOf("home:105")).isEmpty())
        assertEquals(1, db.channelSourceMemory().forAny(listOf("home:108")).size)
        db.channelSourceMemory().deleteForSource("home:")
        assertTrue(db.channelSourceMemory().forAny(listOf("home:108")).isEmpty())
    }
}
