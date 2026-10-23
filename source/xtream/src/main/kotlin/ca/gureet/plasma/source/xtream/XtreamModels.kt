// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.xtream

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.time.Instant

/**
 * Where an Xtream account lives and how to sign in to it. [toString] never shows the credentials,
 * because they also travel in every stream URL (brief 6.10).
 */
data class XtreamLogin(val server: HttpUrl, val username: String, val password: String) {
    override fun toString(): String = "XtreamLogin(${server.host})"

    companion object {
        /** Files people paste along with the server address; only the part before them counts. */
        private val ENDPOINTS = setOf("player_api.php", "get.php", "xmltv.php", "panel_api.php")

        /**
         * The server part of what someone typed: `line.example.org:8080`, a full
         * `http://…/player_api.php?username=…` link, or an address behind a path. Null when it
         * isn't an address at all.
         */
        fun server(text: String): HttpUrl? {
            val trimmed = text.trim().trimEnd('/')
            if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            val segments = url.pathSegments.filter { it.isNotEmpty() }
                .let { if (it.lastOrNull()?.lowercase() in ENDPOINTS) it.dropLast(1) else it }
            return url.newBuilder()
                .query(null)
                .fragment(null)
                .encodedPath("/")
                .apply { segments.forEach { addPathSegment(it) } }
                .build()
        }
    }
}

/** How live streams are fetched (Xtream Stream Format, brief 5.4.8). */
enum class StreamFormat(val extension: String) {
    Ts("ts"),
    Hls("m3u8"),
}

/** The account as the server describes it (brief 5.1, Account info). */
data class XtreamAccount(
    /** "Active", or the reason it isn't, such as "Expired" or "Banned". */
    val status: String?,
    /** Null when the account doesn't expire. */
    val expiresAt: Instant?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    /** What the account may stream: "ts", "m3u8", "rtmp". */
    val outputFormats: List<String>,
    val isTrial: Boolean,
    /** The zone the server writes catch-up times in, such as "Europe/London". */
    val serverTimeZone: String?,
) {
    /** TS where the account allows it, since most IPTV plays best that way; HLS otherwise. */
    val defaultFormat: StreamFormat
        get() = if (outputFormats.isEmpty() || outputFormats.any { it.equals("ts", ignoreCase = true) }) StreamFormat.Ts else StreamFormat.Hls

    fun allows(format: StreamFormat): Boolean =
        outputFormats.isEmpty() || outputFormats.any { it.equals(format.extension, ignoreCase = true) }
}

data class XtreamCategory(val id: String, val name: String)

/** A live channel (or radio station) as `get_live_streams` lists it. */
data class XtreamStream(
    val streamId: String,
    /** The provider's channel number (`num`), which Plasma uses by default (brief 5.2). */
    val number: Int?,
    val name: String,
    val logo: String?,
    /** The XMLTV channel id the guide uses for this stream. */
    val epgChannelId: String?,
    val categoryId: String?,
    /** Days of catch-up the server keeps, or null when it has none. */
    val archiveDays: Int?,
    val isRadio: Boolean,
)

/** A programme from `get_short_epg`, the now-and-next fallback when there's no full guide. */
data class XtreamListing(
    val title: String,
    val description: String?,
    val start: Instant,
    val end: Instant,
)

/** Why an Xtream request failed, in the terms the Add a Source screen explains to people. */
sealed class XtreamException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** The server turned the username and password down. */
    class WrongLogin(val httpCode: Int? = null) : XtreamException("The server didn't accept this username and password")

    /** Signed in, but the account can't play: expired, banned or disabled. */
    class NotActive(val status: String) : XtreamException("The account is $status")

    /** Something answered, but not as an Xtream server does (an HTML page, say). */
    class NotXtream(cause: Throwable? = null) : XtreamException("This address doesn't answer like an Xtream server", cause)

    class Http(val code: Int) : XtreamException("The server answered with HTTP $code")
}
