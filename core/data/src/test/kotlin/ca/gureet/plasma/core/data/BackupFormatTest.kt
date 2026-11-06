// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Back Up Settings' file and its sealed logins (brief 5.17 and 6.10, #108). */
class BackupFormatTest {
    // Few rounds, so the tests stay quick; the file says how many it took.
    private val rounds = 1_000

    @Test fun loginsOpenWithTheirPassphraseOnly() {
        val sealed = BackupCrypto.seal("""{"parentalPin":"pbkdf2:1:a:b"}""", "correct horse", rounds)
        assertEquals(rounds, sealed.iterations)
        assertEquals("""{"parentalPin":"pbkdf2:1:a:b"}""", BackupCrypto.open(sealed, "correct horse"))
        assertNull(BackupCrypto.open(sealed, "Correct horse"))
        assertNull(BackupCrypto.open(sealed, ""))
    }

    @Test fun eachSealingHasItsOwnSaltAndIv() {
        val first = BackupCrypto.seal("same", "pass", rounds)
        val second = BackupCrypto.seal("same", "pass", rounds)
        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.iv, second.iv)
        assertNotEquals(first.data, second.data)
    }

    @Test fun aPassphraseIsTheSameHoweverItsAccentsWereTyped() {
        // "é" as one letter, and as "e" with its accent after it.
        val sealed = BackupCrypto.seal("logins", "café", rounds)
        assertEquals("logins", BackupCrypto.open(sealed, "café"))
    }

    @Test fun loginsTamperedWithDontOpen() {
        val sealed = BackupCrypto.seal("logins", "pass", rounds)
        val data = java.util.Base64.getDecoder().decode(sealed.data).also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertNull(BackupCrypto.open(sealed.copy(data = java.util.Base64.getEncoder().encodeToString(data)), "pass"))
    }

    @Test fun loginsSealedSomeOtherWayAreRefused() {
        val sealed = BackupCrypto.seal("logins", "pass", rounds)
        assertThrows(BackupUnreadable::class.java) { BackupCrypto.open(sealed.copy(kdf = "scrypt"), "pass") }
        assertThrows(BackupUnreadable::class.java) { BackupCrypto.open(sealed.copy(iterations = 0), "pass") }
        // A file asking for days of work isn't worked on.
        assertThrows(BackupUnreadable::class.java) { BackupCrypto.open(sealed.copy(iterations = Int.MAX_VALUE), "pass") }
        assertThrows(BackupUnreadable::class.java) { BackupCrypto.open(sealed.copy(salt = "not base64!"), "pass") }
    }

    @Test fun aBackupReadsBackAsItWasWritten() {
        val backup = PlasmaBackup(
            plasma = "1.0 (178)",
            made = 1_762_200_000_000,
            settings = BackupSettings(choices = mapOf("liveListShows" to 1), homeRowOrder = "favorites,apps"),
            sources = listOf(BackupSource(id = "s1", type = "xtream", name = "Home Server", server = "http://192.0.2.1:8080", priority = 0)),
            favorites = listOf(BackupFavorite("default", "s1:105", 1)),
            channels = listOf(BackupChannel("s1:110", locked = true)),
        )
        val bytes = BackupFile.encode(backup)
        val text = String(bytes, Charsets.UTF_8)
        assertTrue(text, text.trimStart().startsWith("{"))
        assertTrue(text, "\"format\": \"plasma-backup\"" in text)
        assertTrue(text, "\"version\": 1" in text)
        // Nulls are left out rather than written.
        assertFalse(text, "null" in text)
        assertEquals(backup, BackupFile.decode(bytes))
    }

    @Test fun whatAnotherFileIsIsSaid() {
        assertEquals(BackupUnreadable.Reason.NotABackup, reason("hello".toByteArray()))
        assertEquals(BackupUnreadable.Reason.NotABackup, reason("""{"format":"tivimate","version":1}""".toByteArray()))
        assertEquals(BackupUnreadable.Reason.NotABackup, reason(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertEquals(BackupUnreadable.Reason.Newer, reason("""{"format":"plasma-backup","version":2}""".toByteArray()))
    }

    @Test fun whatANewerPlasmaAddsIsLeftAlone() {
        val read = BackupFile.decode("""{"format":"plasma-backup","version":1,"plasma":"1.0","somethingNew":[1,2],"favorites":[{"profileId":"default","channelId":"s1:1","position":1,"colour":"red"}]}""".toByteArray())
        assertEquals(listOf(BackupFavorite("default", "s1:1", 1)), read.favorites)
    }

    @Test fun loginsReadBackAsTheyWereWritten() {
        val logins = BackupLogins(
            sources = mapOf("s1" to SourceLogin(username = "viewer", password = "s3cret")),
            parentalPin = "pbkdf2:10000:c2FsdA==:aGFzaA==",
            profilePins = mapOf("kids" to "pbkdf2:10000:c2FsdA==:aGFzaA=="),
        )
        assertEquals(logins, BackupFile.decodeLogins(BackupFile.encodeLogins(logins)))
    }

    private fun reason(bytes: ByteArray): BackupUnreadable.Reason =
        assertThrows(BackupUnreadable::class.java) { BackupFile.decode(bytes) }.reason
}
