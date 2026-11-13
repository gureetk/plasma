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
 * Movies and Series with the big source's catalog, 6,000 movies and 1,200 series with posters
 * (brief 6.7: 100 ms from a key to what it changes, and smooth frames as focus moves). Plasma opens
 * on Home, started afresh for each iteration, and the dock opens the screen before it's measured.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class VodBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun setUp() = setUpPlasma()

    /** One key at a time through Movies' posters, along a row and back along the next, as a person presses them. */
    @Test
    fun keyToFocusInMovies() = throughThePosters(MOVIES)

    /** The same through Series' posters. */
    @Test
    fun keyToFocusInSeries() = throughThePosters(SERIES)

    private fun throughThePosters(dockSteps: Int) = rule.measurePlasma(
        metrics = listOf(
            KeyToDrawMetric(),
            FrameTimingMetric(),
            FrameSplitMetric(),
            ShownFramesMetric(),
        ),
        iterations = ITERATIONS,
        setupBlock = {
            startAfresh(home()) { waitForHome() }
            device.openFromDock(dockSteps)
        },
    ) {
        // Along the first row of six, down, back along the second, down, and so on.
        for (row in 0 until ROWS) {
            repeat(ALONG) {
                if (row % 2 == 0) device.pressDPadRight() else device.pressDPadLeft()
                Thread.sleep(KEY_GAP_MS)
            }
            if (row < ROWS - 1) {
                device.pressDPadDown()
                Thread.sleep(KEY_GAP_MS)
            }
        }
    }

    private companion object {
        const val ITERATIONS = 5

        /** The dock's tiles to the right of Live TV: the Guide, then Movies, then Series. */
        const val MOVIES = 2
        const val SERIES = 3

        /** Four rows, five keys along each and one down between them: 23 keys. */
        const val ROWS = 4
        const val ALONG = 5
        const val KEY_GAP_MS = 300L
    }
}
