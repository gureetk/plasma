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

    /**
     * [coldStartToHomeCachesKept] with what the screens read worked out where it's collected, on
     * the main thread, as before it was moved off it, to measure a start both ways in one run.
     */
    @Test
    fun coldStartToHomeCachesKeptWithReadsAsBefore() {
        withReadsAsBefore()
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] with what the screens read worked out off the main thread and
     * nothing of Home's held until its world is in, as it was first moved.
     */
    @Test
    fun coldStartToHomeCachesKeptWithReadsOffMainFromTheStart() {
        withReads("offMainFromTheStart")
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] with what the screens read worked out off the main thread from
     * the start, and what Home reads besides its world not read until the world is in: tried, and
     * not taken.
     */
    @Test
    fun coldStartToHomeCachesKeptWithReadsHeldUntilTheWorld() {
        withReads("heldUntilTheWorld")
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] with the world's guide window read beside its first look, as
     * before, to measure what the first world gains by having the processors to itself.
     */
    @Test
    fun coldStartToHomeCachesKeptWithTheWholeGuideBesideTheFirstLook() {
        withTheWholeGuideBesideTheFirstLook()
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] with what the screens read worked out where it's collected, and
     * the guide window read beside the first look, as both were. The world is still asked for as
     * Plasma's screen is made: [coldStartToHomeCachesKeptAsOnMain] is a start as on `main`.
     */
    @Test
    fun coldStartToHomeCachesKeptWithReadsAsBeforeAndTheWholeGuideBesideTheFirstLook() {
        withReadsAsBefore()
        withTheWholeGuideBesideTheFirstLook()
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] with the world asked for only when the first screen's
     * composition asks for it, as before, to measure what a start gains by the world being read
     * beside that composition.
     */
    @Test
    fun coldStartToHomeCachesKeptWithTheWorldAskedForWhenComposed() {
        withTheWorldAskedForWhenComposed()
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] once more, and its twin once more, so that a set can run the two
     * ways turn and turn about: ten starts of each, twice over, tell a smaller difference than
     * ten, and one that isn't the hour's.
     */
    @Test
    fun coldStartToHomeCachesKeptAgain() = coldStartToHomeCachesKept()

    @Test
    fun coldStartToHomeCachesKeptWithTheWorldAskedForWhenComposedAgain() = coldStartToHomeCachesKeptWithTheWorldAskedForWhenComposed()

    /**
     * [coldStartToHomeCachesKept] as a start is on `main` in the three things that bear on its
     * first world: what the screens read worked out where it's collected, the guide window read
     * beside the first look, and the world asked for when the first screen's composition asks.
     */
    @Test
    fun coldStartToHomeCachesKeptAsOnMain() {
        withReadsAsBefore()
        withTheWholeGuideBesideTheFirstLook()
        withTheWorldAskedForWhenComposed()
        coldStartToHomeCachesKept()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
