// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.os.Looper
import android.os.Trace

/**
 * What the main thread is doing, written into a trace, for a benchmark run that asks for it
 * (#127): `--method mainThread --arg sampled` on the benchmark build's setup.
 *
 * A frame's work has trace sections; what the main thread does between two frames mostly has
 * none (the coroutines a composition launches run right after its frame, in a message of their
 * own), and shows in a trace only as the thread running. So a thread of its own takes the main
 * thread's stack every few milliseconds while a trace is being recorded and, unless the main
 * thread is waiting for its next message, writes it as sections on itself: the stack's top first,
 * a few frames to a section, each frame as `class.method:line` in the build's own names (R8's
 * mapping turns them back). A section that starts a stack begins with "S ", one that goes on
 * with it begins with "+ ".
 *
 * Taking a stack stops the main thread for a moment each time, so a run with it is for what its
 * traces say and not for its times, like a run that names composables.
 */
internal object MainThreadSamples {
    @Volatile private var sampler: Thread? = null

    /** Starts or stops it. Starting it twice changes nothing. */
    fun set(on: Boolean) {
        synchronized(this) {
            val running = sampler
            if (on && running == null) {
                sampler = Thread(::sample, THREAD).apply {
                    isDaemon = true
                    start()
                }
            } else if (!on && running != null) {
                sampler = null
                running.interrupt()
            }
        }
    }

    private fun sample() {
        val main = Looper.getMainLooper().thread
        while (sampler === Thread.currentThread()) {
            try {
                Thread.sleep(EVERY_MS)
            } catch (stopped: InterruptedException) {
                return
            }
            if (!Trace.isEnabled()) continue
            val stack = main.stackTrace
            if (stack.isEmpty() || stack[0].waitsForAMessage()) continue
            write(stack)
        }
    }

    private fun StackTraceElement.waitsForAMessage() = methodName == "nativePollOnce" && className == "android.os.MessageQueue"

    /** [stack]'s first [FRAMES] frames, as many to a section as its name can hold. */
    private fun write(stack: Array<StackTraceElement>) {
        val name = StringBuilder(START)
        for (index in 0 until minOf(stack.size, FRAMES)) {
            val frame = stack[index].run { "$className.$methodName:$lineNumber" }.takeLast(FRAME_CHARS)
            if (name.length > MARK_CHARS && name.length + 1 + frame.length > NAME_CHARS) {
                section(name.toString())
                name.setLength(0)
                name.append(MORE)
            }
            if (name.length > MARK_CHARS) name.append('|')
            name.append(frame)
        }
        section(name.toString())
    }

    private fun section(name: String) {
        Trace.beginSection(name)
        Trace.endSection()
    }

    /** The sampling thread's name, which a trace's reader finds it by (a thread's name holds 15 characters). */
    private const val THREAD = "Plasma samples"

    /** Between two stacks: a few to a frame of 16.7 ms. */
    private const val EVERY_MS = 4L

    /** How far down a stack goes into the trace: past a coroutine's own frames to what resumed it. */
    private const val FRAMES = 28

    /** A section's name can be 127 characters long. */
    private const val NAME_CHARS = 124
    private const val FRAME_CHARS = 110
    private const val START = "S "
    private const val MORE = "+ "
    private const val MARK_CHARS = 2
}
