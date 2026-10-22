// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.fixtures

import androidx.compose.ui.Alignment
import ca.gureet.plasma.core.design.component.focal
import ca.gureet.plasma.core.model.FixedClock
import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The made-up world of the design boards (brief appendix A): Wed 21 Oct 2026, 20:41.
 * Every channel, program, movie, app and person here is fictional. Screens use these until real
 * sources arrive; tests and previews use them for good.
 */
object Sample {
    val zone: ZoneOffset = ZoneOffset.UTC
    val today: LocalDate = LocalDate.of(2026, 10, 21)
    val now: Instant = ZonedDateTime.of(today, LocalTime.of(20, 41), zone).toInstant()
    val clock: PlasmaClock = FixedClock(now, zone)

    /** A time on the sample day ([dayOffset] days from Wed 21 Oct). */
    fun at(hour: Int, minute: Int, dayOffset: Long = 0): ZonedDateTime =
        ZonedDateTime.of(today.plusDays(dayOffset), LocalTime.of(hour, minute), zone)

    // ---- Channels -------------------------------------------------------------------------------

    class Channel(
        val number: Int,
        val name: String,
        val logo: String?,
        val still: String?,
        val stillAlignment: Alignment = Alignment.Center,
        val catchupDays: Int = 0,
        val uhd: Boolean = false,
        val hdr: Boolean = false,
        val locked: Boolean = false,
        val favorite: Boolean = false,
        val group: String = "Entertainment",
    ) {
        val id: String get() = "ch$number"
    }

    val northline = Channel(101, "Northline News", fx("logo_northline"), fx("city2"), focal(55f, 40f), catchupDays = 7, favorite = true, group = "News")
    val meridian = Channel(102, "Meridian 24", fx("logo_meridian"), fx("city2"), focal(40f, 60f), catchupDays = 7, favorite = true, group = "News")
    val arenaNews = Channel(103, "Arena Sports News", fx("logo_arenanews"), fx("stadium2"), group = "Sports")
    val arenaExtra = Channel(104, "Arena Sports Extra", fx("logo_arenaextra"), fx("stadium2"), catchupDays = 7, group = "Sports")
    val arena1 = Channel(105, "Arena Sports 1", fx("logo_arena1"), fx("stadium2"), catchupDays = 7, favorite = true, group = "Sports")
    val arena2 = Channel(106, "Arena Sports 2", fx("logo_arena2"), fx("stadium2"), catchupDays = 7, group = "Sports")
    val velocity = Channel(107, "Velocity Motor", fx("logo_velocity"), fx("motor2"), focal(70f, 60f), catchupDays = 3, uhd = true, hdr = true, favorite = true, group = "Sports")
    val courtside = Channel(108, "Courtside+", fx("logo_courtside"), fx("stadium2"), catchupDays = 1, favorite = true, group = "Sports")
    val fairway = Channel(109, "Fairway Golf", fx("logo_fairway"), fx("aurora2"), group = "Sports")
    val combat = Channel(110, "Combat Arena", fx("logo_combat"), fx("stadium2"), locked = true, group = "Sports")
    val atlas = Channel(120, "Atlas One", fx("logo_atlas"), fx("city2"), favorite = true)
    val cinema = Channel(140, "Cinema Prime 4K", fx("logo_cinemaprime"), fx("lighthouse3"), focal(78f, 35f), uhd = true, favorite = true, group = "Movies")
    val wildframe = Channel(180, "Wildframe 4K", fx("logo_wildframe"), fx("aurora2"), focal(60f, 40f), uhd = true, favorite = true, group = "Documentary")
    val pulse = Channel(200, "Pulse", fx("logo_pulse"), fx("concert2"), catchupDays = 7, favorite = true, group = "Music")
    val orbit = Channel(210, "Orbit Science", fx("logo_orbit"), fx("orbit2"), group = "Documentary")

    val channels = listOf(northline, meridian, arenaNews, arenaExtra, arena1, arena2, velocity, courtside, fairway, combat, atlas, cinema, wildframe, pulse, orbit)

