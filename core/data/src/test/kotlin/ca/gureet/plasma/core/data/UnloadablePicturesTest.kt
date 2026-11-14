// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.data.UnloadablePictures.Why
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnloadablePicturesTest {
    private var now = 0L
    private val pictures = UnloadablePictures { now }
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

    @Test fun aServerIsNamedByItsAddress() {
        assertEquals("img.home.lan", UnloadablePictures.serverOf("https://viewer:s3cret@Img.Home.Lan:8080/a.jpg?x=1#top"))
        assertEquals("img.home.lan", UnloadablePictures.serverOf("http://img.home.lan?x=1"))
        assertEquals("img.home.lan", UnloadablePictures.serverOf("http://img.home.lan"))
        assertEquals("[2001:db8::7]", UnloadablePictures.serverOf("http://[2001:db8::7]:80/p.jpg"))
        assertNull(UnloadablePictures.serverOf("poster.jpg"))
        assertNull(UnloadablePictures.serverOf("https:///a.jpg"))
    }
}
