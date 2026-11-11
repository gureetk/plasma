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
 * Home's rows with the big source's catalog: New in Movies and New in Series, with posters, under
 * an empty Continue Watching, as on a new install (brief 6.7: 100 ms from a key to what it changes,
 * and smooth frames as focus moves). Plasma opens on Home, started afresh for each iteration before
 * it's measured.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class HomeBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun setUp() = setUpPlasma()

    /**
     * From the dock down into the rows, along the first, down to the next and along it, then back
     * up to the dock, one key at a time as a person presses them.
     */
    @Test
    fun keyToFocusOnHome() = rule.measureRepeated(
        packageName = PLASMA,
        metrics = listOf(
            KeyToDrawMetric(),
            FrameTimingMetric(),
            FrameSplitMetric(),
        ),
        iterations = ITERATIONS,
        setupBlock = { startAfresh(home()) { waitForHome() } },
    ) {
        repeat(ROWS) {
            device.pressDPadDown()
            Thread.sleep(KEY_GAP_MS)
            repeat(ALONG) {
                device.pressDPadRight()
                Thread.sleep(KEY_GAP_MS)
            }
        }
        repeat(ROWS) {
            device.pressDPadUp()
            Thread.sleep(KEY_GAP_MS)
        }
    }

    private companion object {
        const val ITERATIONS = 5

        /** Two rows, eight keys along each: 20 keys with the ways down and back up. */
        const val ROWS = 2
        const val ALONG = 8
        const val KEY_GAP_MS = 300L
    }
}
