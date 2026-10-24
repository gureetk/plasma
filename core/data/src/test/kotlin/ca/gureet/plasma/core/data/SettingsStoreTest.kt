// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ca.gureet.plasma.channels.NumberKeys
import ca.gureet.plasma.channels.NumberSource
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.FrameRateMatch
import ca.gureet.plasma.core.model.RemoteKeys
import ca.gureet.plasma.core.model.ScreensaverAfter
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.model.TvTurnsOff
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/** Settings kept across restarts (brief 6.3), and the key that encrypts channel addresses (6.10). */
class SettingsStoreTest {
    private val dir: File = Files.createTempDirectory("settings").toFile()
    private var scope: CoroutineScope? = null

    /** A store on the settings file. Asking again is a restart: one store at a time has the file. */
    private suspend fun store(): SettingsStore {
        stop()
        val fresh = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        return SettingsStore(PreferenceDataStoreFactory.create(scope = fresh) { File(dir, "settings.preferences_pb") })
    }

    private suspend fun stop() {
        val old = scope ?: return
        old.cancel()
        old.coroutineContext[Job]?.join()
        scope = null
    }

    @After fun tearDown() {
        runBlocking { stop() }
        dir.deleteRecursively()
    }

    @Test fun nothingChosenMeansTheDefaults() = runTest {
        val settings = store()
        val chosen = SettingsChoices(settings.choices.first())

        assertEquals(BufferSize.Medium, chosen.buffer)
        assertEquals(FrameRateMatch.SeamlessOnly, chosen.frameRate)
        assertEquals(AudioOutput.Passthrough, chosen.audioOutput)
        assertEquals(TvTurnsOff.StopAfterAMinute, chosen.tvTurnsOff)
        assertTrue(chosen.throughHub)
        assertTrue(chosen.showAndroidApps)
        assertEquals(ScreensaverAfter.TenMinutes, chosen.screensaverAfter)
        assertFalse("Other apps join Continue Watching only once turned on", chosen.includeOtherApps)
        assertEquals(NumberingSettings(), settings.numberingNow())
    }

    @Test fun choicesAreKeptByTheirKeys() = runTest {
        val settings = store()
        settings.choose(SettingKeys.BUFFER, 1)
        settings.choose(SettingKeys.STREAM_HUB, 1)
        settings.choose(SettingKeys.ANDROID_APPS, 1)
        settings.choose(SettingKeys.SCREENSAVER, 3)

        val chosen = SettingsChoices(settings.choices.first())

        assertEquals(BufferSize.Small, chosen.buffer)
        assertFalse(chosen.throughHub)
        assertFalse(chosen.showAndroidApps)
        assertEquals(null, chosen.screensaverAfter.minutes)
    }

    @Test fun channelsSettingsNumberTheChannels() = runTest {
        val settings = store()
        settings.choose(SettingKeys.CHANNEL_NUMBERS, 1)
        settings.choose(SettingKeys.FAVORITE_NUMBERS, 1)
        settings.choose(SettingKeys.GROUP_NUMBERS, 0)
        settings.choose(SettingKeys.NUMBER_KEYS, 1)

        val numbering = settings.numberingNow()

        assertEquals(NumberSource.PlaylistOrder, numbering.source)
        assertFalse(numbering.favoritesCountFromOne)
        assertTrue(numbering.groupsCountFromOne)
        assertEquals(NumberKeys.FullListOnly, numbering.numberKeys)
    }

    @Test fun learnedKeysAreKeptAndForgotten() = runTest {
        val settings = store()
        // A key Android names, and one it only knows by its scan code.
        settings.learnKey(RemoteKeys.idOf(131, 59), 166)
        settings.learnKey(RemoteKeys.idOf(RemoteKeys.UNKNOWN, 402), 183)
        settings.learnKey(RemoteKeys.idOf(131, 59), 167)

        val kept = store().learnedKeys.first()
        assertEquals(mapOf("k131" to 167, "s402" to 183), kept)
        assertEquals(183, RemoteKeys.actsAs(kept, RemoteKeys.UNKNOWN, 402))
        assertEquals(null, RemoteKeys.actsAs(kept, 132, 60))

        val again = store()
        again.forgetKey("k131")
        assertEquals(mapOf("s402" to 183), again.learnedKeys.first())
    }

    @Test fun theAppsRowAndHiddenAppsAreKept() = runTest {
        val settings = store()
        assertNull("Never arranged", settings.homeApps.first())
        assertEquals(emptySet<String>(), settings.hiddenApps.first())

        settings.setHomeApps(listOf("tv.example.sonar", "tv.example.reelbox"))
        settings.setAppHidden("tv.example.pingmeter", hidden = true)
        settings.setAppHidden("tv.example.snapshot", hidden = true)
        settings.setAppHidden("tv.example.pingmeter", hidden = false)

        val again = store()
        assertEquals(listOf("tv.example.sonar", "tv.example.reelbox"), again.homeApps.first())
        assertEquals(setOf("tv.example.snapshot"), again.hiddenApps.first())

        // A row with every app taken off is still arranged.
        again.setHomeApps(emptyList())
        assertEquals(emptyList<String>(), again.homeApps.first())
    }

