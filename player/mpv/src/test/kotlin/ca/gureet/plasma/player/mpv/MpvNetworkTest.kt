// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.mpv

import org.junit.Assert.assertEquals
import org.junit.Test

/** How mpv reconnects (brief 5.4.8, the owner's report of 3 November). */
class MpvNetworkTest {
    @Test fun liveStreamsReconnectAndHlsSegmentsDont() {
        val options = MpvEngine.NETWORK_OPTIONS.toMap()
        // A live stream that goes straight to mpv reconnects when its server drops it.
        assertEquals("reconnect=1,reconnect_streamed=1", options["stream-lavf-o"])
        // mpv hands those to HLS's own connections too; the demuxer's options win there, and turn
        // them off, since a segment sent without its size was asked for again and again.
        assertEquals("reconnect=0,reconnect_streamed=0", options["demuxer-lavf-o"])
    }
}
