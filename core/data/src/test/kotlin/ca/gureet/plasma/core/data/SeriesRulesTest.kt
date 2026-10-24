// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.ProgrammeEntity
import ca.gureet.plasma.core.database.SourceEntity
import ca.gureet.plasma.core.model.FixedClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest

/** Record Series (brief 5.6): rules set what the guide has for them, once each episode; and retention. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SeriesRulesTest {
    private val now = Instant.parse("2026-10-21T20:41:00Z")
    private val clock = FixedClock(now, ZoneOffset.UTC)
    private lateinit var db: PlasmaDatabase
    private lateinit var recordings: RecordingRepository
    private lateinit var rules: SeriesRules

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recordings = RecordingRepository(db, clock)
        rules = SeriesRules(recordings, GuideRepository(db), clock)
    }

    @After fun tearDown() = db.close()

    private fun channel(id: String, name: String, epgKey: String, hidden: Boolean = false) = Channel(
        id = id, sourceId = "home", key = id.substringAfter(':'), name = name, number = 0, numberReason = NumberReason.Playlist,
        logo = null, groupId = null, groupName = null, epgKey = epgKey, catchupDays = null, isRadio = false, hidden = hidden, locked = false,
    )

    private val wildframe = channel("home:104", "Wildframe", "home:wildframe")
    private val wildframeHd = channel("home:204", "Wildframe HD", "home:wildframe-hd")
    private val channels = listOf(wildframe, wildframeHd)

    /** A guide with Kingdoms of Ice on both channels, as the source has it. */
    private suspend fun guide(vararg programmes: ProgrammeEntity) {
        db.sources().upsert(SourceEntity(id = "home", type = "xtream", name = "Home", server = "http://line.example.org/", username = "viewer", passwordEnc = "x", priority = 0, addedAt = 1))
        db.programmes().insertAll(programmes.toList())
        db.programmes().commit("home", 1, at = 1)
    }

    private fun airing(epgKey: String, days: Long, title: String = "Kingdoms of Ice", season: Int? = null, episode: Int? = null, subtitle: String? = null): ProgrammeEntity {
        val start = Instant.parse("2026-10-21T20:00:00Z").plus(Duration.ofDays(days))
        return ProgrammeEntity(
            sourceId = "home", epgKey = epgKey, startAt = start.toEpochMilli(), endAt = start.plus(Duration.ofHours(1)).toEpochMilli(),
            title = title, subtitle = subtitle, description = null, category = "Documentary", icon = null, season = season, episode = episode,
            isNew = false, generation = 1,
        )
    }

    private val padding = { _: RecordingKind -> Duration.ofMinutes(1) to Duration.ofMinutes(5) }

    @Test fun aRuleOnAChannelRecordsEachEpisodeThere() = runTest {
        guide(
            airing("home:wildframe", 1, season = 1, episode = 4),
            // A repeat of the same episode, and another channel's airing, are left.
            airing("home:wildframe", 2, season = 1, episode = 4),
            airing("home:wildframe-hd", 1, season = 1, episode = 4),
            airing("home:wildframe", 3, season = 1, episode = 5),
            // Another title isn't it.
            airing("home:wildframe", 4, title = "Deep Field"),
        )
        val rule = recordings.addRule("kingdoms of ice", Triple(wildframe.id, wildframe.name, null))

        assertEquals(2, rules.schedule(channels, padding))

        val set = recordings.ofRule(rule.id)
        assertEquals(listOf(4, 5), set.map { it.episode })
        assertTrue(set.all { it.channelId == wildframe.id && it.kind == RecordingKind.Series && it.status == RecordingStatus.Scheduled })
        // Again, nothing new.
        assertEquals(0, rules.schedule(channels, padding))
    }

    @Test fun aRuleOnAnyChannelRecordsWhereverItsOn() = runTest {
        guide(
            airing("home:wildframe-hd", 1, subtitle = "The Long Night"),
            // Episode titles tell repeats apart when there are no numbers.
            airing("home:wildframe", 2, subtitle = "The Long Night"),
            airing("home:wildframe", 3, subtitle = "Thaw"),
        )
        val rule = recordings.addRule("Kingdoms of Ice", channel = null)

        rules.schedule(channels, padding)

        assertEquals(listOf(wildframeHd.id to "The Long Night", wildframe.id to "Thaw"), recordings.ofRule(rule.id).map { it.channelId to it.subtitle })
    }

    @Test fun anAiringCancelledIsntSetAgainUnlessAskedFor() = runTest {
        guide(
            airing("home:wildframe", 1, season = 1, episode = 4),
            airing("home:wildframe", 2, season = 1, episode = 5),
            // A repeat of episode 4.
            airing("home:wildframe", 3, season = 1, episode = 4),
        )
        val rule = recordings.addRule("Kingdoms of Ice", Triple(wildframe.id, wildframe.name, null))
        rules.schedule(channels, padding)
        val first = recordings.ofRule(rule.id).first()

        // Skipped in a conflict, say.
        recordings.cancel(first.id)

        // The repeat is set instead, and the airing cancelled isn't set again.
        assertEquals(1, rules.schedule(channels, padding))
        assertEquals(0, rules.schedule(channels, padding))
        assertEquals(listOf(5, 4), recordings.ofRule(rule.id).map { it.episode })
        assertTrue(recordings.pending().none { it.id == first.id })
        // Record on it by hand sets it again, still the rule's.
        val again = recordings.schedule(ProgrammeToRecord(wildframe.id, wildframe.name, null, first.title, start = first.start, end = first.end), Duration.ZERO, Duration.ZERO)
        assertEquals(RecordingStatus.Scheduled, again.status)
        assertEquals(rule.id, again.ruleId)
    }

    @Test fun deletingARuleTakesWhatItSetThatsStillToCome() = runTest {
        guide(airing("home:wildframe", 1, season = 1, episode = 4))
        val rule = recordings.addRule("Kingdoms of Ice", Triple(wildframe.id, wildframe.name, null))
        rules.schedule(channels, padding)

        recordings.deleteRule(rule.id)

        assertTrue(recordings.pending().isEmpty())
        assertTrue(recordings.rulesNow().isEmpty())
    }

    @Test fun retentionKeepsTheNewestAndForgetsTheWatched() = runTest {
        // Keep Last was chosen before these were recorded.
        val earlier = RecordingRepository(db, FixedClock(now.minus(Duration.ofDays(10)), ZoneOffset.UTC))
        val rule = earlier.addRule("Kingdoms of Ice", Triple(wildframe.id, wildframe.name, null), keepLast = 2)
        val oldest = done(5, rule.id)
        recordings.keep(done(4, rule.id).id, true)
        done(3, rule.id)
        done(2, rule.id)
        val watched = done(40, ruleId = null, title = "Business Wrap")
        recordings.played(watched.id, 100, 100)

        // Keep Last 2: the two newest that aren't kept stay; the kept one stays too.
        assertEquals(setOf(oldest.id), recordings.expired(deleteWatchedAfter = null).map { it.id }.toSet())
        // Watched today, so not yet after 7 days.
        assertEquals(setOf(oldest.id), recordings.expired(Duration.ofDays(7)).map { it.id }.toSet())
        assertEquals(setOf(oldest.id, watched.id), RecordingRepository(db, FixedClock(now.plus(Duration.ofDays(8)), ZoneOffset.UTC)).expired(Duration.ofDays(7)).map { it.id }.toSet())
    }

    @Test fun retentionWaitsForTheNextRecordingAndCountsFromWhenItWasChosen() = runTest {
        val rule = recordings.addRule("Kingdoms of Ice", Triple(wildframe.id, wildframe.name, null))
        val oldest = done(5, rule.id)
        done(4, rule.id)
        done(3, rule.id)
        val watched = done(40, ruleId = null, title = "Business Wrap")
        // Watched a month ago.
        RecordingRepository(db, FixedClock(now.minus(Duration.ofDays(30)), ZoneOffset.UTC)).played(watched.id, 100, 100)

        // Keep Last 2, chosen now: nothing goes until the next one's recorded.
        recordings.setKeepLast(rule.id, 2)
        assertTrue(recordings.expired(deleteWatchedAfter = null).isEmpty())
        // After 7 Days, chosen now: what was watched long ago goes a week from now.
        assertTrue(recordings.expired(Duration.ofDays(7), watchedSince = now).isEmpty())
        val weekOn = RecordingRepository(db, FixedClock(now.plus(Duration.ofDays(7)), ZoneOffset.UTC))
        assertEquals(setOf(watched.id), weekOn.expired(Duration.ofDays(7), watchedSince = now).map { it.id }.toSet())

        // The next one, recorded after Keep Last was chosen: the oldest go.
        val tomorrow = RecordingRepository(db, FixedClock(now.plus(Duration.ofDays(1)), ZoneOffset.UTC))
        val next = recordings.schedule(ProgrammeToRecord(wildframe.id, wildframe.name, null, "Kingdoms of Ice", start = now.plus(Duration.ofHours(20)), end = now.plus(Duration.ofHours(21))), Duration.ZERO, Duration.ZERO, ruleId = rule.id)
        tomorrow.started(next.id, "/drive/next.ts")
        tomorrow.progress(next.id, 1_000)
        tomorrow.finished(next.id, failed = false)
        assertEquals(2, tomorrow.expired(deleteWatchedAfter = null).size)
        assertTrue(oldest.id in tomorrow.expired(deleteWatchedAfter = null).map { it.id })
    }

    /** A recording of [title] that aired [days] ago and was recorded whole. */
    private suspend fun done(days: Long, ruleId: String?, title: String = "Kingdoms of Ice"): Recording {
        val start = now.minus(Duration.ofDays(days))
        val programme = ProgrammeToRecord(wildframe.id, wildframe.name, null, title, start = start, end = start.plus(Duration.ofHours(1)))
        val recording = recordings.schedule(programme, Duration.ZERO, Duration.ZERO, ruleId = ruleId)
        recordings.started(recording.id, "/drive/$days.ts")
        recordings.finished(recording.id, failed = false)
        return recording
    }
}
