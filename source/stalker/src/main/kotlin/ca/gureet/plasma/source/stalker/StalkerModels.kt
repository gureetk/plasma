// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.stalker

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.time.Instant
import kotlin.random.Random

/**
 * A Stalker / Ministra portal as people type it (brief 7.1): `http://host/c/`,
 * `http://host:port/stalker_portal/c/` or `http://host/portal.php`. [base] is the address without
 * its `c/` page; the API answers at one of [endpoints].
 */
data class StalkerPortal(val base: HttpUrl, private val typedEndpoint: HttpUrl? = null) {
    /**
     * Where the box's requests may go, the likeliest first: what was typed, `<base>/server/load.php`
     * and `<host>/portal.php`, and on a bare host also `/stalker_portal/server/load.php`.
     */
    val endpoints: List<HttpUrl>
        get() {
            val root = base.newBuilder().encodedPath("/").build()
            val loadPhp = base.newBuilder().addPathSegments("server/load.php").build()
            val portalPhp = root.newBuilder().addPathSegment("portal.php").build()
            val candidates = if (base.encodedPath == "/") {
                listOf(portalPhp, loadPhp, root.newBuilder().addPathSegments("stalker_portal/server/load.php").build())
            } else {
                listOf(loadPhp, portalPhp)
            }
            return (listOfNotNull(typedEndpoint) + candidates).distinct()
        }

    /** The portal's own page, which a MAG box sends as its Referer. */
    val referer: HttpUrl get() = base.newBuilder().addPathSegment("c").addPathSegment("").build()

    override fun toString(): String = "StalkerPortal(${base.host})"

    companion object {
        /** The portal in what someone typed, or null when it isn't a web address. */
        fun of(text: String): StalkerPortal? {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            var segments = url.pathSegments.filter { it.isNotEmpty() }
            var typed: HttpUrl? = null
            when {
                segments.lastOrNull()?.lowercase() == "portal.php" -> {
                    typed = url.newBuilder().query(null).fragment(null).build()
                    segments = segments.dropLast(1)
                }
                segments.takeLast(2).map { it.lowercase() } == listOf("server", "load.php") -> {
                    typed = url.newBuilder().query(null).fragment(null).build()
                    segments = segments.dropLast(2)
                }
                else -> {
                    if (segments.lastOrNull()?.lowercase() == "index.html") segments = segments.dropLast(1)
                    if (segments.lastOrNull()?.lowercase() == "c") segments = segments.dropLast(1)
                }
            }
            val base = url.newBuilder()
                .query(null)
                .fragment(null)
                .encodedPath("/")
                .apply { segments.forEach { addPathSegment(it) } }
                .apply { if (segments.isNotEmpty()) addPathSegment("") }
                .build()
            return StalkerPortal(base, typed)
        }
    }
}

/**
 * The box Plasma presents itself as (brief 7.1): the MAC address the provider registered, and a
 * MAG model. The serial number, device ids and signature are optional; a portal that checks them
 * needs the registered box's own.
 */
data class StalkerDevice(
    /** Upper case with colons: "00:1A:79:4C:2E:91". */
    val mac: String,
    /** The IANA zone the portal writes guide times in, such as "America/Toronto". */
    val timeZone: String,
    val model: String = DEFAULT_MODEL,
    val serialNumber: String? = null,
    val deviceId: String? = null,
    val deviceId2: String? = null,
    val signature: String? = null,
) {
    // The MAC is what a portal knows the account by: it stays out of logs.
    override fun toString(): String = "StalkerDevice($model)"

    companion object {
        const val DEFAULT_MODEL = "MAG250"

        /** The prefix MAG boxes' MAC addresses have. */
        const val MAG_PREFIX = "00:1A:79"

        /** A MAC address in what someone typed, with or without separators; null when it isn't one. */
        fun normalizeMac(text: String): String? {
            val hex = text.trim().filter { it != ':' && it != '-' && it != '.' && it != ' ' }.uppercase()
            if (hex.length != MAC_HEX_DIGITS || hex.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
            return hex.chunked(2).joinToString(":")
        }

        /** A MAC address in MAG's range, for a TV that hasn't one registered yet (brief 7.1). */
        fun newMac(random: Random = Random.Default): String =
            MAG_PREFIX + (1..3).joinToString("") { ":%02X".format(random.nextInt(BYTE_VALUES)) }

        private const val MAC_HEX_DIGITS = 12
        private const val BYTE_VALUES = 256
    }
}

/** The account as the portal describes it (brief 5.1, Account info). */
data class StalkerAccount(
    /** False when the portal has blocked this box. */
    val active: Boolean,
    /** Null when the portal gives no end date, or one Plasma can't read. */
    val expiresAt: Instant?,
    /** The end date as the portal writes it, for one Plasma can't read ("Unlimited"). */
    val expiryText: String?,
    /** How often the portal wants to hear from the box while it plays, in seconds. */
    val watchdogSeconds: Int?,
)

/** A channel genre: the portal's word for a group. */
data class StalkerGenre(val id: String, val title: String)

/** A live channel. Its [cmd] becomes a playable address only through [StalkerClient.createLink]. */
data class StalkerChannel(
    val id: String,
    val name: String,
    val number: Int?,
    val cmd: String,
    val logo: String?,
    val genreId: String?,
    val xmltvId: String?,
    /** How many days back the portal keeps the channel's programmes; null without an archive. */
    val archiveDays: Int?,
)

/** A programme from the portal's own guide. */
data class StalkerProgramme(
    /** The portal's id for it, which its archive is reached by. */
    val id: String?,
    val start: Instant,
    val end: Instant,
    val title: String,
    val description: String?,
    val category: String?,
    /** The portal has it in its archive. */
    val archived: Boolean,
)

/** What went wrong talking to a portal, in terms Add a Source and the player can explain. */
sealed class StalkerException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** Nothing at the address answers as a portal does. */
    class NotStalker(cause: Throwable? = null) : StalkerException("This address doesn't answer like a Stalker portal", cause)

    /** The portal turns this box down: its MAC address isn't registered, or the portal won't sign it in. */
    class Refused(val httpCode: Int? = null) : StalkerException("The portal didn't accept this MAC address")

    /** The portal knows the box, but has blocked it. */
    class Blocked : StalkerException("The portal has blocked this box")

    /** The portal gave no address to play, with its reason ("nothing_to_play", "limit"…). */
    class NoLink(val reason: String?) : StalkerException("The portal gave no address to play" + (reason?.let { " ($it)" } ?: ""))

    class Http(val code: Int) : StalkerException("The portal answered with HTTP $code")
}
