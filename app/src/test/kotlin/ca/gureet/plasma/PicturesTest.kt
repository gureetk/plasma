// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.core.data.UnloadablePictures
import coil3.network.HttpException
import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.okhttp.asNetworkClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PicturesTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()

    /** A thread of its own for each picture asked for, as Coil's fetches have. */
    private val fetching: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Before fun start() = server.start()

    @After fun stop() {
        // Closed already by a test that ends a read that way.
        runCatching { server.close() }
        fetching.close()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    /** A picture whose response begins at once and then comes slowly: 2 MB at 16 KB a tenth of a second, some 13 s. */
    private fun slowPicture() = server.enqueue(
        MockResponse.Builder()
            .body(Buffer().write(ByteArray(SLOW_BYTES)))
            .throttleBody(THROTTLE_BYTES, THROTTLE_MS, TimeUnit.MILLISECONDS)
            .build(),
    )

    /**
     * Asks [client] for a picture on a thread of its own, reading the response whole as Coil reads
     * one into its disk cache; [reading] is completed once the response has begun, and [failed]
     * with the error if the read ends with one.
     */
    private fun CoroutineScope.ask(client: NetworkClient, reading: CompletableDeferred<Unit>, failed: CompletableDeferred<IOException>): Job =
        launch(fetching) {
            try {
                client.executeRequest(NetworkRequest(server.url("/poster.jpg").toString())) { response ->
                    reading.complete(Unit)
                    response.body!!.writeTo(Buffer())
                }
            } catch (error: IOException) {
                failed.complete(error)
            }
        }

    @Test fun coilsOwnClientReadsAPictureLetGoToItsEnd() = runBlocking {
        // What Plasma's pictures had: let go while it comes, the picture keeps its thread reading.
        slowPicture()
        val reading = CompletableDeferred<Unit>()
        val failed = CompletableDeferred<IOException>()
        val picture = ask(http.asNetworkClient(), reading, failed)
        withTimeout(WAIT_MS) { reading.await() }
        delay(INTO_THE_BODY_MS)
        picture.cancel()
        delay(STILL_READING_MS)
        assertFalse("Coil's own client stopped reading a picture let go: Pictures.kt's LettingGo may no longer be needed", picture.isCompleted)
        // The server goes, and the read with it.
        server.close()
        withTimeout(WAIT_MS) { picture.join() }
        assertTrue(failed.isCompleted)
    }

    @Test fun aPictureLetGoStopsComingAtOnce() = runBlocking {
        slowPicture()
        val reading = CompletableDeferred<Unit>()
        val failed = CompletableDeferred<IOException>()
        val picture = ask(LettingGo(http), reading, failed)
        withTimeout(WAIT_MS) { reading.await() }
        delay(INTO_THE_BODY_MS)
        val letGo = System.nanoTime()
        picture.cancel()
        withTimeout(WAIT_MS) { picture.join() }
        val took = (System.nanoTime() - letGo) / NANOS_PER_MS
        assertTrue("A picture let go took $took ms to stop", took < STOPS_WITHIN_MS)
        // It ended as let go, which Coil counts as cancelled, not as a picture that failed.
        assertFalse("A picture let go ended as a picture that failed", failed.isCompleted)
    }

    @Test fun aPictureThatComesIsReadWholeAndItsConnectionKept() = runBlocking {
        repeat(2) { server.enqueue(MockResponse.Builder().body(Buffer().write(ByteArray(SMALL_BYTES))).build()) }
        val client = LettingGo(http)
        repeat(2) {
            val read = client.executeRequest(NetworkRequest(server.url("/poster.jpg").toString())) { response ->
                Buffer().also { response.body!!.writeTo(it) }.size
            }
            assertEquals(SMALL_BYTES.toLong(), read)
        }
        // Nothing was cancelled: the second picture came over the first one's connection.
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals(first.connectionIndex, second.connectionIndex)
        assertEquals(1, second.exchangeIndex)
    }

    @Test fun aPictureThatTakesTooLongIsGivenUp() = runBlocking {
        slowPicture()
        val started = System.nanoTime()
        try {
            LettingGo(pictureCalls(http, callSeconds = 1)).executeRequest(NetworkRequest(server.url("/poster.jpg").toString())) { response ->
                response.body!!.writeTo(Buffer())
            }
            fail("A picture that takes 13 s came within 1 s")
        } catch (expected: IOException) {
            // Given up at its time limit.
        }
        val took = (System.nanoTime() - started) / NANOS_PER_MS
        assertTrue("A picture over its time limit took $took ms to be given up", took < GIVEN_UP_WITHIN_MS)
    }

    @Test fun countsSayWhereEachPictureCameFromAndWhatsStillComing() {
        var now = 0L
        val counts = PictureCounts { now }
        val pictures = List(5) { Any() }
        pictures.forEach(counts::started)
        now = 2 * NANOS_PER_SECOND
        counts.came(pictures[0], PictureFrom.Memory)
        counts.came(pictures[1], PictureFrom.Network)
        assertTrue(counts.failed(pictures[2], "HTTP 404"))
        counts.letGo(pictures[3])
        now = 5 * NANOS_PER_SECOND
        assertEquals(
            listOf(
                "Pictures since Plasma started: 5 asked for; 1 from memory, 0 from the TV's storage, 1 from the network; 1 couldn't load; 1 let go before they came",
                "Pictures that couldn't load: HTTP 404 × 1",
                "Pictures coming now: 1, the first for 5.0 s; the slowest from the network so far took 2.0 s",
            ),
            counts.lines(),
        )
    }

    @Test fun serversAreCountedByNumberWithoutTheirNames() {
        val counts = PictureCounts { 0L }
        val pictures = List(4) { Any() }
        pictures.forEach(counts::started)
        counts.came(pictures[0], PictureFrom.Network, "images.home.lan")
        counts.failed(pictures[1], "UnknownHostException", "gone.home.lan")
        counts.failed(pictures[2], "UnknownHostException", "gone.home.lan")
        // From memory, a picture's server isn't counted.
        counts.came(pictures[3], PictureFrom.Memory, "images.home.lan")
        val line = counts.lines().last()
        assertEquals("Picture servers, numbered as first asked, without their names: 2; #2 0 came, couldn't load: UnknownHostException × 2; #1 1 came", line)
        assertFalse(line.contains("home.lan"))
    }

    @Test fun aFailureSaysWhetherThePictureWillEverLoad() {
        assertEquals(UnloadablePictures.Why.NoServer, whyNot(UnknownHostException("gone.home.lan")))
        assertEquals(UnloadablePictures.Why.NotThere, whyNot(HttpException(NetworkResponse(code = 404))))
        assertEquals(UnloadablePictures.Why.NotThere, whyNot(HttpException(NetworkResponse(code = 410))))
        assertEquals(UnloadablePictures.Why.AddressTooLong, whyNot(HttpException(NetworkResponse(code = 414))))
        assertEquals(UnloadablePictures.Why.Untrusted, whyNot(SSLHandshakeException("Chain validation failed")))
        assertEquals(UnloadablePictures.Why.Other, whyNot(HttpException(NetworkResponse(code = 503))))
        assertEquals(UnloadablePictures.Why.Other, whyNot(SocketTimeoutException("timeout")))
    }

    @Test fun aKindOfFailureIsNewOnlyTheFirstTime() {
        val counts = PictureCounts { 0L }
        val pictures = List(3) { Any() }
        pictures.forEach(counts::started)
        assertTrue(counts.failed(pictures[0], "SocketTimeoutException"))
        assertFalse(counts.failed(pictures[1], "SocketTimeoutException"))
        assertTrue(counts.failed(pictures[2], "HTTP 403"))
        assertEquals("Pictures that couldn't load: SocketTimeoutException × 2, HTTP 403 × 1", counts.lines()[1])
    }

    @Test fun aFailureSaysWhatWentWrongButNoServer() {
        assertEquals("HTTP 404", pictureFailure(HttpException(NetworkResponse(code = 404))))
        assertEquals("UnknownHostException (…)", pictureFailure(UnknownHostException("posters.example.com")))
        assertEquals(
            "SocketTimeoutException (failed to connect to /… (port 8080) after 10000ms)",
            pictureFailure(SocketTimeoutException("failed to connect to /203.0.113.7 (port 8080) after 10000ms")),
        )
        assertEquals("IOException (unexpected end of stream on …)", pictureFailure(IOException("unexpected end of stream on http://posters.example.com:8080/a/b.jpg")))
        assertEquals("IOException", pictureFailure(IOException()))
        assertEquals("ENOSPC (No space left on device)", withoutPlaces("ENOSPC (No space left on device)"))
        assertEquals("connect to … failed", withoutPlaces("connect to [2001:db8::7]:443 failed"))
    }

    @Test fun threadsAreCountedByFamilyWithWhatTheyreDoing() {
        val reading = arrayOf(
            StackTraceElement("java.net.SocketInputStream", "socketRead0", null, -2),
            StackTraceElement("coil3.network.SourceResponseBody", "writeTo", null, 1),
        )
        val waiting = arrayOf(StackTraceElement("jdk.internal.misc.Unsafe", "park", null, -2))
        val threads = linkedMapOf(
            Thread("DefaultDispatcher-worker-1") to reading,
            Thread("DefaultDispatcher-worker-2") to reading,
            Thread("DefaultDispatcher-worker-3") to waiting,
            Thread("OkHttp https://posters.example.com/a.jpg") to waiting,
            Thread("main") to waiting,
        )
        assertEquals(
            listOf("Threads: 5; DefaultDispatcher-worker 3 (2 reading from the network, 2 in Coil); OkHttp 1; main 1"),
            threadLines(threads),
        )
        assertEquals("plasma-hub-reader", threadFamily("plasma-hub-reader"))
        assertEquals("Thread", threadFamily("Thread-7"))
    }

    private companion object {
        const val SLOW_BYTES = 2 * 1024 * 1024
        const val THROTTLE_BYTES = 16L * 1024
        const val THROTTLE_MS = 100L
        const val SMALL_BYTES = 64 * 1024
        const val WAIT_MS = 5_000L
        const val INTO_THE_BODY_MS = 300L
        const val STILL_READING_MS = 1_000L
        const val STOPS_WITHIN_MS = 1_000L
        const val GIVEN_UP_WITHIN_MS = 3_000L
        const val NANOS_PER_MS = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
