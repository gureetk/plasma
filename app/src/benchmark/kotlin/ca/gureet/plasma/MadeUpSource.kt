// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import ca.gureet.plasma.core.fixtures.R as FixturesR
import java.io.IOException
import java.io.OutputStream
import java.io.Writer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Brief 6.7's big source, for the benchmarks (#127), as an Xtream server on this TV's own loopback
 * address: [CHANNELS] channels in [GROUPS] groups with [PROGRAMS] programs, a week of them from two
 * days before now, and [MOVIES] movies and [SERIES] series with posters, so Movies, Series and
 * Home's rows can be measured too. The last group's channels have a program every half hour, on the
 * half hour, as most of a real guide has: four whole programs in the Guide's window, where the
 * others' 75 minutes put two or three in it, the first and the last cut by its edges. The posters are the sample world's own pictures, each under an
 * address of its own, as a provider's are. Everything in it is made up: the channels and the
 * movies play nothing.
 *
 * It listens on a port of its own for as long as Plasma runs ([start], from `BenchmarkSetup`), since
 * Plasma keeps the source's address and loads posters from it while a benchmark measures, after
 * each restart.
 */
internal class MadeUpSource private constructor(private val context: Context) {
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName(LOOPBACK), PORT), BACKLOG)
    }

    /** The sample world's posters, read once each, the first time one is asked for. */
    private val posters: List<ByteArray> by lazy { POSTERS.map { context.resources.openRawResource(it).use { input -> input.readBytes() } } }

    init {
        thread(name = "MadeUpSource", isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (closed: IOException) {
                    break
                }
                thread(name = "MadeUpSource answer", isDaemon = true) {
                    socket.use { runCatching { answer(it) } }
                }
            }
        }
    }

    /** One request, answered as HTTP/1.1 with the connection closed after it. */
    private fun answer(socket: Socket) {
        val request = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val target = request.readLine()?.split(' ')?.getOrNull(1) ?: return
        // The headers, read to the blank line after them and left.
        var header = request.readLine()
        while (!header.isNullOrEmpty()) header = request.readLine()
        val path = target.substringBefore('?')
        val query = queryOf(target.substringAfter('?', ""))
        val out = socket.getOutputStream().buffered(BUFFER)
        when {
            path == "/player_api.php" -> api(out, query)
            path == "/xmltv.php" -> send(out, "application/xml", ::writeGuide)
            path.startsWith("/poster/") -> poster(out, path.removePrefix("/poster/").substringBefore('.'))
            else -> out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        }
        out.flush()
    }

    private fun api(out: OutputStream, query: Map<String, String>) {
        if (query["username"] != USERNAME || query["password"] != PASSWORD) {
            send(out, JSON) { it.write("{\"user_info\":{\"auth\":0}}") }
            return
        }
        when (query["action"]) {
            null -> send(out, JSON) { it.write(ACCOUNT) }
            "get_live_categories" -> send(out, JSON) { writeCategories(it, GROUPS, ::groupName, LIVE_CATEGORY) }
            "get_live_streams" -> send(out, JSON, ::writeChannels)
            "get_vod_categories" -> send(out, JSON) { writeCategories(it, MOVIE_GROUPS, ::movieGroupName, MOVIE_CATEGORY) }
            "get_vod_streams" -> send(out, JSON, ::writeMovies)
            "get_series_categories" -> send(out, JSON) { writeCategories(it, SERIES_GROUPS, ::seriesGroupName, SERIES_CATEGORY) }
            "get_series" -> send(out, JSON, ::writeSeries)
            "get_vod_info" -> send(out, JSON) { writeMovieInfo(it, query["vod_id"]?.toIntOrNull() ?: 0) }
            "get_series_info" -> send(out, JSON) { writeSeriesInfo(it, query["series_id"]?.toIntOrNull() ?: 0) }
            else -> send(out, JSON) { it.write("[]") }
        }
    }

    private fun send(out: OutputStream, type: String, body: (Writer) -> Unit) {
        out.write("HTTP/1.1 200 OK\r\nContent-Type: $type; charset=utf-8\r\nConnection: close\r\n\r\n".toByteArray())
        val writer = out.writer(Charsets.UTF_8).buffered(BUFFER)
        body(writer)
        writer.flush()
    }

    private fun poster(out: OutputStream, name: String) {
        val id = name.drop(1).toIntOrNull()
        if (id == null) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        val bytes = posters[id % posters.size]
        out.write("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
    }

    private fun writeCategories(out: Writer, count: Int, name: (Int) -> String, first: Int) {
        out.write("[")
        for (group in 1..count) {
            if (group > 1) out.write(",")
            out.write("{\"category_id\":\"${first + group}\",\"category_name\":\"${name(group)}\",\"parent_id\":0}")
        }
        out.write("]")
    }

    private fun writeChannels(out: Writer) {
        out.write("[")
        for (channel in 1..CHANNELS) {
            if (channel > 1) out.write(",")
            out.write(
                "{\"num\":$channel,\"name\":\"${nameOf(channel)}\",\"stream_type\":\"live\",\"stream_id\":$channel," +
                    "\"stream_icon\":\"\",\"epg_channel_id\":\"$ID_PREFIX$channel\",\"category_id\":\"${LIVE_CATEGORY + groupOf(channel)}\"," +
                    "\"tv_archive\":0,\"tv_archive_duration\":0}",
            )
        }
        out.write("]")
    }

    private fun writeMovies(out: Writer) {
        val now = Instant.now().epochSecond
        out.write("[")
        for (movie in 1..MOVIES) {
            if (movie > 1) out.write(",")
            val id = MOVIE_IDS + movie
            out.write(
                "{\"num\":$movie,\"name\":\"${movieName(movie)}\",\"stream_type\":\"movie\",\"stream_id\":$id," +
                    "\"stream_icon\":\"$BASE/poster/m$id.jpg\",\"rating\":\"${ratingOf(movie)}\",\"added\":\"${now - movie * ADDED_STEP_S}\"," +
                    "\"category_id\":\"${MOVIE_CATEGORY + (movie - 1) % MOVIE_GROUPS + 1}\",\"container_extension\":\"mkv\"}",
            )
        }
        out.write("]")
    }

    private fun writeSeries(out: Writer) {
        val now = Instant.now().epochSecond
        out.write("[")
        for (show in 1..SERIES) {
            if (show > 1) out.write(",")
            val id = SERIES_IDS + show
            out.write(
                "{\"num\":$show,\"name\":\"${seriesName(show)}\",\"series_id\":$id,\"cover\":\"$BASE/poster/s$id.jpg\"," +
                    "\"plot\":\"A made-up series, for the benchmarks.\",\"cast\":\"\",\"director\":\"\",\"genre\":\"Drama\"," +
                    "\"releaseDate\":\"${YEAR_FIRST + show % YEARS}-01-01\",\"last_modified\":\"${now - show * ADDED_STEP_S}\"," +
                    "\"rating\":\"${ratingOf(show)}\",\"category_id\":\"${SERIES_CATEGORY + (show - 1) % SERIES_GROUPS + 1}\"}",
            )
        }
        out.write("]")
    }

    private fun writeMovieInfo(out: Writer, id: Int) {
        val movie = id - MOVIE_IDS
        out.write(
            "{\"info\":{\"cover_big\":\"$BASE/poster/m$id.jpg\",\"movie_image\":\"$BASE/poster/m$id.jpg\"," +
                "\"plot\":\"A made-up movie, for the benchmarks.\",\"genre\":\"Drama\",\"duration_secs\":$MOVIE_SECONDS," +
                "\"releasedate\":\"${YEAR_FIRST + movie % YEARS}-01-01\",\"rating\":\"${ratingOf(movie)}\"}," +
                "\"movie_data\":{\"stream_id\":$id,\"name\":\"${movieName(movie)}\",\"container_extension\":\"mkv\"}}",
        )
    }

    private fun writeSeriesInfo(out: Writer, id: Int) {
        val show = id - SERIES_IDS
        out.write("{\"info\":{\"name\":\"${seriesName(show)}\",\"cover\":\"$BASE/poster/s$id.jpg\",\"plot\":\"A made-up series, for the benchmarks.\"},")
        out.write("\"seasons\":[{\"season_number\":1,\"name\":\"Season 1\"}],\"episodes\":{\"1\":[")
        for (episode in 1..EPISODES) {
            if (episode > 1) out.write(",")
            out.write(
                "{\"id\":\"${id * EPISODES_PER_ID + episode}\",\"episode_num\":$episode,\"title\":\"Episode $episode\"," +
                    "\"container_extension\":\"mkv\",\"info\":{\"duration_secs\":$EPISODE_SECONDS}}",
            )
        }
        out.write("]}}")
    }

    private fun writeGuide(out: Writer) {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv generator-info-name=\"Plasma benchmarks\">\n")
        for (channel in 1..CHANNELS) {
            out.write("<channel id=\"$ID_PREFIX$channel\"><display-name>${nameOf(channel)}</display-name></channel>\n")
        }
        val hour = Instant.now().truncatedTo(ChronoUnit.HOURS)
        val first = hour.minus(PAST)
        for (channel in 1..CHANNELS) {
            val halfHours = groupOf(channel) == HALF_HOUR_GROUP
            // Each channel's programs start at a different quarter hour, as real guides' do; the
            // last group's are on the half hour, from six hours back.
            var start = if (halfHours) hour.minus(HALF_HOURS_PAST) else first.plus(QUARTER.multipliedBy((channel % QUARTERS).toLong()))
            val length = if (halfHours) HALF_HOUR else LENGTH
            for (index in 0 until PROGRAMS_PER_CHANNEL) {
                val stop = start.plus(length)
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

    /** The group a channel is in, from 1. */
    private fun groupOf(channel: Int) = (channel - 1) / (CHANNELS / GROUPS) + 1

    private fun groupName(group: Int) = "Group %02d".format(group)

    private fun movieGroupName(group: Int) = "Movie Group %02d".format(group)

    private fun seriesGroupName(group: Int) = "Series Group %02d".format(group)

    /** Names of different lengths, as a catalog's are, with the year after them. */
    private fun movieName(movie: Int) = "${MOVIE_TITLES[movie % MOVIE_TITLES.size]} $movie (${YEAR_FIRST + movie % YEARS})"

    private fun seriesName(show: Int) = "${SERIES_TITLES[show % SERIES_TITLES.size]} $show"

    private fun ratingOf(item: Int) = "%.1f".format(Locale.ROOT, RATING_LOW + (item % RATINGS) / 10f)

    private fun queryOf(query: String): Map<String, String> = query.split('&').filter { '=' in it }.associate { pair ->
        URLDecoder.decode(pair.substringBefore('='), "UTF-8") to URLDecoder.decode(pair.substringAfter('='), "UTF-8")
    }

    companion object {
        /** Brief 6.7: 1,500 channels and 200,000 programs. */
        const val CHANNELS = 1_500
        const val GROUPS = 30
        const val PROGRAMS_PER_CHANNEL = 134
        const val PROGRAMS = CHANNELS * PROGRAMS_PER_CHANNEL

        /** A catalog as big as a provider's often is. */
        const val MOVIES = 6_000
        const val SERIES = 1_200
        const val MOVIE_GROUPS = 24
        const val SERIES_GROUPS = 12

        /** Add a Source's login for it. */
        const val USERNAME = "benchmark"
        const val PASSWORD = "benchmark"

        /** Its own port, the same after each restart, since the source's address keeps it. */
        private const val PORT = 47_817
        private const val LOOPBACK = "127.0.0.1"

        /** The server's address, for Add a Source. */
        const val BASE = "http://$LOOPBACK:$PORT"

        @Volatile private var running: MadeUpSource? = null

        /** Starts it for as long as Plasma runs, once; a second call finds it running. */
        fun start(context: Context): Unit = synchronized(this) {
            if (running == null) running = runCatching { MadeUpSource(context.applicationContext) }.getOrNull()
        }

        private const val JSON = "application/json"
        private const val ACCOUNT = "{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"exp_date\":null,\"max_connections\":\"2\"," +
            "\"active_cons\":\"0\",\"allowed_output_formats\":[\"ts\",\"m3u8\"]},\"server_info\":{\"timezone\":\"UTC\"}}"
        private const val ID_PREFIX = "made-up."
        private const val BACKLOG = 16
        private const val BUFFER = 64 * 1024
        private const val QUARTERS = 4

        /** Where each list's category ids and items' ids start, so none are the same. */
        private const val LIVE_CATEGORY = 0
        private const val MOVIE_CATEGORY = 100
        private const val SERIES_CATEGORY = 200
        private const val MOVIE_IDS = 100_000
        private const val SERIES_IDS = 500_000
        private const val EPISODES_PER_ID = 100
        private const val EPISODES = 10

        private const val ADDED_STEP_S = 3_600L
        private const val MOVIE_SECONDS = 6_300
        private const val EPISODE_SECONDS = 2_700
        private const val YEAR_FIRST = 1990
        private const val YEARS = 35
        private const val RATING_LOW = 5.0f
        private const val RATINGS = 40

        /** 134 programs of 75 minutes are a week. */
        private val LENGTH: Duration = Duration.ofMinutes(75)
        private val PAST: Duration = Duration.ofDays(2)
        private val QUARTER: Duration = Duration.ofMinutes(15)

        /**
         * The group whose channels have half-hour programs: the last, channels 1,451 to 1,500, far
         * from the rows the other benchmarks measure. `GuideBenchmark` goes to one by its number.
         */
        private const val HALF_HOUR_GROUP = GROUPS

        /**
         * 134 programs of half an hour are 67 hours: from six hours before now they cover the rest
         * of the day the Guide loads, which ends four hours past midnight, at any hour of the day,
         * and tomorrow's.
         */
        private val HALF_HOUR: Duration = Duration.ofMinutes(30)
        private val HALF_HOURS_PAST: Duration = Duration.ofHours(6)
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

        private val MOVIE_TITLES = listOf(
            "The Quiet Harbour",
            "Ember",
            "Signals from the Northern Observatory",
            "Paper Kites",
            "Low Orbit",
            "The Orchard at the End of the Valley Road",
            "Salt",
            "Velvet Hours",
        )

        private val SERIES_TITLES = listOf(
            "Night Desk",
            "Small Claims",
            "The Kingsway Files",
            "Heat Index",
            "Quiet Hours on the Coast",
            "Tumble",
        )

        /** The sample world's posters (core/fixtures), which the made-up catalog's posters take turns with. */
        private val POSTERS = listOf(
            FixturesR.drawable.fx_poster_carto,
            FixturesR.drawable.fx_poster_ember,
            FixturesR.drawable.fx_poster_kite,
            FixturesR.drawable.fx_poster_lighthouse,
            FixturesR.drawable.fx_poster_loworbit,
            FixturesR.drawable.fx_poster_neon,
            FixturesR.drawable.fx_poster_nightdesk,
            FixturesR.drawable.fx_poster_orchard,
            FixturesR.drawable.fx_poster_paper,
            FixturesR.drawable.fx_poster_polar,
            FixturesR.drawable.fx_poster_salt,
            FixturesR.drawable.fx_poster_velvet,
        )
    }
}
