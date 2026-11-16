// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pictures that can't be loaded on this TV (#228), as Plasma's picture loader finds them: one
 * that isn't on its server (404 or 410), whose address is too long for its server (414), that its
 * server refuses (401 or 403), or whose server can't be found by its name, doesn't answer or can't
 * be trusted. A movie's or a series' poster among them shows as a placeholder does (the owner's
 * #81): the title's own poster from its details, or the title in type; a channel's logo, as one
 * that's missing does.
 *
 * They're kept in [store] for a day ([RECHECK_MS]), so a poster found in place of one shows at
 * once the next time Plasma starts, without its server being asked first; then each is tried
 * again. A server a picture comes from is dropped from them at once.
 *
 * A server that isn't found, doesn't answer, or whose certificate can't be trusted, may be the
 * network's doing for a moment, so it counts only against a server no picture has come from in
 * this run, and only while the network works: Android says it reaches the internet
 * ([networkWorks]), or pictures have come from other servers in the last minute. After
 * [GIVEN_UP_AFTER] such failures the server itself is known, and its other pictures aren't asked
 * for at all. [PictureServers] checks every server the pictures are on, and tells what it finds
 * ([checked]): a server given up then, or kept up to date, before a picture of it is on screen.
 */
class UnloadablePictures(
    private val now: () -> Long = System::currentTimeMillis,
    private val store: Store? = null,
    private val networkWorks: () -> Boolean = { false },
) {
    /** Why a picture couldn't load. */
    enum class Why {
        /** Its server's name wasn't found. */
        NoServer,

        /** Its server didn't answer: it turned the connection away, or let it time out. */
        NoAnswer,

        /** Its server says it isn't there (404 or 410). */
        NotThere,

        /** Its address is too long for its server (414). */
        AddressTooLong,

        /** Its server won't give it (401 or 403). */
        Refused,

        /** Its server's certificate can't be trusted. */
        Untrusted,

        /** Its server gives something that isn't a picture Plasma can show: a web page, as a name that has lapsed does. */
        NotPictures,

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

    /** Where what's known is kept between runs: a few lines of text. */
    interface Store {
        /** Reads what was kept, if anything, and hands it to [read], on whatever thread suits. */
        fun read(read: (String) -> Unit)

        /** Keeps what [text] gives, when it suits: it's asked for it then. */
        fun write(text: () -> String)
    }

    private val lock = Any()

    // Each picture and server known, with when it was found.
    private val pictures = LinkedHashMap<String, Long>()
    private val servers = LinkedHashMap<String, Long>()
    private val cameFrom = HashSet<String>()
    private val failures = HashMap<String, Int>()
    private var lastCame: Long? = null
    private var askedAt: Long? = null
    private var reaches = false
    private val state = MutableStateFlow(Known.NONE)

    /** What's known so far, changing as more is found. */
    val known: StateFlow<Known> = state.asStateFlow()

    init {
        store?.read(::merge)
    }

    /** What was kept from earlier runs, added to what's known, but what's due to be tried again. */
    private fun merge(kept: String) {
        synchronized(lock) {
            val since = now() - RECHECK_MS
            for (line in kept.lineSequence()) {
                val parts = line.split('\t')
                val at = parts.getOrNull(2)?.toLongOrNull() ?: continue
                if (at < since) continue
                when (parts[0]) {
                    PICTURE -> pictures.putIfAbsent(parts[1], at)
                    SERVER -> if (parts[1] !in cameFrom) servers.putIfAbsent(parts[1], at)
                }
            }
            publish()
        }
    }

    /** A picture came from [picture]'s server, over the network: it's there after all. */
    fun came(picture: String) {
        val server = serverOf(picture) ?: return
        synchronized(lock) {
            cameFrom += server
            lastCame = now()
            failures.remove(server)
            if (servers.remove(server) != null) {
                pictures.keys.removeAll { serverOf(it) == server }
                changed()
            }
        }
    }

    /** [picture] couldn't load, for [why]. */
    fun failed(picture: String, why: Why) {
        val server = serverOf(picture) ?: return
        synchronized(lock) {
            val at = now()
            when (why) {
                Why.NotThere, Why.AddressTooLong, Why.Refused, Why.NotPictures -> if (pictures.put(picture, at) != null) return
                Why.NoServer, Why.NoAnswer, Why.Untrusted -> {
                    if (server in cameFrom || !works(at)) return
                    pictures[picture] = at
                    val count = (failures[server] ?: 0) + 1
                    failures[server] = count
                    if (count >= GIVEN_UP_AFTER) servers[server] = at
                }
                Why.Other -> return
            }
            changed()
        }
    }

    /**
     * What [PictureServers] found of [server], by its name as [serverOf] gives it: it gives
     * pictures ([dead] null), so it's kept as one a picture came from; or none of its pictures can
     * load, for [dead], so it's given up at once, or again, for another day. One a picture has come
     * from in this run isn't given up this way: the check that says so may be the network's doing.
     */
    fun checked(server: String, dead: Why?) {
        synchronized(lock) {
            if (dead == null) {
                cameFrom += server
                lastCame = now()
                failures.remove(server)
                if (servers.remove(server) != null) {
                    pictures.keys.removeAll { serverOf(it) == server }
                    changed()
                }
                return
            }
            if (server in cameFrom) return
            servers[server] = now()
            changed()
        }
    }

    /** Whether the network works: a picture came from some server lately, or Android says so. */
    private fun works(at: Long): Boolean {
        val came = lastCame
        if (came != null && at - came <= LATELY_MS) return true
        // Android is asked at most once a second, however many fail together.
        val asked = askedAt
        if (asked == null || at - asked >= ASK_EVERY_MS) {
            askedAt = at
            reaches = runCatching(networkWorks).getOrDefault(false)
        }
        return reaches
    }

    /** Under [lock]: what's known now shown, and kept for the next run. */
    private fun changed() {
        publish()
        store?.write(::text)
    }

    /** Under [lock]: what's known now, without what's due to be tried again, shown. */
    private fun publish() {
        val since = now() - RECHECK_MS
        pictures.values.removeAll { it < since }
        servers.values.removeAll { it < since }
        trim(pictures, PICTURES_KEPT)
        trim(servers, SERVERS_KEPT)
        state.value = Known(pictures.keys.toSet(), servers.keys.toSet())
    }

    /** What's known, as [Store] keeps it: a line for each server and picture, with when it was found. */
    private fun text(): String = synchronized(lock) {
        buildString {
            fun line(kind: String, name: String, at: Long) {
                // An address with a tab or a line's end in it would break the lines: it isn't kept.
                if (name.none { it == '\t' || it == '\n' || it == '\r' }) append(kind).append('\t').append(name).append('\t').append(at).append('\n')
            }
            servers.forEach { (server, at) -> line(SERVER, server, at) }
            pictures.forEach { (picture, at) -> line(PICTURE, picture, at) }
        }
    }

    /** The oldest go when there are more than [most]. */
    private fun trim(map: LinkedHashMap<String, Long>, most: Int) {
        val over = map.size - most
        if (over <= 0) return
        val oldest = map.entries.sortedBy { it.value }.take(over).map { it.key }
        oldest.forEach(map::remove)
    }

    /**
     * A [Store] in [file], written a moment after a change ([WRITE_AFTER_MS]) on a thread of its
     * own, so that many changes together are written once.
     */
    class FileStore(private val file: File) : Store {
        private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "plasma-unloadable-pictures").apply { isDaemon = true }
        }
        private val lock = Any()
        private var pending: (() -> String)? = null

        override fun read(read: (String) -> Unit) {
            worker.execute { runCatching { if (file.isFile) read(file.readText()) } }
        }

        override fun write(text: () -> String) {
            val scheduled = synchronized(lock) {
                val was = pending != null
                pending = text
                was
            }
            if (!scheduled) worker.schedule(::flush, WRITE_AFTER_MS, TimeUnit.MILLISECONDS)
        }

        private fun flush() {
            val text = synchronized(lock) { pending.also { pending = null } } ?: return
            runCatching {
                file.parentFile?.mkdirs()
                val fresh = File(file.path + ".new")
                fresh.writeText(text())
                if (!fresh.renameTo(file)) {
                    file.delete()
                    fresh.renameTo(file)
                }
            }
        }
    }

    companion object {
        /** Failures of a server's name or certificate before every picture of it is given up. */
        const val GIVEN_UP_AFTER = 3

        /** How lately a picture has to have come from another server for the network to count as working. */
        const val LATELY_MS = 60_000L

        /** How long a picture or server is known not to load before it's tried again: a day. */
        const val RECHECK_MS = 24L * 60 * 60 * 1000

        /** How long a [FileStore] waits after a change before writing. */
        const val WRITE_AFTER_MS = 2_000L

        private const val ASK_EVERY_MS = 1_000L

        private const val PICTURES_KEPT = 5_000
        private const val SERVERS_KEPT = 500
        private const val PICTURE = "picture"
        private const val SERVER = "server"

        /**
         * Why a picture's server couldn't be asked for it, as [error], how asking failed, says: its
         * name wasn't found, it didn't answer (a connection turned away or that timed out), or its
         * certificate can't be trusted; anything else may well work next time.
         */
        fun whyNot(error: Throwable): Why = when (error) {
            is UnknownHostException -> Why.NoServer
            is SSLHandshakeException, is SSLPeerUnverifiedException -> Why.Untrusted
            is ConnectException, is NoRouteToHostException, is PortUnreachableException -> Why.NoAnswer
            // A connection that timed out, not a picture that took too long once connected.
            is SocketTimeoutException -> if (error.message?.contains("connect", ignoreCase = true) == true) Why.NoAnswer else Why.Other
            else -> Why.Other
        }

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
