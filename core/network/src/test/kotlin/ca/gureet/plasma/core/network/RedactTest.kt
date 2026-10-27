// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class RedactTest {
    @Test fun hidesXtreamCredentialsInPaths() {
        assertEquals("http://line.example.org:8080/live/***/***/105.ts", Redact.url("http://line.example.org:8080/live/viewer/s3cret/105.ts"))
        assertEquals("http://line.example.org/movie/***/***/77.mkv", Redact.url("http://line.example.org/movie/viewer/s3cret/77.mkv"))
        assertEquals(
            "http://line.example.org/timeshift/***/***/120/2026-10-21:19-45/105.ts",
            Redact.url("http://line.example.org/timeshift/viewer/s3cret/120/2026-10-21:19-45/105.ts"),
        )
    }

    @Test fun hidesSecretQueryValues() {
        assertEquals(
            "http://line.example.org/player_api.php?username=***&password=***&action=get_live_streams",
            Redact.url("http://line.example.org/player_api.php?username=viewer&password=s3cret&action=get_live_streams"),
        )
        assertEquals("https://cdn.example.net/a.m3u8?token=***#t=1", Redact.url("https://cdn.example.net/a.m3u8?token=abc#t=1"))
    }

    @Test fun hidesUserInfo() {
        assertEquals("http://***@portal.example.net/c/", Redact.url("http://user:pass@portal.example.net/c/"))
    }

    @Test fun leavesOrdinaryUrlsAlone() {
        val url = "https://img.example.com/logos/arena1.png?w=300"
        assertEquals(url, Redact.url(url))
        assertEquals("http://provider.example/live/105.ts", Redact.url("http://provider.example/live/105.ts"))
    }

    @Test fun redactsUrlsAndMacAddressesInText() {
        assertEquals(
            "Failed http://line.example.org/live/***/***/105.ts for 00:1A:79:**:**:**",
            Redact.text("Failed http://line.example.org/live/viewer/s3cret/105.ts for 00:1A:79:4C:2E:91"),
        )
    }
}