    /** Favorites in the user's order; they count from 1 (appendix A). */
    val favorites = listOf(arena1, velocity, northline, meridian, wildframe, atlas, courtside, cinema, pulse)

    /** A channel group with its count as the boards show it (appendix A), and the sample channels in it. */
    class Group(
        val id: String,
        val name: String,
        val count: Int,
        val channels: List<Channel>,
        /** All Channels, Favorites and Recently Watched, listed above the divider. */
        val smart: Boolean = false,
        /** Favorites count from 1 in the mock settings (5.2); other groups keep full-list numbers. */
        val countsFromOne: Boolean = false,
    )

    private fun inGroup(name: String) = channels.filter { it.group == name }

    val groups = listOf(
        Group("all", "All Channels", 1_284, channels, smart = true),
        Group("favorites", "Favorites", 24, favorites, smart = true, countsFromOne = true),
        Group("recent", "Recently Watched", 18, listOf(arena1, velocity, northline, cinema, wildframe, pulse), smart = true),
        Group("sports", "Sports", 186, inGroup("Sports")),
        Group("news", "News", 92, inGroup("News")),
        Group("movies", "Movies", 140, inGroup("Movies")),
        Group("entertainment", "Entertainment", 212, inGroup("Entertainment")),
        Group("documentary", "Documentary", 58, inGroup("Documentary")),
        Group("kids", "Kids", 64, emptyList()),
        Group("music", "Music", 41, inGroup("Music")),
        Group("uhd", "4K UHD", 36, channels.filter { it.uhd }),
    )

    /** What Maya watched last: Arena Sports 1, in Sports. */
    val lastChannel = arena1

    // ---- Programs -------------------------------------------------------------------------------

    class Program(
        val channel: Channel,
        val title: String,
        val start: ZonedDateTime,
        val end: ZonedDateTime,
        val subtitle: String? = null,
        val description: String? = null,
        val recording: Boolean = false,
        val recordingScheduled: Boolean = false,
        val reminder: Boolean = false,
        val badges: List<String> = emptyList(),
    ) {
        val isLive: Boolean get() = isLiveAt(now)
        val progress: Float get() = progressAt(now)
        val left: Duration get() = leftAt(now)

        fun isLiveAt(time: Instant): Boolean = !start.toInstant().isAfter(time) && end.toInstant().isAfter(time)
        fun progressAt(time: Instant): Float =
            (Duration.between(start.toInstant(), time).toMillis().toFloat() / Duration.between(start, end).toMillis()).coerceIn(0f, 1f)
        fun leftAt(time: Instant): Duration = Duration.between(time, end.toInstant())
    }

    val harbourCity = Program(
        arena1, "Harbour City v Redmoor", at(19, 45), at(22, 0),
        subtitle = "City League, Matchweek 9",
        description = "Second-placed Harbour City host a Redmoor side unbeaten in six. Coverage continues after the final whistle with reaction from Kingsway Park.",
        recording = true,
        badges = listOf("HD", "5.1", "CC"),
    )

