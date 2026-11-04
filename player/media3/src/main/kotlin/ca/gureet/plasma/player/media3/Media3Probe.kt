// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.media3

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** What the start of a movie's file says of its picture (#113). */
data class ProbedVideo(val range: VideoRange, val width: Int? = null, val height: Int? = null)

/** A picture's dynamic range, as far as its file's headers tell. HDR10+ isn't in them: it reads as HDR10. */
enum class VideoRange { Sdr, Hdr10, Hlg, DolbyVision }

/**
 * Reads the start of a movie's or episode's file with Media3's extractors (brief 5.4.3, rule 3;
 * #113), for one its provider didn't probe: what its picture is, so HDR and Dolby Vision Movies can
 * send a Dolby Vision one to the Android player. It reads until the picture's format is known,
 * over one connection at a time (a range for each jump, as for an MP4 whose index is at its end),
 * at most [MAX_BYTES], and closes it.
 */
@OptIn(UnstableApi::class)
object Media3Probe {
    /** [url]'s picture, asked for with [headers]; null when it can't be told within [timeoutMs]. */
    suspend fun video(http: Call.Factory, url: String, headers: Map<String, String>, timeoutMs: Long): ProbedVideo? {
        // A connection that stops sending is let go when the time's up, as well as the wait.
        val calls = (http as? OkHttpClient)?.newBuilder()?.callTimeout(timeoutMs, TimeUnit.MILLISECONDS)?.build() ?: http
        return withTimeoutOrNull(timeoutMs) {
            runInterruptible(Dispatchers.IO) {
                try {
                    HttpFile(calls, url, headers).use { read(it) }
                } catch (failed: IOException) {
                    null
                } catch (failed: RuntimeException) {
                    // A container Media3 trips over plays as it would have without.
                    null
                }
            }
        }
    }

    /** The picture in [file], once the one of [extractors] that knows its container has its format. */
    internal fun read(file: SeekableFile, extractors: () -> Array<Extractor> = ::defaultExtractors): ProbedVideo? {
        var from = 0L
        var input: ExtractorInput = DefaultExtractorInput(file.reader, 0, file.open(0))
        val extractor = choose(input, extractors()) ?: return null
        val formats = Formats()
        try {
            extractor.init(formats)
            val at = PositionHolder()
            // What earlier ranges read, before the one open now.
            var readBefore = 0L
            while (true) {
                formats.video?.let { return probed(it) }
                // Every track is known, and none is a picture.
                if (formats.ended && formats.allKnown && formats.any) return null
                if (readBefore + input.position - from > MAX_BYTES) return null
                val result = try {
                    // Matroska's reads on past the header until it has a sample.
                    extractor.read(input, at)
                } catch (failed: Exception) {
                    // A file that goes wrong after its header has said what its picture is.
                    formats.video?.let { return probed(it) }
                    throw failed
                }
                when (result) {
                    Extractor.RESULT_CONTINUE -> Unit
                    Extractor.RESULT_SEEK -> {
                        readBefore += input.position - from
                        from = at.position
                        val left = file.open(from)
                        input = DefaultExtractorInput(file.reader, from, if (left == C.LENGTH_UNSET.toLong()) left else from + left)
                    }
                    else -> return formats.video?.let(::probed)
                }
            }
        } finally {
            extractor.release()
        }
    }

    /** Media3's extractors, for every container ExoPlayer reads. */
    private fun defaultExtractors(): Array<Extractor> = DefaultExtractorsFactory().setTextTrackTranscodingEnabled(false).createExtractors()

    /** The first of [extractors] that recognizes the file's start, as ExoPlayer picks one; the others go. */
    private fun choose(input: ExtractorInput, extractors: Array<Extractor>): Extractor? {
        val chosen = extractors.firstOrNull { extractor ->
            try {
                extractor.sniff(input)
            } catch (short: EOFException) {
                false
            } finally {
                input.resetPeekPosition()
            }
        }
        extractors.filter { it !== chosen }.forEach(Extractor::release)
        return chosen
    }

