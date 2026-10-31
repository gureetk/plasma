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
                    backdrop = obj.firstUrl("backdrop_path"),
                    categoryId = obj.categoryId(),
                    plot = obj.string("plot"),
                    cast = obj.string("cast"),
                    director = obj.string("director"),
                    genre = obj.string("genre"),
                    year = obj.year("releaseDate", "release_date", "year") ?: yearIn(name),
                    rating = obj.rating(),
                    updated = obj.long("last_modified")?.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
                    trailer = youTubeId(obj.string("youtube_trailer")),
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

    /** A movie's details (brief 5.9): plot, cast, running time, and its file as the server probed it. */
    suspend fun movieInfo(streamId: String): XtreamMovieInfo = withContext(Dispatchers.IO) {
        val root = get("get_vod_info", mapOf("vod_id" to streamId)) as? JsonObject ?: throw XtreamException.NotXtream()
        // A movie without details has "info": [] on some panels.
        val info = root["info"] as? JsonObject ?: EMPTY
        val data = root["movie_data"] as? JsonObject
        val posters = listOfNotNull(info.httpUrl("cover_big"), info.httpUrl("movie_image")).distinct()
        XtreamMovieInfo(
            plot = info.string("plot") ?: info.string("description"),
            cast = info.string("cast") ?: info.string("actors"),
            director = info.string("director"),
            genre = info.string("genre"),
            durationSecs = info.durationSecs(),
            poster = posters.firstOrNull(),
            backdrop = info.firstUrl("backdrop_path"),
            trailer = youTubeId(info.string("youtube_trailer") ?: info.string("trailer")),
            year = info.year("releasedate", "release_date", "year"),
            rating = info.rating(),
            age = info.age(),
            extension = data?.string("container_extension")?.trimStart('.')?.lowercase(),
            video = info.video(),
            audio = info.audio(),
            posters = posters,
        )
    }

    /** A series' details and its episodes, season by season (brief 5.9). */
    suspend fun seriesInfo(seriesId: String): XtreamSeriesInfo = withContext(Dispatchers.IO) {
        val root = get("get_series_info", mapOf("series_id" to seriesId)) as? JsonObject ?: throw XtreamException.NotXtream()
        val info = root["info"] as? JsonObject ?: EMPTY
        // Episodes come as {"1": [..], "2": [..]}, as [[..], [..]] or as one list, by panel.
        val lists: List<JsonElement> = when (val episodes = root["episodes"]) {
            is JsonObject -> episodes.values.toList()
            is JsonArray -> episodes.toList()
            else -> emptyList()
        }
        val episodes = lists.flatMap { list -> (list as? JsonArray)?.toList() ?: listOf(list) }.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            val details = obj["info"] as? JsonObject ?: EMPTY
            XtreamEpisode(
                id = id,
                season = obj.int("season") ?: details.int("season") ?: 1,
                number = obj.int("episode_num") ?: details.int("episode_num") ?: 0,
                title = episodeTitle(obj.string("title")) ?: episodeTitle(details.string("name")),
                extension = obj.string("container_extension")?.trimStart('.')?.lowercase(),
                durationSecs = details.durationSecs(),
                plot = details.string("plot"),
                still = details.httpUrl("movie_image") ?: details.httpUrl("cover_big"),
                year = details.year("releasedate", "release_date", "air_date"),
                added = obj.long("added")?.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
                video = details.video(),
                audio = details.audio(),
            )
        }.distinctBy { it.id }.sortedWith(compareBy({ it.season }, { it.number }))
        XtreamSeriesInfo(
            plot = info.string("plot"),
            cast = info.string("cast"),
            director = info.string("director"),
            genre = info.string("genre"),
            backdrop = info.firstUrl("backdrop_path"),
            trailer = youTubeId(info.string("youtube_trailer")),
            year = info.year("releaseDate", "release_date", "year"),
            rating = info.rating(),
            age = info.age(),
            episodes = episodes,
        )
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

        val EMPTY = JsonObject(emptyMap())

        /** An age rating is short ("13+", "PG-13"); anything longer is a note, not a rating. */
        const val AGE_LENGTH = 6

        fun JsonObject.age(): String? = (string("age") ?: string("mpaa_rating"))?.takeIf { it.length <= AGE_LENGTH }

        /** `duration_secs`, or else `duration` as "01:58:00" or "118 min". */
        fun JsonObject.durationSecs(): Int? = int("duration_secs")?.takeIf { it > 0 } ?: string("duration")?.let(::secondsOf)

        val MINUTES = Regex("""^(\d+)\s*min""")

        /** "01:58:00" or "118 min" in seconds; none for anything else, or for nothing at all. */
        fun secondsOf(text: String): Int? {
            val trimmed = text.trim()
            val parts = trimmed.split(':')
            if (parts.size == 3 && parts.all { part -> part.isNotEmpty() && part.all(Char::isDigit) }) {
                return parts.fold(0) { total, part -> total * 60 + part.toInt() }.takeIf { it > 0 }
            }
            return MINUTES.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()?.times(60)?.takeIf { it > 0 }
        }

        /** The first web address of [key], which may be one address or a list of them. */
        fun JsonObject.firstUrl(key: String): String? = when (val value = this[key]) {
            is JsonArray -> value.firstNotNullOfOrNull { element ->
                (element as? JsonPrimitive)?.content?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            }
            else -> httpUrl(key)
        }

        /** A file's picture, from the server's probe of it (ffprobe's names). */
        fun JsonObject.video(): XtreamVideo? = (this["video"] as? JsonObject)?.let { video ->
            XtreamVideo(
                codec = video.string("codec_name")?.lowercase(),
                profile = video.string("profile"),
                width = video.int("width")?.takeIf { it > 0 },
                height = video.int("height")?.takeIf { it > 0 },
                frameRate = (video.string("avg_frame_rate") ?: video.string("r_frame_rate"))?.let(::rateOf),
                hdr = hdrOf(video),
            )
        }

        /** A file's main sound, from the server's probe of it. */
        fun JsonObject.audio(): XtreamAudio? = (this["audio"] as? JsonObject)?.let { audio ->
            XtreamAudio(
                codec = audio.string("codec_name")?.lowercase(),
                channels = audio.int("channels")?.takeIf { it > 0 },
                language = (audio["tags"] as? JsonObject)?.string("language")?.lowercase()?.takeIf { it != "und" },
                bitRate = audio.int("bit_rate")?.takeIf { it > 0 },
            )
        }

        /** "24000/1001" or "25" frames a second; none for "0/0". */
        fun rateOf(text: String): Float? {
            val (top, bottom) = text.split('/').let { it[0].toFloatOrNull() to (it.getOrNull(1)?.toFloatOrNull() ?: 1f) }
            if (top == null || bottom <= 0f) return null
            return (top / bottom).takeIf { it > 0f && it < MAX_FRAME_RATE }
        }

        const val MAX_FRAME_RATE = 1_000f

        /**
         * A probed video's HDR, from its side data, its tag and its transfer (ffprobe's names): a
         * Dolby Vision record or tag makes it Dolby Vision, SMPTE 2094-40 metadata HDR10+, PQ HDR10
         * and HLG HLG.
         */
        fun hdrOf(video: JsonObject): String? {
            val sideData = (video["side_data_list"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("side_data_type") }
            return when {
                sideData.any { it.contains("DOVI", ignoreCase = true) || it.contains("Dolby Vision", ignoreCase = true) } -> "Dolby Vision"
                video.string("codec_tag_string")?.lowercase() in DOLBY_VISION_TAGS -> "Dolby Vision"
                sideData.any { it.contains("2094-40") || it.contains("HDR10+") } -> "HDR10+"
                video.string("color_transfer") == "smpte2084" -> "HDR10"
                video.string("color_transfer") == "arib-std-b67" -> "HLG"
                else -> null
            }
        }

        /** MP4's sample entries for Dolby Vision over HEVC, H.264 and AV1. */
        val DOLBY_VISION_TAGS = setOf("dvhe", "dvh1", "dvav", "dva1", "dav1")

        val YOUTUBE_ID = Regex("""[A-Za-z0-9_-]{11}""")
        val YOUTUBE_LINK = Regex("""(?:[?&]v=|youtu\.be/|/embed/|/shorts/)([A-Za-z0-9_-]{11})""")

        /** A YouTube video's id, given as the id or as a link to it; none for anything else. */
        fun youTubeId(text: String?): String? {
            val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (YOUTUBE_ID.matches(value)) return value
            return YOUTUBE_LINK.find(value)?.groupValues?.get(1)
        }

        /** "S01E04", "S1 E4" or "s01e04" in a title. */
        val EPISODE_CODE = Regex("""\bS\d{1,3}\s*E\d{1,4}\b""", RegexOption.IGNORE_CASE)

        /**
         * An episode's own title: "Atlas Station - S02E04 - Dead Reckoning" is "Dead Reckoning", and
         * a title that's only the series and the number is none.
         */
        fun episodeTitle(title: String?): String? {
            val trimmed = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val code = EPISODE_CODE.find(trimmed) ?: return trimmed
            return trimmed.substring(code.range.last + 1).trim().trimStart('-', '–', '—', ':', '.', '|').trim().takeIf { it.isNotEmpty() }
        }

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
