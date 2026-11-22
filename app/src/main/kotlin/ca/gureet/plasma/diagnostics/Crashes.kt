// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File

/**
 * No crashes and no ANRs (#131): with no telemetry (brief 6.10), what goes wrong is kept on the
 * TV, in Plasma's own log, for Export Diagnostics. An uncaught exception's stack is written before
 * Android ends the app ([handler]); at the next start, Android's own record of how Plasma ended
 * since (an ANR, a native crash, being stopped for memory) is added ([exits]).
 */
internal object Crashes {
    const val TOPIC = "crash"

    /** Writes an uncaught exception to [log], then lets [previous], Android's, end the app as usual. */
    fun handler(log: DiagnosticLog, previous: Thread.UncaughtExceptionHandler?): Thread.UncaughtExceptionHandler =
        Thread.UncaughtExceptionHandler { thread, error ->
            runCatching { log.noteNow(TOPIC, "Uncaught on ${thread.name}: ${error.stackTraceToString().take(MAX_STACK)}") }
            previous?.uncaughtException(thread, error)
        }

    /**
     * How Plasma's process ended since [since] (ms), as Android recorded it, the oldest first:
     * what's worth a line, and when the newest ended. Only from Android 11.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun exits(context: Context, since: Long): Pair<List<String>, Long> {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList<String>() to since
        val all = runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS) }.getOrDefault(emptyList())
        val newer = all.filter { it.timestamp > since }.sortedBy { it.timestamp }
        val lines = newer.mapNotNull { exit -> lineOf(exit.reason, exit.description, exit.importance)?.let { it + traceOf(exit) } }
        return lines to (newer.maxOfOrNull { it.timestamp } ?: since)
    }

    /**
     * A line for an exit [reason] worth noting, with Android's [description]; null for an end
     * that's nothing wrong (Plasma or the user closing it, an update). [importance] says whether
     * Plasma was on screen.
     */
    fun lineOf(reason: Int, description: String?, importance: Int): String? {
        val what = when (reason) {
            ApplicationExitInfo.REASON_ANR -> "Not responding (ANR)"
            ApplicationExitInfo.REASON_CRASH -> "Crashed"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Crashed in native code"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Stopped by Android for memory"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Stopped by Android for using too much"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Failed to start"
            else -> return null
        }
        val where = if (importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) "on screen" else "in the background"
        return "$what, $where" + (description?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
    }

    /** An ANR's or native crash's own trace, its first lines: the main thread's, where it's stuck. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun traceOf(exit: ApplicationExitInfo): String {
        if (exit.reason != ApplicationExitInfo.REASON_ANR && exit.reason != ApplicationExitInfo.REASON_CRASH_NATIVE) return ""
        val lines = runCatching { exit.traceInputStream?.bufferedReader()?.use { reader -> reader.lineSequence().take(MAX_TRACE_LINES).toList() } }.getOrNull()
        return lines?.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "\n") ?: ""
    }

    /** When the newest exit noted ended, kept beside the logs so each is noted once. */
    fun seen(folder: File): Long = runCatching { File(folder, SEEN).readText().trim().toLong() }.getOrDefault(0L)

    fun markSeen(folder: File, at: Long) {
        runCatching {
            folder.mkdirs()
            File(folder, SEEN).writeText(at.toString())
        }
    }

    private const val SEEN = "exits-seen"
    private const val MAX_EXITS = 8
    private const val MAX_STACK = 8_000
    private const val MAX_TRACE_LINES = 60
}
