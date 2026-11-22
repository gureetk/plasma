// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.mpv

import org.junit.Assert.assertEquals
import org.junit.Test

/** What mpv says of each stream, and how its lines are named (brief 5.17 and 6.10). */
class MpvLogTest {
    private val hub = "http://127.0.0.1:41234/live/9f2c4a.ts"
    private val provider = "http://media.home.lan/live/viewer/s3cret/105.ts"

    @Test fun throughTheHubItSaysItsWarningsAndWithSavePlayerLogsEverything() {
        assertEquals("warn", MpvEngine.logLevel(hub, verbose = false))
        assertEquals("v", MpvEngine.logLevel(hub, verbose = true))
        // A seek in the rewind buffer, and local catch-up, come from the hub too.
        assertEquals("warn", MpvEngine.logLevel("$hub?at=1880", verbose = false))
        assertEquals("warn", MpvEngine.logLevel("http://127.0.0.1:41234/local/77ab.ts", verbose = false))
    }

    @Test fun straightFromItsProviderOnlyItsErrors() {
        // Everything else would name the provider's address, with the account's details in it.
        assertEquals("error", MpvEngine.logLevel(provider, verbose = false))
        assertEquals("error", MpvEngine.logLevel(provider, verbose = true))
        assertEquals("error", MpvEngine.logLevel("https://media.home.lan/hls/105/index.m3u8", verbose = true))
    }

    @Test fun eachLineHasItsLevelsName() {
        assertEquals("fatal", MpvEngine.levelName(10))
        assertEquals("error", MpvEngine.levelName(20))
        assertEquals("warn", MpvEngine.levelName(30))
        assertEquals("info", MpvEngine.levelName(40))
    }
}