    val schedule: Map<Channel, List<Program>> = mapOf(
        northline to listOf(
            Program(northline, "Evening Bulletin", at(20, 0), at(20, 30)),
            Program(northline, "Business Wrap", at(20, 30), at(21, 0)),
            Program(northline, "The World Tonight", at(21, 0), at(22, 0)),
        ),
        meridian to listOf(
            Program(meridian, "Markets Close", at(19, 0), at(21, 0)),
            Program(meridian, "Late Edition", at(21, 0), at(21, 30)),
            Program(meridian, "Meridian Debate", at(21, 30), at(22, 30)),
        ),
        arenaNews to listOf(Program(arenaNews, "Sports Desk", at(20, 0), at(21, 0))),
        arenaExtra to listOf(Program(arenaExtra, "Transfer Talk", at(20, 30), at(21, 30))),
        arena1 to listOf(
            harbourCity,
            Program(arena1, "Match of the Week Extra", at(22, 0), at(23, 0), reminder = true),
            Program(arena1, "Football Tonight", at(23, 0), at(0, 30, 1)),
            Program(arena1, "Fight Night: Main Card", at(0, 30, 1), at(3, 0, 1), recordingScheduled = true),
        ),
        arena2 to listOf(Program(arena2, "Grand Slam Tennis: Quarter-final", at(19, 0), at(22, 30))),
        velocity to listOf(
            Program(velocity, "Endurance 24: Night Stint", at(20, 0), at(21, 30)),
            Program(velocity, "Pit Lane", at(21, 30), at(22, 0)),
            Program(velocity, "Endurance 24: Night Stint, Part 2", at(22, 0), at(23, 30)),
        ),
        courtside to listOf(Program(courtside, "City Lights v Northport", at(20, 30), at(23, 0))),
        fairway to listOf(Program(fairway, "Coastal Classic, Round 2", at(18, 0), at(22, 0))),
        combat to listOf(Program(combat, "Fight Night: Prelims", at(20, 0), at(22, 0))),
        atlas to listOf(
            Program(atlas, "The Quiz Hour", at(20, 0), at(21, 0)),
            Program(atlas, "Baker's Dozen", at(21, 0), at(22, 0), reminder = true),
        ),
        cinema to listOf(
            Program(cinema, "The Last Lighthouse", at(19, 30), at(21, 45)),
            Program(cinema, "Glass Orchard", at(21, 45), at(23, 40), recordingScheduled = true),
        ),
        wildframe to listOf(
            Program(wildframe, "Kingdoms of Ice, Episode 3", at(20, 0), at(21, 0)),
            Program(wildframe, "Rivers of the North", at(21, 0), at(22, 0)),
        ),
        pulse to listOf(Program(pulse, "Late Night Sessions", at(20, 0), at(22, 0))),
        orbit to listOf(Program(orbit, "Deep Field", at(20, 0), at(21, 0))),
    )

    fun nowOn(channel: Channel, at: Instant = now): Program? = schedule[channel]?.firstOrNull { it.isLiveAt(at) }
    fun nextOn(channel: Channel, at: Instant = now): Program? = schedule[channel]?.firstOrNull { it.start.toInstant().isAfter(at) }

    /** The zap banner and reminder board (11) is a minute before ten: 21:59. */
    val zapTime: Instant = at(21, 59).toInstant()
    val zapClock: PlasmaClock = FixedClock(zapTime, zone)

    // ---- Movies and series ----------------------------------------------------------------------

    class Movie(
        val id: String,
        val title: String,
        val poster: String,
        val year: Int = 2025,
        val genre: String = "Drama",
        val minutes: Int = 120,
    )

    val movies = listOf(
        Movie("neon", "Neon Tide", fx("poster_neon"), 2026, "Thriller", 112),
        Movie("orchard", "Glass Orchard", fx("poster_orchard"), 2026, "Drama", 115),
        Movie("ember", "Ember Road", fx("poster_ember"), 2026, "Action", 128),
        Movie("loworbit", "Low Orbit", fx("poster_loworbit"), 2025, "Sci-Fi", 121),
        Movie("paper", "Paper Kingdoms", fx("poster_paper"), 2025, "Fantasy", 133),
        Movie("salt", "Saltwater Hymn", fx("poster_salt"), 2025, "Drama", 104),
        Movie("polar", "Polar Night", fx("poster_polar"), 2025, "Thriller", 109),
        Movie("velvet", "Velvet Hour", fx("poster_velvet"), 2025, "Romance", 117),
        Movie("nightdesk", "The Night Desk", fx("poster_nightdesk"), 2025, "Crime", 124),
        Movie("carto", "The Cartographer", fx("poster_carto"), 2024, "Mystery", 126),
        Movie("lighthouse", "The Last Lighthouse", fx("poster_lighthouse"), 2025, "Drama", 134),
        Movie("kite", "Kite Season", fx("poster_kite"), 2024, "Family", 101),
    )

    /** A category in Movies or Series, with its count as the boards show it. */
    class Category(val id: String, val name: String, val count: Int, val smart: Boolean = false)

