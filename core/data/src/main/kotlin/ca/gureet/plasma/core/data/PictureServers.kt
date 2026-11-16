// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.PictureServerRow
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Checks the servers the pictures Plasma shows are on (#228): the catalogs' posters, the posters
 * found in titles' details, and the channels' and the guide's logos. A big catalog has tens of
 * thousands of pictures on a handful of servers, and a server whose name has lapsed, that doesn't
 * answer, or that answers every address with a web page, has none that load; asked once each,
 * they're known before any of their pictures is on screen, and their pictures show as missing ones
 * do: a title's own poster or its title, a channel's name or the guide's logo.
 *
 * Each server is asked for one of its pictures, and a second unless the first settles it
 * ([verdict]), four servers at a time, the ones with the most pictures first; a server that
 * answers is kept as one pictures come from, so a failure of one of its pictures while Plasma runs
 * doesn't count against it. Some [START_AFTER_MS] after Plasma starts, again when a source's
 * channels, guide or catalog have been read again ([run]'s `changes`), and every [EVERY_MS]. A
 * server is given up only if another answered in the same check, or Android says the network
 * reaches the internet: otherwise every failure may be the network's own.
 */
class PictureServers internal constructor(
    private val servers: suspend (most: Int) -> List<PictureServerRow>,
    private val ask: suspend (picture: String) -> Look,
    private val unloadable: UnloadablePictures,
    private val networkWorks: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** What one picture of a server, asked for, showed of the server. */
    enum class Look {
        /** A picture came. */
        Picture,

        /** The server answered, but hasn't this picture (404 or 410). */
        Missing,

        /** The address is too long for the server (414). */
        TooLong,

        /** The server won't give it (401 or 403). */
        Refused,

        /** The server answered with something that isn't a picture Plasma can show, but isn't a web page: a drawing (SVG). */
        Unshowable,

        /** The server answered with a web page, as a name that has lapsed does. */
        WebPage,

        /** The server's name wasn't found. */
        NoServer,

        /** The server didn't answer: it turned the connection away, or let it time out. */
        NoAnswer,

        /** The server's certificate can't be trusted. */
        Untrusted,

        /** Anything else: the server busy (429, 5xx), a read that timed out, a redirect that went nowhere. */
        Unsure,
    }

    /** What a check found, for Export Diagnostics. */
    data class Check(
        /** When it ended, in Unix ms. */
        val at: Long,
        val servers: Int,
        /** How many answer: with a picture, or with word that one isn't there. */
        val answer: Int,
        /** How many were given up, by why. */
        val givenUp: Map<UnloadablePictures.Why, Int>,
        /** How many said too little to tell, or would have been given up but nothing showed the network works. */
        val unsure: Int,
    )

    @Volatile
    var last: Check? = null
        private set

    /**
     * Checks some [START_AFTER_MS] after it's called, then after each of [changes] (but its first,
     * which is how things are now), [SETTLE_MS] later so a source's channels, guide and catalog
     * read one after another are checked once, and every [EVERY_MS] in between. Until cancelled.
     */
    suspend fun run(changes: Flow<Any?>) = coroutineScope {
        val changed = Channel<Unit>(Channel.CONFLATED)
        launch { changes.drop(1).collect { changed.trySend(Unit) } }
        delay(START_AFTER_MS)
        while (true) {
            try {
                check()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                // The database out of reach for a moment: the next check comes as usual.
            }
            withTimeoutOrNull(EVERY_MS) {
                changed.receive()
                delay(SETTLE_MS)
                changed.tryReceive()
            }
        }
    }

    /** Checks every server now, and tells [unloadable] what it found. */
    suspend fun check(): Check {
        val rows = servers(MOST_SERVERS)
        val slots = Semaphore(AT_ONCE)
        val found = coroutineScope {
            rows.map { row -> async { slots.withPermit { row to verdict(looksAt(row)) } } }.awaitAll()
        }
        // Only a network that works can say a server is gone.
        val trusted = found.any { (_, verdict) -> verdict.answers } || runCatching(networkWorks).getOrDefault(false)
        val givenUp = HashMap<UnloadablePictures.Why, Int>()
        var answer = 0
        var unsure = 0
        for ((row, verdict) in found) {
            val server = UnloadablePictures.serverOf(row.firstPicture) ?: continue
            verdict.missing.forEach { (picture, why) -> unloadable.failed(picture, why) }
            when {
                verdict.answers -> {
                    unloadable.checked(server, dead = null)
                    answer++
                }
                verdict.dead != null && trusted -> {
                    unloadable.checked(server, verdict.dead)
                    givenUp.merge(verdict.dead, 1, Int::plus)
                }
                else -> unsure++
            }
        }
        return Check(now(), rows.size, answer, givenUp, unsure).also { last = it }
    }

    /**
     * [row]'s first picture asked for, and its last too unless the first says enough: a picture,
     * or a server that isn't found, doesn't answer or can't be trusted.
     */
    private suspend fun looksAt(row: PictureServerRow): List<Pair<String, Look>> {
        val looks = ArrayList<Pair<String, Look>>(2)
        for (picture in listOf(row.firstPicture, row.lastPicture).distinct()) {
            val look = ask(picture)
            looks += picture to look
            if (look == Look.Picture || look == Look.NoServer || look == Look.NoAnswer || look == Look.Untrusted) break
        }
        return looks
    }

    /** What a server's looks say of it: it answers, or none of its pictures can load, and why. */
    internal class Verdict(
        /** It gave a picture, or said one it hasn't isn't there. */
        val answers: Boolean,
        val dead: UnloadablePictures.Why?,
        /** Its pictures asked for that can't load though it answers, and why. */
        val missing: List<Pair<String, UnloadablePictures.Why>>,
    )

    internal companion object {
        /** How long after Plasma starts the first check comes: Home has its first pictures by then. */
        const val START_AFTER_MS = 20_000L

        /** How long after a source is read again its pictures are checked: its channels, guide and catalog come one after another. */
        const val SETTLE_MS = 30_000L

        /** How often the servers are checked while Plasma runs, as the TV's home can for days. */
        const val EVERY_MS = 6L * 60 * 60 * 1000

        /** How many servers are asked at once. */
        const val AT_ONCE = 4

        /** The most servers checked: the ones with the most pictures. */
        const val MOST_SERVERS = 300

        /** How long a server has to take a connection, and to give the start of a picture. */
        const val CONNECT_SECONDS = 10L
        const val CALL_SECONDS = 20L

        /** How much of a picture is read to tell what it is. */
        const val SNIFFED_BYTES = 16L

        /**
         * A server's verdict from what its pictures, asked for in turn, showed ([looks]): any
         * picture says it gives them; a name not found, no answer or a certificate not trusted
         * says it's gone; web pages for all, or refusals for all, say none of its pictures can
         * load; a picture missing, too long or a drawing says only that picture can't, and the
         * server answers.
         */
        fun verdict(looks: List<Pair<String, Look>>): Verdict {
            val missing = looks.mapNotNull { (picture, look) ->
                when (look) {
                    Look.Missing -> picture to UnloadablePictures.Why.NotThere
                    Look.TooLong -> picture to UnloadablePictures.Why.AddressTooLong
                    Look.Unshowable -> picture to UnloadablePictures.Why.NotPictures
                    else -> null
                }
            }
            val seen = looks.map { it.second }
            val dead = when {
                Look.Picture in seen -> null
                Look.NoServer in seen -> UnloadablePictures.Why.NoServer
                Look.Untrusted in seen -> UnloadablePictures.Why.Untrusted
                Look.NoAnswer in seen -> UnloadablePictures.Why.NoAnswer
                seen.isNotEmpty() && seen.all { it == Look.WebPage } -> UnloadablePictures.Why.NotPictures
                seen.isNotEmpty() && seen.all { it == Look.Refused } -> UnloadablePictures.Why.Refused
                else -> null
            }
            val answers = dead == null && seen.any { it == Look.Picture || it == Look.Missing || it == Look.TooLong || it == Look.Unshowable }
            return Verdict(answers = answers, dead = dead, missing = missing)
        }

        /** What an answer with [code], [type] and starting with [start] says of the picture asked for. */
        fun lookOf(code: Int, type: String?, start: ByteArray): Look = when (code) {
            404, 410 -> Look.Missing
            414 -> Look.TooLong
            401, 403 -> Look.Refused
            in 200..299 -> lookOfBody(type, start)
            else -> Look.Unsure
        }

        /** What a picture's first bytes, [start], and the [type] its server gives it, say it is. */
        fun lookOfBody(type: String?, start: ByteArray): Look {
            fun at(i: Int, vararg bytes: Int) = start.size >= i + bytes.size && bytes.indices.all { start[i + it] == bytes[it].toByte() }
            fun text(i: Int, text: String) = at(i, *text.map { it.code }.toIntArray())
            val picture = at(0, 0xFF, 0xD8, 0xFF) || // JPEG
                at(0, 0x89, 'P'.code, 'N'.code, 'G'.code) ||
                text(0, "GIF8") ||
                (text(0, "RIFF") && text(8, "WEBP")) ||
                text(0, "BM") ||
                text(4, "ftyp") || // HEIF and AVIF
                at(0, 0x00, 0x00, 0x01, 0x00) // an icon
            if (picture) return Look.Picture
            val words = String(start, Charsets.ISO_8859_1).trimStart('﻿', 'ï', '»', '¿', ' ', '\t', '\r', '\n').lowercase()
            return when {
                words.startsWith("<svg") || words.startsWith("<?xml") -> Look.Unshowable
                words.startsWith("<") || type?.lowercase()?.startsWith("text/html") == true -> Look.WebPage
                else -> Look.Unsure
            }
        }

        /** What a failed ask, [error], says of the picture's server: as [UnloadablePictures.whyNot] has it. */
        fun lookOf(error: IOException): Look = when (UnloadablePictures.whyNot(error)) {
            UnloadablePictures.Why.NoServer -> Look.NoServer
            UnloadablePictures.Why.Untrusted -> Look.Untrusted
            UnloadablePictures.Why.NoAnswer -> Look.NoAnswer
            else -> Look.Unsure
        }

        /**
         * Asks for pictures with [http], with a short time to connect and to answer: the start of
         * each picture is read, to tell what it is, and the rest left.
         */
        fun asking(http: OkHttpClient): suspend (String) -> Look {
            val calls = http.newBuilder()
                .connectTimeout(CONNECT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
            return { picture ->
                val request = runCatching { Request.Builder().url(picture).build() }.getOrNull()
                if (request == null) {
                    Look.Unsure
                } else {
                    runInterruptible(Dispatchers.IO) {
                        try {
                            calls.newCall(request).execute().use { response ->
                                val start = if (response.isSuccessful) {
                                    val source = response.body.source()
                                    source.request(SNIFFED_BYTES)
                                    source.buffer.copy().readByteArray(minOf(source.buffer.size, SNIFFED_BYTES))
                                } else {
                                    ByteArray(0)
                                }
                                lookOf(response.code, response.header("Content-Type"), start)
                            }
                        } catch (failed: IOException) {
                            lookOf(failed)
                        }
                    }
                }
            }
        }
    }
}