    private fun probed(format: Format) = ProbedVideo(
        rangeOf(format),
        width = format.width.takeIf { it != Format.NO_VALUE },
        height = format.height.takeIf { it != Format.NO_VALUE },
    )

    /** Dolby Vision by its configuration record, which makes Media3 name it so; HDR10 and HLG by the transfer. */
    internal fun rangeOf(format: Format): VideoRange = when {
        format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> VideoRange.DolbyVision
        format.codecs?.substringBefore('.')?.lowercase() in DOLBY_VISION_CODECS -> VideoRange.DolbyVision
        format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> VideoRange.Hdr10
        format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> VideoRange.Hlg
        else -> VideoRange.Sdr
    }

    /** As much of a file as is read for it: a few seconds of a broadcast's picture, many times a header. */
    internal const val MAX_BYTES = 8L * 1024 * 1024

    /** MP4's sample entries for Dolby Vision, as codec strings start. */
    private val DOLBY_VISION_CODECS = setOf("dvhe", "dvh1", "dvav", "dva1", "dav1", "dvc1")
}

/** A file read from where it's opened, and opened again elsewhere as the extractor jumps. */
@OptIn(UnstableApi::class)
internal interface SeekableFile : AutoCloseable {
    /** Opens it at [position], closing what was open: how much is left from there, or [C.LENGTH_UNSET]. */
    fun open(position: Long): Long

    /** Reads from where it was opened. */
    val reader: DataReader
}

/** A file over HTTP: one request at a time, with a range for each jump. */
@OptIn(UnstableApi::class)
private class HttpFile(private val http: Call.Factory, private val url: String, private val headers: Map<String, String>) : SeekableFile {
    private var response: Response? = null
    private var stream: InputStream? = null

    override fun open(position: Long): Long {
        close()
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            if (position > 0) header("Range", "bytes=$position-")
        }.build()
        val opened = http.newCall(request).execute()
        response = opened
        if (!opened.isSuccessful) throw IOException("HTTP ${opened.code}")
        // A server that sends the whole file for a range can't be jumped in.
        if (position > 0 && opened.code != HTTP_PARTIAL) throw IOException("No ranges")
        stream = opened.body.byteStream()
        val length = opened.body.contentLength()
        return if (length < 0) C.LENGTH_UNSET.toLong() else length
    }

    override val reader = DataReader { buffer, offset, length ->
        if (length == 0) return@DataReader 0
        val read = stream?.read(buffer, offset, length) ?: throw IOException("Not open")
        if (read < 0) C.RESULT_END_OF_INPUT else read
    }

    override fun close() {
        stream = null
        response?.close()
        response = null
    }

    private companion object {
        const val HTTP_PARTIAL = 206
    }
}

/** What the extractor finds: each track's format, as it comes. Samples go. */
@OptIn(UnstableApi::class)
private class Formats : ExtractorOutput {
    private val tracks = LinkedHashMap<Int, Track>()
    var ended = false
        private set

    val any: Boolean get() = tracks.isNotEmpty()
    val allKnown: Boolean get() = tracks.values.all { it.format != null }

    /** The first track's format that's a picture. */
    val video: Format? get() = tracks.values.firstNotNullOfOrNull { track -> track.format?.takeIf { MimeTypes.isVideo(it.sampleMimeType) } }

    override fun track(id: Int, type: Int): TrackOutput = tracks.getOrPut(id) { Track() }

    override fun endTracks() {
        ended = true
    }

    override fun seekMap(seekMap: SeekMap) = Unit

    private class Track : TrackOutput {
        var format: Format? = null
        private val samples = DiscardingTrackOutput()

        override fun format(format: Format) {
            this.format = format
        }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
            samples.sampleData(input, length, allowEndOfInput, sampleDataPart)

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) = samples.sampleData(data, length, sampleDataPart)

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) = Unit
    }
}
