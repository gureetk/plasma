// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.data.UnloadablePictures.Why
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnloadablePicturesTest {
    private var now = 0L
    private val pictures = UnloadablePictures({ now })
    private val known get() = pictures.known.value

    @Test fun aPictureThatIsntThereIsKnownAtOnce() {
        pictures.failed("https://images.home.lan/a.jpg", Why.NotThere)
        pictures.failed("https://images.home.lan/b.jpg?size=" + "x".repeat(10), Why.AddressTooLong)
        assertTrue("https://images.home.lan/a.jpg" in known)
        assertTrue("https://images.home.lan/b.jpg?size=" + "x".repeat(10) in known)
        // Only that picture: its server's others are asked for.
        assertFalse("https://images.home.lan/c.jpg" in known)
        assertFalse(null in known)
    }

    @Test fun aServerNotFoundCountsOnlyWhilePicturesComeFromOthers() {
        // Nothing has come yet: the network may not be there.
        pictures.failed("https://gone.home.lan/1.jpg", Why.NoServer)
        assertFalse("https://gone.home.lan/1.jpg" in known)

        pictures.came("https://images.home.lan/a.jpg")
        pictures.failed("https://gone.home.lan/1.jpg", Why.NoServer)
        assertTrue("https://gone.home.lan/1.jpg" in known)
        assertFalse("https://gone.home.lan/2.jpg" in known)

        // A third failure gives the server up: its other pictures aren't asked for.
        pictures.failed("https://gone.home.lan/2.jpg", Why.NoServer)
        pictures.failed("https://gone.home.lan/3.jpg", Why.Untrusted)
        assertTrue("https://GONE.home.lan:8080/9.jpg" in known)
        assertEquals(3 to 1, known.sizes)
    }

    @Test fun aServerPicturesCameFromIsNeverGivenUp() {
        pictures.came("https://images.home.lan/a.jpg")
        repeat(5) { pictures.failed("https://images.home.lan/$it.jpg", Why.NoServer) }
        assertEquals(0 to 0, known.sizes)
    }

    @Test fun longAfterAnyPictureCameAServerNotFoundIsTheNetworksDoing() {
        pictures.came("https://images.home.lan/a.jpg")
        now += UnloadablePictures.LATELY_MS + 1
        pictures.failed("https://gone.home.lan/1.jpg", Why.NoServer)
        assertFalse("https://gone.home.lan/1.jpg" in known)
    }

    @Test fun otherFailuresMayWorkNextTime() {
        pictures.came("https://images.home.lan/a.jpg")
        pictures.failed("https://slow.home.lan/1.jpg", Why.Other)
        assertEquals(UnloadablePictures.Known.NONE, known)
    }

    @Test fun whenAndroidSaysTheNetworkWorksAServerNotFoundCountsAtOnce() {
        var reaches = true
        val pictures = UnloadablePictures({ now }, networkWorks = { reaches })
        // Nothing has come from any server yet, but Android says the network reaches the internet.
        pictures.failed("https://gone.home.lan/1.jpg", Why.NoServer)
        assertTrue("https://gone.home.lan/1.jpg" in pictures.known.value)

        // It's asked once a second at most.
        reaches = false
        pictures.failed("https://gone.home.lan/2.jpg", Why.NoServer)
        assertTrue("https://gone.home.lan/2.jpg" in pictures.known.value)
        now += 1_000
        pictures.failed("https://gone.home.lan/3.jpg", Why.NoServer)
        assertFalse("https://gone.home.lan/3.jpg" in pictures.known.value)
    }

    @Test fun whatsKnownIsKeptForADay() {
        val store = KeptInMemory()
        val first = UnloadablePictures({ now }, store) { true }
        first.failed("https://images.home.lan/missing.jpg", Why.NotThere)
        repeat(UnloadablePictures.GIVEN_UP_AFTER) { first.failed("https://gone.home.lan/$it.jpg", Why.NoServer) }

        // The next run knows them before anything is asked for.
        now += 60 * 60 * 1000
        val next = UnloadablePictures({ now }, store)
        assertTrue("https://images.home.lan/missing.jpg" in next.known.value)
        assertTrue("https://gone.home.lan/anything.jpg" in next.known.value)

        // A day after they were found, they're tried again.
        now += UnloadablePictures.RECHECK_MS
        val later = UnloadablePictures({ now }, store)
        assertEquals(0 to 0, later.known.value.sizes)
    }

    @Test fun aServerAPictureComesFromIsntGivenUpAnyMore() {
        val store = KeptInMemory()
        val first = UnloadablePictures({ now }, store) { true }
        repeat(UnloadablePictures.GIVEN_UP_AFTER) { first.failed("https://flaky.home.lan/$it.jpg", Why.NoServer) }
        val next = UnloadablePictures({ now }, store)
        assertTrue("https://flaky.home.lan/9.jpg" in next.known.value)

        // A logo comes from it after all: its pictures are asked for again, in this run and the next.
        next.came("https://flaky.home.lan/logo.png")
        assertEquals(0 to 0, next.known.value.sizes)
        assertEquals(0 to 0, UnloadablePictures({ now }, store).known.value.sizes)
    }

    @Test fun aFileKeepsThemForTheNextRun() {
        val file = File.createTempFile("unloadable", ".txt").apply { delete() }
        try {
            val first = UnloadablePictures({ now }, UnloadablePictures.FileStore(file))
            first.failed("https://images.home.lan/missing.jpg", Why.NotThere)
            // Written a moment later, on a thread of its own.
            val waited = System.nanoTime()
            while (!file.isFile && System.nanoTime() - waited < 10_000_000_000L) Thread.sleep(50)

            val next = UnloadablePictures({ now }, UnloadablePictures.FileStore(file))
            val read = System.nanoTime()
            while ("https://images.home.lan/missing.jpg" !in next.known.value && System.nanoTime() - read < 5_000_000_000L) Thread.sleep(20)
            assertTrue("https://images.home.lan/missing.jpg" in next.known.value)
        } finally {
            file.delete()
        }
    }

    /** A [UnloadablePictures.Store] in memory, read and written at once. */
    private class KeptInMemory : UnloadablePictures.Store {
        private var kept: String? = null

        override fun read(read: (String) -> Unit) {
            kept?.let(read)
        }

        override fun write(text: () -> String) {
            kept = text()
        }
    }

    @Test fun aServerThatDoesntAnswerCountsAsOneNotFoundDoes() {
        pictures.came("https://images.home.lan/a.jpg")
        pictures.failed("https://quiet.home.lan/1.jpg", Why.NoAnswer)
        pictures.failed("https://quiet.home.lan/2.jpg", Why.NoAnswer)
        assertFalse("https://quiet.home.lan/9.jpg" in known)
        pictures.failed("https://quiet.home.lan/3.jpg", Why.NoAnswer)
        assertTrue("https://quiet.home.lan/9.jpg" in known)
    }

    @Test fun aPictureItsServerWontGiveIsKnownAtOnceAndOnlyIt() {
        pictures.failed("https://images.home.lan/private.jpg", Why.Refused)
        assertTrue("https://images.home.lan/private.jpg" in known)
        assertFalse("https://images.home.lan/other.jpg" in known)
    }

    @Test fun aCheckGivesAServerUpAtOnceOrKeepsIt() {
        // The check is trusted for the network: it gives up a server at once, its pictures with it.
        pictures.checked("lapsed.home.lan", Why.NotPictures)
        assertTrue("https://lapsed.home.lan/poster.jpg" in known)

        // A server that answers a check is one pictures come from: it's taken back, and a failure
        // of it later doesn't count.
        pictures.checked("lapsed.home.lan", dead = null)
        assertFalse("https://lapsed.home.lan/poster.jpg" in known)
        repeat(4) { pictures.failed("https://lapsed.home.lan/$it.jpg", Why.NoServer) }
        assertFalse("https://lapsed.home.lan/9.jpg" in known)
    }

    @Test fun aCheckDoesntGiveUpAServerAPictureCameFromInThisRun() {
        pictures.came("https://images.home.lan/a.jpg")
        pictures.checked("images.home.lan", Why.NoServer)
        assertFalse("https://images.home.lan/b.jpg" in known)
    }

    @Test fun aCheckThatFindsAServerGoneAgainKeepsItForAnotherDay() {
        pictures.checked("gone.home.lan", Why.NoServer)
        now += UnloadablePictures.RECHECK_MS - 1_000
        pictures.checked("gone.home.lan", Why.NoServer)
        now += 2_000
        pictures.failed("https://images.home.lan/missing.jpg", Why.NotThere)
        assertTrue("https://gone.home.lan/poster.jpg" in known)
    }

    @Test fun howAskingFailedSaysWhy() {
        assertEquals(Why.NoServer, UnloadablePictures.whyNot(java.net.UnknownHostException("gone.home.lan")))
        assertEquals(Why.NoAnswer, UnloadablePictures.whyNot(java.net.ConnectException("Failed to connect to …")))
        assertEquals(Why.NoAnswer, UnloadablePictures.whyNot(java.net.SocketTimeoutException("Connect timed out")))
        // A picture that took too long once connected may come next time.
        assertEquals(Why.Other, UnloadablePictures.whyNot(java.net.SocketTimeoutException("timeout")))
        assertEquals(Why.Untrusted, UnloadablePictures.whyNot(javax.net.ssl.SSLHandshakeException("Chain validation failed")))
        assertEquals(Why.Other, UnloadablePictures.whyNot(java.io.IOException("unexpected end of stream")))
    }

    @Test fun aServerIsNamedByItsAddress() {
        assertEquals("img.home.lan", UnloadablePictures.serverOf("https://viewer:s3cret@Img.Home.Lan:8080/a.jpg?x=1#top"))
        assertEquals("img.home.lan", UnloadablePictures.serverOf("http://img.home.lan?x=1"))
        assertEquals("img.home.lan", UnloadablePictures.serverOf("http://img.home.lan"))
        assertEquals("[2001:db8::7]", UnloadablePictures.serverOf("http://[2001:db8::7]:80/p.jpg"))
        assertNull(UnloadablePictures.serverOf("poster.jpg"))
        assertNull(UnloadablePictures.serverOf("https:///a.jpg"))
    }
}
