// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Guide with the big source (brief 6.7: 60 fps with 1,500 channels and 200,000 programs, and
 * 100 ms from a key to what it changes). Plasma opens on it as when the TV turns on, started
 * afresh for each iteration before it's measured.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class GuideBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun setUp() = setUpPlasma()

    /** Down through the channels as fast as keys come, as a held key does, and back up. */
    @Test
    fun scrollTheGuide() = rule.measurePlasma(
        metrics = listOf(FrameTimingMetric(), FrameSplitMetric(), ShownFramesMetric()),
        iterations = ITERATIONS,
        setupBlock = { startAfresh(guide()) },
    ) {
        repeat(MOVES) { device.pressDPadDown() }
        repeat(MOVES) { device.pressDPadUp() }
        // The last key's frames.
        Thread.sleep(SETTLE_MS)
    }

    /** One key at a time, down the channels and then across their programs, as a person presses them. */
    @Test
    fun keyToFocusInTheGuide() = rule.measurePlasma(
        metrics = listOf(KeyToDrawMetric()),
        iterations = ITERATIONS,
        setupBlock = { startAfresh(guide()) },
    ) {
        repeat(KEYS) {
            device.pressDPadDown()
            Thread.sleep(KEY_GAP_MS)
        }
        repeat(KEYS) {
            device.pressDPadRight()
            Thread.sleep(KEY_GAP_MS)
        }
    }

    /**
     * One key at a time along a channel's programs and back, twice: most keys take the window half
     * an hour with them, and every row on screen with the window. These rows' programs are 75
     * minutes long, two or three to a window, the first and the last cut by its edges. Six keys
     * each way stay inside the day the Guide has loaded at any hour (to four hours past midnight):
     * a Right past the last program loaded does nothing, and leaves no frame to time.
     */
    @Test
    fun keyToFocusAlongTheGuide() = rule.measurePlasma(
        metrics = listOf(
            KeyToDrawMetric(),
            FrameTimingMetric(),
            FrameSplitMetric(),
            ShownFramesMetric(),
        ),
        iterations = ITERATIONS,
        setupBlock = { startAfresh(guide()) },
    ) {
        repeat(ROUNDS) {
            repeat(ALONG) { right() }
            repeat(ALONG) { left() }
        }
    }

    /**
     * One key at a time along a row of half-hour programs, as most of a real guide has (the big
     * source's last group): four whole programs in the window, and each key that moves the window
     * takes one out of every row on screen and brings one in.
     */
    @Test
    fun keyToFocusAlongHalfHours() = alongHalfHours()

    /** The same keys with a row's cells told apart by their places, as before #222, to measure the two in one run. */
    @Test
    fun keyToFocusAlongHalfHoursWithCellsByPlace() {
        withGuideCellsByPlace()
        alongHalfHours()
    }

    /**
     * Focus starts on the program on now, the window's second. Eight Rights: two to the window's
     * last program, then six that each move the window on. Nine Lefts: three to its first program,
     * then six that each move it back to where it started. Nine Rights again. Eighteen of the 26
     * keys move the window, the same ones at any hour, inside the day the Guide has loaded.
     */
    private fun alongHalfHours() = rule.measurePlasma(
        metrics = listOf(
            KeyToDrawMetric(),
            FrameTimingMetric(),
            FrameSplitMetric(),
            ShownFramesMetric(),
        ),
        iterations = ITERATIONS,
        setupBlock = {
            startAfresh(guide())
            device.toGuideChannel(HALF_HOUR_CHANNEL)
        },
    ) {
        repeat(OUT) { right() }
        repeat(BACK) { left() }
        repeat(BACK) { right() }
    }

    /** Right, and the wait a person leaves before the next key. */
    private fun right() {
        device.pressDPadRight()
        Thread.sleep(KEY_GAP_MS)
    }

    /** Left, and the same wait. */
    private fun left() {
        device.pressDPadLeft()
        Thread.sleep(KEY_GAP_MS)
    }

    private companion object {
        const val ITERATIONS = 5
        const val MOVES = 60
        const val KEYS = 15
        const val ALONG = 6
        const val ROUNDS = 2
        const val OUT = 8
        const val BACK = 9

        /** A channel in the middle of the big source's last group, whose programs are half an hour long (`MadeUpSource`). */
        const val HALF_HOUR_CHANNEL = 1_476
        const val KEY_GAP_MS = 300L
        const val SETTLE_MS = 1_000L
    }
}
