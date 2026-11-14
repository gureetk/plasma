// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pictures that can't be loaded on this TV (#228), as Plasma's picture loader finds them: one
 * that isn't on its server (404 or 410), whose address is too long for its server (414), or whose
 * server can't be found by its name or can't be trusted. A movie's or a series' poster among them
 * shows as a placeholder does (the owner's #81): the movie's own poster from its details, or the
 * title in type. They're known until Plasma starts again, so each is tried once a run.
 *
 * A server that isn't found, or whose certificate can't be trusted, may be the network's doing
 * for a moment, so it counts only against a server no picture has come from in this run, and
 * only while pictures are coming from other servers. After [GIVEN_UP_AFTER] such failures the
 * server itself is known, and its other pictures aren't asked for at all.
 */
class UnloadablePictures(private val now: () -> Long = System::currentTimeMillis) {
    /** Why a picture couldn't load. */
    enum class Why {
        /** Its server's name wasn't found. */
        NoServer,

        /** Its server says it isn't there (404 or 410). */
        NotThere,

        /** Its address is too long for its server (414). */
        AddressTooLong,

        /** Its server's certificate can't be trusted. */
        Untrusted,

        /** Anything else, which may well work next time. */
        Other,
    }

    /** What's known not to load: pictures by their address, and every picture of a server given up. */
    class Known internal constructor(private val pictures: Set<String>, private val servers: Set<String>) {
        operator fun contains(picture: String?): Boolean {
            if (picture == null) return false
            if (picture in pictures) return true
            return servers.isNotEmpty() && serverOf(picture)?.let { it in servers } == true
        }

        /** How many pictures and servers are known, for Export Diagnostics. */
        val sizes: Pair<Int, Int> get() = pictures.size to servers.size

        companion object {
            val NONE = Known(emptySet(), emptySet())
        }
    }

    private val lock = Any()
    private val pictures = HashSet<String>()
    private val servers = HashSet<String>()
    private val cameFrom = HashSet<String>()
    private val failures = HashMap<String, Int>()
    private var lastCame: Long? = null
    private val state = MutableStateFlow(Known.NONE)

    /** What's known so far, changing as more is found. */
    val known: StateFlow<Known> = state.asStateFlow()

    /** A picture came from [picture]'s server, over the network. */
    fun came(picture: String) {
        val server = serverOf(picture) ?: return
        synchronized(lock) {
            cameFrom += server
            lastCame = now()
        }
    }

    /** [picture] couldn't load, for [why]. */
    fun failed(picture: String, why: Why) {
        val server = serverOf(picture) ?: return
        synchronized(lock) {
            when (why) {
                Why.NotThere, Why.AddressTooLong -> if (!pictures.add(picture)) return
                Why.NoServer, Why.Untrusted -> {
                    val came = lastCame
                    // A server pictures have come from, or a network nothing has come over lately.
                    if (server in cameFrom || came == null || now() - came > LATELY_MS) return
                    pictures += picture
                    val count = (failures[server] ?: 0) + 1
                    failures[server] = count
                    if (count >= GIVEN_UP_AFTER) servers += server
                }
                Why.Other -> return
            }
            state.value = Known(pictures.toSet(), servers.toSet())
        }
    }

    companion object {
        /** Failures of a server's name or certificate before every picture of it is given up. */
        const val GIVEN_UP_AFTER = 3

        /** How lately a picture has to have come from another server for the network to count as working. */
        const val LATELY_MS = 60_000L

        /** [picture]'s server, as its address names it, in lower case; null for an address with none. */
        fun serverOf(picture: String): String? {
            val start = picture.indexOf("://").takeIf { it > 0 }?.plus(3) ?: return null
            var end = picture.length
            for (i in start until picture.length) {
                val c = picture[i]
                if (c == '/' || c == '?' || c == '#') {
                    end = i
                    break
                }
            }
            val authority = picture.substring(start, end).substringAfterLast('@')
            val host = if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')
            return host.lowercase().takeIf { it.isNotEmpty() }
        }
    }
}
