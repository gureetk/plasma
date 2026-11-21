// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import android.view.KeyEvent
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
    fun coldStartToHomeCachesKept() = startWithCachesKept(ITERATIONS)

    /**
     * Starts that aren't read, to run before a set's own: [coldStartToHomeCachesKept] three times
     * over. The first minutes after a set's apps are installed needn't be like the rest of its
     * session, and what's compared shouldn't have them on one side only. A class's tests run in
     * the order of their names' hashes: this name's is lower than any of the tests a set is made
     * of.
     */
    @Test
    fun warmUpWithColdStarts() = startWithCachesKept(3 * ITERATIONS)

    private fun startWithCachesKept(iterations: Int) {
        var since = 0L
        rule.measurePlasma(
            metrics = listOf(StartupTimingMetric(), FullyDrawnCheckMetric(), StartupSplitMetric(), RowsFrameMetric()),
            iterations = iterations,
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
     * edge made all at once, and the player made on the main thread as the app is composed.
     */
    @Test
    fun coldStartToHomeCachesKeptAsOnMain() {
        asOnMain()
        coldStartToHomeCachesKept()
    }

    /**
     * What a start on `main` has another way than as built: what the screens read worked out
     * where it's collected, the guide window read beside the first look, what's below Home's
     * screen edge made all at once and, unless [thePlayerToo] is false, the player made on the
     * main thread as the app is composed.
     */
    private fun asOnMain(thePlayerToo: Boolean = true) {
        withReadsAsBefore()
        withTheWholeGuideBesideTheFirstLook()
        withWhatsBelowHomeMade("atOnce")
        if (thePlayerToo) withThePlayerMade("whenComposed")
    }

    /**
     * [coldStartToHomeCachesKeptAsOnMain] once more. A class's tests run in the order of their
     * names' hashes, and the second turns are named for their places: in a set, a way's two
     * turns should lie about as far before the set's middle as after it, so that the ways share
     * the session's drift.
     */
    @Test
    fun coldStartToHomeCachesKeptAsOnMainOnceMore() = coldStartToHomeCachesKeptAsOnMain()

    /**
     * [coldStartToHomeCachesKept] with the four values Plasma's screen waits for worked out off
     * the main thread and the app composed in the frame after the launch screen's; where as
     * built, and on `main`, they're worked out where they're collected, on the main thread, and
     * the app is composed whenever the last is in. Tried, and not taken: the first screen shows
     * no sooner for it. [RowsFrameMetric] says when the app's composition starts each way.
     */
    @Test
    fun coldStartToHomeCachesKeptWithTheStartValuesOffMain() {
        withTheStartValuesOffMain()
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] with the player made on the main thread as the app is composed,
     * as it was while the screensaver's watch took it there, where as built it's made off the
     * main thread once Home has what it shows: [RowsFrameMetric] says what the app's composition
     * takes each way, and when the player is made.
     */
    @Test
    fun coldStartToHomeCachesKeptWithThePlayerMadeWhenComposed() {
        withThePlayerMade("whenComposed")
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKept] with the player asked for as the app is first composed and made
     * off the main thread beside it, its surface joining the screen when it comes: under the
     * launch screen, most often. Tried, and not taken: the player is there sooner and Home later.
     */
    @Test
    fun coldStartToHomeCachesKeptWithThePlayerAskedForAsComposed() {
        withThePlayerMade("asComposed")
        coldStartToHomeCachesKept()
    }

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

    /**
     * [coldStartToHomeCachesKeptOnALivedInHomeWithWhatsBelowMadeAtOnce] with the player made on
     * the main thread as the app is composed, as on `main`: the rows at once with what the
     * screens read and the guide window as built, which is `main` with those two changes in.
     * Its twin with the player as built says what the player's move is worth there.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithWhatsBelowMadeAtOnceAndThePlayerMadeWhenComposed() {
        withALivedInHome()
        withWhatsBelowHomeMade("atOnce")
        withThePlayerMade("whenComposed")
        coldStartToHomeCachesKept()
    }

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeAgain() = coldStartToHomeCachesKeptOnALivedInHome()

    /**
     * Second turns, and one test under another name, each named for its place in a set
     * ([coldStartToHomeCachesKeptAsOnMainOnceMore]): the single turns come straight after the
     * starts that aren't read, and each way with two turns has them about as far before the
     * set's middle as after it.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithWhatsBelowMadeAtOnceAndThePlayerMadeWhenComposedAgain() =
        coldStartToHomeCachesKeptOnALivedInHomeWithWhatsBelowMadeAtOnceAndThePlayerMadeWhenComposed()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithItsTilesMadeThreeAFrameToCheck() = coldStartToHomeCachesKeptOnALivedInHomeWithThreeTilesBelowAFrame()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithWhatsBelowMadeAtOnceAgain() = coldStartToHomeCachesKeptOnALivedInHomeWithWhatsBelowMadeAtOnce()

    /**
     * [coldStartToHomeCachesKeptOnALivedInHome] as a start is on `main`
     * ([coldStartToHomeCachesKeptAsOnMain]). Its twin has the player alone as built: made off the
     * main thread once Home has what it shows, where on `main` it's made on the main thread as
     * the app is composed. Made all at once, the rows below Home's edge take one long frame as
     * their contents come, and the player made once ready is being made about then:
     * [RowsFrameMetric] says what that frame and the ones around it take each way, and
     * `afterHomeKeyWaitMs` what a key would find.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeAsOnMain() {
        withALivedInHome()
        asOnMain()
        coldStartToHomeCachesKept()
    }

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeAsOnMainButThePlayerMadeOnceReady() {
        withALivedInHome()
        asOnMain(thePlayerToo = false)
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKeptOnALivedInHome] with the player made on the main thread as the
     * app is composed, as on `main`: what the player made once ready does to the frames in which
     * the rows below take their turns.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithThePlayerMadeWhenComposed() {
        withALivedInHome()
        withThePlayerMade("whenComposed")
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKeptOnALivedInHome] with the turns in the order the pieces asked, as
     * they first were. With no key that's all that differs from as built, where the pieces
     * nearest the screen go first whenever they ask: what the order alone does to the frames
     * after Home's.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithTheTurnsAsBefore() {
        withALivedInHome()
        withWhatsBelowHomeMade("asBefore")
        coldStartToHomeCachesKept()
    }

    /** The second turns, named for their places in a set ([coldStartToHomeCachesKeptAsOnMainOnceMore]). */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeAsOnMainOnceAgain() = coldStartToHomeCachesKeptOnALivedInHomeAsOnMain()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeAsOnMainButThePlayerMadeOnceReadySecondTime() = coldStartToHomeCachesKeptOnALivedInHomeAsOnMainButThePlayerMadeOnceReady()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithThePlayerMadeWhenComposedASecondTime() = coldStartToHomeCachesKeptOnALivedInHomeWithThePlayerMadeWhenComposed()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithTheTurnsAsBeforeASecondTime() = coldStartToHomeCachesKeptOnALivedInHomeWithTheTurnsAsBefore()

    /** [coldStartToHomeCachesKept] with what's below Home's screen edge made two pieces at a frame, where as built it's one. */
    @Test
    fun coldStartToHomeCachesKeptWithTwoPiecesBelowAFrame() {
        withWhatsBelowHomeMade("twoAFrame")
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKeptOnALivedInHome] with three tiles' worth of what's below Home's
     * screen edge made at a frame, where as built it's a piece a frame: a row's shell and a wide
     * tile still have a frame to themselves, and plain tiles go three together. A tile a frame
     * is a tile a refresh, however little of the refresh it takes; three are meant to fill two
     * refreshes, so that the tiles are done in two thirds of the time at frames twice as long.
     * [RowsFrameMetric] says what the frames after Home's take each way, and when all is made.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithThreeTilesBelowAFrame() {
        withALivedInHome()
        withWhatsBelowHomeMade("threeAFrame")
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
     * started from nothing with Android's caches kept, and once Home has taken its focus a key
     * every [FIRST_KEYS_GAP_MS], sent as a remote sends them, through the time in which the rows
     * below the screen's edge get their contents. [FIRST_KEYS] to the right along the dock and as
     * many back, which bring nothing into sight.
     *
     * The first key comes at a different moment in each iteration ([FIRST_KEYS_AFTER_FOCUS_MS]),
     * from the instant Home can take it to half a second later, the same moments for every way:
     * each way of making what's below has its own bad moment for a key (all at once, the long
     * frame the rows are made in; in turns, the while it takes them all to have had theirs), and
     * keys that all began at one moment would find one way's and miss the other's. Read each
     * iteration against the same one of the other way.
     *
     * As built the rows below are made in turns that keep out of a key's way; [FirstKeysMetric]
     * says how long each key waited and took, when its frame was drawn counted from ready, how
     * soon after Home's focus the keys began, and what the frames around the keys were.
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

    /**
     * [coldStartToHomeCachesKeptOnALivedInHome] with the world's guide window not read until
     * Home's rows below the screen's edge have their contents and are made, a second and a half
     * at most, where as built its read begins as Home is about to show: what Home's first
     * frames, and the rows' reads and turns, gain by having the processors and the database's
     * threads to themselves. With another screen than Home in front it doesn't wait.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithTheWholeGuideAfterItsRows() {
        withALivedInHome()
        withTheWholeGuideAfterTheFirstScreen()
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKeptOnALivedInHome] with what begins to be read before the first
     * world is in going on off the main thread once the world is in, where as built it stays on
     * the main thread for as long as it lasts. Home's rows begin their reads as Home's route is
     * composed, before the world; on the main thread each step of them then waits for a gap
     * between Home's first frames. The marks say when each row's contents come each way
     * (`Plasma.homeContinuingRead` and the others), and [RowsFrameMetric] what Home's first
     * frames take with those reads running beside them.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOver() {
        withALivedInHome()
        withReads(CHANGING_OVER)
        coldStartToHomeCachesKept()
    }

    /** [coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOver] and [coldStartToHomeCachesKeptOnALivedInHomeWithTheWholeGuideAfterItsRows] together. */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOverAndTheWholeGuideAfterItsRows() {
        withALivedInHome()
        withReads(CHANGING_OVER)
        withTheWholeGuideAfterTheFirstScreen()
        coldStartToHomeCachesKept()
    }

    /**
     * [coldStartToHomeCachesKeptOnALivedInHomeWithItsRowsMadeBeforeItShows] with the reads
     * changing over ([coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOver]): Home
     * waits for its rows' contents, and they come sooner.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithItsRowsMadeBeforeItShowsAndItsReadsChangingOver() {
        withALivedInHome()
        withWhatsBelowHomeMade("beforeItShows")
        withReads(CHANGING_OVER)
        coldStartToHomeCachesKept()
    }

    /** [firstKeysDownAndUpALivedInHome] with the reads changing over: the rows' contents come sooner, and the turns are done sooner. */
    @Test
    fun firstKeysDownAndUpALivedInHomeWithItsReadsChangingOver() {
        withALivedInHome()
        withReads(CHANGING_OVER)
        firstKeys(downTheRows = true)
    }

    /** [firstKeysDownAndUpALivedInHomeWithItsRowsMadeBeforeItShows] with the reads changing over. */
    @Test
    fun firstKeysDownAndUpALivedInHomeWithItsRowsMadeBeforeItShowsAndItsReadsChangingOver() {
        withALivedInHome()
        withWhatsBelowHomeMade("beforeItShows")
        withReads(CHANGING_OVER)
        firstKeys(downTheRows = true)
    }

    /**
     * The tests above under the names that put them in their places in a set
     * ([coldStartToHomeCachesKeptAsOnMainOnceMore]): as built once straight after the starts that
     * aren't read, to tell whether the session is still slow there; then each way's first turn,
     * the first keys in the middle, and the second turns in the opposite order, so that a drift
     * over the session falls on every way alike.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeAheadOfTheRest() = coldStartToHomeCachesKeptOnALivedInHome()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOverForAStart() = coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOver()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithTheWholeGuideAfterItsRowsToBeginWith() = coldStartToHomeCachesKeptOnALivedInHomeWithTheWholeGuideAfterItsRows()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOverAndTheWholeGuideAfterItsRowsEarlyOn() =
        coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOverAndTheWholeGuideAfterItsRows()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithItsRowsMadeBeforeItShowsAndItsReadsChangingOverEarlyOn() =
        coldStartToHomeCachesKeptOnALivedInHomeWithItsRowsMadeBeforeItShowsAndItsReadsChangingOver()

    @Test
    fun firstKeysDownAndUpALivedInHomeWithItsReadsChangingOverTheFirstTime() = firstKeysDownAndUpALivedInHomeWithItsReadsChangingOver()

    @Test
    fun firstKeysDownAndUpALivedInHomeWithItsRowsMadeBeforeItShowsAndItsReadsChangingOverAFirstTime() =
        firstKeysDownAndUpALivedInHomeWithItsRowsMadeBeforeItShowsAndItsReadsChangingOver()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithItsRowsMadeBeforeItShowsAndItsReadsChangingOverOnceMore() =
        coldStartToHomeCachesKeptOnALivedInHomeWithItsRowsMadeBeforeItShowsAndItsReadsChangingOver()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOverAndTheWholeGuideAfterItsRowsAgain() =
        coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOverAndTheWholeGuideAfterItsRows()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithTheWholeGuideAfterItsRowsAnotherTime() = coldStartToHomeCachesKeptOnALivedInHomeWithTheWholeGuideAfterItsRows()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOverAfresh() = coldStartToHomeCachesKeptOnALivedInHomeWithItsReadsChangingOver()

    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeInTheSecondHalf() = coldStartToHomeCachesKeptOnALivedInHome()

    /**
     * [coldStartToHomeCachesKeptOnALivedInHome] with Home waiting under the launch screen for
     * its rows' contents, and making all of it in its first frame: Home shows later, by as long
     * as the contents take to come and to be made, and complete, with no row left to make once
     * it's seen. Ready and the full display are then after Home's own frame, where as built
     * they're before it: read them with that said, and [RowsFrameMetric]'s frames after Home's
     * as the frames of Home fading in.
     */
    @Test
    fun coldStartToHomeCachesKeptOnALivedInHomeWithItsRowsMadeBeforeItShows() {
        withALivedInHome()
        withWhatsBelowHomeMade("beforeItShows")
        coldStartToHomeCachesKept()
    }

    /** [firstKeysDownAndUpALivedInHome] on a Home that waited for its rows ([coldStartToHomeCachesKeptOnALivedInHomeWithItsRowsMadeBeforeItShows]). */
    @Test
    fun firstKeysDownAndUpALivedInHomeWithItsRowsMadeBeforeItShows() {
        withALivedInHome()
        withWhatsBelowHomeMade("beforeItShows")
        firstKeys(downTheRows = true)
    }

    /** [firstKeysOnALivedInHome] with three tiles' worth made at a frame ([coldStartToHomeCachesKeptOnALivedInHomeWithThreeTilesBelowAFrame]). */
    @Test
    fun firstKeysOnALivedInHomeWithThreeTilesBelowAFrame() {
        withALivedInHome()
        withWhatsBelowHomeMade("threeAFrame")
        firstKeys(downTheRows = false)
    }

    /** [firstKeysOnALivedInHome] with the turns going on while keys come, where as built they keep out of a key's way. */
    @Test
    fun firstKeysOnALivedInHomeWithTurnsThroughKeys() {
        withALivedInHome()
        withWhatsBelowHomeMade("throughKeys")
        firstKeys(downTheRows = false)
    }

    /**
     * [firstKeysOnALivedInHome] with the keys going down Home's rows and back up to the dock: a
     * Down can bring rows into sight, and a row that hasn't had its turn by then is made whole in
     * that key's frame. It's the turns at their worst: someone who goes straight down the moment
     * Home can take a key finds every row still to make. As built the nearest rows have their
     * turns first and the turns keep out of the way for a while after every key. Made all at
     * once, the rows are made in one long frame as Home takes its focus, and a key that comes in
     * it waits it out: each key's time from ready says which way has someone further along.
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

    /**
     * [firstKeysDownAndUpALivedInHome] with three tiles' worth made at a frame
     * ([coldStartToHomeCachesKeptOnALivedInHomeWithThreeTilesBelowAFrame]): more of a row is
     * made by the time a Down shows it.
     */
    @Test
    fun firstKeysDownAndUpALivedInHomeWithThreeTilesBelowAFrame() {
        withALivedInHome()
        withWhatsBelowHomeMade("threeAFrame")
        firstKeys(downTheRows = true)
    }

    /** [firstKeysDownAndUpALivedInHome] with the turns going on while keys come: rows get made between one Down and the next. */
    @Test
    fun firstKeysDownAndUpALivedInHomeWithTurnsThroughKeys() {
        withALivedInHome()
        withWhatsBelowHomeMade("throughKeys")
        firstKeys(downTheRows = true)
    }

    /**
     * [firstKeysDownAndUpALivedInHome] with the turns as they first were: in the order the
     * pieces asked, and with a piece in the frame that answers a key. As built the pieces
     * nearest the screen go first and the frame that answers a key has no piece in it.
     */
    @Test
    fun firstKeysDownAndUpALivedInHomeWithTheTurnsAsBefore() {
        withALivedInHome()
        withWhatsBelowHomeMade("asBefore")
        firstKeys(downTheRows = true)
    }

    /**
     * [firstKeysDownAndUpALivedInHome] with the turns going on almost at once behind Up and Down,
     * where as built they wait after them as long as after any other key: what going on is worth
     * to the rows the next Down shows, and what it costs the frames of the key's own motion.
     * Tried, and not taken: the keys are no quicker for it on the whole.
     */
    @Test
    fun firstKeysDownAndUpALivedInHomeWithTurnsOnBehindUpAndDown() {
        withALivedInHome()
        withWhatsBelowHomeMade("onBehindUpAndDown")
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
                FirstKeysMetric(keys = 2 * FIRST_KEYS),
            ),
            iterations = ITERATIONS,
            setupBlock = {
                wakeTheTv()
                killProcess()
                since = deviceSeconds()
            },
        ) {
            // Home is composed in the frame after ready and takes its focus a frame later: a key
            // that comes before that isn't Home's to take. The wait for its word of that is
            // begun before the launch, so that it's listening when the word comes, and the
            // first key comes this iteration's time after it.
            val homesFocus = homesFocusAhead(since)
            startActivityAndWait()
            homesFocus()
            Thread.sleep(FIRST_KEYS_AFTER_FOCUS_MS[(iteration ?: 0) % FIRST_KEYS_AFTER_FOCUS_MS.size])
            repeat(FIRST_KEYS) { firstKey(if (downTheRows) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_RIGHT) }
            repeat(FIRST_KEYS) { firstKey(if (downTheRows) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_LEFT) }
            // The log has had its word of ready for a while: read now for the metrics that go by
            // it, and then the time for what was waiting on the remote to be made within the trace.
            waitForFullyDrawn(since, thenMs = AFTER_FIRST_KEYS_MS)
        }
    }

    /** One of the first keys, sent without waiting for Plasma to take it, and the time to the next. */
    private fun firstKey(keyCode: Int) {
        pressWithoutWaiting(keyCode)
        Thread.sleep(FIRST_KEYS_GAP_MS)
    }

    private companion object {
        const val ITERATIONS = 10

        /** The way of reading in which what begins before the first world is in goes on off the main thread once it is ([withReads]). */
        const val CHANGING_OVER = "changingOverAtTheWorld"

        /**
         * The first keys: this many out, along the dock from its first tile or down the rows
         * from the dock, and as many back to where focus began. Four rows down is Home's fourth.
         */
        const val FIRST_KEYS = 4

        /**
         * From Home's focus to the first key, iteration by iteration: five moments from at once
         * to half a second, each twice, as far before the middle of the ten as after it, so that
         * a drift over a test's iterations falls on every moment alike. The third and the fifth
         * iteration, which a set most often has traced, are the soonest and the middle one.
         */
        val FIRST_KEYS_AFTER_FOCUS_MS = longArrayOf(390, 130, 0, 520, 260, 260, 520, 0, 130, 390)

        /** Between two of the first keys: a person stepping along without stopping. */
        const val FIRST_KEYS_GAP_MS = 150L

        /** After the last key, for what was waiting on the remote to be made within the trace. */
        const val AFTER_FIRST_KEYS_MS = 2_000L
    }
}
