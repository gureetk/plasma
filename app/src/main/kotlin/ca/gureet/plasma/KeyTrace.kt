// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.os.Build
import android.os.Trace
import android.view.KeyEvent
import android.view.View
import androidx.annotation.RequiresApi

/**
 * A key press to what it changes on screen (brief 6.7: 100 ms), for the benchmarks (#127). While
 * the system is tracing, each key pressed starts a trace section, [SECTION], which ends as the
 * next frame is drawn, so a trace says how long each key took to show. Nothing is done while
 * nothing traces. It ends where the frame is drawn, before the display shows it.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class KeyTrace(view: View) {
    /** The sections started and not yet drawn, by their cookies. */
    private val waiting = ArrayDeque<Int>()
    private var next = 0

    init {
        view.viewTreeObserver.addOnDrawListener {
            while (waiting.isNotEmpty()) Trace.endAsyncSection(SECTION, waiting.removeFirst())
        }
    }

    /** [event] is on its way to the screens. */
    fun pressed(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN || !Trace.isEnabled()) return
        val cookie = next++
        Trace.beginAsyncSection(SECTION, cookie)
        waiting.addLast(cookie)
    }

    companion object {
        /** The section's name, which the benchmark module measures. */
        const val SECTION = "Plasma.keyToDraw"
    }
}
