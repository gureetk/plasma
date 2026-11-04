// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.diagnostics

import ca.gureet.plasma.core.network.Redact
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A log kept in two files used in turn, `<name>-0.log` and `<name>-1.log` in [dir], so it never
 * takes much more than twice [maxBytes]: once the file being written would pass [maxBytes], the
 * other one is emptied and written instead. [read] gives both, the older first. Safe to use from
 * any thread.
 */
internal class RotatingLog(private val dir: File, name: String, private val maxBytes: Long) {
    private val files = arrayOf(File(dir, "$name-0.log"), File(dir, "$name-1.log"))
    private var current = if (files[1].lastModified() > files[0].lastModified()) 1 else 0
    private var written = files[current].length()
    private var out: OutputStream? = null

    /** Adds [text] at the end; [flush] writes it out at once rather than with what follows. */
    @Synchronized
    fun append(text: String, flush: Boolean = false) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (written > 0 && written + bytes.size > maxBytes) turn()
        try {
            val stream = out ?: open()
            stream.write(bytes)
            if (flush) stream.flush()
            written += bytes.size
        } catch (failed: IOException) {
            // A full disk loses the line, not Plasma.
            close()
        }
    }

    @Synchronized
    fun flush() {
        try {
            out?.flush()
        } catch (failed: IOException) {
            close()
        }
    }

    /** Everything kept, the older file first. */
    @Synchronized
    fun read(): ByteArray {
        flush()
        val all = ByteArrayOutputStream()
        for (file in listOf(files[1 - current], files[current])) {
            if (file.exists()) runCatching { all.write(file.readBytes()) }
        }
        return all.toByteArray()
    }

    @Synchronized
    fun close() {
        runCatching { out?.close() }
        out = null
    }

    private fun turn() {
        close()
        current = 1 - current
        files[current].delete()
        written = 0
    }

    private fun open(): OutputStream {
        dir.mkdirs()
        return FileOutputStream(files[current], true).buffered(BUFFER_BYTES).also { out = it }
    }

    private companion object {
        const val BUFFER_BYTES = 16 * 1024
    }
}

/**
 * Plasma's own log (brief 5.17), for Export Diagnostics: what happens to streams, sources, the
 * guide and updates. Each line has the TV's time and a topic, and loses its credentials
 * ([Redact.text]) before it's written, on a thread of its own so nothing waits for the disk.
 * Servers' names are taken out only when a report is made, so each report names them the same way.
 */
class DiagnosticLog internal constructor(
    private val file: RotatingLog,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "plasma-log").apply { isDaemon = true } }

    // The writer's own: a line said again and again is counted rather than written each time.
    private var last: String? = null
    private var repeats = 0

    /** Notes [message] under [topic], such as "player" or "sources". */
    fun note(topic: String, message: String) {
        val at = clock()
        writer.execute {
            val line = "[$topic] ${Redact.text(message)}"
            if (line == last) {
                repeats++
            } else {
                flushRepeats(at)
                last = line
                file.append("${stamp(at)} $line\n", flush = true)
            }
        }
    }

    /** Everything kept, the oldest first, once the lines waiting to be written are. */
    fun read(): ByteArray = writer.submit<ByteArray> {
        flushRepeats(clock())
        file.read()
    }.get()

    private fun flushRepeats(at: Long) {
        if (repeats > 0) file.append("${stamp(at)} [log] The line before came $repeats more times\n", flush = true)
        repeats = 0
    }

    private fun stamp(at: Long): String = STAMP.format(Instant.ofEpochMilli(at).atZone(zone()))

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }
}

/**
 * Save Player Logs (brief 5.17): while it's on, what VLC, mpv and the Android player write to
 * Android's log goes into [log] too, line by line, with credentials taken out. Android lets an app
 * read only its own lines ([command] asks for this process's), so nothing of other apps' comes in;
 * what Android still holds from before it was turned on comes first.
 */
internal class EngineLogCapture(
    private val log: RotatingLog,
    private val command: List<String>,
) {
    private var process: Process? = null

    val running: Boolean @Synchronized get() = process != null

    @Synchronized
    fun start() {
        if (process != null) return
        val started = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (failed: IOException) {
            return
        }
        process = started
        Thread({ copy(started) }, "plasma-engine-log").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        process?.destroy()
        process = null
        log.flush()
    }

    private fun copy(source: Process) {
        var lines = 0
        try {
            source.inputStream.bufferedReader().forEachLine { line ->
                log.append(Redact.text(line) + "\n")
                if (++lines % FLUSH_EVERY == 0) log.flush()
            }
        } catch (ended: IOException) {
            // Stopped, or Android's log went away.
        } finally {
            log.flush()
            synchronized(this) { if (process === source) process = null }
        }
    }

    companion object {
        private const val FLUSH_EVERY = 200

        /** Android's log for the process [pid], with each line's time and thread. */
        fun logcat(pid: Int): List<String> = listOf("logcat", "-v", "threadtime", "--pid=$pid")
    }
}

/** One file of Export Diagnostics' bundle (brief 5.17), as it's kept, before it's made safe to share. */
internal class ReportFile(val name: String, val text: String)

/** Export Diagnostics' bundle: each of [files] made safe to share with [scrub], in one ZIP. */
internal fun diagnosticsZip(files: List<ReportFile>, scrub: (String) -> String): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        for (file in files) {
            zip.putNextEntry(ZipEntry(file.name))
            zip.write(scrub(file.text).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return bytes.toByteArray()
}