    @Test fun hiddenPlayNextItemsAndAppsAreKept() = runTest {
        val settings = store()
        settings.hidePlayNext(setOf("tv.example.reelbox|ep-12"))
        settings.hidePlayNext(setOf("tv.example.reelbox|ep-13"))
        settings.setPlayNextAppHidden("tv.example.tumble", hidden = true)
        settings.setPlayNextAppHidden("tv.example.kinetic", hidden = true)
        settings.setPlayNextAppHidden("tv.example.kinetic", hidden = false)

        val again = store()
        assertEquals(setOf("tv.example.reelbox|ep-12", "tv.example.reelbox|ep-13"), again.hiddenPlayNext.first())
        assertEquals(setOf("tv.example.tumble"), again.hiddenPlayNextApps.first())
    }

    @Test fun hiddenJellyfinItemsKeepWhenTheyWereLastWatched() = runTest {
        val settings = store()
        settings.hideJellyfin("kite", java.time.Instant.ofEpochMilli(1_000))
        settings.hideJellyfin("atlas", null)
        // Hidden again later: the newer time replaces the older.
        settings.hideJellyfin("kite", java.time.Instant.ofEpochMilli(2_000))

        assertEquals(mapOf("kite" to 2_000L, "atlas" to 0L), store().hiddenJellyfin.first())
    }

    @Test fun aReplacedHomeIsRememberedWithTheBootLastAsked() = runTest {
        val settings = store()
        assertFalse(settings.homeReplaced())
        assertNull(settings.homeBackAskedBoot())
        settings.setHomeReplaced(true)
        settings.setHomeBackAskedBoot(41)

        val again = store()
        assertTrue(again.homeReplaced())
        assertEquals(41, again.homeBackAskedBoot())
    }

    @Test fun theTvsOwnScreensaverIsKeptToPutBack() = runTest {
        val settings = store()
        assertNull(settings.tvScreensaverBefore())
        settings.setTvScreensaverBefore("tv.example.dreams/.Backdrop")
        assertEquals("tv.example.dreams/.Backdrop", store().tvScreensaverBefore())
        store().setTvScreensaverBefore(null)
        assertNull(store().tvScreensaverBefore())
    }

    @Test fun theParentalPinIsKeptAsASaltedHash() = runTest {
        var settings = store()
        assertFalse(settings.pinSet.first())
        assertFalse(settings.isPin("1234"))

        settings.setPin("2580")

        assertTrue(settings.pinSet.first())
        assertTrue(settings.isPin("2580"))
        assertFalse(settings.isPin("1234"))
        // The file has no PIN in it, only its salted hash, and it stays across a restart.
        val kept = File(dir, "settings.preferences_pb").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("2580" in kept)
        assertTrue("pbkdf2:" in kept)
        settings = store()
        assertTrue(settings.isPin("2580"))

        // The same PIN again is salted afresh.
        settings.setPin("2580")
        assertNotEquals(kept, File(dir, "settings.preferences_pb").readBytes().toString(Charsets.ISO_8859_1))

        settings.setPin(null)
        assertFalse(settings.pinSet.first())
        assertFalse(settings.isPin("2580"))
    }

    @Test fun parentalControlsHaveTheBriefsDefaults() {
        val defaults = SettingsChoices()
        assertTrue(defaults.lockAdultGroups)
        assertEquals(18, defaults.ageLimit)
        assertEquals(0, defaults.unlockFor)
        assertFalse(defaults.lockSettings)
        assertNull(SettingsChoices(mapOf(SettingKeys.AGE_LIMIT to 0)).ageLimit)
        assertEquals(12, SettingsChoices(mapOf(SettingKeys.AGE_LIMIT to 1)).ageLimit)
    }

    @Test fun theDataKeyIsMadeOnceAndKeptSealed() = runTest {
        val first = Vault(FakeSecretBox(), store())
        val sealed = first.fields().seal("http://provider.example/live/viewer/s3cret/9105.ts")

        // After a restart, the same key opens what was sealed before.
        val again = Vault(FakeSecretBox(), store())

        assertEquals("http://provider.example/live/viewer/s3cret/9105.ts", again.fields().open(sealed))
        assertFalse("s3cret" in sealed)
        assertNotEquals("The same address seals differently each time", sealed, first.fields().seal("http://provider.example/live/viewer/s3cret/9105.ts"))
    }
}
