// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import ca.gureet.plasma.remote.app.tv.LinkStatus
import ca.gureet.plasma.remote.app.tv.NetworkTv
import ca.gureet.plasma.remote.app.tv.NotThePairedTv
import ca.gureet.plasma.remote.app.tv.PairResult
import ca.gureet.plasma.remote.app.tv.PhoneApp
import ca.gureet.plasma.remote.app.tv.TvDialer
import ca.gureet.plasma.remote.app.tv.TvSocket
import ca.gureet.plasma.remote.app.tv.pair
import ca.gureet.plasma.remote.protocol.RemoteCategory
import ca.gureet.plasma.remote.protocol.RemoteChannel
import ca.gureet.plasma.remote.protocol.RemoteKey
import ca.gureet.plasma.remote.protocol.RemoteList
import ca.gureet.plasma.remote.protocol.RemoteLists
import ca.gureet.plasma.remote.protocol.RemotePlaying
import ca.gureet.plasma.remote.protocol.RemoteProtocol
import ca.gureet.plasma.remote.protocol.RemoteState
import ca.gureet.plasma.remote.protocol.RemoteTitle
import ca.gureet.plasma.remote.protocol.RemoteTitles
import ca.gureet.plasma.remote.protocol.TvMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The phone's side of the protocol (brief 5.21 and 5.22), against a TV that answers as Plasma's does. */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkTvTest {
    private val app = PhoneApp("android", "0.1.0")

    /** A connection to a pretend TV: what the phone sends is kept, and [tv] answers it. */
    private class FakeSocket(val tv: FakeSocket.(type: String, fields: Map<String, String?>) -> Unit) : TvSocket {
        val fromTv = Channel<String>(Channel.UNLIMITED)
        val sent = mutableListOf<String>()
        var closed = false
        override val incoming: ReceiveChannel<String> get() = fromTv

        override suspend fun send(text: String) {
            sent += text
            val (type, fields) = RemoteProtocol.parse(text) ?: return
            tv(type, fields)
        }

        override fun close() {
            closed = true
            fromTv.close()
        }

        fun answer(text: String) {
            fromTv.trySend(text)
        }

        val sentTypes: List<String> get() = sent.mapNotNull { RemoteProtocol.parse(it)?.first }
    }

    private val playing = RemoteState(playing = RemotePlaying("tv:105", "105", "Arena Sports 1", "Harbour City v Redmoor"))

    /** Plasma's answers: welcome, the code 7KQ4MZ, the token "t0k3n", the state, the lists. */
    private fun plasma(): FakeSocket = FakeSocket { type, fields ->
        when (type) {
            "hello" -> answer(TvMessages.welcome("tv-1", "Living Room TV", "1.0.0"))
            "pair" -> if (fields["code"] == "7KQ4MZ") {
                answer(TvMessages.paired("t0k3n"))
                answer(TvMessages.state(playing))
            } else {
                answer(TvMessages.error(RemoteProtocol.Reasons.CODE))
            }
            "auth" -> answer(if (fields["token"] == "t0k3n") TvMessages.state(playing) else TvMessages.error(RemoteProtocol.Reasons.AUTH))
            "ping" -> answer(TvMessages.pong())
            "titles" -> answer(
                TvMessages.titles(
                    RemoteTitles(fields["kind"].orEmpty(), listOf(RemoteCategory("continue", "Continue Watching", 1)), "continue", 0, listOf(RemoteTitle("m:1", "The Last Lighthouse", 2025, progress = 0.42f, poster = true))),
                ),
            )
            "lists" -> answer(TvMessages.lists(RemoteLists(listOf(RemoteList("favorites", "Favorites", 1, true)), "favorites", 0, listOf(RemoteChannel("tv:105", "1", "Arena Sports 1")))))
        }
    }

    @Test fun theRightCodePairsAndGivesTheToken() = runTest {
        val result = pair(plasma(), app, " 7kq4mz ", "Maya’s Pixel")

        val paired = assertIs<PairResult.Paired>(result)
        assertEquals("tv-1", paired.welcome.tvId)
        assertEquals("Living Room TV", paired.welcome.name)
        assertEquals("t0k3n", paired.token)
    }

    @Test fun aWrongCodeIsTurnedAway() = runTest {
        assertEquals(PairResult.Refused(RemoteProtocol.Reasons.CODE), pair(plasma(), app, "AAAAAA", "Phone"))
    }

    @Test fun somethingThatIsntPlasmaIsNoAnswer() = runTest {
        assertEquals(PairResult.NoAnswer, pair(FakeSocket { _, _ -> }, app, "7KQ4MZ", "Phone"))
    }

    @Test fun aPairedPhoneConnectsWithItsTokenAndSeesWhatPlays() = runTest {
        val socket = plasma()
        val tv = NetworkTv("Living Room TV", "t0k3n", app, TvDialer { socket })
        tv.keepConnected(backgroundScope)
        runCurrent()

        assertEquals(listOf("hello", "auth"), socket.sentTypes)
        assertEquals(LinkStatus.Connected, tv.status.value)
        assertEquals("Harbour City v Redmoor", tv.state.value.playing?.title)

        tv.key(RemoteKey.Ok, down = true)
        tv.key(RemoteKey.Ok, down = false)
        tv.tune("tv:106", "sports")
        runCurrent()
        val keys = socket.sent.drop(2).map { RemoteProtocol.parse(it)!! }
        assertEquals(listOf("key" to "down", "key" to "up", "tune" to null), keys.map { it.first to it.second["action"] })
        assertEquals("sports", keys[2].second["list"])
    }

    @Test fun listsComeBackAsAsked() = runTest {
        val socket = plasma()
        val tv = NetworkTv("Living Room TV", "t0k3n", app, TvDialer { socket })
        tv.keepConnected(backgroundScope)
        runCurrent()

        val lists = tv.lists(null, 0)
        assertEquals("favorites", lists?.list)
        assertEquals("Arena Sports 1", lists?.channels?.single()?.name)
    }

    @Test fun moviesAndSeriesComeBackAsAskedAndATitleOpens() = runTest {
        val socket = plasma()
        val tv = NetworkTv("Living Room TV", "t0k3n", app, TvDialer { socket })
        tv.keepConnected(backgroundScope)
        runCurrent()

        val titles = tv.titles(RemoteTitles.SERIES, null, 0)
        assertEquals("series", titles?.kind)
        assertEquals(0.42f, titles?.titles?.single()?.progress)

        tv.open(RemoteTitles.MOVIES, "m:1", play = true)
        runCurrent()
        assertEquals("open" to mapOf("kind" to "movies", "id" to "m:1", "play" to "true"), RemoteProtocol.parse(socket.sent.last()))
    }

    @Test fun aForgottenPhoneStopsAndAsksToPairAgain() = runTest {
        var forgotten: String? = null
        var dials = 0
        val tv = NetworkTv("Living Room TV", "old", app, TvDialer { dials++; plasma() }, onForgotten = { forgotten = it })
        tv.keepConnected(backgroundScope)
        advanceTimeBy(60_000)

        assertEquals(RemoteProtocol.Reasons.AUTH, forgotten)
        assertEquals(1, dials)
    }

    @Test fun aLostConnectionIsMadeAgainAtOnceThenSaysItCantReach() = runTest {
        var dials = 0
        val tv = NetworkTv("Living Room TV", "t0k3n", app, TvDialer {
            dials++
            if (dials == 1) plasma().also { it.close() } else error("Unreachable")
        })
        tv.keepConnected(backgroundScope)
        runCurrent()
        // The first try again is at once, and the app says nothing yet.
        assertEquals(2, dials)
        assertEquals(LinkStatus.Unreachable, tv.status.value)
        advanceTimeBy(600)
        assertTrue(dials >= 3)
    }

    @Test fun twoUnansweredPingsConnectAgain() = runTest {
        val sockets = mutableListOf<FakeSocket>()
        // A TV that stops answering after the state: no pongs.
        val tv = NetworkTv("Living Room TV", "t0k3n", app, TvDialer {
            FakeSocket { type, _ -> if (type == "auth") answer(TvMessages.state(playing)) }.also { sockets += it }
        })
        tv.keepConnected(backgroundScope)
        advanceTimeBy(NetworkTv.PING_MS * 2 + 1)
        assertEquals(1, sockets.size)
        assertEquals(2, sockets[0].sentTypes.count { it == "ping" })
        advanceTimeBy(NetworkTv.PING_MS + 1)
        runCurrent()

        assertTrue(sockets[0].closed)
        assertEquals(2, sockets.size)
    }

    @Test fun anotherCertificateStopsAndAsksToPairAgain() = runTest {
        var ended: String? = null
        var dials = 0
        val tv = NetworkTv("Living Room TV", "t0k3n", app, TvDialer { dials++; throw NotThePairedTv() }, onForgotten = { ended = it })
        val job = tv.keepConnected(backgroundScope)
        advanceTimeBy(60_000)

        assertEquals(NetworkTv.CERTIFICATE, ended)
        assertEquals(1, dials)
        assertTrue(job.isCompleted)
    }

    @Test fun leavingTheFrontClosesTheConnectionAndComingBackConnectsAtOnce() = runTest {
        val sockets = mutableListOf<FakeSocket>()
        val tv = NetworkTv("Living Room TV", "t0k3n", app, TvDialer { plasma().also { sockets += it } })
        val front = tv.keepConnected(backgroundScope)
        runCurrent()
        assertEquals(LinkStatus.Connected, tv.status.value)

        front.cancel()
        runCurrent()
        assertTrue(sockets.single().closed)
        assertEquals(LinkStatus.Connecting, tv.status.value)

        tv.keepConnected(backgroundScope)
        runCurrent()
        assertEquals(2, sockets.size)
        assertEquals(LinkStatus.Connected, tv.status.value)
    }

    @Test fun aConnectionThatWorkedIsTriedAgainAtOnceEachTimeItDrops() = runTest {
        val sockets = mutableListOf<FakeSocket>()
        val tv = NetworkTv("Living Room TV", "t0k3n", app, TvDialer { plasma().also { sockets += it } })
        tv.keepConnected(backgroundScope)
        runCurrent()
        repeat(3) {
            sockets.last().close()
            runCurrent()
            // At once, and never "can't reach": each one got through.
            assertEquals(it + 2, sockets.size)
            assertEquals(LinkStatus.Connected, tv.status.value)
        }
    }

    @Test fun keysPressedWhileTheTvCantBeReachedGoNowhere() = runTest {
        val tv = NetworkTv("Living Room TV", "t0k3n", app, TvDialer { error("Unreachable") })
        tv.keepConnected(backgroundScope)
        tv.key(RemoteKey.Ok, down = true)
        runCurrent()
        assertEquals(null, tv.lists(null, 0))
    }
}
