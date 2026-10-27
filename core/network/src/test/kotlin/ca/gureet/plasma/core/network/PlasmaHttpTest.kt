// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PlasmaHttpTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    @Test fun sendsTheDefaultUserAgentUnlessARequestSetsOne() {
        val client = PlasmaHttp.client(UserAgents.vlc("3.0.21"))
        server.enqueue(MockResponse(body = "ok"))
        server.enqueue(MockResponse(body = "ok"))

        client.newCall(Request.Builder().url(server.url("/a")).build()).execute().close()
        client.newCall(Request.Builder().url(server.url("/b")).header("User-Agent", "MAG250").build()).execute().close()

        assertEquals("VLC/3.0.21 LibVLC/3.0.21", server.takeRequest().headers["User-Agent"])
        assertEquals("MAG250", server.takeRequest().headers["User-Agent"])
    }
}
