// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.model.HomeRow
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.network.PlasmaHttp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 * Back Up Settings and Restore from Backup (brief 5.17, #108): a backup made on one TV, put back
 * on another, whose sources then load from a fake Xtream server.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupsTest {
    private val xtream = FakeXtreamServer()
    private val clock = FixedClock(Instant.parse("2026-11-03T20:00:00Z"), ZoneOffset.UTC)
    private val databases = mutableListOf<PlasmaDatabase>()
    private val folders = mutableListOf<File>()

    /** Another TV's Keystore: what one seals, the other can't open. */
    private class OtherTvSecrets : SecretBox {
        override fun seal(plain: String): String = "other:" + plain.reversed()

        override fun open(sealed: String): String {
            require(sealed.startsWith("other:")) { "Sealed by another TV" }
            return sealed.removePrefix("other:").reversed()
        }
    }

    /** A TV: its own database, settings and Keystore. Backups seal with few rounds, so tests stay quick. */
    private fun tv(secrets: SecretBox = FakeSecretBox()): PlasmaData {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val folder = Files.createTempDirectory("settings").toFile()
        databases += db
        folders += folder
        val settings = SettingsStore(PreferenceDataStoreFactory.create { File(folder, "settings.preferences_pb") })
        return PlasmaData(db, PlasmaHttp.client("Plasma test"), settings, secrets, clock, backupRounds = ROUNDS)
    }

    @After fun tearDown() {
        databases.forEach { it.close() }
        xtream.close()
        folders.forEach { it.deleteRecursively() }
    }

    @Test fun everythingComesBackOnAnotherTvWithItsLogins() = runTest {
        val old = tv()
        val id = old.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source.id
        old.importer.importChannels(id)
        old.channels.toggleFavorite("$id:9105")
        old.channels.toggleFavorite("$id:9101")
        old.channels.setHidden("$id:9108", true)
        old.channels.setLocked("$id:9107", true)
        old.channels.setGroupHidden("$id:12", true)
        old.categories.rename(CategoryKind.Live, "$id:7", "Headlines")
        old.settings.choose(SettingKeys.LIVE_LIST_SHOWS, 1)
        old.settings.setHomeRowOrder(listOf(HomeRow.Apps) + HomeRow.entries.filter { it != HomeRow.Apps })
        old.settings.setPin("2468")
        val kids = old.profiles.add("Sam", avatar = null, kind = ProfileKind.Kids, maxAge = 7)!!
        old.profiles.setPin(kids, "1357")

        val bytes = BackupFile.encode(old.backups.make("1.0 (178)", passphrase = "correct horse"))
        // The file has no login in it as it is.
        val text = String(bytes, Charsets.UTF_8)
        for (secret in listOf("viewer", "s3cret")) assertFalse(secret, secret in text)

        val new = tv(OtherTvSecrets())
        val backup = BackupFile.decode(bytes)
        assertTrue(new.sources.all().isEmpty())
        val preview = new.backups.preview(backup)
        assertEquals(1, preview.sources.size)
        assertEquals(2, preview.profiles)
        assertEquals(2, preview.favorites)
        assertTrue(preview.withLogins)

        // A wrong passphrase changes nothing.
        assertEquals(RestoreResult.WrongPassphrase, new.backups.restore(backup, "Correct horse"))
        assertTrue(new.sources.all().isEmpty())

        assertEquals(RestoreResult.Restored(1, needLogins = emptyList()), new.backups.restore(backup, "correct horse"))
        // The source keeps its id, so everything finds its channels as they load: it signs in with its password.
        new.importer.importChannels(id)
        assertEquals(listOf("$id:9105", "$id:9101"), new.channels.favoriteIds.first())
        val channels = new.database.channels().forSource(id).associateBy { it.id }
        assertTrue(channels.getValue("$id:9108").hidden)
        assertTrue(channels.getValue("$id:9107").locked)
        assertFalse(channels.getValue("$id:9105").hidden || channels.getValue("$id:9105").locked)
        val groups = new.database.groups().forSource(id).associateBy { it.id }
        assertTrue(groups.getValue("$id:12").hidden)
        assertEquals("Headlines", groups.getValue("$id:7").customName)
        assertEquals(1, new.settings.choices.first()[SettingKeys.LIVE_LIST_SHOWS])
        assertEquals(HomeRow.Apps, new.settings.homeRowOrder.first().first())
        assertTrue(new.settings.isPin("2468"))
        assertTrue(new.profiles.isPin(kids, "1357"))
        assertTrue(new.settings.setupDoneNow())
        // Sealed with this TV's Keystore, not the other's.
        assertTrue(new.database.sources().get(id)!!.passwordEnc!!.startsWith("other:"))
        // The flags were for that load only.
        assertEquals(emptyList<String>() to emptyList<String>(), new.settings.restoredFlags(id))
    }

    @Test fun withoutLoginsTheSameTvKeepsItsOwnAndAnotherAsksForThem() = runTest {
        val tv = tv()
        val id = tv.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source.id
        tv.importer.importChannels(id)
        tv.channels.toggleFavorite("$id:9105")
        tv.settings.setPin("2468")
        val backup = tv.backups.make("1.0 (178)", passphrase = null, beforeUpdate = true)
        assertNull(backup.logins)
        assertTrue(backup.beforeUpdate)

        // Back on the same TV, as after an update gone wrong: its own logins and PIN stay.
        tv.channels.toggleFavorite("$id:9105")
        tv.settings.choose(SettingKeys.LIVE_LIST_SHOWS, 1)
        assertEquals(RestoreResult.Restored(1, needLogins = emptyList()), tv.backups.restore(backup, passphrase = null))
        tv.importer.importChannels(id)
        assertEquals(listOf("$id:9105"), tv.channels.favoriteIds.first())
        assertNull(tv.settings.choices.first()[SettingKeys.LIVE_LIST_SHOWS])
        assertTrue(tv.settings.isPin("2468"))

        // On another TV, the source comes back without them.
        val other = tv(OtherTvSecrets())
        val name = tv.sources.all().single().name
        assertEquals(RestoreResult.Restored(1, needLogins = listOf(name)), other.backups.restore(backup, passphrase = null))
        val source = other.database.sources().get(id)!!
        assertNull(source.passwordEnc)
        assertNull(source.username)
        assertFalse(other.settings.pinSet.first())
    }

    @Test fun aBackupWithLoginsCanStillBeRestoredWithoutThem() = runTest {
        val old = tv()
        val id = old.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source.id
        val backup = old.backups.make("1.0 (178)", passphrase = "correct horse")

        val new = tv(OtherTvSecrets())
        assertEquals(RestoreResult.Restored(1, needLogins = listOf(old.sources.all().single().name)), new.backups.restore(backup, passphrase = null))
        assertNotNull(new.database.sources().get(id))
    }

    @Test fun restoringReplacesWhatWasHere() = runTest {
        val tv = tv()
        val first = tv.sources.addXtream(xtream.baseUrl, "viewer", "s3cret").source.id
        tv.importer.importChannels(first)
        val empty = tv(OtherTvSecrets()).backups.make("1.0 (178)", passphrase = null)

        tv.backups.restore(empty, passphrase = null)
        assertTrue(tv.sources.all().isEmpty())
        assertTrue(tv.database.channels().all().isEmpty())
        assertTrue(tv.channels.favoriteIds.first().isEmpty())
    }

    @Test fun aBackupLeavesOutWhatBelongsToThisTv() = runTest {
        val tv = tv()
        tv.settings.tvId()
        tv.settings.deviceMac()
        tv.settings.setHomeReplaced(true)
        val text = String(BackupFile.encode(tv.backups.make("1.0 (178)", passphrase = "pass")), Charsets.UTF_8)
        assertFalse(text, "tvId" in text || "deviceMac" in text || "homeReplaced" in text || "dataKey" in text)
    }

    private companion object {
        const val ROUNDS = 1_000
    }
}