    val movieCategories = listOf(
        Category("recent", "Recently Added", 212, smart = true),
        Category("continue", "Continue Watching", 3, smart = true),
        Category("mylist", "My List", 14, smart = true),
        Category("uhd", "4K UHD", 640, smart = true),
        Category("action", "Action", 1_204),
        Category("animation", "Animation", 388),
        Category("comedy", "Comedy", 962),
        Category("documentary", "Documentary", 471),
        Category("drama", "Drama", 1_880),
        Category("family", "Family", 530),
        Category("scifi", "Sci-Fi", 702),
        Category("thriller", "Thriller", 1_115),
        Category("romance", "Romance", 644),
        Category("mystery", "Mystery", 318),
        Category("crime", "Crime", 590),
    )

    /** Recently Added, newest first (board 13). */
    val recentlyAdded: List<Movie> by lazy {
        listOf("neon", "lighthouse", "orchard", "ember", "loworbit", "salt", "carto", "velvet", "polar", "paper", "nightdesk", "kite")
            .map { id -> movies.first { it.id == id } }
    }

    /** The Last Lighthouse in full (appendix A, board 14). */
    object LastLighthouse {
        val movie get() = movies.first { it.id == "lighthouse" }
        const val TITLE_ART = "fixture://titleart_lighthouse"
        const val BACKDROP = "fixture://lighthouse3"
        /** The page's color, from the backdrop's night sky (board 14). */
        const val PAGE_COLOR = 0xFF07090C
        val genres = listOf("Drama", "Mystery")
        val badges = listOf("13+", "4K", "HDR", "5.1", "CC")
        const val SYNOPSIS = "A winter storm cuts the island off for a week. Nora returns to settle her late father’s affairs " +
            "and finds the light still burning — and his keeper’s log still being written."
        val cast = listOf("Mara Ellison", "Aden Foley", "June Okafor")
        const val DIRECTOR = "Ines Calloway"
        const val RESUME_LEFT_MINUTES = 62
        val moreLikeThis = listOf("salt", "polar", "carto", "nightdesk", "velvet", "orchard", "loworbit", "ember", "neon", "paper", "kite")
    }

    class Series(
        val id: String,
        val title: String,
        val poster: String,
        val badge: String? = null,
        /** Part watched: shown as a bar on the poster. */
        val progress: Float? = null,
    )

    val series = listOf(
        Series("atlas", "Atlas Station", fx("series_atlas"), progress = 0.69f),
        Series("kingsway", "Kingsway", fx("series_kingsway"), badge = "NEW EPISODE"),
        Series("harbour", "Harbour Lights", fx("series_harbour")),
        Series("panel", "The Panel", fx("series_panel"), badge = "NEW SEASON"),
        Series("northof", "North of Nowhere", fx("series_northof")),
        Series("lastorders", "Last Orders", fx("series_lastorders")),
        Series("nightstint", "Night Stint", fx("series_nightstint")),
        Series("smallclaims", "Small Claims", fx("series_smallclaims")),
        Series("backstage", "Backstage", fx("series_backstage")),
        Series("heatindex", "Heat Index", fx("series_heatindex"), badge = "NEW EPISODE"),
        Series("quiethours", "Quiet Hours", fx("series_quiethours")),
        Series("tumble", "Tumble Town", fx("series_tumble")),
    )

    val seriesCategories = listOf(
        Category("new", "New Episodes", 86, smart = true),
        Category("continue", "Continue Watching", 4, smart = true),
        Category("mylist", "My List", 9, smart = true),
        Category("returning", "Returning Soon", 31, smart = true),
        Category("comedy", "Comedy", 412),
        Category("crime", "Crime", 296),
        Category("documentary", "Documentary", 188),
        Category("drama", "Drama", 744),
        Category("kids", "Kids", 230),
        Category("reality", "Reality", 160),
        Category("scifi", "Sci-Fi", 141),
        Category("thriller", "Thriller", 205),
        Category("animation", "Animation", 97),
    )

    class Episode(val number: Int, val title: String, val minutes: Int, val watched: Boolean = false, val leftMinutes: Int? = null) {
        val still: String get() = "fixture://ep_atlas_s2e$number"
    }

