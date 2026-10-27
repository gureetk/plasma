// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Connecting to adbd and running commands, against a fake adbd on 127.0.0.1 (brief 5.13). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AdbConnectionTest {
    private val key = AdbKey.generate()

    @Test fun aKnownKeySignsInAndRunsCommands() {
        FakeAdbd(knows = listOf(key), shell = { "ran: $it\n" }).use { adbd ->
            var asked = false
            AdbConnection.connect("127.0.0.1", adbd.port, key, "Plasma", onAsking = { asked = true }).use { adb ->
                assertEquals("ran: echo hi\n", adb.shell("echo hi"))
                assertEquals("ran: pm list packages\n", adb.shell("pm list packages"))
            }
            adbd.await()
            assertFalse(asked)
            assertEquals(listOf("echo hi", "pm list packages"), adbd.commands.toList())
        }
    }

    @Test fun aNewKeyIsOfferedAndTheTvAsks() {
        FakeAdbd(knows = emptyList(), allowed = true, shell = { "ok" }).use { adbd ->
            var asked = false
            AdbConnection.connect("127.0.0.1", adbd.port, key, "Plasma", onAsking = { asked = true }).use { adb ->
                assertEquals("ok", adb.shell("true"))
            }
            adbd.await()
            assertTrue(asked)
            // The key's text, named, with adb's NUL.
            assertEquals(key.publicKeyText("Plasma") + "\u0000", adbd.offered.get())
        }
    }

    @Test fun aKeyTheTvTurnsDownIsRefused() {
        FakeAdbd(knows = emptyList(), allowed = false).use { adbd ->
            val refused = runCatching { AdbConnection.connect("127.0.0.1", adbd.port, key, "Plasma") }.exceptionOrNull()
            assertTrue("Got $refused", refused is AdbRefused)
            adbd.await()
        }
    }

    @Test fun wirelessDebuggingTakesAPairedKeyOverTls() {
        FakeAdbd(tls = true, paired = listOf(key), shell = { "over tls: $it" }).use { adbd ->
            AdbConnection.connect("127.0.0.1", adbd.port, key, "Plasma").use { adb ->
                assertEquals("over tls: cmd package resolve-activity", adb.shell("cmd package resolve-activity"))
            }
            adbd.await()
        }
    }

    @Test fun wirelessDebuggingTurnsAnUnpairedKeyDown() {
        FakeAdbd(tls = true, paired = listOf(AdbKey.generate())).use { adbd ->
            val refused = runCatching { AdbConnection.connect("127.0.0.1", adbd.port, key, "Plasma") }.exceptionOrNull()
            assertTrue("Got $refused", refused is AdbRefused)
            adbd.await()
        }
    }
}
