// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Guide with the big source (brief 6.7: 60 fps with 1,500 channels and 200,000 programs, and
 * 100 ms from a key to what it changes). Plasma opens on it as when the TV turns on.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class GuideBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun setUp() = withBigSource()

    /** Down through the channels as fast as keys come, as a held key does, and back up. */
    @Test
    fun scrollTheGuide() = rule.measureRepeated(
        packageName = PLASMA,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = {
            startActivityAndWait(guide())
            device.waitForChannels()
        },
    ) {
        repeat(MOVES) { device.pressDPadDown() }
        repeat(MOVES) { device.pressDPadUp() }
        device.waitForIdle()
    }

    /** One key at a time, down the channels and then across their programs, as a person presses them. */
    @Test
    fun keyToFocusInTheGuide() = rule.measureRepeated(
        packageName = PLASMA,
        metrics = listOf(
            KeyToDrawMetric(),
        ),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = {
            startActivityAndWait(guide())
            device.waitForChannels()
        },
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

    private companion object {
        const val ITERATIONS = 5
        const val MOVES = 60
        const val KEYS = 15
        const val KEY_GAP_MS = 300L
    }
}
