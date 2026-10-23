// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.xtream

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

    suspend fun liveCategories(): List<XtreamCategory> = withContext(Dispatchers.IO) {
        getArray("get_live_categories").mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("category_id") ?: return@mapNotNull null
            XtreamCategory(id = id, name = obj.string("category_name") ?: id)
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
                // Newer panels list several categories; the first is the main one.
                categoryId = obj.string("category_id") ?: (obj["category_ids"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content },
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

    private fun get(action: String?, params: Map<String, String> = emptyMap()): JsonElement {
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
        http.newCall(request).execute().use { response ->
            when (response.code) {
                in 200..299 -> Unit
                401, 403 -> throw XtreamException.WrongLogin(response.code)
                else -> throw XtreamException.Http(response.code)
            }
            val body = response.body.string()
            return runCatching { json.parseToJsonElement(body) }.getOrElse { throw XtreamException.NotXtream(it) }
        }
    }

    private companion object {
        val json = Json { isLenient = true }

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
