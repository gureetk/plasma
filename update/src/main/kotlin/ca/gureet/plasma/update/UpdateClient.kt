// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Reads the update file and downloads an update's APK (brief 5.18), over Plasma's own HTTP client.
 * GitHub's release files redirect to its storage, which the client follows.
 */
class UpdateClient(private val http: OkHttpClient) {
    /**
     * What the update file at [address] offers a TV with Plasma [installedCode], Android [sdk] and
     * processor types [abis].
     */
    suspend fun check(address: String, installedCode: Long, sdk: Int, abis: List<String>): UpdateCheck = withContext(Dispatchers.IO) {
        val url = address.toHttpUrlOrNull() ?: throw UpdateException(UpdateProblem.BadFile)
        val text = call(url.toString()) { response ->
            val body = response.body
            // An update file is small; anything big isn't one.
            if (body.contentLength() > MAX_FILE_BYTES) throw UpdateException(UpdateProblem.BadFile)
            body.source().use { source ->
                source.request(MAX_FILE_BYTES + 1)
                if (source.buffer.size > MAX_FILE_BYTES) throw UpdateException(UpdateProblem.BadFile)
                source.buffer.readUtf8()
            }
        }
        UpdateManifest.parse(text).checkFor(installedCode, sdk, abis, url)
    }

    /**
     * Downloads [update]'s APK to [into], checking its SHA-256 as it arrives. [progress] hears how
     * much has come, from 0 to 1, or −1 when the server doesn't say how big it is. Nothing is left
     * at [into] unless the whole APK arrived as the update file says.
     */
    suspend fun download(update: Update, into: File, progress: (Float) -> Unit = {}) =
        fetch(update.url, update.sha256, into, maxBytes = Long.MAX_VALUE, progress = progress)

    /**
     * Downloads [update]'s baseline profile for this TV's Android to [into], when it has one: dex
     * metadata that Android's installer compiles the update with as it installs it (the owner's
     * ask, 1 November). It's checked against its SHA-256, and has to hold a profile and nothing
     * else, no compiled code. Nothing is left at [into] unless it's whole and checked; false when
     * there's no profile to download.
     */
    suspend fun downloadProfile(update: Update, into: File): Boolean {
        val url = update.profileUrl ?: return false
        val sha256 = update.profileSha256 ?: return false
        fetch(url, sha256, into, maxBytes = MAX_PROFILE_BYTES, check = ::isProfileOnly)
        return true
    }

    /**
     * Downloads [url] to [into], checking its SHA-256 against [sha256] as it arrives, and it with
     * [check] once it's whole. More than [maxBytes] is damaged.
     */
    private suspend fun fetch(
        url: String,
        sha256: String,
        into: File,
        maxBytes: Long,
        progress: (Float) -> Unit = {},
        check: (File) -> Boolean = { true },
    ) = withContext(Dispatchers.IO) {
        val part = File(into.path + PART)
        into.parentFile?.mkdirs()
        part.delete()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            call(url) { response ->
                val total = response.body.contentLength()
                if (total > maxBytes) throw UpdateException(UpdateProblem.Damaged)
                var done = 0L
                var shown = -1
                response.body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            done += read
                            if (done > maxBytes) throw UpdateException(UpdateProblem.Damaged)
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            val percent = if (total > 0) (done * 100 / total).toInt() else -1
                            if (percent != shown) {
                                shown = percent
                                progress(if (total > 0) done.toFloat() / total else -1f)
                            }
                        }
                    }
                }
            }
            if (hex(digest.digest()) != sha256.lowercase() || !check(part)) throw UpdateException(UpdateProblem.Damaged)
            into.delete()
            if (!part.renameTo(into)) throw UpdateException(UpdateProblem.NoRoom)
        } catch (full: IOException) {
            throw UpdateException(if (isFull(full)) UpdateProblem.NoRoom else UpdateProblem.CantReach, full)
        } finally {
            part.delete()
        }
    }

    /** Asks for [url] and reads the answer with [read]; errors become [UpdateException]s. */
    private inline fun <T> call(url: String, read: (Response) -> T): T {
        val request = Request.Builder().url(url).build()
        val response = try {
            http.newCall(request).execute()
        } catch (unreachable: IOException) {
            throw UpdateException(UpdateProblem.CantReach, unreachable)
        }
        return response.use {
            when {
                it.code == HTTP_NOT_FOUND -> throw UpdateException(UpdateProblem.NotThere)
                !it.isSuccessful -> throw UpdateException(UpdateProblem.CantReach)
                else -> read(it)
            }
        }
    }

    private fun isFull(error: IOException): Boolean = generateSequence<Throwable>(error) { it.cause }.any { "ENOSPC" in it.message.orEmpty() || "No space left" in it.message.orEmpty() }

    /**
     * Dex metadata holding a profile and nothing more: the profile, its metadata and a manifest
     * naming the version. Compiled code in one is Android's own app store's to send.
     */
    private fun isProfileOnly(file: File): Boolean = try {
        ZipFile(file).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            PROFILE in names && names.all { it in PROFILE_ENTRIES }
        }
    } catch (unreadable: IOException) {
        false
    }

    companion object {
        /** Where a download goes until it's whole and checked. */
        private const val PART = ".part"
        private const val BUFFER_BYTES = 64 * 1024
        private const val MAX_FILE_BYTES = 256L * 1024

        /** A profile is some tens of KB; anything this big isn't one. */
        private const val MAX_PROFILE_BYTES = 4L * 1024 * 1024
        private const val PROFILE = "primary.prof"
        private val PROFILE_ENTRIES = setOf(PROFILE, "primary.profm", "manifest.json")
        private const val HTTP_NOT_FOUND = 404

        /** A digest in lowercase hexadecimal, as update files give it. */
        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
