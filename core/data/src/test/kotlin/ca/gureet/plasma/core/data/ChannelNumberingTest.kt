// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.DuplicateRule
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.network.PlasmaHttp
import org.junit.After
import org.junit.Assert.assertEquals
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Duplicate Numbers (brief 4.30 and 5.2) on two accounts: Home, the primary, and Sports Extras,
 * whose channels clash with Home's and with each other.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChannelNumberingTest {
    private val home = FakeXtreamServer()
    private val extras = FakeXtreamServer().apply {
        streams = listOf(
            // The same channel as Home's: merged, so its clash isn't the user's to settle.
            FakeStream("7105", 105, "Arena Sports 1", epgId = "arena1.example"),
            FakeStream("7108", 108, "Courtside Classics"),
            FakeStream("7211", 212, "Ringside 1"),
            FakeStream("7212", 212, "Ringside 2"),
        )
    }
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
        home.close()
        extras.close()
        settingsDir.deleteRecursively()
    }

    private suspend fun bothSources(): Pair<Source, Source> {
        val first = data.sources.addXtream(home.baseUrl, "viewer", "s3cret", "Home Portal").source
        data.importer.importChannels(first.id)
        val second = data.sources.addXtream(extras.baseUrl, "viewer", "s3cret", "Sports Extras").source
        data.importer.importChannels(second.id)
        return first to second
    }

    private suspend fun listed(name: String, sourceId: String) = data.channels.lineup.first().listed.first { it.name == name && it.sourceId == sourceId }

    private suspend fun refreshBoth(sources: Pair<Source, Source>) {
        data.importer.importChannels(sources.first.id)
        data.importer.importChannels(sources.second.id)
    }

    @Test fun importsMoveDuplicatesAndSayWhich() = runTest {
        val first = data.sources.addXtream(home.baseUrl, "viewer", "s3cret", "Home Portal").source
        data.importer.importChannels(first.id)
        val second = data.sources.addXtream(extras.baseUrl, "viewer", "s3cret", "Sports Extras").source
        val announced = async(start = CoroutineStart.UNDISPATCHED) { data.importer.newDuplicates.first() }

        data.importer.importChannels(second.id)

        assertEquals(listOf("${second.id}:7105", "${second.id}:7108", "${second.id}:7212"), announced.await())
        val classics = listed("Courtside Classics", second.id)
        assertEquals(1108, classics.number)
        assertEquals(NumberReason.DuplicateFix, classics.numberReason)
        assertEquals(108, classics.wantedNumber)
        assertEquals(212, listed("Ringside 1", second.id).number)
        assertEquals(1212, listed("Ringside 2", second.id).number)
        // Merged, Extras' Arena Sports 1 doesn't show; its number does no harm.
        assertTrue(data.channels.channels.first().none { it.id == "${second.id}:7105" })
    }

    @Test fun keepSwapAndChooseSurviveARefresh() = runTest {
        val sources = bothSources()
        val (homeId, extrasId) = sources.first.id to sources.second.id

        data.numbering.keep("$extrasId:7212")
        data.numbering.swap("$extrasId:7108")
        refreshBoth(sources)

        val ringside = listed("Ringside 2", extrasId)
        assertEquals(1212, ringside.number)
        assertEquals(NumberReason.User, ringside.numberReason)
        assertEquals(212, ringside.wantedNumber)
        // Swapped: Courtside Classics has 108, and Courtside+ has 1108 because of it.
        assertEquals(108, listed("Courtside Classics", extrasId).number)
        val courtside = listed("Courtside+", homeId)
        assertEquals(1108, courtside.number)
        assertEquals(NumberReason.User, courtside.numberReason)
        assertEquals(108, courtside.wantedNumber)

        // A rule change leaves what the user chose alone.
        data.numbering.setRule(DuplicateRule.NextFree)
        assertEquals(1212, listed("Ringside 2", extrasId).number)
        assertEquals(1108, listed("Courtside+", homeId).number)
    }

    @Test fun chooseTakesOnlyAFreeNumber() = runTest {
        val (_, extras) = bothSources()

        assertFalse(data.numbering.choose("${extras.id}:7108", 105))
        assertEquals(1108, listed("Courtside Classics", extras.id).number)

        assertTrue(data.numbering.choose("${extras.id}:7108", 500))
        val classics = listed("Courtside Classics", extras.id)
        assertEquals(500, classics.number)
        assertEquals(NumberReason.User, classics.numberReason)
        assertEquals(108, classics.wantedNumber)
    }

    @Test fun anotherRuleMovesTheRestNowAndAfterRefreshes() = runTest {
        val sources = bothSources()
        val extrasId = sources.second.id

        data.numbering.setRule(DuplicateRule.NextFree)

        // 109 is Pulse's, so Courtside Classics goes to 110.
        assertEquals(110, listed("Courtside Classics", extrasId).number)
        assertEquals(213, listed("Ringside 2", extrasId).number)
        assertEquals(DuplicateRule.NextFree, data.settings.numberingNow().duplicateRule)
        refreshBoth(sources)
        assertEquals(110, listed("Courtside Classics", extrasId).number)
    }

    @Test fun anUnmergedChannelShowsFromEachSource() = runTest {
        val (homeSource, extrasSource) = bothSources()

        data.numbering.unmerge("${homeSource.id}:9105")

        val shown = data.channels.channels.first().filter { it.name == "Arena Sports 1" }
        assertEquals(listOf(105, 1105), shown.map { it.number })
        assertEquals(listOf("${homeSource.id}:9105", "${extrasSource.id}:7105"), shown.map { it.id })
        assertTrue(shown.none { it.isMerged })

        // Merge Duplicate Channels turned back on merges it again.
        data.settings.mergeAgain()
        assertEquals(listOf(105), data.channels.channels.first().filter { it.name == "Arena Sports 1" }.map { it.number })
    }

    @Test fun aNumberChangedOnAnyChannelStaysUntilChannelsAreNumberedAfresh() = runTest {
        val sources = bothSources()
        val homeId = sources.first.id

        // Change Number, on a channel no other wanted the number of.
        assertTrue(data.numbering.choose("$homeId:9101", 501))
        refreshBoth(sources)
        val news = listed("Northline News", homeId)
        assertEquals(501, news.number)
        assertEquals(NumberReason.User, news.numberReason)
        // 101 is free now: typing it finds nothing, and Change Number can give it back.
        assertTrue(data.channels.lineup.first().listed.none { it.number == 101 })

        data.numbering.numberAfresh()
        assertEquals(101, listed("Northline News", homeId).number)
        assertTrue(db.numberOverrides().all().isEmpty())
    }

    @Test fun numberingAfreshFollowsChannelNumbersAndDropsTheClashesChoices() = runTest {
        val (homeSource, extrasSource) = bothSources()
        data.numbering.choose("${extrasSource.id}:7108", 500)

        data.settings.choose(SettingKeys.CHANNEL_NUMBERS, 1)
        data.numbering.numberAfresh()

        // In playlist order: Home's five, then Extras' four.
        val all = data.channels.lineup.first().listed
        assertEquals((1..9).toList(), all.map { it.number })
        assertEquals(1, listed("Northline News", homeSource.id).number)
        assertEquals(7, listed("Courtside Classics", extrasSource.id).number)
        assertTrue(db.numberOverrides().all().isEmpty())

        // From the playlists again: the clashes come back, fixed by the rule.
        data.settings.choose(SettingKeys.CHANNEL_NUMBERS, 0)
        data.numbering.numberAfresh()
        assertEquals(1108, listed("Courtside Classics", extrasSource.id).number)
        assertEquals(105, listed("Arena Sports 1", homeSource.id).number)
    }
}
