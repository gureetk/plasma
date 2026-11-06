// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

/**
 * Just enough of `multipart/form-data` for the restore page (brief 5.17, #108): a phone's browser
 * sends a file and a field or two that way. Anything it can't read is refused whole.
 */
internal object Multipart {
    /** One field of the form; a file's [filename] is the phone's name for it. */
    class Part(val name: String, val filename: String?, val bytes: ByteArray) {
        val text: String get() = String(bytes, Charsets.UTF_8)
    }

    private const val MAX_PARTS = 16
    private const val MAX_BOUNDARY = 70
    private val CRLF = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
    private val HEAD_END = CRLF + CRLF
    private val DASHES = byteArrayOf('-'.code.toByte(), '-'.code.toByte())

    /** The parts of [body], sent as [contentType]; null when it isn't multipart or can't be read. */
    fun parse(contentType: String?, body: ByteArray): List<Part>? {
        if (contentType == null || !contentType.trim().lowercase().startsWith("multipart/form-data")) return null
        val boundary = contentType.split(';').map { it.trim() }
            .firstOrNull { it.lowercase().startsWith("boundary=") }
            ?.substringAfter('=')?.trim()?.removeSurrounding("\"")
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_BOUNDARY }
            ?: return null
        val delimiter = DASHES + boundary.toByteArray(Charsets.ISO_8859_1)
        val parts = mutableListOf<Part>()
        var at = indexOf(body, delimiter, 0)
        if (at < 0) return null
        while (true) {
            at += delimiter.size
            // "--" after a delimiter: that was the last part.
            if (startsWith(body, DASHES, at)) return parts
            if (!startsWith(body, CRLF, at)) return null
            at += CRLF.size
            val headEnd = indexOf(body, HEAD_END, at)
            if (headEnd < 0) return null
            val headers = String(body, at, headEnd - at, Charsets.UTF_8).split("\r\n")
            val contentStart = headEnd + HEAD_END.size
            val next = indexOf(body, CRLF + delimiter, contentStart)
            if (next < 0) return null
            val disposition = headers.firstOrNull { it.lowercase().startsWith("content-disposition:") } ?: return null
            val params = disposition.substringAfter(':').split(';').map { it.trim() }
            val name = param(params, "name") ?: return null
            parts += Part(name, param(params, "filename"), body.copyOfRange(contentStart, next))
            if (parts.size > MAX_PARTS) return null
            at = next + CRLF.size
        }
    }

    private fun param(params: List<String>, name: String): String? = params
        .firstOrNull { it.substringBefore('=').trim().equals(name, ignoreCase = true) && '=' in it }
        ?.substringAfter('=')?.trim()?.removeSurrounding("\"")

    private fun startsWith(body: ByteArray, prefix: ByteArray, at: Int): Boolean {
        if (at < 0 || at + prefix.size > body.size) return false
        for (i in prefix.indices) if (body[at + i] != prefix[i]) return false
        return true
    }

    private fun indexOf(body: ByteArray, pattern: ByteArray, from: Int): Int {
        var at = from
        val last = body.size - pattern.size
        while (at <= last) {
            if (startsWith(body, pattern, at)) return at
            at++
        }
        return -1
    }
}
