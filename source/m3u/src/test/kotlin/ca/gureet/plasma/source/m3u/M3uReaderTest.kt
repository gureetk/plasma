// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class M3uReaderTest {
    private fun read(text: String): Pair<List<M3uHeader>, List<M3uEntry>> = read(text.toByteArray())

    private fun read(bytes: ByteArray): Pair<List<M3uHeader>, List<M3uEntry>> {
        val headers = mutableListOf<M3uHeader>()
        val entries = mutableListOf<M3uEntry>()
        M3uReader.read(ByteArrayInputStream(bytes), onHeader = { headers += it }, onEntry = { entries += it })
        return headers to entries
    }

    @Test
    fun readsTheBriefsExampleEntry() {
        val (_, entries) = read(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="arena1.example" tvg-name="Arena Sports 1" tvg-logo="https://img.example/arena1.png" tvg-chno="105" group-title="Sports" catchup="shift" catchup-days="7",Arena Sports 1
            #EXTVLCOPT:http-user-agent=Mozilla/5.0
            http://provider.example/live/105.ts
            """.trimIndent(),
        )
        val entry = entries.single()
        assertEquals("Arena Sports 1", entry.name)
        assertEquals("http://provider.example/live/105.ts", entry.url)
        assertEquals("arena1.example", entry.tvgId)
        assertEquals("Arena Sports 1", entry.tvgName)
        assertEquals("https://img.example/arena1.png", entry.logo)
        assertEquals(105, entry.number)
        assertEquals("Sports", entry.group)
        assertEquals("shift", entry.catchup)
        assertEquals(7, entry.catchupDays)
        assertEquals("Mozilla/5.0", entry.userAgent)
        assertEquals(M3uKind.Live, entry.kind)
    }

    @Test
    fun theHeaderNamesTheGuides() {
        val (headers, _) = read(
            """
            #EXTM3U url-tvg="http://guide.example/a.xml.gz,http://guide.example/b.xml" x-tvg-url="http://guide.example/a.xml.gz"
            #EXTINF:-1,Harbour News
            http://provider.example/live/1.ts
            """.trimIndent(),
        )
        assertEquals(listOf("http://guide.example/a.xml.gz", "http://guide.example/b.xml"), headers.single().guideUrls)
    }

    @Test
    fun theHeadersCatchUpAppliesToChannelsWithoutTheirOwn() {
        val (_, entries) = read(
            """
            #EXTM3U catchup="append" catchup-days="3" catchup-source="?utc={utc}"
            #EXTINF:-1 tvg-id="news.example",Harbour News
            http://provider.example/live/1.ts
            #EXTINF:-1 catchup="flussonic" catchup-days="5",Arena Sports 1
            http://provider.example/live/2.ts
            """.trimIndent(),
        )
        assertEquals("append", entries[0].catchup)
        assertEquals(3, entries[0].catchupDays)
        assertEquals("?utc={utc}", entries[0].catchupSource)
        assertEquals("flussonic", entries[1].catchup)
        assertEquals(5, entries[1].catchupDays)
    }

    @Test
    fun commasInsideQuotesBelongToTheValueAndCommasAfterToTheTitle() {
        val (_, entries) = read(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id=news.example group-title="News, Weather",Harbour News, Late Edition
            http://provider.example/live/1.ts
            """.trimIndent(),
        )
        val entry = entries.single()
        assertEquals("news.example", entry.tvgId)
        assertEquals("News, Weather", entry.group)
        assertEquals("Harbour News, Late Edition", entry.name)
    }

    @Test
    fun extgrpGivesTheGroupWhenTheEntryHasNone() {
        val (_, entries) = read(
            """
            #EXTM3U
            #EXTINF:-1,Harbour News
            #EXTGRP:News
            http://provider.example/live/1.ts
            #EXTINF:-1 group-title="Sports",Arena Sports 1
            #EXTGRP:News
            http://provider.example/live/2.ts
            #EXTINF:-1,Polar Kids
            http://provider.example/live/3.ts
            """.trimIndent(),
        )
        assertEquals(listOf("News", "Sports", null), entries.map { it.group })
    }

    @Test
    fun onlyTheUserAgentAndReferrerOptionsAreKept() {
        val (_, entries) = read(
            """
            #EXTM3U
            #EXTINF:-1,Harbour News
            #EXTVLCOPT:network-caching=1000
            #EXTVLCOPT:http-referrer=http://portal.example/
            #EXTVLCOPT:http-user-agent=PlasmaTest/1.0
            http://provider.example/live/1.ts
            #EXTINF:-1,Arena Sports 1
            http://provider.example/live/2.ts
            """.trimIndent(),
        )
        assertEquals("PlasmaTest/1.0", entries[0].userAgent)
        assertEquals("http://portal.example/", entries[0].referrer)
        // Options belong to one channel only.
        assertNull(entries[1].userAgent)
        assertNull(entries[1].referrer)
    }

    @Test
    fun moviesAndSeriesAreToldApartByTheirAddresses() {
        val (_, entries) = read(
            """
            #EXTM3U
            #EXTINF:-1,Harbour News
            http://provider.example/user/pass/1
            #EXTINF:-1,The Lighthouse
            http://provider.example/movie/user/pass/20.mkv
            #EXTINF:-1,North Of S01 E01
            http://provider.example/series/user/pass/30.mp4
            #EXTINF:-1,Polar
            http://files.example/films/polar.mp4?token=abc
            #EXTINF:-1,Arena Sports 1
            http://provider.example/hls/arena1/index.m3u8
            """.trimIndent(),
        )
        assertEquals(
            listOf(M3uKind.Live, M3uKind.Movie, M3uKind.Series, M3uKind.Movie, M3uKind.Live),
            entries.map { it.kind },
        )
    }

    @Test
    fun aWebPageIsNotAPlaylist() {
        val failed = runCatching { read("<!DOCTYPE html>\n<html><body>Log in</body></html>") }.exceptionOrNull()
        assertTrue(failed is NotAPlaylist)
        assertTrue(runCatching { read("") }.exceptionOrNull() is NotAPlaylist)
    }

    @Test
    fun readsGzippedPlaylistsAndSkipsTheByteOrderMark() {
        val text = "﻿#EXTM3U\n#EXTINF:-1,Harbour News\nhttp://provider.example/live/1.ts\n"
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
        assertEquals("Harbour News", read(zipped).second.single().name)
        assertEquals("Harbour News", read(text).second.single().name)
    }

    @Test
    fun entriesWithoutATitleAreNamedAfterTheirAddressAndBarePathsAreSkipped() {
        val (_, entries) = read(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="Harbour News",
            http://provider.example/live/1.ts
            http://provider.example/streams/arena%20one.m3u8
            #EXTINF:-1,Relative
            segment1.ts
            #EXTINF:-1 radio="true",Coast FM
            http://radio.example/coast
            """.trimIndent(),
        )
        assertEquals(listOf("Harbour News", "arena one", "Coast FM"), entries.map { it.name })
        assertFalse(entries[0].isRadio)
        assertTrue(entries[2].isRadio)
    }

    @Test
    fun keysStayPutWhenAddressesChange() {
        val entries = listOf(
            M3uEntry("Harbour News", "http://provider.example/user/pass/101"),
            M3uEntry("Harbour News", "http://provider.example/live/user/pass/101.ts"),
            M3uEntry("Arena Sports 1", "http://cdn.example/hls/arena1/index.m3u8?token=1", tvgId = "Arena1.example"),
            M3uEntry("Arena Sports 1 HD", "http://cdn.example/hls/arena1hd/index.m3u8?token=1", tvgId = "Arena1.example"),
            M3uEntry("Polar  Kids", "http://cdn.example/kids/index.m3u8?token=2"),
            M3uEntry("polar kids", "http://cdn.example/kids2/index.m3u8?token=2"),
        )
        assertEquals(
            listOf("id:101", "id:101#2", "tvg:arena1.example/arena sports 1", "tvg:arena1.example/arena sports 1 hd", "name:polar kids", "name:polar kids#2"),
            M3uKeys.of(entries),
        )
        // A new token in the address keeps the key.
        assertEquals(
            M3uKeys.base(entries[2]),
            M3uKeys.base(entries[2].copy(url = "http://cdn.example/hls/arena1/index.m3u8?token=99")),
        )
    }
}
