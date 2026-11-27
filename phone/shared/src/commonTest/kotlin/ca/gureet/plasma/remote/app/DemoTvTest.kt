// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import ca.gureet.plasma.remote.app.tv.DemoTv
import ca.gureet.plasma.remote.app.ui.clock
import ca.gureet.plasma.remote.protocol.RemoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The demo TV (brief 5.22) answers as Plasma would, with the boards' data. */
class DemoTvTest {
    private val tv = DemoTv("Living Room TV")

    @Test fun itStartsOnTheBoardsMatchTwoMinutesBehindLive() {
        val playing = tv.state.value.playing!!
        assertEquals("105", playing.number)
        assertEquals("Harbour City v Redmoor", playing.title)
        assertEquals(134, playing.behindLive)
        assertTrue(playing.progress!! < playing.live!!)
        assertEquals("Favorite", tv.state.value.keys["red"])
    }

    @Test fun channelKeysAndNumbersChangeChannelAndGoLive() {
        tv.key(RemoteKey.ChannelUp, down = true)
        tv.key(RemoteKey.ChannelUp, down = false)
        assertEquals("106", tv.state.value.playing!!.number)
        assertEquals(0, tv.state.value.playing!!.behindLive)
        tv.key(RemoteKey.ChannelDown, down = true)
        assertEquals("105", tv.state.value.playing!!.number)
        "180".forEach { tv.digits(it.toString()) }
        assertEquals("Wildframe 4K", tv.state.value.playing!!.name)
    }

    @Test fun playPausePausesAndGoLiveCatchesUp() {
        tv.key(RemoteKey.PlayPause, down = true)
        assertTrue(tv.state.value.playing!!.paused)
        tv.goLive()
        assertEquals(0, tv.state.value.playing!!.behindLive)
        assertEquals(false, tv.state.value.playing!!.paused)
    }

    @Test fun favoritesCountFromOneAndShowWhatPlays() = runTest {
        val lists = tv.lists(null, 0)
        assertEquals("favorites", lists.list)
        assertEquals(listOf("1", "2", "3"), lists.channels.take(3).map { it.number })
        assertTrue(lists.channels.first().playing)
        tv.tune(lists.channels[1].id, lists.list)
        assertEquals("Velocity Motor", tv.state.value.playing!!.name)
    }

    @Test fun behindLiveReadsAsAClock() {
        assertEquals("2:14", clock(134))
        assertEquals("0:05", clock(5))
        assertEquals("1:02:14", clock(3734))
    }
}
