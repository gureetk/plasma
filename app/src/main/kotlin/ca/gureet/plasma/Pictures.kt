// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import coil3.EventListener
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.network.ConnectivityChecker
import coil3.network.HttpException
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.okhttp.asNetworkClient
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/*
 * Pictures from the network: posters, backdrops, logos and stills (brief 6.1), through Coil.
 *
 * Coil fetches and decodes on Dispatchers.IO, the threads much of Plasma waits on, and a picture
 * that nothing wants any more can keep coming: Coil cancels its call only while waiting for the
 * response to begin. Once it has begun, Coil reads it to its end into its disk cache on one of
 * those threads, whatever becomes of the request. Moving through a few categories of posters from
 * a server that sends them slowly can leave those threads reading pictures no one will see, and
 * then nothing loads, not even a picture already on the TV's storage, which waits for a thread too.
 *
 * So pictures have threads of their own, a picture let go stops coming at once, and none takes
 * longer than [PICTURE_CALL_SECONDS].
 */

/** How many pictures come from the network or the TV's storage at once, on threads of their own. */
internal const val PICTURE_FETCHES = 16

/** How many are decoded at once: Coil's own limit for decoding, on threads of their own. */
internal const val PICTURE_DECODES = 4

/** How long a picture's server has to answer before the picture is given up. */
internal const val PICTURE_CONNECT_SECONDS = 10L

/** The longest a picture takes to come, from asking to its last byte, before it's given up. */
internal const val PICTURE_CALL_SECONDS = 30L

/** Plasma's loader of pictures from the network, with [watch] told what each picture does. */
internal fun pictureLoader(context: Context, http: OkHttpClient, watch: PictureWatch): ImageLoader {
    val calls = pictureCalls(http)
    return ImageLoader.Builder(context)
        .components { add(NetworkFetcher.Factory(networkClient = { LettingGo(calls) })) }
        .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(PICTURE_FETCHES))
        .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(PICTURE_DECODES))
        .eventListenerFactory(watch)
        .build()
}

/** [http] for pictures: its user agent, connections and redirects, with a picture's time limits. */
internal fun pictureCalls(http: OkHttpClient, callSeconds: Long = PICTURE_CALL_SECONDS): OkHttpClient = http.newBuilder()
    .connectTimeout(PICTURE_CONNECT_SECONDS, TimeUnit.SECONDS)
    .callTimeout(callSeconds, TimeUnit.SECONDS)
    .build()

/**
 * Coil's own way of asking OkHttp for a picture, which also cancels the picture's call when the
 * picture is let go: the read that holds a thread then ends at once.
 */
internal class LettingGo(private val calls: Call.Factory) : NetworkClient {
    override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (response: NetworkResponse) -> T): T {
        val call = AtomicReference<Call?>()
        val done = AtomicBoolean(false)
        val kept = object : Call.Factory {
            override fun newCall(request: Request): Call = calls.newCall(request).also(call::set)
        }
        return coroutineScope {
            // Waits for the picture to be let go, on no thread of its own: its cancel then runs on
            // the thread that lets it go, while the thread reading it is still busy reading.
            val letGo = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    if (!done.get()) call.get()?.cancel()
                }
            }
            try {
                kept.asNetworkClient().executeRequest(request, block)
            } catch (ended: IOException) {
                // A picture let go ends with its call's cancel: it was let go, it didn't fail.
                ensureActive()
                throw ended
            } finally {
                done.set(true)
                letGo.cancel()
            }
        }
    }
}

/**
 * What Plasma's pictures have done since it started, for Export Diagnostics (5.17): where each
 * came from, why one couldn't, which are still coming and for how long. [failedFirst] hears of
 * each kind of failure the first time it happens.
 */
internal class PictureWatch(
    private val failedFirst: (kind: String) -> Unit = {},
    private val now: () -> Long = System::nanoTime,
) : EventListener.Factory {
    private val counts = PictureCounts(now)

    override fun create(request: ImageRequest): EventListener = object : EventListener() {
        override fun onStart(request: ImageRequest) {
            counts.started(this)
        }

        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
            val from = when (result.dataSource) {
                DataSource.MEMORY_CACHE, DataSource.MEMORY -> PictureFrom.Memory
                DataSource.DISK -> PictureFrom.Storage
                DataSource.NETWORK -> PictureFrom.Network
            }
            counts.came(this, from)
        }

        override fun onError(request: ImageRequest, result: ErrorResult) {
            val kind = pictureFailure(result.throwable)
            if (counts.failed(this, kind)) failedFirst(kind)
        }

        override fun onCancel(request: ImageRequest) {
            counts.letGo(this)
        }
    }

    /** What [counts] says, then the loader's caches, the TV's storage, the network and the threads. */
    fun lines(context: Context, loader: ImageLoader?, http: OkHttpClient): List<String> = buildList {
        addAll(counts.lines())
        if (loader != null) {
            val memory = loader.memoryCache?.let { "memory ${it.size / MB} of ${it.maxSize / MB} MB" }
            val storage = loader.diskCache?.let { "storage ${it.size / MB} of ${it.maxSize / MB} MB" }
            add("Picture caches: ${listOfNotNull(memory, storage).joinToString().ifEmpty { "none" }}")
        }
        add("Free on the TV's storage: ${number(context.cacheDir.usableSpace / MB)} MB")
        val online = runCatching { ConnectivityChecker(context).isOnline() }.getOrNull()
        if (online != null) add("Pictures see the network as ${if (online) "there" else "not there"}")
        add("Network calls: ${http.dispatcher.runningCallsCount()} running, ${http.dispatcher.queuedCallsCount()} waiting")
        addAll(threadLines(Thread.getAllStackTraces()))
    }

    private companion object {
        const val MB = 1024L * 1024L
    }
}

