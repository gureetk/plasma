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
 * channels and never before 0.95 s.
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
        rule.measureRepeated(
            packageName = PLASMA,
            metrics = listOf(StartupTimingMetric(), FullyDrawnCheckMetric()),
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

    private companion object {
        const val ITERATIONS = 10
    }
}
