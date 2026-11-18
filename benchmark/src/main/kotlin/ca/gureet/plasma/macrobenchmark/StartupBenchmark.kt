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
 * channels and never before 0.95 s. [StartupSplitMetric] says where the time goes, and
 * [RowsFrameMetric] what Home's first frames take after that, as its rows get their contents.
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
            metrics = listOf(StartupTimingMetric(), FullyDrawnCheckMetric(), StartupSplitMetric(), RowsFrameMetric()),
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
            metrics = listOf(StartupTimingMetric(), FullyDrawnCheckMetric(), StartupSplitMetric(), RowsFrameMetric()),
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
     * [coldStartToHomeCachesKept] as a start is on `main`: what the screens read worked out where
     * it's collected, the guide window read beside the first look, and what's below Home's screen
     * edge made all at once.
     */
    @Test
    fun coldStartToHomeCachesKeptAsOnMain() {
        withReadsAsBefore()
        withTheWholeGuideBesideTheFirstLook()
        withWhatsBelowHomeMade("atOnce")
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] with what's below Home's screen edge made all at once, in the
     * frame its contents come, as before it was made in turns: [RowsFrameMetric] says what Home's
     * first frames take each way.
     */
    @Test
    fun coldStartToHomeCachesKeptWithWhatsBelowMadeAtOnce() {
        withWhatsBelowHomeMade("atOnce")
        coldStartToHomeCachesKept()
    }

    @Test
    fun coldStartToHomeCachesKeptWithWhatsBelowMadeAtOnceAgain() = coldStartToHomeCachesKeptWithWhatsBelowMadeAtOnce()

    /**
     * [coldStartToHomeCachesKept] on a Home that's been lived in ([withALivedInHome]): Favorite
     * Channels is its first row, and Continue Watching, New in Movies, New in Series and Apps are
     * all below the screen's edge, two keys from the dock, where on a new install New in Movies is
     * one key away and so is made at once. Its twin makes what's below all at once, as before.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHome() {
        withALivedInHome()
        coldStartToHomeCachesKept()
    }

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithWhatsBelowMadeAtOnce() {
        withALivedInHome()
        withWhatsBelowHomeMade("atOnce")
        coldStartToHomeCachesKept()
    }

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeAgain() = coldStartToHomeCachesKeptOnALivedInHome()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithWhatsBelowMadeAtOnceAgain() = coldStartToHomeCachesKeptOnALivedInHomeWithWhatsBelowMadeAtOnce()

    /** [coldStartToHomeCachesKept] with what's below Home's screen edge made two pieces at a frame, where as built it's one. */
    @Test
    fun coldStartToHomeCachesKeptWithTwoPiecesBelowAFrame() {
        withWhatsBelowHomeMade("twoAFrame")
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] with the world asked for as Plasma's screen is made, ahead of the
     * screens, where as built the first screen's composition asks for it. Tried, and not taken:
     * the world is made sooner, and Home shows no sooner for it. Kept for a set that measures it
     * again with what would have to go with it.
     */
    @Test
    fun coldStartToHomeCachesKeptWithTheWorldAskedForAhead() {
        withTheWorldAskedForAhead()
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] once more, so that a set can run two ways of a start turn and
     * turn about: ten starts of each, twice over, tell a smaller difference than ten, and one
     * that isn't the hour's.
     */
    @Test
    fun coldStartToHomeCachesKeptAgain() = coldStartToHomeCachesKept()

    @Test
    fun coldStartToHomeCachesKeptWithTheWorldAskedForAheadAgain() = coldStartToHomeCachesKeptWithTheWorldAskedForAhead()

    private companion object {
        const val ITERATIONS = 10
    }
}
