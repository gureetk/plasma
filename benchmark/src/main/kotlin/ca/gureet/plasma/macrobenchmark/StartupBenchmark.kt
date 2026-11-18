// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
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
     * it's collected, the guide window read beside the first look, what's below Home's screen
     * edge made all at once, the player made as the app is composed, and the values Plasma's
     * screen waits for worked out where they're collected.
     */
    @Test
    fun coldStartToHomeCachesKeptAsOnMain() {
        withReadsAsBefore()
        withTheWholeGuideBesideTheFirstLook()
        withWhatsBelowHomeMade("atOnce")
        withThePlayerMade("whenComposed")
        withTheStartValuesWhereCollected()
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKeptAsOnMain] once more. A class's tests run in the order of their names'
     * hashes, and the second turns are named for their places: in a set of [coldStartToHomeCachesKept],
     * this way, the start values where collected and the player made once ready, each with its
     * second turn, every way's two turns are as far from the set's middle as each other, so that
     * the ways share the session's drift.
     */
    @Test
    fun coldStartToHomeCachesKeptAsOnMainOnceMore() = coldStartToHomeCachesKeptAsOnMain()

    /**
     * [coldStartToHomeCachesKept] with the four values Plasma's screen waits for worked out where
     * they're collected, on the main thread, and the app composed whenever the last is in, as
     * before; where as built they're worked out off the main thread and the app is composed in
     * the frame after the launch screen's. [RowsFrameMetric] says when the app's composition
     * starts each way, and `timeToInitialDisplayMs` that the launch screen comes no later.
     */
    @Test
    fun coldStartToHomeCachesKeptWithTheStartValuesWhereCollected() {
        withTheStartValuesWhereCollected()
        coldStartToHomeCachesKept()
    }

    /** The second turn, named for its place in a set ([coldStartToHomeCachesKeptAsOnMainOnceMore]). */
    @Test
    fun coldStartToHomeCachesKeptWithTheStartValuesWhereCollectedOverAgain() = coldStartToHomeCachesKeptWithTheStartValuesWhereCollected()

    /**
     * [coldStartToHomeCachesKept] with the player made on the main thread as the app is composed,
     * as it was while the screensaver's watch took it there, where as built it's asked for as the
     * app is first composed and made off the main thread beside it: [RowsFrameMetric] says what
     * the app's composition takes each way, and when the player is made.
     */
    @Test
    fun coldStartToHomeCachesKeptWithThePlayerMadeWhenComposed() {
        withThePlayerMade("whenComposed")
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] with the player made off the main thread once Home has what it
     * shows, as it was first moved there: its arrival, and the frame in which its surface joins
     * the screen, then come after Home shows.
     */
    @Test
    fun coldStartToHomeCachesKeptWithThePlayerMadeOnceReady() {
        withThePlayerMade("onceReady")
        coldStartToHomeCachesKept()
    }

    /** The second turn, named for its place in a set ([coldStartToHomeCachesKeptAsOnMainOnceMore]). */
    @Test
    fun coldStartToHomeCachesKeptWithThePlayerMadeOnceReadySecondTurn() = coldStartToHomeCachesKeptWithThePlayerMadeOnceReady()

    @Test
    fun coldStartToHomeCachesKeptWithThePlayerMadeWhenComposedAgain() = coldStartToHomeCachesKeptWithThePlayerMadeWhenComposed()

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

    /**
     * The first keys as Home shows, on a Home that's been lived in ([withALivedInHome]): Plasma
     * started from nothing with Android's caches kept, and a key every [FIRST_KEYS_GAP_MS] from the
     * moment it's ready, through the time in which the rows below the screen's edge get their
     * contents. The first key comes while Home is still being composed, and says how long that
     * held it. Then [FIRST_KEYS_OUT] to the right along the dock and [FIRST_KEYS_BACK] back, which
     * bring nothing into sight. As built the rows below are made in turns that wait for the remote
     * to rest; [FirstKeysMetric] says how long each key waited and took, and what the frames
     * around the keys were.
     */
    @Test
    fun firstKeysOnALivedInHome() {
        withALivedInHome()
        firstKeys(downTheRows = false)
    }

    /** [firstKeysOnALivedInHome] with what's below Home's screen edge made all at once, as on `main`. */
    @Test
    fun firstKeysOnALivedInHomeWithWhatsBelowMadeAtOnce() {
        withALivedInHome()
        withWhatsBelowHomeMade("atOnce")
        firstKeys(downTheRows = false)
    }

    /** [firstKeysOnALivedInHome] with the turns going on while keys come, where as built they wait for the remote to rest. */
    @Test
    fun firstKeysOnALivedInHomeWithTurnsThroughKeys() {
        withALivedInHome()
        withWhatsBelowHomeMade("throughKeys")
        firstKeys(downTheRows = false)
    }

    /**
     * [firstKeysOnALivedInHome] with the keys going down Home's rows and back up: each Down can
     * bring rows into sight, and a row that hasn't had its turn by then is made whole in that
     * key's frame. It's the turns at their worst: someone who goes straight down as Home shows
     * finds every row still to make, and as built the turns wait while the keys come.
     */
    @Test
    fun firstKeysDownAndUpALivedInHome() {
        withALivedInHome()
        firstKeys(downTheRows = true)
    }

    /** [firstKeysDownAndUpALivedInHome] with what's below Home's screen edge made all at once, as on `main`. */
    @Test
    fun firstKeysDownAndUpALivedInHomeWithWhatsBelowMadeAtOnce() {
        withALivedInHome()
        withWhatsBelowHomeMade("atOnce")
        firstKeys(downTheRows = true)
    }

    /** [firstKeysDownAndUpALivedInHome] with the turns going on while keys come: rows get made between one Down and the next. */
    @Test
    fun firstKeysDownAndUpALivedInHomeWithTurnsThroughKeys() {
        withALivedInHome()
        withWhatsBelowHomeMade("throughKeys")
        firstKeys(downTheRows = true)
    }

    private fun firstKeys(downTheRows: Boolean) {
        var since = 0L
        rule.measurePlasma(
            metrics = listOf(
                StartupTimingMetric(),
                FullyDrawnCheckMetric(),
                StartupSplitMetric(),
                RowsFrameMetric(),
                FirstKeysMetric(keys = 1 + FIRST_KEYS_OUT + FIRST_KEYS_BACK),
            ),
            iterations = ITERATIONS,
            setupBlock = {
                wakeTheTv()
                killProcess()
                since = deviceSeconds()
            },
        ) {
            startActivityAndWait()
            // Ready is the launch screen starting to go, and Home is composed in the frame after
            // it. The first key comes while that frame is at work: it's taken up as the frame
            // ends, before or after Home has taken focus, so what it moves isn't certain. It
            // goes to the right along the dock, where either way leaves the keys after it
            // something to move: they find Home with its focus on the dock.
            waitForFullyDrawn(since, thenMs = 0)
            firstKey { pressDPadRight() }
            repeat(FIRST_KEYS_OUT) { firstKey { if (downTheRows) pressDPadDown() else pressDPadRight() } }
            repeat(FIRST_KEYS_BACK) { firstKey { if (downTheRows) pressDPadUp() else pressDPadLeft() } }
            Thread.sleep(AFTER_FIRST_KEYS_MS)
        }
    }

    /** One of the first keys, which returns once Plasma has taken it, and the time to the next. */
    private fun firstKey(press: UiDevice.() -> Boolean) {
        device.press()
        Thread.sleep(FIRST_KEYS_GAP_MS)
    }

    private companion object {
        const val ITERATIONS = 10

        /**
         * The first keys after the one that comes as Home is composed: this many along the dock
         * or down the rows. From the dock's second tile, where that first key can leave focus,
         * four more to the right reach its last; four rows down is Home's fourth.
         */
        const val FIRST_KEYS_OUT = 4

        /** Then this many back: one fewer, so that none finds nothing to move from the dock's first tile. */
        const val FIRST_KEYS_BACK = 3

        /** Between two of the first keys: a person stepping along without stopping. */
        const val FIRST_KEYS_GAP_MS = 150L

        /** After the last key, for what was waiting on the remote to be made within the trace. */
        const val AFTER_FIRST_KEYS_MS = 2_000L
    }
}
