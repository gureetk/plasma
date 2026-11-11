// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to an interactive Home (brief 6.7: 1.5 s on a mid-range box), with the big source:
 * the time to full display is when Plasma's launch screen starts to go, which is once Home has its
 * channels and never before 0.95 s. [StartupSplitMetric] says where the time goes.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun setUp() = setUpPlasma()

    @Test
    fun coldStartToHome() {
        var since = 0L
        rule.measurePlasma(
            metrics = listOf(StartupTimingMetric(), FullyDrawnCheckMetric(), StartupSplitMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            // Plasma is stopped after the setup, and the benchmarks' backdrop comes back. Pressing
            // Home would bring the TV's own Home instead, and a launcher's Home button override.
            setupBlock = {
                wakeTheTv()
                since = deviceSeconds()
            },
        ) {
            startActivityAndWait()
            waitForFullyDrawn(since)
        }
    }

    /**
     * The same, as when Android has stopped Plasma's process in the background: it starts afresh,
     * but with what Android keeps between starts, such as its compiled shaders, which
     * [coldStartToHome]'s cold start clears first, as for the first start after an update.
     */
    @Test
    fun coldStartToHomeCachesKept() {
        var since = 0L
        rule.measurePlasma(
            metrics = listOf(StartupTimingMetric(), FullyDrawnCheckMetric(), StartupSplitMetric()),
            iterations = ITERATIONS,
            // No start mode: the setup stops Plasma itself, and clears nothing.
            setupBlock = {
                wakeTheTv()
                killProcess()
                since = deviceSeconds()
            },
        ) {
            startActivityAndWait()
            waitForFullyDrawn(since)
        }
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
