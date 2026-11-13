// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each of the dock's screens the first time it's opened after Plasma starts, and then a second
 * time (#127): Live TV, the Guide, Movies and Series, with the big source. Plasma is started
 * afresh on Home for each iteration, and focus is on the screen's tile before it's measured.
 *
 * A screen's first opening pays for whatever Plasma hasn't done yet since it started; the second
 * pays only for the screen. The difference is what a start could get ready ahead, and
 * [OpeningMetric] gives both.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class FirstOpenBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun setUp() = setUpPlasma()

    @Test
    fun firstOpenOfLiveTv() = firstOpen(LIVE_TV) { waitForChannels() }

    @Test
    fun firstOpenOfTheGuide() = firstOpen(GUIDE) { waitForChannels() }

    @Test
    fun firstOpenOfMovies() = firstOpen(MOVIES) { waitForTitles() }

    @Test
    fun firstOpenOfSeries() = firstOpen(SERIES) { waitForTitles() }

    /**
     * OK on the dock's tile [dockSteps] to the right of Live TV, Back, and OK again, with a rest
     * after each. Nothing is asked of the screen while it comes, since UI Automator's look at a
     * screen runs on Plasma's own main thread: [there] checks it once the rest is over.
     */
    private fun firstOpen(dockSteps: Int, there: UiDevice.() -> Unit) = rule.measurePlasma(
        metrics = listOf(
            OpeningMetric(),
            KeyToDrawMetric(),
            FrameTimingMetric(),
            FrameSplitMetric(),
            ShownFramesMetric(),
        ),
        iterations = ITERATIONS,
        setupBlock = {
            startAfresh(home()) { waitForHome() }
            device.toDockTile(dockSteps)
            Thread.sleep(REST_MS)
        },
    ) {
        device.pressDPadCenter()
        Thread.sleep(OPENING_MS)
        device.there()
        device.pressBack()
        Thread.sleep(REST_MS)
        device.waitForHome()
        device.pressDPadCenter()
        Thread.sleep(OPENING_MS)
        device.there()
    }

    private companion object {
        const val ITERATIONS = 5

        /** The dock's tiles, counted to the right of Live TV. */
        const val LIVE_TV = 0
        const val GUIDE = 1
        const val MOVIES = 2
        const val SERIES = 3

        /** Longer than a screen takes to come with its pictures, on a slow device too. */
        const val OPENING_MS = 6_000L

        /** For Home to come back, or focus to settle on a tile. */
        const val REST_MS = 2_000L
    }
}
