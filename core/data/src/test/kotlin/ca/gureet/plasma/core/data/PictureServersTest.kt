// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.data.PictureServers.Look
import ca.gureet.plasma.core.data.UnloadablePictures.Why
import ca.gureet.plasma.core.database.PictureServerRow
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

/**
 * Checking the servers the pictures are on (#228): each asked for a picture or two, what each
 * answer says of the server, and what's given up, kept or left alone.
 */
class PictureServersTest {
    private val unloadable = UnloadablePictures({ 0L })

    @Test fun aPictureIsToldByItsFirstBytes() {
        assertEquals(Look.Picture, PictureServers.lookOfBody("image/jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals(Look.Picture, PictureServers.lookOfBody(null, bytes(0x89, 'P'.code, 'N'.code, 'G'.code, 0x0D, 0x0A)))
        assertEquals(Look.Picture, PictureServers.lookOfBody("text/plain", "GIF89a".toByteArray()))
        assertEquals(Look.Picture, PictureServers.lookOfBody(null, "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)))
        assertEquals(Look.Picture, PictureServers.lookOfBody(null, "\u0000\u0000\u0000\u001CftypAVIF".toByteArray(Charsets.ISO_8859_1)))
        // A web page, as a name that has lapsed shows for every address, whatever it's called.
        assertEquals(Look.WebPage, PictureServers.lookOfBody("text/html; charset=utf-8", "<!DOCTYPE html><ht".toByteArray()))
        assertEquals(Look.WebPage, PictureServers.lookOfBody(null, "\n  <html><head>".toByteArray()))
        assertEquals(Look.WebPage, PictureServers.lookOfBody("text/html", ByteArray(0)))
        // A drawing Plasma can't show, on a server that answers.
        assertEquals(Look.Unshowable, PictureServers.lookOfBody("image/svg+xml", "<svg xmlns=\"http".toByteArray()))
        assertEquals(Look.Unsure, PictureServers.lookOfBody("application/octet-stream", bytes(0x01, 0x02, 0x03)))
    }

    @Test fun anAnswerIsToldByItsCode() {
        val jpeg = bytes(0xFF, 0xD8, 0xFF)
        assertEquals(Look.Picture, PictureServers.lookOf(200, "image/jpeg", jpeg))
        assertEquals(Look.Missing, PictureServers.lookOf(404, "text/html", ByteArray(0)))
        assertEquals(Look.Missing, PictureServers.lookOf(410, null, ByteArray(0)))
        assertEquals(Look.TooLong, PictureServers.lookOf(414, null, ByteArray(0)))
        assertEquals(Look.Refused, PictureServers.lookOf(403, null, ByteArray(0)))
        assertEquals(Look.Refused, PictureServers.lookOf(401, null, ByteArray(0)))
        assertEquals(Look.Unsure, PictureServers.lookOf(503, null, ByteArray(0)))
        assertEquals(Look.Unsure, PictureServers.lookOf(429, null, ByteArray(0)))
    }

    @Test fun aFailedAskIsToldByHowItFailed() {
        assertEquals(Look.NoServer, PictureServers.lookOf(UnknownHostException("lapsed.home.lan")))
        assertEquals(Look.NoAnswer, PictureServers.lookOf(ConnectException("Failed to connect to …")))
        assertEquals(Look.NoAnswer, PictureServers.lookOf(SocketTimeoutException("Connect timed out")))
        assertEquals(Look.Unsure, PictureServers.lookOf(SocketTimeoutException("Read timed out")))
    }

    @Test fun aServersVerdictComesFromItsPictures() {
        fun verdict(vararg looks: Look) = PictureServers.verdict(looks.mapIndexed { i, look -> "https://a.home.lan/$i.jpg" to look })

        // Any picture says it answers; one missing is only that one.
        verdict(Look.Missing, Look.Picture).let {
            assertTrue(it.answers)
            assertNull(it.dead)
            assertEquals(listOf("https://a.home.lan/0.jpg" to Why.NotThere), it.missing)
        }
        // A name not found, no answer, a certificate not trusted: it's gone.
        assertEquals(Why.NoServer, verdict(Look.NoServer).dead)
        assertEquals(Why.NoAnswer, verdict(Look.NoAnswer).dead)
        assertEquals(Why.Untrusted, verdict(Look.Untrusted).dead)
        // Web pages, or refusals, for both: none of its pictures can load.
        assertEquals(Why.NotPictures, verdict(Look.WebPage, Look.WebPage).dead)
        assertEquals(Why.Refused, verdict(Look.Refused, Look.Refused).dead)
        // Both missing: it answers, and only those two can't load.
        verdict(Look.Missing, Look.Missing).let {
            assertTrue(it.answers)
            assertNull(it.dead)
            assertEquals(2, it.missing.size)
        }
        // A web page and a picture that's missing: it answers, as a server does.
        assertTrue(verdict(Look.WebPage, Look.Missing).answers)
        // Busy: too little to tell.
        verdict(Look.Unsure, Look.Unsure).let {
            assertFalse(it.answers)
            assertNull(it.dead)
        }
    }

    @Test fun aCheckGivesUpTheServersThatAreGoneAndKeepsTheRest() = runTest {
        val asked = mutableListOf<String>()
        val check = PictureServers(
            servers = { listOf(row("images.home.lan"), row("lapsed.home.lan"), row("parked.home.lan"), row("busy.home.lan")) },
            ask = { picture ->
                asked += picture
                when (UnloadablePictures.serverOf(picture)) {
                    "images.home.lan" -> Look.Picture
                    "lapsed.home.lan" -> Look.NoServer
                    "parked.home.lan" -> Look.WebPage
                    else -> Look.Unsure
                }
            },
            unloadable = unloadable,
            networkWorks = { false },
            now = { 7L },
        ).check()

        assertEquals(PictureServers.Check(at = 7L, servers = 4, answer = 1, givenUp = mapOf(Why.NoServer to 1, Why.NotPictures to 1), unsure = 1), check)
        val known = unloadable.known.value
        assertTrue("https://lapsed.home.lan/any.jpg" in known)
        assertTrue("https://parked.home.lan/any.jpg" in known)
        assertFalse("https://images.home.lan/any.jpg" in known)
        assertFalse("https://busy.home.lan/any.jpg" in known)
        // A picture, or a name not found, says enough: the second picture isn't asked for.
        assertEquals(1, asked.count { it.contains("images.home.lan") })
        assertEquals(1, asked.count { it.contains("lapsed.home.lan") })
        assertEquals(2, asked.count { it.contains("parked.home.lan") })
    }

    @Test fun withNothingAnsweringAndNoWordFromAndroidNoServerIsGivenUp() = runTest {
        val check = PictureServers(
            servers = { listOf(row("images.home.lan"), row("lapsed.home.lan")) },
            ask = { Look.NoServer },
            unloadable = unloadable,
            networkWorks = { false },
        ).check()
        assertEquals(2, check.unsure)
        assertEquals(UnloadablePictures.Known.NONE, unloadable.known.value)

        // Android saying the network reaches the internet is enough.
        PictureServers(servers = { listOf(row("lapsed.home.lan")) }, ask = { Look.NoServer }, unloadable = unloadable, networkWorks = { true }).check()
        assertTrue("https://lapsed.home.lan/any.jpg" in unloadable.known.value)
    }

    private fun row(server: String) = PictureServerRow(server, pictures = 10, firstPicture = "https://$server/a.jpg", lastPicture = "https://$server/z.jpg")

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
