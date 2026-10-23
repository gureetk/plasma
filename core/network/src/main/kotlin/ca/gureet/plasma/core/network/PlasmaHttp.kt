// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * The HTTP client every provider request goes through (brief 6.1): redirects followed, plain
 * `http` allowed, and one user agent unless a request sets its own.
 */
object PlasmaHttp {
    /** How long to wait for a server before calling it unreachable (Connection Timeout, 5.4.8). */
    const val CONNECT_TIMEOUT_SECONDS = 15L

    /** Provider APIs can take a while to send a big channel list. */
    const val READ_TIMEOUT_SECONDS = 60L

    fun client(userAgent: String): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(UserAgentInterceptor(userAgent))
        .build()
}

/** Sets [userAgent] on requests that don't name one themselves (per-source overrides, 5.1). */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(USER_AGENT) != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(USER_AGENT, userAgent).build())
    }

    private companion object {
        const val USER_AGENT = "User-Agent"
    }
}

object UserAgents {
    /**
     * libVLC's own user agent, which providers see from VLC-based players (brief 5.4.2). The app
     * passes the bundled libVLC's version; this is the fallback until it's known.
     */
    fun vlc(version: String = DEFAULT_VLC_VERSION): String = "VLC/$version LibVLC/$version"

    const val DEFAULT_VLC_VERSION = "3.0.21"
}
