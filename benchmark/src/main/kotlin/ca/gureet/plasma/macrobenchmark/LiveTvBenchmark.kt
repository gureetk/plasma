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
 * Live TV's channel list with the big source (brief 6.7: 100 ms from a key to what it changes).
 * Plasma opens on it as Home does, started afresh for each iteration before it's measured.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class LiveTvBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun setUp() = setUpPlasma()

    /** One key at a time down All Channels, as a person presses them, with the frames it takes. */
    @Test
    fun keyToFocusInLiveTv() = downTheChannels()

    /** The same keys with each row's focus motion set up as it was before #221, to measure the two in one run. */
    @Test
    fun keyToFocusInLiveTvWithFocusMotionAsBefore() {
        withFocusMotionAsBefore()
        downTheChannels()
    }

    private fun downTheChannels() = rule.measurePlasma(
        metrics = listOf(
            KeyToDrawMetric(),
            FrameTimingMetric(),
            FrameSplitMetric(),
            ShownFramesMetric(),
        ),
        iterations = ITERATIONS,
        setupBlock = { startAfresh(channelList()) },
    ) {
        repeat(KEYS) {
            device.pressDPadDown()
            Thread.sleep(KEY_GAP_MS)
        }
    }

    private companion object {
        const val ITERATIONS = 5
        const val KEYS = 20
        const val KEY_GAP_MS = 300L
    }
}