/** Where a picture came from. */
internal enum class PictureFrom { Memory, Storage, Network }

/** The counts behind [PictureWatch], by the listener of each picture. */
internal class PictureCounts(private val now: () -> Long) {
    private val lock = Any()
    private var asked = 0
    private val came = IntArray(PictureFrom.entries.size)
    private var letGo = 0
    private val failures = LinkedHashMap<String, Int>()
    private val coming = HashMap<Any, Long>()
    private var slowestNanos = 0L

    fun started(picture: Any) {
        synchronized(lock) {
            asked++
            coming[picture] = now()
        }
    }

    fun came(picture: Any, from: PictureFrom) {
        synchronized(lock) {
            came[from.ordinal]++
            val start = coming.remove(picture)
            if (start != null && from == PictureFrom.Network) slowestNanos = maxOf(slowestNanos, now() - start)
        }
    }

    /** True the first time a picture fails this way. */
    fun failed(picture: Any, kind: String): Boolean = synchronized(lock) {
        coming.remove(picture)
        val before = failures[kind] ?: 0
        failures[kind] = before + 1
        before == 0
    }

    fun letGo(picture: Any) {
        synchronized(lock) {
            letGo++
            coming.remove(picture)
        }
    }

    fun lines(): List<String> = synchronized(lock) {
        val failed = failures.values.sum()
        buildList {
            add(
                "Pictures since Plasma started: ${number(asked)} asked for; ${number(came[PictureFrom.Memory.ordinal])} from memory, " +
                    "${number(came[PictureFrom.Storage.ordinal])} from the TV's storage, ${number(came[PictureFrom.Network.ordinal])} from the network; " +
                    "${number(failed)} couldn't load; ${number(letGo)} let go before they came",
            )
            if (failures.isNotEmpty()) {
                add("Pictures that couldn't load: " + failures.entries.sortedByDescending { it.value }.take(FAILURE_KINDS).joinToString { (kind, count) -> "$kind × ${number(count)}" })
            }
            val oldest = coming.values.minOrNull()
            add(
                "Pictures coming now: ${number(coming.size)}" + (oldest?.let { ", the first for ${seconds(now() - it)}" }.orEmpty()) +
                    "; the slowest from the network so far took ${seconds(slowestNanos)}",
            )
        }
    }

    private companion object {
        const val FAILURE_KINDS = 8
    }
}

/**
 * Why a picture couldn't load, in a few words and without any server's name or address: the
 * error's kind, and for an answer from the server its code.
 */
internal fun pictureFailure(error: Throwable): String {
    if (error is HttpException) return "HTTP ${error.response.code}"
    val kind = error.javaClass.simpleName.ifEmpty { "Error" }
    val message = error.message?.let(::withoutPlaces)?.trim()?.take(MESSAGE_CHARS)
    return if (message.isNullOrEmpty()) kind else "$kind ($message)"
}

/** [text] with anything that could name or find a server taken out: addresses, host names, IPs. */
internal fun withoutPlaces(text: String): String = text
    .replace(Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://\S+"""), "…")
    .replace(Regex("""\[[0-9a-fA-F:.]+](?::\d+)?"""), "…")
    .replace(Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b(?::\d+)?"""), "…")
    .replace(Regex("""\b(?:[a-zA-Z0-9-]+\.)+[a-zA-Z][a-zA-Z0-9-]*\b(?::\d+)?"""), "…")

/**
 * The threads as they are, by family (a pool's threads share a name but for their number): how
 * many of each, and of those how many are reading from the network and how many are in Coil. A
 * family whose every thread is reading from the network is what keeps pictures from coming.
 */
internal fun threadLines(threads: Map<Thread, Array<StackTraceElement>>): List<String> {
    class Family(var count: Int = 0, var reading: Int = 0, var inCoil: Int = 0)
    val families = LinkedHashMap<String, Family>()
    for ((thread, stack) in threads) {
        val family = families.getOrPut(threadFamily(thread.name)) { Family() }
        family.count++
        if (stack.any { it.isNetworkRead() }) family.reading++
        if (stack.any { it.className.startsWith("coil3.") }) family.inCoil++
    }
    val shown = families.entries
        .sortedByDescending { it.value.count }
        .take(THREAD_FAMILIES)
        .joinToString("; ") { (name, family) ->
            val doing = listOfNotNull(
                family.reading.takeIf { it > 0 }?.let { "$it reading from the network" },
                family.inCoil.takeIf { it > 0 }?.let { "$it in Coil" },
            )
            "$name ${family.count}" + if (doing.isEmpty()) "" else " (${doing.joinToString()})"
        }
    return listOf("Threads: ${number(threads.size)}; $shown")
}

/** A thread's family: its name without its number, and an HTTP call's thread without its address. */
internal fun threadFamily(name: String): String = when {
    name.startsWith("OkHttp") -> "OkHttp"
    else -> name.replace(Regex("""[-\s#]*\d+$"""), "").ifEmpty { "(no name)" }
}

/** A frame that reads from a connection: a socket's, TLS's, or Android's own under them. */
private fun StackTraceElement.isNetworkRead(): Boolean {
    val reads = methodName.contains("read", ignoreCase = true) || methodName.startsWith("recv")
    val connection = className.contains("Socket") || className.contains("conscrypt", ignoreCase = true) || className == "libcore.io.Linux"
    return reads && connection
}

private fun number(value: Number): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

private fun seconds(nanos: Long): String = String.format(Locale.US, "%.1f s", nanos / NANOS_PER_SECOND)

private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val MESSAGE_CHARS = 80
private const val THREAD_FAMILIES = 8
