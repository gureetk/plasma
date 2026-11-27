// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The QR code's address and the mDNS advert (brief 5.22), as the TV writes them and the apps read them. */
class DiscoveryTest {
    private val fingerprint = "q1fZ8mUe0bS3kQyHn2c7Vt5pWx9rJdA4LgEoBiKs-_Y"

    @Test fun theQrCodesAddressReadsBackWithItsFingerprint() {
        val invite = RemoteInvite("192.168.1.42:8484", "7KQ4MZ", fingerprint)

        assertEquals("http://192.168.1.42:8484/remote?code=7KQ4MZ&fp=$fingerprint", invite.url)
        assertEquals(invite, RemoteInvite.parse(invite.url))
    }

    @Test fun anAddressWithoutAFingerprintIsAnInviteToTrustOnFirstUse() {
        assertEquals(RemoteInvite("192.168.1.42:8484", "7KQ4MZ"), RemoteInvite.parse("http://192.168.1.42:8484/remote?code=7kq4mz"))
        assertEquals("http://[fe80::1]:8484/remote?code=7KQ4MZ", RemoteInvite("[fe80::1]:8484", "7KQ4MZ").url)
        assertEquals(RemoteInvite("[fe80::1]:8484", "7KQ4MZ"), RemoteInvite.parse("http://[fe80::1]:8484/remote/?code=7KQ4MZ"))
    }

    @Test fun otherCodesAreNotInvites() {
        // Add a Source's code, another app's, an address with no code, a code with a broken fingerprint.
        assertNull(RemoteInvite.parse("http://192.168.1.42:8484/pair?code=7KQ4MZ"))
        assertNull(RemoteInvite.parse("https://example.org/remote?code=7KQ4MZ"))
        assertNull(RemoteInvite.parse("http://192.168.1.42:8484/remote"))
        assertNull(RemoteInvite.parse("http://192.168.1.42:8484/remote?code=7KQ4MZ&fp=short"))
        assertNull(RemoteInvite.parse("http://user@192.168.1.42:8484/remote?code=7KQ4MZ"))
        assertNull(RemoteInvite.parse("WIFI:S:Home;T:WPA;P:secret;;"))
    }

    @Test fun aFingerprintIsSha256InBase64UrlWithoutPadding() {
        assertTrue(RemoteInvite.isFingerprint(fingerprint))
        assertFalse(RemoteInvite.isFingerprint("$fingerprint="))
        assertFalse(RemoteInvite.isFingerprint(fingerprint.replace('-', '+')))
    }

    @Test fun theAdvertsTxtEntriesAndItsJsonSayTheSame() {
        val advert = RemoteAdvert("tv-1", "Living Room TV", RemoteProtocol.VERSION, 8486)

        assertEquals(mapOf("id" to "tv-1", "name" to "Living Room TV", "v" to "1", "tls" to "8486"), advert.txt())
        assertEquals(advert, RemoteAdvert.fromTxt(advert.txt()))
        assertEquals(advert, RemoteAdvert.fromJson(advert.json()))
        assertEquals("_plasma-remote._tcp", RemoteAdvert.SERVICE_TYPE)
    }

    @Test fun anAdvertWithAnEntryMissingIsNoTv() {
        assertNull(RemoteAdvert.fromTxt(mapOf("id" to "tv-1", "name" to "Living Room TV", "v" to "1")))
        assertNull(RemoteAdvert.fromTxt(mapOf("id" to "tv-1", "name" to "Living Room TV", "v" to "1", "tls" to "0")))
        assertNull(RemoteAdvert.fromTxt(mapOf("id" to "", "name" to "Living Room TV", "v" to "1", "tls" to "8486")))
        assertNull(RemoteAdvert.fromJson("{}"))
        // A newer TV's extra entries are let be.
        assertEquals("tv-1", RemoteAdvert.fromTxt(mapOf("id" to "tv-1", "name" to "TV", "v" to "2", "tls" to "8486", "more" to "x"))?.id)
    }
}
