// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import java.io.IOException
import java.io.OutputStream
import java.io.Writer
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.concurrent.thread

/**
 * Brief 6.7's big source, for the benchmarks (#127): [CHANNELS] channels in [GROUPS] groups and
 * [PROGRAMS] programs, a week of them from two days before [now], served as an M3U playlist and
 * an XMLTV guide on this TV's own loopback address while it's open. Everything in it is made up:
 * the channels play nothing.
 */
internal class MadeUpSource(private val now: Instant) : AutoCloseable {
    private val server = ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK))
    private val base = "http://$LOOPBACK:${server.localPort}"

    /** The playlist's address, for Add a Source. */
    val playlist = "$base/playlist.m3u"

    /** The guide's address, for Add a Source's guide. */
    val guide = "$base/guide.xml"

    init {
        thread(name = "MadeUpSource", isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (closed: IOException) {
                    break
                }
                thread(name = "MadeUpSource answer", isDaemon = true) { socket.use(::answer) }
            }
        }
    }

    override fun close() = server.close()

    /** One request, answered as HTTP/1.1 with the connection closed after it. */
    private fun answer(socket: Socket) {
        val request = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val path = request.readLine()?.split(' ')?.getOrNull(1)?.substringBefore('?') ?: return
        // The headers, read to the blank line after them and left.
        var header = request.readLine()
        while (!header.isNullOrEmpty()) header = request.readLine()
        val out = socket.getOutputStream().buffered(BUFFER)
        when (path) {
            "/playlist.m3u" -> send(out, "audio/x-mpegurl", ::writePlaylist)
            "/guide.xml" -> send(out, "application/xml", ::writeGuide)
            else -> out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        }
        out.flush()
    }

    private fun send(out: OutputStream, type: String, body: (Writer) -> Unit) {
        out.write("HTTP/1.1 200 OK\r\nContent-Type: $type; charset=utf-8\r\nConnection: close\r\n\r\n".toByteArray())
        val writer = out.writer(Charsets.UTF_8).buffered(BUFFER)
        body(writer)
        writer.flush()
    }

    private fun writePlaylist(out: Writer) {
        out.write("#EXTM3U\n")
        for (channel in 1..CHANNELS) {
            out.write("#EXTINF:-1 tvg-id=\"$ID_PREFIX$channel\" tvg-chno=\"$channel\" group-title=\"${groupOf(channel)}\",${nameOf(channel)}\n")
            out.write("$base/live/$channel.ts\n")
        }
    }

    private fun writeGuide(out: Writer) {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv generator-info-name=\"Plasma benchmarks\">\n")
        for (channel in 1..CHANNELS) {
            out.write("<channel id=\"$ID_PREFIX$channel\"><display-name>${nameOf(channel)}</display-name></channel>\n")
        }
        val first = now.truncatedTo(ChronoUnit.HOURS).minus(PAST)
        for (channel in 1..CHANNELS) {
            // Each channel's programs start at a different quarter hour, as real guides' do.
            var start = first.plus(QUARTER.multipliedBy((channel % QUARTERS).toLong()))
            for (index in 0 until PROGRAMS_PER_CHANNEL) {
                val stop = start.plus(LENGTH)
                val title = TITLES[(channel + index) % TITLES.size]
                out.write(
                    "<programme start=\"${XMLTV_TIME.format(start)}\" stop=\"${XMLTV_TIME.format(stop)}\" channel=\"$ID_PREFIX$channel\">" +
                        "<title>$title</title><desc>A made-up program on ${nameOf(channel)}, for the benchmarks.</desc></programme>\n",
                )
                start = stop
            }
        }
        out.write("</tv>\n")
    }

    private fun nameOf(channel: Int) = "Channel $channel"

    private fun groupOf(channel: Int) = "Group %02d".format((channel - 1) / (CHANNELS / GROUPS) + 1)

    companion object {
        /** Brief 6.7: 1,500 channels and 200,000 programs. */
        const val CHANNELS = 1_500
        const val GROUPS = 30
        const val PROGRAMS_PER_CHANNEL = 134
        const val PROGRAMS = CHANNELS * PROGRAMS_PER_CHANNEL

        private const val LOOPBACK = "127.0.0.1"
        private const val ID_PREFIX = "made-up."
        private const val BACKLOG = 8
        private const val BUFFER = 64 * 1024
        private const val QUARTERS = 4

        /** 134 programs of 75 minutes are a week. */
        private val LENGTH: Duration = Duration.ofMinutes(75)
        private val PAST: Duration = Duration.ofDays(2)
        private val QUARTER: Duration = Duration.ofMinutes(15)
        private val XMLTV_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(ZoneOffset.UTC)

        /** Titles of different lengths, as a guide's are. */
        private val TITLES = listOf(
            "Morning Report",
            "The Long Weekend",
            "Quiz Night",
            "Coastal Kitchens: Island Recipes from the North",
            "Late Edition",
            "Field Notes",
            "Championship Highlights and Analysis",
            "Weather",
            "Midnight Movie: The Lighthouse Keeper's Daughter",
            "Documentary Hour",
        )
    }
}
