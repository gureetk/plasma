// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an HLS recording reads of a playlist (brief 5.6). */
class HlsPlaylistTest {
    private val base = "http://line.example.org/live/viewer/s3cret/105.m3u8".toHttpUrl()

    @Test fun aMasterPlaylistListsItsVariants() {
        val playlist = HlsPlaylist.parse(
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            http://cdn.line.example.org/1080/index.m3u8
            """.trimIndent(),
            base,
        ) as HlsPlaylist.Master

        assertEquals(
            listOf(
                HlsPlaylist.Variant("http://line.example.org/live/viewer/s3cret/720/index.m3u8".toHttpUrl(), 1_280_000),
                HlsPlaylist.Variant("http://cdn.line.example.org/1080/index.m3u8".toHttpUrl(), 5_000_000),
            ),
            playlist.variants,
        )
    }

    @Test fun aMediaPlaylistNumbersItsSegmentsWithTheirKeys() {
        val playlist = HlsPlaylist.parse(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:1041
            #EXTINF:6.0,
            seg1041.ts
            #EXT-X-KEY:METHOD=AES-128,URI="https://line.example.org/keys/k1?a=1,b=2",IV=0x0000000000000000000000000000ABCD
            #EXTINF:6.0,
            seg1042.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:5.96,
            seg1043.ts
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:6.0,
            seg1044.ts
            """.trimIndent(),
            base,
        ) as HlsPlaylist.Media

        assertEquals(6_000L, playlist.targetDurationMs)
        assertEquals(1041L, playlist.firstSequence)
        assertEquals(listOf(1041L, 1042L, 1043L, 1044L), playlist.segments.map { it.sequence })
        assertEquals("http://line.example.org/live/viewer/s3cret/seg1042.ts".toHttpUrl(), playlist.segments[1].url)
        assertNull(playlist.segments[0].key)
        val key = playlist.segments[1].key!!
        assertEquals("AES-128", key.method)
        // A quoted value keeps its commas.
        assertEquals("https://line.example.org/keys/k1?a=1,b=2".toHttpUrl(), key.url)
        assertArrayEquals(ByteArray(14) + byteArrayOf(0xAB.toByte(), 0xCD.toByte()), key.iv)
        assertTrue(playlist.segments[2].key === key)
        assertNull(playlist.segments[3].key)
        assertEquals(false, playlist.ended)
        assertEquals(false, playlist.fragmented)
    }

    @Test fun byteRangesCarryOnFromTheLastOne() {
        val playlist = HlsPlaylist.parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:4
            #EXTINF:4,
            #EXT-X-BYTERANGE:1000@0
            all.ts
            #EXTINF:4,
            #EXT-X-BYTERANGE:1500
            all.ts
            #EXT-X-ENDLIST
            """.trimIndent(),
            base,
        ) as HlsPlaylist.Media

        assertEquals(listOf(0L until 1_000L, 1_000L until 2_500L), playlist.segments.map { it.range })
        assertTrue(playlist.ended)
    }

    @Test fun fmp4SegmentsFollowTheirInitSegment() {
        val playlist = HlsPlaylist.parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:4
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
            #EXTINF:4,
            seg1.m4s
            #EXT-X-DISCONTINUITY
            #EXT-X-MAP:URI="init2.mp4"
            #EXTINF:4,
            seg2.m4s
            """.trimIndent(),
            base,
        ) as HlsPlaylist.Media

        assertTrue(playlist.fragmented)
        assertEquals(
            listOf(
                HlsPlaylist.Init("http://line.example.org/live/viewer/s3cret/init.mp4".toHttpUrl(), 0L until 720L),
                HlsPlaylist.Init("http://line.example.org/live/viewer/s3cret/init2.mp4".toHttpUrl()),
            ),
            playlist.segments.map { it.init },
        )
    }

    @Test fun aVariantsSeparateAudioIsItsGroupsDefault() {
        val playlist = HlsPlaylist.parse(
            """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Commentary",LANGUAGE="en",URI="audio/commentary.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,URI="audio/main.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="muxed",NAME="English",DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,AUDIO="aud"
            video/1080.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1000000,AUDIO="muxed"
            video/480.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=500000
            video/240.m3u8
            """.trimIndent(),
            base,
        ) as HlsPlaylist.Master

        assertEquals(listOf("aud", "muxed", null), playlist.variants.map { it.audio })
        assertEquals("http://line.example.org/live/viewer/s3cret/audio/main.m3u8".toHttpUrl(), playlist.audioOf(playlist.variants[0])?.url)
        assertEquals("en", playlist.audioOf(playlist.variants[0])?.language)
        // Sound in the variant's own segments, and no group at all.
        assertNull(playlist.audioOf(playlist.variants[1]))
        assertNull(playlist.audioOf(playlist.variants[2]))
    }

    @Test fun whatIsntAPlaylistIsNone() {
        assertNull(HlsPlaylist.parse("<html>Sorry</html>", base))
        assertNull(HlsPlaylist.parse("", base))
    }
}
