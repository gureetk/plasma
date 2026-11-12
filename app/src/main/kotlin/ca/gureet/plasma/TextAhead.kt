// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Measures text ahead of the main thread (#127).
 *
 * Compose hands each text of a few words or more to the executor it's given
 * (`LocalBackgroundTextMeasurementExecutor`) as the text is composed, from Android 9 on a TV with
 * four cores or more. What's worked out here is the words' shaping, which Android keeps: when the
 * main thread lays the same words out, later in the frame or in the next one, most of that work
 * is done. It's a head start and nothing else: a text nobody measured ahead is laid out as it
 * always was, and the result is the same either way.
 *
 * So this does as little as it can:
 * - One thread. A frame's texts come one after another, and the main thread lays them out in the
 *   same order, so one thread ahead of it is enough.
 * - Room for [waiting] texts. When more come than that, the one that has waited longest goes: the
 *   main thread is the nearest to reaching it, and the ones it reaches last are the ones there's
 *   still time for.
 * - A text that can't be measured here ends the head starts: [failed] hears of it once, and
 *   nothing is taken after it. The main thread then lays everything out by itself, as it did.
 */
internal class TextAhead(
    waiting: Int = WAITING,
    private val failed: (RuntimeException) -> Unit = {},
) : Executor {
    private val thread = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(waiting),
        { task -> Thread(task, "plasma-text").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    @Volatile
    private var stopped = false

    override fun execute(task: Runnable) {
        if (stopped) return
        thread.execute {
            if (stopped) return@execute
            try {
                task.run()
            } catch (failure: RuntimeException) {
                stopped = true
                failed(failure)
            }
        }
    }

    private companion object {
        /** More than a screen's texts of a few words or more: the Guide's, at its fullest. */
        const val WAITING = 128
    }
}
