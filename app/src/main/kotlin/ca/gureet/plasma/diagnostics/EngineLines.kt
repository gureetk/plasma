// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.diagnostics

import ca.gureet.plasma.player.EngineLog

/**
 * An engine's own warnings and errors in Plasma's log (brief 5.17): mpv's, as it says them, each
 * with the part of mpv that said it ("cplayer", "ffmpeg/demuxer") and its level. A damaged stream
 * can bring dozens a second, which would push the rest of the log out, so each part has at most
 * [perMinute] lines a minute. What's left out is counted, and the count goes in a line of its own
 * once that part's minute is over and the engine says anything more. Called on the engine's own
 * thread; [note] mustn't wait.
 */
internal class EngineLines(
    private val note: (String) -> Unit,
    /** A clock that only goes forward, in ms. */
    private val now: () -> Long = { System.nanoTime() / NANOS_PER_MS },
    private val perMinute: Int = PER_MINUTE,
) : EngineLog {
    /** One part's minute: since when, the lines noted in it, and those left out. */
    private class Minute(val start: Long) {
        var noted = 0
        var leftOut = 0
    }

    private val minutes = HashMap<String, Minute>()

    @Synchronized
    override fun line(module: String, level: String, text: String) {
        val at = now()
        // Minutes that are over: what they left out, said once.
        val over = minutes.filterValues { at - it.start >= MINUTE_MS }
        for ((name, minute) in over) {
            if (minute.leftOut > 0) note("$name: ${minute.leftOut} more lines left out (at most $perMinute a minute)")
            minutes.remove(name)
        }
        val minute = minutes.getOrPut(module) { Minute(at) }
        if (minute.noted < perMinute) {
            minute.noted++
            note("$module $level: $text")
        } else {
            minute.leftOut++
        }
    }

    private companion object {
        const val PER_MINUTE = 20
        const val MINUTE_MS = 60_000L
        const val NANOS_PER_MS = 1_000_000L
    }
}
