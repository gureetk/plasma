// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URLDecoder

/** One request to the pairing server: small, and form-encoded when it has a body. */
class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    /** By lower-case name. */
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    /** The fields of a form-encoded body; nothing for any other kind. */
    fun form(): Map<String, String> =
        if (headers["content-type"]?.startsWith(FORM_TYPE) == true) Http.parseQuery(String(body, Charsets.UTF_8)) else emptyMap()

    private companion object {
        const val FORM_TYPE = "application/x-www-form-urlencoded"
    }
}

/** A request bigger than a form ever is. */
class TooLarge : IOException("The request is too large")

/** Just enough HTTP/1.1 for a phone's browser: one request per connection, then close. */
object Http {
    private const val MAX_HEAD_BYTES = 8 * 1024
    private const val MAX_BODY_BYTES = 16 * 1024

    /** The headers every answer carries: nothing cached, framed, sniffed, scripted or referred on. */
    private val SAFETY = listOf(
        "Cache-Control: no-store",
        "X-Content-Type-Options: nosniff",
        "X-Frame-Options: DENY",
        "Referrer-Policy: no-referrer",
        "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
    )

    /**
     * Reads one request from [input], or null when the connection closes first.
     *
     * @throws TooLarge when its head or body is bigger than a form needs.
     */
    fun read(input: InputStream): HttpRequest? {
        val budget = intArrayOf(MAX_HEAD_BYTES)
        val requestLine = readLine(input, budget) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input, budget) ?: return null
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY_BYTES) throw TooLarge()
        val body = input.readNBytes(length)
        if (body.size < length) return null
        val target = parts[1]
        return HttpRequest(
            method = parts[0],
            path = target.substringBefore('?'),
            query = parseQuery(target.substringAfter('?', "")),
            headers = headers,
            body = body,
        )
    }

    /** `a=1&b=two%20words` as a map; a name given twice keeps its first value. */
    fun parseQuery(text: String): Map<String, String> {
        val fields = LinkedHashMap<String, String>()
        for (pair in text.split('&')) {
            if (pair.isEmpty()) continue
            val name = decode(pair.substringBefore('=')) ?: continue
            val value = decode(pair.substringAfter('=', "")) ?: continue
            fields.putIfAbsent(name, value)
        }
        return fields
    }

    /** Answers with [status] and the page [html], then the connection closes. */
    fun respond(out: OutputStream, status: Int, html: String?) {
        val body = html?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val head = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reasonOf(status)).append("\r\n")
            if (html != null) append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            SAFETY.forEach { append(it).append("\r\n") }
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    /**
     * Answers with [status] and [body] of [type], under the content security policy [policy];
     * kept a day with [cache], such as a logo.
     */
    fun respondWith(out: OutputStream, status: Int, type: String, body: ByteArray, policy: String, cache: Boolean = false) {
        val head = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reasonOf(status)).append("\r\n")
            append("Content-Type: ").append(type).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append(if (cache) "Cache-Control: private, max-age=86400" else "Cache-Control: no-store").append("\r\n")
            SAFETY.filter { !it.startsWith("Cache-Control") && !it.startsWith("Content-Security-Policy") }.forEach { append(it).append("\r\n") }
            append("Content-Security-Policy: ").append(policy).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private fun readLine(input: InputStream, budget: IntArray): String? {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return null
            budget[0]--
            if (budget[0] < 0) throw TooLarge()
            if (byte == '\n'.code) return line.toString().removeSuffix("\r")
            line.append(byte.toChar())
        }
    }

    private fun decode(text: String): String? = try {
        URLDecoder.decode(text, Charsets.UTF_8)
    } catch (malformed: IllegalArgumentException) {
        null
    }

    private fun reasonOf(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        413 -> "Content Too Large"
        else -> "Error"
    }
}

/**
 * A phone on the home network (brief 5.15): a private IPv4 range, a link-local or unique local
 * IPv6 address, or the TV itself.
 */
fun isHomeNetwork(address: InetAddress): Boolean = when {
    address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress -> true
    // fc00::/7, IPv6's private range.
    address is Inet6Address -> (address.address[0].toInt() and 0xFE) == 0xFC
    else -> false
}