    /** Atlas Station in full (appendix A, board 16). */
    object AtlasStation {
        const val TITLE_ART = "fixture://titleart_atlas"
        const val BACKDROP = "fixture://orbit2"
        /** The page's color, from the backdrop's space (board 16). */
        const val PAGE_COLOR = 0xFF020308
        val genres = listOf("Sci-Fi", "Drama")
        const val YEARS = "2024–2026"
        const val SEASONS = 3
        val badges = listOf("16+", "4K", "HDR", "5.1")
        const val SYNOPSIS = "Two hundred kilometers above a failing colony, the last crew of a research station keeps the lights " +
            "on — until the cities below go dark one by one."
        const val CURRENT_SEASON = 2
        val season2 = listOf(
            Episode(1, "Uplink", 52, watched = true),
            Episode(2, "Thin Air", 49, watched = true),
            Episode(3, "Perihelion", 55, watched = true),
            Episode(4, "Dead Reckoning", 58, leftMinutes = 18),
            Episode(5, "The Quiet Side", 51),
            Episode(6, "Burn", 54),
            Episode(7, "Long Night", 50),
            Episode(8, "Signal", 61),
        )
    }

    // ---- Catch-up ---------------------------------------------------------------------------------

    /** Programs already shown that the archive keeps (Search and Continue Watching, boards 02 and 03). */
    val pastPrograms: List<Program> by lazy {
        listOf(
            Program(pulse, "Late Night Sessions", at(22, 0, -2), at(23, 30, -2)),
            Program(arena1, "Tuesday Night Football", at(19, 30, -1), at(21, 45, -1)),
            Program(wildframe, "Kingdoms of Ice, Episode 2", at(20, 0, -1), at(21, 0, -1)),
        )
    }

    // ---- Apps -----------------------------------------------------------------------------------

    class App(val packageName: String, val label: String, val banner: String, val onHome: Boolean)

    val apps = listOf(
        App("tv.example.reelbox", "Reelbox", fx("app_reelbox"), true),
        App("tv.example.lantern", "Lantern Browser", fx("app_lantern"), true),
        App("tv.example.fileport", "Fileport", fx("app_fileport"), true),
        App("tv.example.sonar", "Sonar", fx("app_sonar"), true),
        App("tv.example.snapshot", "Snapshot", fx("app_snapshot"), true),
        App("tv.example.shieldline", "Shieldline VPN", fx("app_shieldline"), true),
        App("tv.example.brightside", "Brightside Weather", fx("app_brightside"), false),
        App("tv.example.cloudnest", "Cloudnest", fx("app_cloudnest"), false),
        App("tv.example.driftwood", "Driftwood Podcasts", fx("app_driftwood"), false),
        App("tv.example.kinetic", "Kinetic", fx("app_kinetic"), false),
        App("tv.example.pingmeter", "Pingmeter", fx("app_pingmeter"), false),
        App("tv.example.quarry", "Quarry", fx("app_quarry"), false),
        App("tv.example.tumble", "Tumble", fx("app_tumble"), false),
    )

    // ---- Profiles -------------------------------------------------------------------------------

    /** A profile on Who's Watching (board 05). */
    class Profile(
        val id: String,
        val name: String,
        val initial: String,
        /** The profile's picture; a guest has none and shows a figure instead. */
        val avatar: String? = null,
        val pin: Boolean = false,
        /** Kids profiles carry an age rating: "Ages 7 and under". */
        val maxAge: Int? = null,
        /** Guests keep no history. */
        val guest: Boolean = false,
        val lastWatched: String? = null,
    )

    val maya = Profile("maya", "Maya", "M", fx("avatar_maya"), lastWatched = "Arena Sports 1")
    val profiles = listOf(
        maya,
        Profile("daniel", "Daniel", "D", fx("avatar_daniel"), pin = true),
        Profile("kids", "Kids", "K", fx("avatar_kids"), maxAge = 7),
        Profile("guest", "Guest", "G", guest = true),
    )
}
