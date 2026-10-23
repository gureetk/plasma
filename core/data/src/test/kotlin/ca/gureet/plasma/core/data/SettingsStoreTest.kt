// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ca.gureet.plasma.channels.NumberKeys
import ca.gureet.plasma.channels.NumberSource
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.FrameRateMatch
import ca.gureet.plasma.core.model.RemoteKeys
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.model.TvTurnsOff
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertEquals(NumberingSettings(), settings.numberingNow())
    }

    @Test fun choicesAreKeptByTheirKeys() = runTest {
        val settings = store()
        settings.choose(SettingKeys.BUFFER, 1)
        settings.choose(SettingKeys.STREAM_HUB, 1)

        val chosen = SettingsChoices(settings.choices.first())

        assertEquals(BufferSize.Small, chosen.buffer)
        assertFalse(chosen.throughHub)
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
