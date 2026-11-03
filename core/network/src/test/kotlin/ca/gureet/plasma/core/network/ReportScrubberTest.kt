// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportScrubberTest {
    private fun scrubber(secrets: List<String> = emptyList(), hosts: List<String> = emptyList()) = ReportScrubber(secrets, hosts)

    @Test fun namesEachServerOnceAndKeepsItsPort() {
        val scrubber = scrubber()

        assertEquals(
            "GET http://server-1:8080/live/***/***/105.ts then http://server-2/epg.xml and http://server-1:8080/live/***/***/106.ts",
            scrubber.scrub(
                "GET http://line.example.org:8080/live/viewer/s3cret/105.ts then http://guide.example.net/epg.xml " +
                    "and http://LINE.example.org:8080/live/viewer/s3cret/106.ts",
            ),
        )
        assertEquals(2, scrubber.serverCount)
    }

    @Test fun keepsTheNamesAcrossFilesOfOneReport() {
        val scrubber = scrubber()
        scrubber.scrub("http://line.example.org/a")

        assertEquals("http://server-2/b http://server-1/c", scrubber.scrub("http://other.example.com/b http://line.example.org/c"))
    }

    @Test fun findsKnownServersOutsideUrls() {
        val scrubber = scrubber(hosts = listOf("line.example.org", "portal.example.net"))

        assertEquals(
            "UnknownHostException: server-1 (portal: server-2, cdn.server-1) not line.example.orgx.com",
            scrubber.scrub("UnknownHostException: line.example.org (portal: Portal.Example.net, cdn.line.example.org) not line.example.orgx.com"),
        )
    }

    @Test fun namesAddressesButNotTheTvsOwn() {
        assertEquals(
            "connect to server-1:8080 failed; hub at http://127.0.0.1:41234/s/1 and [::1]; version 3.0.21",
            scrubber().scrub("connect to 203.0.113.7:8080 failed; hub at http://127.0.0.1:41234/s/1 and [::1]; version 3.0.21"),
        )
    }

    @Test fun hidesKnownSecretsWhereverTheyShow() {
        val scrubber = scrubber(secrets = listOf("viewer", "s3cret", "00:1A:79:4C:2E:91", "ab"))
        val out = scrubber.scrub("access path /live/viewer/s3cret/105.ts as viewer; mac 00:1A:79:4C:2E:91; tab")

        // Redact.text takes a MAC address's last half first, and keeps the maker's half.
        assertEquals("access path /live/***/***/105.ts as ***; mac 00:1A:79:**:**:**; tab", out)
        assertFalse("viewer" in out)
        assertTrue(out.endsWith("tab"))
    }

    @Test fun takesCredentialsOutAsRedactDoes() {
        assertEquals(
            "http://server-1/player_api.php?username=***&password=***&action=get_live_streams",
            scrubber().scrub("http://line.example.org/player_api.php?username=viewer&password=s3cret&action=get_live_streams"),
        )
    }

    @Test fun namesIpv6ServersInUrls() {
        assertEquals("http://server-1:8000/live.ts", scrubber().scrub("http://[2001:db8::7]:8000/live.ts"))
    }
}
