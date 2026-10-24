// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.xtream

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeToSequence

/**
 * A client for an Xtream Codes server's `player_api.php` (brief 7.2), ported from the earlier
 * iptv-player and reviewed for Plasma.
 *
 * Panels disagree on JSON types: numbers arrive as strings, booleans as "0" and "1", and missing
 * fields as null or "". So answers are read leniently from [JsonElement]s rather than strict
 * models. Calls run on [Dispatchers.IO].
 *
 * @param userAgent the source's own user agent (5.1); the HTTP client's default otherwise.
 */
class XtreamClient(
    private val http: OkHttpClient,
    val login: XtreamLogin,
    private val userAgent: String? = null,
) {
    /**
     * Signs in and returns the account, or throws: [XtreamException.WrongLogin] when the server
     * turns the login down, [XtreamException.NotActive] when the account can't play, and
     * [XtreamException.NotXtream] when the address isn't an Xtream server.
     */
    suspend fun signIn(): XtreamAccount = withContext(Dispatchers.IO) {
        // A wrong login gets {"user_info":{"auth":0}} from most panels, and [] from some.
        val root = get(action = null) as? JsonObject ?: throw XtreamException.WrongLogin()
        val user = root["user_info"] as? JsonObject ?: throw XtreamException.NotXtream()
        if (user.int("auth") != 1) throw XtreamException.WrongLogin()
        val account = XtreamAccount(
            status = user.string("status"),
            expiresAt = user.long("exp_date")?.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
            maxConnections = user.int("max_connections")?.takeIf { it > 0 },
            activeConnections = user.int("active_cons"),
            outputFormats = (user["allowed_output_formats"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
                .orEmpty(),
            isTrial = user.int("is_trial") == 1,
            serverTimeZone = (root["server_info"] as? JsonObject)?.string("timezone"),
        )
        val status = account.status
        if (status != null && !status.equals("Active", ignoreCase = true)) throw XtreamException.NotActive(status)
        account
    }

    suspend fun liveCategories(): List<XtreamCategory> = categories("get_live_categories")

    /** The movie categories, in the server's order (brief 5.9). */
    suspend fun movieCategories(): List<XtreamCategory> = categories("get_vod_categories")

    /** The series categories, in the server's order. */
    suspend fun seriesCategories(): List<XtreamCategory> = categories("get_series_categories")

    private suspend fun categories(action: String): List<XtreamCategory> = withContext(Dispatchers.IO) {
        getArray(action).mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("category_id") ?: return@mapNotNull null
            XtreamCategory(id = id, name = obj.string("category_name") ?: id)
        }
    }

    /**
     * Every movie, in the server's order, handed to [each] as it's read: a catalog of tens of
     * thousands is never in memory whole (brief 6.4).
     */
    suspend fun movies(each: suspend (XtreamMovie) -> Unit) = withContext(Dispatchers.IO) {
        eachOf("get_vod_streams") { obj ->
            val id = obj.string("stream_id") ?: return@eachOf
            val name = obj.string("name") ?: obj.string("title") ?: id
            each(
                XtreamMovie(
                    streamId = id,
                    name = name,
                    poster = obj.httpUrl("stream_icon") ?: obj.httpUrl("cover"),
                    categoryId = obj.categoryId(),
                    added = obj.long("added")?.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
                    rating = obj.rating(),
                    extension = obj.string("container_extension")?.trimStart('.')?.lowercase(),
                    year = obj.year("year", "release_date", "releaseDate") ?: yearIn(name),
                ),
            )
        }
    }

    /** Every series, in the server's order, handed to [each] as it's read. */
    suspend fun series(each: suspend (XtreamSeries) -> Unit) = withContext(Dispatchers.IO) {
        eachOf("get_series") { obj ->
            val id = obj.string("series_id") ?: return@eachOf
            val name = obj.string("name") ?: obj.string("title") ?: id
            each(
                XtreamSeries(
                    seriesId = id,
                    name = name,
                    poster = obj.httpUrl("cover"),
                    backdrop = (obj["backdrop_path"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content?.trim() }
                        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                        ?: obj.httpUrl("backdrop_path"),
                    categoryId = obj.categoryId(),
                    plot = obj.string("plot"),
                    cast = obj.string("cast"),
                    director = obj.string("director"),
                    genre = obj.string("genre"),
                    year = obj.year("releaseDate", "release_date", "year") ?: yearIn(name),
                    rating = obj.rating(),
                    updated = obj.long("last_modified")?.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
                    trailer = obj.string("youtube_trailer"),
                ),
            )
        }
    }

    /** Every live channel and radio station, in the server's order. */
    suspend fun liveStreams(): List<XtreamStream> = withContext(Dispatchers.IO) {
        getArray("get_live_streams").mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("stream_id") ?: return@mapNotNull null
            XtreamStream(
                streamId = id,
                number = obj.int("num")?.takeIf { it > 0 },
                name = obj.string("name") ?: id,
                logo = obj.httpUrl("stream_icon"),
                epgChannelId = obj.string("epg_channel_id"),
                categoryId = obj.categoryId(),
                archiveDays = if (obj.int("tv_archive") == 1) obj.int("tv_archive_duration")?.takeIf { it > 0 } else null,
                isRadio = obj.string("stream_type")?.startsWith("radio") == true,
            )
        }
    }

    /** The next few programmes on one stream, the fallback for now and next (brief 5.3). */
    suspend fun shortEpg(streamId: String, limit: Int = 4): List<XtreamListing> = withContext(Dispatchers.IO) {
        val root = get("get_short_epg", mapOf("stream_id" to streamId, "limit" to limit.toString())) as? JsonObject
        (root?.get("epg_listings") as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            // Unix times are reliable; the local-time strings are in the server's own zone.
            val start = obj.long("start_timestamp")?.let(Instant::ofEpochSecond) ?: return@mapNotNull null
            val end = obj.long("stop_timestamp")?.let(Instant::ofEpochSecond) ?: return@mapNotNull null
            XtreamListing(
                title = obj.string("title")?.let(::decodeBase64) ?: return@mapNotNull null,
                description = obj.string("description")?.let(::decodeBase64)?.takeIf(String::isNotBlank),
                start = start,
                end = end,
            )
        }
    }

    /** Where a live stream plays from. Carries the credentials: never log it unredacted. */
    fun liveUrl(streamId: String, format: StreamFormat): String = login.server.newBuilder()
        .addPathSegment("live")
        .addPathSegment(login.username)
        .addPathSegment(login.password)
        .addPathSegment("$streamId.${format.extension}")
        .build()
        .toString()

    /**
     * Where a past programme plays from the server's archive (brief 7.2):
     * `/timeshift/U/P/{minutes}/{YYYY-MM-DD:HH-MM}/{stream id}.ts`, with the start written in the
     * server's own time zone. Carries the credentials.
     */
    fun timeshiftUrl(streamId: String, start: Instant, durationMinutes: Int, serverZone: ZoneId): String = login.server.newBuilder()
        .addPathSegment("timeshift")
        .addPathSegment(login.username)
        .addPathSegment(login.password)
        .addPathSegment(durationMinutes.coerceAtLeast(1).toString())
        .addPathSegment(TIMESHIFT_START.format(start.atZone(serverZone)))
        .addPathSegment("$streamId.ts")
        .build()
        .toString()

    /** Where a movie plays from: `/movie/U/P/{id}.{extension}` (brief 7.2). Carries the credentials. */
    fun movieUrl(streamId: String, extension: String?): String = fileUrl("movie", streamId, extension)

    /** Where an episode plays from: `/series/U/P/{id}.{extension}`. Carries the credentials. */
    fun episodeUrl(episodeId: String, extension: String?): String = fileUrl("series", episodeId, extension)

    private fun fileUrl(kind: String, id: String, extension: String?): String = login.server.newBuilder()
        .addPathSegment(kind)
        .addPathSegment(login.username)
        .addPathSegment(login.password)
        .addPathSegment("$id.${extension?.takeIf { it.isNotBlank() } ?: DEFAULT_EXTENSION}")
        .build()
        .toString()

    /** The full guide (brief 5.3). Carries the credentials. */
    fun xmltvUrl(): String = login.server.newBuilder()
        .addPathSegment("xmltv.php")
        .addQueryParameter("username", login.username)
        .addQueryParameter("password", login.password)
        .build()
        .toString()

    private fun getArray(action: String): JsonArray = when (val element = get(action)) {
        is JsonArray -> element
        // Some panels answer an empty list with {} or null.
        is JsonObject, JsonNull -> JsonArray(emptyList())
        else -> throw XtreamException.NotXtream()
    }

    /**
     * Reads [action]'s list an element at a time as it arrives, handing each object to [each]. An
     * empty list may come as `{}` or null; anything else that isn't a list of objects isn't Xtream.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun eachOf(action: String, each: suspend (JsonObject) -> Unit) {
        open(action).use { response ->
            val elements = try {
                json.decodeToSequence(response.body.byteStream(), JsonElement.serializer(), DecodeSequenceMode.AUTO_DETECT).iterator()
            } catch (error: SerializationException) {
                throw XtreamException.NotXtream(error)
            }
            while (true) {
                val element = try {
                    if (!elements.hasNext()) break
                    elements.next()
                } catch (error: SerializationException) {
                    throw XtreamException.NotXtream(error)
                } catch (error: IllegalArgumentException) {
                    throw XtreamException.NotXtream(error)
                }
                currentCoroutineContext().ensureActive()
                when (element) {
                    is JsonObject -> each(element)
                    JsonNull -> Unit
                    // A web page or a message, read leniently as a word: not a list at all.
                    else -> throw XtreamException.NotXtream()
                }
            }
        }
    }

    private fun get(action: String?, params: Map<String, String> = emptyMap()): JsonElement =
        open(action, params).use { response ->
            val body = response.body.string()
            runCatching { json.parseToJsonElement(body) }.getOrElse { throw XtreamException.NotXtream(it) }
        }

    /** Asks for [action]; the caller reads and closes the answer. */
    private fun open(action: String?, params: Map<String, String> = emptyMap()): Response {
        val url: HttpUrl = login.server.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", login.username)
            .addQueryParameter("password", login.password)
            .apply { if (action != null) addQueryParameter("action", action) }
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .apply { userAgent?.let { header("User-Agent", it) } }
            .build()
        val response = http.newCall(request).execute()
        when (response.code) {
            in 200..299 -> return response
            401, 403 -> {
                response.close()
                throw XtreamException.WrongLogin(response.code)
            }
            else -> {
                response.close()
                throw XtreamException.Http(response.code)
            }
        }
    }

    private companion object {
        val TIMESHIFT_START: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
        val json = Json { isLenient = true }

        /** What a file without an extension of its own is asked for as. */
        const val DEFAULT_EXTENSION = "mp4"

        /** A year a title ends with: "Glass Orchard (2026)". */
        val TITLE_YEAR = Regex("""\((\d{4})\)\s*$""")

        fun yearIn(name: String): Int? = TITLE_YEAR.find(name)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in YEARS }

        val YEARS = 1900..2100

        /** The first of [keys] that holds a year, as "2025" or "2025-03-14". */
        fun JsonObject.year(vararg keys: String): Int? = keys.firstNotNullOfOrNull { key -> string(key)?.take(4)?.toIntOrNull()?.takeIf { it in YEARS } }

        /** Newer panels list several categories; the first is the main one. */
        fun JsonObject.categoryId(): String? =
            string("category_id") ?: (this["category_ids"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }

        /** Out of 10: `rating`, or else twice `rating_5based`; none for 0. */
        fun JsonObject.rating(): Float? =
            (string("rating")?.toFloatOrNull() ?: string("rating_5based")?.toFloatOrNull()?.times(2))?.takeIf { it > 0f && it <= 10f }

        fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

        fun JsonObject.long(key: String): Long? = string(key)?.toDoubleOrNull()?.toLong()

        fun JsonObject.int(key: String): Int? = long(key)?.toInt()

        fun JsonObject.httpUrl(key: String): String? =
            string(key)?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

        /** Short-EPG text is Base64 on most panels and plain on a few. */
        fun decodeBase64(text: String): String =
            runCatching { String(Base64.getDecoder().decode(text), Charsets.UTF_8) }
                .getOrNull()
                ?.takeIf { decoded -> decoded.isNotEmpty() && decoded.none { it == '�' || (it.isISOControl() && it !in "\n\r\t") } }
                ?: text
    }
}
