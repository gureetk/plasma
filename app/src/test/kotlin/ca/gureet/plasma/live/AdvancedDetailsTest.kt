// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.SourceDetails
import ca.gureet.plasma.feature.settings.AddSourceForm
import ca.gureet.plasma.feature.settings.AdvancedFields
import ca.gureet.plasma.feature.settings.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Add a Source › Advanced (brief 4.21) between the form and the data layer. */
class AdvancedDetailsTest {
    private fun form(type: SourceType, advanced: Map<String, String>) =
        AddSourceForm(type, "Home Portal", "http://portal.example.net/c/", "", "", primary = false, advanced = advanced)

    @Test fun aPortalsBoxAndLogin() {
        val details = detailsOf(
            form(
                SourceType.Stalker,
                mapOf(
                    AdvancedFields.STB_MODEL to "MAG254",
                    AdvancedFields.TIME_ZONE to "Europe/London",
                    AdvancedFields.SERIAL to "SN0123456789A",
                    AdvancedFields.DEVICE_ID to "DEV1",
                    AdvancedFields.DEVICE_ID2 to "DEV2",
                    AdvancedFields.SIGNATURE to "SIG",
                    AdvancedFields.USER_AGENT to "Custom agent",
                    AdvancedFields.LOGIN to "family",
                    AdvancedFields.LOGIN_PASSWORD to "s3cret",
                ),
            ),
        )

        assertEquals(
            SourceDetails(
                userAgent = "Custom agent", stbModel = "MAG254", serialNumber = "SN0123456789A", deviceId = "DEV1", deviceId2 = "DEV2",
                signature = "SIG", timeZone = "Europe/London", login = "family", password = "s3cret",
            ),
            details,
        )
        // Edit shows it all again, but the password.
        assertEquals(
            mapOf(
                AdvancedFields.USER_AGENT to "Custom agent", AdvancedFields.STB_MODEL to "MAG254", AdvancedFields.SERIAL to "SN0123456789A",
                AdvancedFields.DEVICE_ID to "DEV1", AdvancedFields.DEVICE_ID2 to "DEV2", AdvancedFields.SIGNATURE to "SIG",
                AdvancedFields.TIME_ZONE to "Europe/London", AdvancedFields.LOGIN to "family",
            ),
            advancedOf(details.copy(password = null)),
        )
    }

    @Test fun theUsualChoicesAreTheDefaults() {
        val details = detailsOf(form(SourceType.Stalker, mapOf(AdvancedFields.STB_MODEL to "MAG250", AdvancedFields.LOGIN_PASSWORD to "stray")))

        // MAG250 is the usual box, and a password without a login goes nowhere.
        assertEquals(SourceDetails(), details)
    }

    @Test fun anEmptyPasswordWithALoginKeepsTheOneStored() {
        val details = detailsOf(form(SourceType.Stalker, mapOf(AdvancedFields.LOGIN to "family")))

        assertEquals("family", details.login)
        assertEquals("", details.password)
    }

    @Test fun anXtreamServersFormatAndGuide() {
        val details = detailsOf(form(SourceType.Xtream, mapOf(AdvancedFields.FORMAT to "m3u8", AdvancedFields.EXTRA_GUIDE to "http://guide.example.org/extra.xml")))

        assertEquals(SourceDetails(streamFormat = "m3u8", extraGuide = "http://guide.example.org/extra.xml"), details)
        // Edit shows the format it plays in, whichever it is.
        assertEquals(mapOf(AdvancedFields.FORMAT to "ts"), advancedOf(SourceDetails(streamFormat = "ts")))
        assertNull(detailsOf(form(SourceType.Xtream, mapOf(AdvancedFields.FORMAT to "mkv"))).streamFormat)
    }

    @Test fun aPlaylistsRefreshInterval() {
        assertEquals(0, detailsOf(form(SourceType.M3U, mapOf(AdvancedFields.REFRESH to "0"))).refreshHours)
        assertEquals(6, detailsOf(form(SourceType.M3U, mapOf(AdvancedFields.REFRESH to "6"))).refreshHours)
        assertNull(detailsOf(form(SourceType.M3U, mapOf(AdvancedFields.REFRESH to "5"))).refreshHours)
        // Without its own, it's Portals & Playlists' Refresh: nothing to show on Edit.
        assertEquals(emptyMap<String, String>(), advancedOf(SourceDetails()))
        assertEquals(mapOf(AdvancedFields.REFRESH to "12"), advancedOf(SourceDetails(refreshHours = 12)))
    }
}
