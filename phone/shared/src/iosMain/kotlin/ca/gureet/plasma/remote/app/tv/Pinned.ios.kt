// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ca.gureet.plasma.remote.app.tv

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionWebSocketCloseCode
import platform.Foundation.NSURLSessionWebSocketCloseCodeNormalClosure
import platform.Foundation.NSURLSessionWebSocketDelegateProtocol
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Foundation.credentialForTrust
import platform.Foundation.dataTaskWithURL
import platform.Foundation.serverTrust
import platform.Security.SecCertificateCopyData
import platform.Security.SecTrustGetCertificateAtIndex
import platform.Security.SecTrustRef
import platform.darwin.NSObject
import platform.posix.memcpy

/** The leaf certificate's fingerprint, as the TV's QR code carries it. */
private fun fingerprintOf(trust: SecTrustRef): String? {
    val certificate = SecTrustGetCertificateAtIndex(trust, 0) ?: return null
    val der = CFBridgingRelease(SecCertificateCopyData(certificate)) as? NSData ?: return null
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { out -> CC_SHA256(der.bytes, der.length.convert(), out.addressOf(0).reinterpret<UByteVar>()) }
    return fingerprintOf(digest)
}

/**
 * Trusts the TV's certificate when its fingerprint is [pinned], or any the first time when
 * [pinned] is null (and says which). Names aren't checked: the TV is known by its certificate.
 */
private class Pinning(
    private val pinned: String?,
    private val seen: (String) -> Unit,
    private val opened: () -> Unit = {},
    private val ended: (String?) -> Unit = {},
) : NSObject(), NSURLSessionWebSocketDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        didReceiveChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) {
        val space = didReceiveChallenge.protectionSpace
        val trust = space.serverTrust
        if (space.authenticationMethod != NSURLAuthenticationMethodServerTrust || trust == null) {
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            return
        }
        val fingerprint = fingerprintOf(trust)
        if (fingerprint != null && (pinned == null || fingerprint == pinned)) {
            seen(fingerprint)
            completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
        } else {
            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
        }
    }

    override fun URLSession(session: NSURLSession, webSocketTask: NSURLSessionWebSocketTask, didOpenWithProtocol: String?) = opened()

    override fun URLSession(session: NSURLSession, webSocketTask: NSURLSessionWebSocketTask, didCloseWithCode: NSURLSessionWebSocketCloseCode, reason: NSData?) = ended(null)

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) = ended(didCompleteWithError?.localizedDescription)
}

private class UrlSessionTvSocket : TvSocket {
    val channel = Channel<String>(Channel.UNLIMITED)
    var task: NSURLSessionWebSocketTask? = null
    var session: NSURLSession? = null

    override val incoming: ReceiveChannel<String> get() = channel

    fun receive() {
        val task = task ?: return
        task.receiveMessageWithCompletionHandler { message, error ->
            val text = message?.string
            if (error != null || message == null) {
                channel.close()
            } else {
                if (text != null) channel.trySend(text)
                receive()
            }
        }
    }

    override suspend fun send(text: String) = suspendCancellableCoroutine { sent ->
        val task = task
        if (task == null) {
            sent.resumeWithException(IllegalStateException("The connection to the TV has closed"))
            return@suspendCancellableCoroutine
        }
        task.sendMessage(NSURLSessionWebSocketMessage(string = text)) { error ->
            if (error == null) sent.resume(Unit) else sent.resumeWithException(IllegalStateException(error.localizedDescription))
        }
    }

    override fun close() {
        task?.cancelWithCloseCode(NSURLSessionWebSocketCloseCodeNormalClosure, null)
        session?.finishTasksAndInvalidate()
        channel.close()
    }
}

actual suspend fun openTvSocket(endpoint: TvEndpoint, pinned: String?, seen: (String) -> Unit): TvSocket =
    suspendCancellableCoroutine { opening ->
        val socket = UrlSessionTvSocket()
        val delegate = Pinning(
            pinned,
            seen,
            opened = { if (opening.isActive) opening.resume(socket) },
            ended = { why ->
                socket.channel.close()
                if (opening.isActive) opening.resumeWithException(IllegalStateException(why ?: "The TV closed the connection"))
            },
        )
        val session = NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.ephemeralSessionConfiguration, delegate, null)
        val task = session.webSocketTaskWithURL(NSURL.URLWithString(endpoint.url("wss", "/remote/ws"))!!)
        socket.task = task
        socket.session = session
        socket.receive()
        task.resume()
        opening.invokeOnCancellation {
            task.cancel()
            session.invalidateAndCancel()
        }
    }

actual suspend fun fetchFromTv(endpoint: TvEndpoint, pinned: String, path: String): ByteArray? =
    suspendCancellableCoroutine { fetched ->
        val session = NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.ephemeralSessionConfiguration, Pinning(pinned, {}), null)
        val url = NSURL.URLWithString(endpoint.url("https", path))
        if (url == null) {
            fetched.resume(null)
            return@suspendCancellableCoroutine
        }
        val task = session.dataTaskWithURL(url) { data, _, error ->
            val bytes = if (error == null && data != null) data.toByteArray() else null
            session.finishTasksAndInvalidate()
            if (fetched.isActive) fetched.resume(bytes)
        }
        task.resume()
        fetched.invokeOnCancellation { session.invalidateAndCancel() }
    }

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
