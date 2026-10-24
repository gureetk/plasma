// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.stalker

import ca.gureet.plasma.source.stalker.StalkerJson.flag
import ca.gureet.plasma.source.stalker.StalkerJson.int
import ca.gureet.plasma.source.stalker.StalkerJson.long
import ca.gureet.plasma.source.stalker.StalkerJson.string
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Talks to a Stalker / Ministra portal the way a MAG box does (brief 7.1): a handshake for a
 * token, the box's profile, then `itv` calls with the box's MAC address in a cookie. When the
 * portal lets the token go, it signs in again once and repeats the call. Ported from the earlier
 * iptv-player and reviewed for Plasma.
 *
 * Portals disagree on types: numbers come as strings, empty lists as `false`, and errors as web
 * pages. Calls run on [Dispatchers.IO]; one client can serve several at once.
 *
 * @param userAgent the source's own user agent (5.1); a MAG box's otherwise.
 * @param login for a portal that wants a login as well as the MAC address (4.21).
 */
class StalkerClient(
    private val http: OkHttpClient,
    val portal: StalkerPortal,
    val device: StalkerDevice,
    val userAgent: String = USER_AGENT,
    val login: StalkerLogin? = null,
) {
    private class Session(val endpoint: HttpUrl, val token: String, val profile: JsonObject)

    private val signingIn = Mutex()

    @Volatile private var session: Session? = null

    /** The headers the portal's streams expect: most check for a MAG box's user agent. */
    val playbackHeaders: Map<String, String> get() = mapOf(USER_AGENT_HEADER to userAgent)

    /**
     * Signs in afresh and returns the account: [StalkerException.NotStalker] when nothing at the
     * address answers as a portal, [StalkerException.Refused] when the portal turns the box down,
     * [StalkerException.Blocked] when it has blocked it, and an [IOException] when it can't be reached.
     */
    suspend fun connect(): StalkerAccount = withContext(Dispatchers.IO) { accountOf(signIn(force = true)) }

    /** The account as the portal describes it now. */
    suspend fun account(): StalkerAccount = withContext(Dispatchers.IO) { accountOf(sessionOrSignIn()) }

    /** The portal's channel genres, without its "All". */
    suspend fun genres(): List<StalkerGenre> = withContext(Dispatchers.IO) {
        (call("itv", "get_genres") as? JsonArray).orEmpty().mapNotNull { element ->
            val genre = element as? JsonObject ?: return@mapNotNull null
            val id = genre.string("id") ?: return@mapNotNull null
            if (id == "*") null else StalkerGenre(id, genre.string("title") ?: id)
        }
    }

    /**
     * Every live channel, in the portal's order: the whole list at once, read as it downloads, or
     * page by page from a portal that only answers that way.
     */
    suspend fun channels(): List<StalkerChannel> = withContext(Dispatchers.IO) {
        val channels = ArrayList<StalkerChannel>()
        stream("itv", "get_all_channels") { body -> StalkerJson.eachItem(body) { item -> channelOf(item)?.let(channels::add) } }
        if (channels.isEmpty()) {
            for (page in 1..MAX_CHANNEL_PAGES) {
                val answer = call("itv", "get_ordered_list", mapOf("genre" to "*", "p" to page.toString(), "sortby" to "number", "fav" to "0", "hd" to "0"))
                    as? JsonObject ?: break
                val items = (answer["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                items.mapNotNullTo(channels, ::channelOf)
                if (items.isEmpty() || !answer.hasMorePages(page)) break
            }
        }
        channels.distinctBy { it.id }
    }

    /**
     * A playable address for a channel's `cmd`, asked for at play time: portals hand out links
     * that expire (brief 7.1). Throws [StalkerException.NoLink] when the portal gives none.
     */
    suspend fun createLink(cmd: String): String = withContext(Dispatchers.IO) {
        val params = mapOf("cmd" to cmd, "series" to "", "forced_storage" to "0", "disable_ad" to "0", "download" to "0", "force_ch_link_check" to "0")
        linkOf(call("itv", "create_link", params) as? JsonObject)
    }

    /**
     * The portal's own guide for the next [hours], a channel at a time as it downloads: the
     * portal's channel id and its programmes. Returns how many channels it covered.
     */
    suspend fun guide(hours: Int, onChannel: (channelId: String, programmes: List<StalkerProgramme>) -> Unit): Int = withContext(Dispatchers.IO) {
        stream("itv", "get_epg_info", mapOf("period" to hours.toString())) { body ->
            StalkerJson.eachEntry(body) { channelId, programmes -> onChannel(channelId, programmesOf(programmes)) }
        } ?: 0
    }

    /**
     * The portal's id for the programme in its archive on [channelId] that starts at [start], or
     * null when it kept none. Reads that day's guide page by page; guides from different feeds can
     * disagree by a minute or two, so the closest start within five minutes counts.
     */
    suspend fun archivedProgramme(channelId: String, start: Instant): String? = withContext(Dispatchers.IO) {
        val date = start.atZone(zone()).toLocalDate().toString()
        for (page in 1..MAX_DAY_PAGES) {
            val answer = call("epg", "get_simple_data_table", mapOf("ch_id" to channelId, "date" to date, "p" to page.toString()))
                as? JsonObject ?: return@withContext null
            val match = (answer["data"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { it.flag("mark_archive") }
                .mapNotNull { event -> event.long("start_timestamp")?.let { event to kotlin.math.abs(it - start.epochSecond) } }
                .filter { (_, distance) -> distance <= START_TOLERANCE_SECONDS }
                .minByOrNull { (_, distance) -> distance }
            if (match != null) return@withContext match.first.string("id")
            if (!answer.hasMorePages(page)) return@withContext null
        }
        null
    }

    /** A playable address for a programme in the archive, found by [archivedProgramme]. */
    suspend fun archiveLink(programmeId: String): String = withContext(Dispatchers.IO) {
        val params = mapOf("cmd" to "auto /media/$programmeId.mpg", "series" to "", "forced_storage" to "", "disable_ad" to "0", "download" to "0")
        linkOf(call("tv_archive", "create_link", params) as? JsonObject)
    }

    /**
     * How often to call [watchdog] while a channel plays: well inside the timeout the portal gave
     * the box, or every 80 seconds when it gave none (brief 7.1).
     */
    val watchdogInterval: Duration
        get() {
            val timeout = session?.profile?.int("watchdog_timeout")?.takeIf { it > 0 } ?: DEFAULT_WATCHDOG_SECONDS
            return Duration.ofSeconds((timeout * 2L / 3).coerceIn(MIN_WATCHDOG_SECONDS, MAX_WATCHDOG_SECONDS))
        }

    /**
     * Tells the portal the box is still there, as some expect every minute or two while a channel
     * plays (brief 7.1).
     */
    suspend fun watchdog() {
        withContext(Dispatchers.IO) { call("watchdog", "get_events", mapOf("init" to "0", "cur_play_type" to "1", "event_active_id" to "0")) }
    }

    private fun channelOf(item: JsonObject): StalkerChannel? {
        val id = item.string("id") ?: return null
        val cmd = item.string("cmd") ?: return null
        val archived = item.flag("tv_archive") || item.flag("enable_tv_archive") || item.flag("archive")
        return StalkerChannel(
            id = id,
            name = item.string("name") ?: id,
            number = item.int("number")?.takeIf { it > 0 },
            cmd = cmd,
            logo = item.string("logo")?.let(::absoluteUrl),
            genreId = item.string("tv_genre_id"),
            xmltvId = item.string("xmltv_id"),
            archiveDays = if (archived) archiveDaysOf(item.int("tv_archive_duration")) else null,
        )
    }

    private fun programmesOf(element: JsonElement): List<StalkerProgramme> = (element as? JsonArray).orEmpty().mapNotNull { item ->
        val event = item as? JsonObject ?: return@mapNotNull null
        val start = event.long("start_timestamp")?.let(Instant::ofEpochSecond) ?: return@mapNotNull null
        val end = event.long("stop_timestamp")?.let(Instant::ofEpochSecond)?.takeIf { it.isAfter(start) } ?: return@mapNotNull null
        StalkerProgramme(
            id = event.string("id"),
            start = start,
            end = end,
            title = event.string("name") ?: return@mapNotNull null,
            description = event.string("descr"),
            category = event.string("category"),
            archived = event.flag("mark_archive"),
        )
    }

    private fun linkOf(answer: JsonObject?): String {
        val error = answer?.string("error")
        val cmd = answer?.string("cmd")?.let(::stripPlayerPrefix)?.takeIf { it.isNotEmpty() }
        if (error != null || cmd == null) throw StalkerException.NoLink(error)
        return cmd
    }

    private suspend fun accountOf(session: Session): StalkerAccount {
        val profile = session.profile
        // The account's own page has the end date on most portals, oddly in "phone".
        val info = try {
            call("account_info", "get_main_info") as? JsonObject
        } catch (unanswered: IOException) {
            null
        }
        val dates = listOfNotNull(info?.string("end_date"), info?.string("phone"), profile.string("expire_billing_date"), profile.string("tariff_expired_date"))
        val expiresAt = dates.firstNotNullOfOrNull(::instantOf)
        // A word such as "Unlimited" in place of a date; never a phone number.
        val expiryText = if (expiresAt == null) dates.firstOrNull { text -> text.any(Char::isLetter) && text.none(Char::isDigit) } else null
        return StalkerAccount(
            active = !profile.flag("blocked"),
            expiresAt = expiresAt,
            expiryText = expiryText,
            watchdogSeconds = profile.int("watchdog_timeout")?.takeIf { it > 0 },
        )
    }

    private suspend fun sessionOrSignIn(): Session = session ?: signIn(force = false)

    /**
     * Signs in, one call at a time. [stale] is the session a call found refused: when another
     * call has signed in since, its session serves.
     */
    private suspend fun signIn(force: Boolean, stale: Session? = null): Session = signingIn.withLock {
        val current = session
        if (current != null && (!force || (stale != null && current !== stale))) return@withLock current
        session = null
        handshake().also { session = it }
    }

    /**
     * The handshake on each endpoint the portal may answer at, with an empty token and then, for
     * portals that want one, a made-up token and its hash; then the box's profile (brief 7.1).
     */
    private fun handshake(): Session {
        var unreachable: IOException? = null
        var answered = false
        var refused: Int? = null
        for (endpoint in portal.endpoints) {
            for (offered in listOf("", randomToken())) {
                val params = if (offered.isEmpty()) mapOf("token" to "") else mapOf("token" to offered, "prehash" to sha1(offered))
                val token: String? = try {
                    send(endpoint, "stb", "handshake", params, token = null) { response ->
                        answered = true
                        when {
                            // The box turned down here; another endpoint may be the portal's own.
                            response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN -> {
                                refused = response.code
                                null
                            }
                            response.isSuccessful -> (StalkerJson.js(response.body.string()) as? JsonObject)?.string("token")
                            else -> null
                        }
                    }
                } catch (failed: IOException) {
                    unreachable = failed
                    null
                }
                if (token != null) {
                    val (renewed, profile) = profile(endpoint, token)
                    val session = Session(endpoint, renewed ?: token, profile)
                    login?.let { authorize(session, it) }
                    return session
                }
            }
        }
        refused?.let { throw StalkerException.Refused(it) }
        // Nothing answered at all: the server can't be reached. Something did, but not as a portal.
        if (!answered) unreachable?.let { throw it }
        throw StalkerException.NotStalker()
    }

    /** The box's profile, which most portals want before anything else; it may come with a new token. */
    private fun profile(endpoint: HttpUrl, token: String): Pair<String?, JsonObject> {
        val serial = device.serialNumber ?: md5(device.mac).take(SERIAL_LENGTH)
        val params = mapOf(
            "hd" to "1",
            "ver" to PROFILE_VERSION,
            "num_banks" to "2",
            "sn" to serial,
            "stb_type" to device.model,
            "client_type" to "STB",
            "image_version" to IMAGE_VERSION,
            "video_out" to "hdmi",
            "device_id" to device.deviceId.orEmpty(),
            "device_id2" to device.deviceId2.orEmpty(),
            "signature" to device.signature.orEmpty(),
            "auth_second_step" to "0",
            "hw_version" to HW_VERSION,
            "not_valid_token" to "0",
            "hw_version_2" to sha1(device.mac),
            "timestamp" to (System.currentTimeMillis() / MILLIS_PER_SECOND).toString(),
            "api_signature" to API_SIGNATURE,
            "prehash" to "",
        )
        val profile = send(endpoint, "stb", "get_profile", params, token) { response ->
            if (refused(response)) throw StalkerException.Refused(response.code)
            if (!response.isSuccessful) throw StalkerException.Http(response.code)
            StalkerJson.js(response.body.string()) as? JsonObject ?: JsonObject(emptyMap())
        }
        if (profile.flag("blocked")) throw StalkerException.Blocked()
        return profile.string("token") to profile
    }

    /**
     * The login and password, for a portal that wants them (brief 7.1): `do_auth` answers `true`
     * when it takes them. [StalkerException.WrongLogin] when it doesn't.
     */
    private fun authorize(session: Session, login: StalkerLogin) {
        val params = mapOf(
            "login" to login.login,
            "password" to login.password,
            "device_id" to device.deviceId.orEmpty(),
            "device_id2" to device.deviceId2.orEmpty(),
        )
        val accepted = send(session.endpoint, "stb", "do_auth", params, session.token) { response ->
            if (refused(response)) throw StalkerException.Refused(response.code)
            if (!response.isSuccessful) throw StalkerException.Http(response.code)
            val answer = StalkerJson.js(response.body.string()) as? JsonPrimitive
            answer?.booleanOrNull == true || answer?.contentOrNull == "1"
        }
        if (!accepted) throw StalkerException.WrongLogin()
    }

    /** A small answer's `js`, signing in first, and once more if the portal let the token go. */
    private suspend fun call(type: String, action: String, params: Map<String, String> = emptyMap()): JsonElement? =
        withSession(type, action, params) { response -> StalkerJson.js(response.body.string()) }

    /** A big answer, read by [read] as it downloads; null from a portal that hasn't the action. */
    private suspend fun <T : Any> stream(type: String, action: String, params: Map<String, String> = emptyMap(), read: (InputStream) -> T): T? =
        withSession(type, action, params) { response -> read(response.body.byteStream()) }

    private suspend fun <T : Any> withSession(type: String, action: String, params: Map<String, String>, read: (Response) -> T?): T? {
        var current = sessionOrSignIn()
        var signedInAgain = false
        while (true) {
            val outcome = send(current.endpoint, type, action, params, current.token) { response ->
                when {
                    refused(response) -> null
                    // A portal without this action.
                    response.code == HTTP_NOT_FOUND -> Answer<T>(null)
                    !response.isSuccessful -> throw StalkerException.Http(response.code)
                    else -> Answer(read(response))
                }
            }
            if (outcome != null) return outcome.value
            if (signedInAgain) throw StalkerException.Refused()
            current = signIn(force = true, stale = current)
            signedInAgain = true
        }
    }

    /** What a call read; null inside from a portal that hasn't the action. */
    private class Answer<T>(val value: T?)

    /** The portal let the token go: HTTP 401 or 403, or "Authorization failed." in place of an answer. */
    private fun refused(response: Response): Boolean {
        if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) return true
        if (!response.isSuccessful) return false
        return response.peekBody(PEEK_BYTES).string().trimStart().startsWith("Authorization failed", ignoreCase = true)
    }

    private fun <T> send(endpoint: HttpUrl, type: String, action: String, params: Map<String, String>, token: String?, read: (Response) -> T): T {
        val url = endpoint.newBuilder()
            .addQueryParameter("type", type)
            .addQueryParameter("action", action)
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .addQueryParameter("JsHttpRequest", "1-xml")
            .build()
        val request = Request.Builder()
            .url(url)
            .header(USER_AGENT_HEADER, userAgent)
            .header("X-User-Agent", "Model: ${device.model}; Link: WiFi")
            .header("Referer", portal.referer.toString())
            .header("Cookie", "mac=${encode(device.mac)}; stb_lang=en; timezone=${encode(device.timeZone)}")
            .header("Accept", "*/*")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return http.newCall(request).execute().use(read)
    }

    private fun JsonObject.hasMorePages(page: Int): Boolean {
        val total = int("total_items") ?: return false
        val perPage = int("max_page_items")?.takeIf { it > 0 } ?: return false
        return page * perPage < total
    }

    private fun absoluteUrl(path: String): String? = when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> portal.base.resolve(path)?.toString()
        else -> null
    }

    private fun zone(): ZoneId = runCatching { ZoneId.of(device.timeZone) }.getOrDefault(ZoneOffset.UTC)

    /** An end date as portals write it: Unix seconds, "2027-03-12 00:00:00", "2027-03-12" or "March 12, 2027, 12:00 am". */
    private fun instantOf(text: String): Instant? {
        val value = text.trim().takeIf { it.isNotEmpty() && !it.startsWith("0000-00-00") } ?: return null
        value.toLongOrNull()?.let { seconds -> return Instant.ofEpochSecond(seconds).takeIf { seconds > 0 } }
        for (format in DATE_TIMES) runCatching { return LocalDateTime.parse(value, format).atZone(zone()).toInstant() }
        for (format in DATES) runCatching { return LocalDate.parse(value, format).atStartOfDay(zone()).toInstant() }
        return null
    }

    companion object {
        /** A MAG box's user agent; portals vary, and `rev: 250` is also common (brief 7.1). */
        const val USER_AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

        private const val USER_AGENT_HEADER = "User-Agent"
        private const val PROFILE_VERSION =
            "ImageDescription: 0.2.18-r23-250; ImageDate: Wed Aug 29 10:49:53 EEST 2018; PORTAL version: 5.6.2; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c"
        private const val IMAGE_VERSION = "218"
        private const val HW_VERSION = "1.7-BD-00"
        private const val API_SIGNATURE = "262"
        private const val SERIAL_LENGTH = 13
        private const val MILLIS_PER_SECOND = 1_000L
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val PEEK_BYTES = 64L
        private const val TOKEN_BYTES = 16
        private const val DEFAULT_WATCHDOG_SECONDS = 120
        private const val MIN_WATCHDOG_SECONDS = 30L
        private const val MAX_WATCHDOG_SECONDS = 120L

        /** Pages through a portal that won't list every channel at once; 14 a page, so up to 28,000. */
        private const val MAX_CHANNEL_PAGES = 2_000

        /** A day's guide is around ten pages on the busiest channels. */
        private const val MAX_DAY_PAGES = 20
        private const val START_TOLERANCE_SECONDS = 5 * 60L

        private val DATE_TIMES: List<DateTimeFormatter> = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "MMMM d, yyyy, h:mm a", "MMM d, yyyy, h:mm a")
            .map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH) }
        private val DATES: List<DateTimeFormatter> = listOf("yyyy-MM-dd", "dd.MM.yyyy", "MMMM d, yyyy", "MMM d, yyyy")
            .map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH) }

        /** Commands look like "ffmpeg http://…" or "auto http://…"; the player only wants the address. */
        fun stripPlayerPrefix(cmd: String): String = cmd.trim().substringAfterLast(' ')

        /**
         * How many days a channel's archive goes back. Ministra counts in hours (24, 168) and
         * Xtream-based portals in days (7); a whole number of days in hours reads as days.
         */
        internal fun archiveDaysOf(duration: Int?): Int? = when {
            duration == null || duration <= 0 -> null
            duration >= HOURS_PER_DAY && duration % HOURS_PER_DAY == 0 -> duration / HOURS_PER_DAY
            duration > MAX_ARCHIVE_DAYS -> (duration + HOURS_PER_DAY - 1) / HOURS_PER_DAY
            else -> duration
        }

        private const val HOURS_PER_DAY = 24
        private const val MAX_ARCHIVE_DAYS = 31

        private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)

        private fun randomToken(): String = Random.nextBytes(TOKEN_BYTES).joinToString("") { "%02X".format(it) }

        private fun md5(text: String): String = hex(MessageDigest.getInstance("MD5").digest(text.toByteArray())).uppercase()

        private fun sha1(text: String): String = hex(MessageDigest.getInstance("SHA-1").digest(text.toByteArray())).uppercase()

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
