// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.SourceTest
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.core.data.SourcePlayback
import ca.gureet.plasma.core.data.SourceType
import ca.gureet.plasma.feature.settings.ConnectProblem
import ca.gureet.plasma.feature.settings.SourceHealth
import ca.gureet.plasma.feature.settings.SourcePlaybackInfo
import ca.gureet.plasma.source.stalker.StalkerException
import ca.gureet.plasma.source.xtream.XtreamException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.ZoneOffset

/** Portals & Playlists on real sources (brief 4.20): their order, state and own settings. */
class SettingsFactsTest {
    private fun source(id: String, priority: Int, enabled: Boolean = true, lastError: String? = null) = Source(
        id = id, type = SourceType.Xtream, name = id, server = "http://$id.example.org/", username = "viewer", priority = priority,
        enabled = enabled, status = "Active", expiresAt = null, maxConnections = 2, activeConnections = 0, streamFormat = "ts",
        userAgent = null, channelsRefreshedAt = null, guideRefreshedAt = null, lastError = lastError, serverTimeZone = "Europe/London",
        outputFormats = listOf("m3u8", "ts"),
    )

    private fun facts(vararg sources: Source, tests: Map<String, SourceTest> = emptyMap()) =
        settingsFacts(sources.toList(), mapOf("home" to 5), tests, mergedChannels = 0, ZoneOffset.UTC, audio = null).sources

    @Test fun channelsCountsTheRealMergedChannels() {
        val withSources = settingsFacts(listOf(source("home", 0), source("backup", 1)), emptyMap(), emptyMap(), mergedChannels = 3, ZoneOffset.UTC, audio = null)

        assertEquals(3, withSources.mergedChannels)
    }

    @Test fun theFirstSourceIsPrimaryAndTheAccountShows() {
        val (home, backup) = facts(source("home", 0), source("backup", 1))

        assertTrue(home.primary)
        assertFalse(backup.primary)
        assertEquals(5, home.channels)
        assertEquals("Active", home.accountStatus)
        assertEquals(listOf("m3u8", "ts"), home.formats)
        assertEquals("Europe/London", home.timeZone)
    }

    @Test fun healthSaysWhatsHappeningMostRecentFirst() {
        val failed = SourceTest.Failed(XtreamException.WrongLogin())
        val sources = facts(
            source("off", 0, enabled = false),
            source("testing", 1),
            source("failed", 2, lastError = "The account is Expired"),
            source("unreachable", 3),
            source("broken", 4, lastError = "The account is Expired"),
            source("passed", 5),
            source("quiet", 6),
            tests = mapOf(
                "off" to SourceTest.Passed(40),
                "testing" to SourceTest.Testing,
                "failed" to failed,
                "unreachable" to SourceTest.Failed(IOException("No route")),
                "passed" to SourceTest.Passed(42),
            ),
        )

        assertEquals(
            listOf(
                SourceHealth.Off,
                SourceHealth.Testing,
                SourceHealth.TestFailed(ConnectProblem.WrongLogin),
                SourceHealth.TestFailed(ConnectProblem.CantReach),
                SourceHealth.RefreshFailed,
                SourceHealth.Connected(pingMs = 42),
                SourceHealth.Connected(pingMs = null),
            ),
            sources.map { it.health },
        )
        assertFalse(sources.first().enabled)
    }

    @Test fun aPortalShowsItsBoxAndPlaysAsAMagBox() {
        val portal = source("portal", 0).copy(type = SourceType.Stalker, mac = "00:1A:79:4C:2E:91", outputFormats = emptyList(), serverTimeZone = null)

        val (info) = facts(portal, tests = mapOf("portal" to SourceTest.Failed(StalkerException.Refused(401))))

        assertEquals(ca.gureet.plasma.feature.settings.SourceType.Stalker, info.type)
        assertEquals("00:1A:79:4C:2E:91", info.mac)
        assertEquals("MAG250", info.playback.userAgent)
        assertEquals(SourceHealth.TestFailed(ConnectProblem.MacRefused), info.health)
    }

    @Test fun playbackForThisSourceGoesBothWays() {
        val playback = SourcePlayback(streamFormat = "m3u8", bufferChoice = 1, audioOutputChoice = null, epgOffsetMinutes = 60, catchupOffsetMinutes = -120)
        val info = playback.toInfo(userAgent = "Custom/1.0")

        assertEquals(SourcePlaybackInfo("m3u8", 1, null, 60, -120, "Custom/1.0"), info)
        assertEquals(playback, info.toPlayback())
    }
}
