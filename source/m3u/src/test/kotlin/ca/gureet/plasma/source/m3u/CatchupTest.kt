// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Catch-up addresses from a playlist's templates (brief 7.3). */
class CatchupTest {
    private val start = Instant.parse("2026-10-21T19:45:00Z")
    private val end = Instant.parse("2026-10-21T22:00:00Z")
    private val now = Instant.parse("2026-10-21T20:41:00Z")
    private val utc: ZoneId = ZoneOffset.UTC

    private fun url(mode: CatchupMode, source: String?, live: String, zone: ZoneId = utc, id: String? = null) =
        Catchup.url(mode, source, live, start, end, now, zone, id)

    @Test fun readsTheModesPlaylistsUse() {
        assertEquals(CatchupMode.Shift, Catchup.modeOf("shift"))
        assertEquals(CatchupMode.Shift, Catchup.modeOf("timeshift"))
        assertEquals(CatchupMode.Flussonic, Catchup.modeOf(" FS "))
        assertEquals(CatchupMode.Xc, Catchup.modeOf("xc"))
        assertNull(Catchup.modeOf("rewind"))
        assertNull(Catchup.modeOf(null))
    }

    @Test fun defaultFillsTheWholeAddress() {
        assertEquals(
            "http://archive.example/arena1/1792611900-1792620000.ts?token=1",
            url(CatchupMode.Default, "http://archive.example/arena1/{utc}-{utcend}.ts?token=1", "http://provider.example/live/1.ts"),
        )
        assertEquals(
            "http://archive.example/arena1/1792611900/8100",
            url(CatchupMode.Default, "http://archive.example/arena1/\${start}/{duration}", "http://provider.example/live/1.ts"),
        )
        assertNull(url(CatchupMode.Default, null, "http://provider.example/live/1.ts"))
    }

    @Test fun appendAddsToTheLiveAddress() {
        assertEquals(
            "http://provider.example/live/1.ts?start=2026-10-21:19-45&dur=135",
            url(CatchupMode.Append, "?start={Y}-{m}-{d}:{H}-{M}&dur={duration:60}", "http://provider.example/live/1.ts"),
        )
    }

    @Test fun shiftAddsTheTimes() {
        assertEquals(
            "http://provider.example/live/1.ts?utc=1792611900&lutc=1792615260",
            url(CatchupMode.Shift, null, "http://provider.example/live/1.ts"),
        )
        assertEquals(
            "http://provider.example/live/1.m3u8?token=a&utc=1792611900&lutc=1792615260",
            url(CatchupMode.Shift, null, "http://provider.example/live/1.m3u8?token=a"),
        )
    }

    @Test fun datePartsFollowTheZone() {
        // 19:45 UTC is 21:45 in Berlin in October (CEST).
        assertEquals(
            "http://archive.example/2026/10/21/21-45.ts",
            url(CatchupMode.Default, "http://archive.example/{Y}/{m}/{d}/{H}-{M}.ts", "http://provider.example/live/1.ts", zone = ZoneId.of("Europe/Berlin")),
        )
        assertEquals(
            "http://archive.example/202610211945/202610212200",
            url(CatchupMode.Default, "http://archive.example/{utc:YmdHM}/{utcend:YmdHM}", "http://provider.example/live/1.ts"),
        )
    }

    @Test fun offsetsAndProgrammeIds() {
        assertEquals(
            "http://archive.example/rel-3360.m3u8?id=evt42",
            url(CatchupMode.Default, "http://archive.example/rel-{offset:1}.m3u8?id={catchup-id}", "http://provider.example/live/1.ts", id = "evt42"),
        )
    }

    @Test fun flussonicArchivesSitBesideTheLiveAddress() {
        assertEquals(
            "http://fs.example/arena1/timeshift_abs-1792611900.ts",
            url(CatchupMode.Flussonic, null, "http://fs.example/arena1/mpegts"),
        )
        assertEquals(
            "http://fs.example/arena1/timeshift_rel-3360.m3u8?token=a",
            url(CatchupMode.Flussonic, null, "http://fs.example/arena1/index.m3u8?token=a"),
        )
        assertEquals(
            "http://fs.example/arena1/video-timeshift_rel-3360.m3u8",
            url(CatchupMode.FlussonicHls, null, "http://fs.example/arena1/video.m3u8"),
        )
        assertNull(url(CatchupMode.Flussonic, null, "http://fs.example/arena1.ts"))
    }

    @Test fun xtreamAddressesBecomeTimeshiftAddresses() {
        assertEquals(
            "http://provider.example:8080/timeshift/viewer/s3cret/135/2026-10-21:19-45/9105.ts",
            url(CatchupMode.Xc, null, "http://provider.example:8080/live/viewer/s3cret/9105.ts"),
        )
        assertEquals(
            "http://provider.example:8080/timeshift/viewer/s3cret/135/2026-10-21:19-45/9105.ts",
            url(CatchupMode.Xc, null, "http://provider.example:8080/viewer/s3cret/9105"),
        )
    }

    @Test fun unknownPlaceholdersStay() {
        assertEquals("http://archive.example/{channel}/1792611900", url(CatchupMode.Default, "http://archive.example/{channel}/{utc}", "http://provider.example/live/1.ts"))
    }
}
