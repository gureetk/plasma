// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.integrations.jellyfin

import java.time.Instant

/** A Jellyfin server (brief 5.19): the address it answers at, and the name it goes by. */
data class JellyfinServer(val address: String, val name: String)

/**
 * A Quick Connect code (brief 5.19): the user approves [code] in another Jellyfin app while Plasma
 * asks the server about it. The rest stays with the client.
 */
class QuickConnectCode internal constructor(
    val code: String,
    internal val secret: String,
    /** The device Plasma signs in as: the one that asked for the code. */
    internal val deviceId: String,
) {
    override fun toString(): String = "QuickConnectCode($code)"
}

/** Signed in to a Jellyfin server: who, where, and the token that stands for them there. */
data class JellyfinLogin(
    val address: String,
    val serverId: String,
    val serverName: String,
    val userId: String,
    val userName: String,
    val token: String,
    /**
     * The device Plasma signed in as. The server keeps one sign-in per device, so each user gets
     * their own (the SDK's advice).
     */
    val deviceId: String,
) {
    // It carries the token.
    override fun toString(): String = "JellyfinLogin($serverName, $userName)"
}

/** What a [JellyfinItem] is. */
enum class JellyfinKind { Movie, Episode, Other }

/** One of the user's items for Continue Watching (brief 5.19). */
data class JellyfinItem(
    val id: String,
    val kind: JellyfinKind,
    /** The movie, or the series an episode belongs to. */
    val title: String,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val image: String? = null,
    /** [image] is 16:9 art rather than a poster. */
    val wide: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** When it was last watched; for an episode up next, when its series was. Null when the server doesn't say. */
    val lastWatched: Instant? = null,
    /** The episode after one that was finished (Next Up), rather than one started. */
    val upNext: Boolean = false,
)

/** Why talking to the server didn't work. */
enum class JellyfinProblem {
    /** Nothing answered: the server is off, or the address is wrong. */
    CantReach,

    /** The server turned the username and password down. */
    WrongLogin,

    /** Quick Connect is turned off on the server. */
    QuickConnectOff,

    /** The server dropped the Quick Connect code: it wasn't approved in time. */
    Expired,

    /** The sign-in no longer works: it was signed out on the server, or the user was removed. */
    SignedOut,

    /** The server said no for another reason, or didn't answer as Jellyfin does. */
    Refused,
}

class JellyfinException(val problem: JellyfinProblem, cause: Throwable? = null) : Exception(problem.name, cause)
