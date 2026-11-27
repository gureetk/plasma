// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import ca.gureet.plasma.remote.app.tv.TvEndpoint
import ca.gureet.plasma.remote.app.tv.fingerprintOf
import ca.gureet.plasma.remote.app.tv.pathSegment
import kotlin.test.Test
import kotlin.test.assertEquals

class PairedTvTest {
    @Test fun aChannelsIdIsOneSegmentOfTheLogosAddress() {
        assertEquals("tv%3A105", pathSegment("tv:105"))
        assertEquals("Arena%20Sports%201", pathSegment("Arena Sports 1"))
        assertEquals("caf%C3%A9-1_2.3~", pathSegment("café-1_2.3~"))
    }

    @Test fun anIpv6AddressIsBracketed() {
        assertEquals("wss://192.168.1.42:8486/remote/ws", TvEndpoint("192.168.1.42", 8486).url("wss", "/remote/ws"))
        assertEquals("wss://[fe80::1]:8486/remote/ws", TvEndpoint("fe80::1", 8486).url("wss", "/remote/ws"))
        assertEquals("wss://[fe80::1]:8486/remote/ws", TvEndpoint("[fe80::1]", 8486).url("wss", "/remote/ws"))
    }

    @Test fun aFingerprintIsBase64UrlWithoutPadding() {
        // SHA-256 is 32 bytes: 43 characters, as the TV's QR code has it.
        val print = fingerprintOf(ByteArray(32) { (it * 8).toByte() })
        assertEquals(43, print.length)
        assertEquals(false, print.any { it == '+' || it == '/' || it == '=' })
    }
}
