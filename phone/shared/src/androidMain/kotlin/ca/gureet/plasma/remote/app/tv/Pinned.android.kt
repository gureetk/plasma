// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.tv

import android.annotation.SuppressLint
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** One client for every TV, so connections and threads are shared; each pin gets its own view of it. */
private val base: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        // NetworkTv pings every 15 s itself, and a WebSocket waits as long as it likes.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
}

/**
 * A client that trusts the TV's certificate when its fingerprint is [pinned], or any the first
 * time when [pinned] is null (and says which), and calls [mismatched] for another. Names aren't
 * checked: the TV is known by its certificate, and its address changes.
 */
@SuppressLint("CustomX509TrustManager", "BadHostnameVerifier")
private fun pinnedClient(pinned: String?, seen: (String) -> Unit, mismatched: () -> Unit = {}): OkHttpClient {
    val trust = object : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("The TV showed no certificate")
            val fingerprint = fingerprintOf(MessageDigest.getInstance("SHA-256").digest(leaf.encoded))
            if (pinned != null && fingerprint != pinned) {
                mismatched()
                throw CertificateException("Not the TV that was paired")
            }
            seen(fingerprint)
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException("Not a server")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    return base.newBuilder()
        .sslSocketFactory(context.socketFactory, trust)
        .hostnameVerifier { _, _ -> true }
        .build()
}

actual suspend fun openTvSocket(endpoint: TvEndpoint, pinned: String?, seen: (String) -> Unit): TvSocket =
    suspendCancellableCoroutine { opening ->
        val socket = OkTvSocket()
        var mismatch = false
        val webSocket = pinnedClient(pinned, seen) { mismatch = true }.newWebSocket(
            Request.Builder().url(endpoint.url("https", "/remote/ws")).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socket.webSocket = webSocket
                    if (opening.isActive) opening.resume(socket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    socket.channel.trySend(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    socket.channel.close()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socket.channel.close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socket.channel.close()
                    if (opening.isActive) opening.resumeWithException(if (mismatch) NotThePairedTv() else t)
                }
            },
        )
        opening.invokeOnCancellation { webSocket.cancel() }
    }

private class OkTvSocket : TvSocket {
    val channel = Channel<String>(Channel.UNLIMITED)

    @Volatile var webSocket: WebSocket? = null

    override val incoming: ReceiveChannel<String> get() = channel

    override suspend fun send(text: String) {
        // OkHttp queues it and sends in order; false once the connection is closing.
        check(webSocket?.send(text) == true) { "The connection to the TV has closed" }
    }

    override fun close() {
        webSocket?.close(1000, null)
        channel.close()
    }
}

actual suspend fun fetchFromTv(endpoint: TvEndpoint, pinned: String, path: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        pinnedClient(pinned, seen = {}).newCall(Request.Builder().url(endpoint.url("https", path)).build()).execute().use { response ->
            if (response.isSuccessful) response.body.bytes() else null
        }
    }.getOrNull()
}
