// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The app's benchmark build, which the benchmarks measure. */
internal const val PLASMA = "ca.gureet.plasma.benchmark"

/** The trace section Plasma opens for each key, closed as the next frame is drawn (`KeyTrace`). */
internal const val KEY_TO_DRAW = "Plasma.keyToDraw"

/** Plasma's section for how long a key waited to be taken up, as `KeyTrace` writes it: its name ends with the milliseconds. */
internal const val KEY_WAITED = "Plasma.keyWaited"

/** Plasma's mark for where Home is going, written each time that changes: its name ends with the place down the page. */
internal const val HOME_GOING_TO = "Plasma.homeGoingTo"

/** Plasma's mark for Home having taken its focus, which its log says too ([waitForHomesFocus]). */
internal const val HOME_TOOK_FOCUS = "Plasma.homeTookFocus"

/** How Home and power-on tell Plasma why it opened (`HomeButton.EXTRA_REASON`). */
private const val EXTRA_REASON = "ca.gureet.plasma.launcher.extra.REASON"

/** A channel's row, as TalkBack reads it: its number, then its name ("1 Channel 1" in the Guide, "1, Channel 1, …" in Live TV). */
private val CHANNEL_ROW: Pattern = Pattern.compile("^\\d+,? Channel \\d+.*")

/** Home's dock tile for Live TV, as TalkBack reads it, where focus starts on Home. */
private const val DOCK_LIVE_TV = "Live TV"

/** A made-up movie's or series' poster, as TalkBack reads it: its name and number first (`MadeUpSource`). */
private val TITLE: Pattern = Pattern.compile(
    "^(The Quiet Harbour|Ember|Signals from the Northern Observatory|Paper Kites|Low Orbit|The Orchard at the End of the Valley Road|" +
        "Salt|Velvet Hours|Night Desk|Small Claims|The Kingsway Files|Heat Index|Quiet Hours on the Coast|Tumble) \\d+.*",
)

/** For the posters on screen to load and fade in, from the made-up source on the TV itself. */
private const val POSTERS_MS = 2_000L

/** For the Guide's rows to settle once focus has jumped to a channel far down the list. */
private const val ROWS_MS = 2_000L

/** For the player's tries at a channel that plays nothing. */
private const val PICTURE_MS = 6_000L

/** After the ring has gone, for the picture's place to be drawn without it. */
private const val PICTURE_REST_MS = 1_000L

/** The turning ring, as TalkBack reads it (core/design's `loading`). */
private const val RING = "Loading"

/** Between keys along the dock, for each to land. */
private const val DOCK_STEP_MS = 300L

/** Long enough for a screen to come up on a slow emulator. */
private const val SCREEN_MS = 60_000L

/** Longer than Plasma's launch screen can stay, 20 s, from the start. */
private const val FULLY_DRAWN_S = 30

/**
 * For the launch screen to fade, 200 ms, and the screen under it to settle, with room to spare on a
 * slow device: StartupTimingMetric times full display by the first frame to end after
 * `reportFullyDrawn`, and stops with an error when the trace ends before one does.
 */
private const val AFTER_FULLY_DRAWN_MS = 3_000L

/** For the device to finish stopping Plasma before a start that failed is tried once more. */
private const val BEFORE_STARTING_AGAIN_MS = 2_000L

private const val TAG = "PlasmaBenchmark"

internal val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

/**
 * Before each benchmark: the big source, the backdrop behind Plasma, and keys at once. And, for a
 * run that asks for it (`-e plasma.mainThreadSamples true`), the main thread's stack in every
 * trace.
 */
internal fun setUpPlasma() {
    withBigSource()
    if (InstrumentationRegistry.getArguments().getString(MAIN_THREAD_SAMPLES) == "true") withMainThreadSampled()
    putBackdropBehind()
    keysAtOnce()
}

/** The run's argument that asks for [withMainThreadSampled] in every benchmark. */
private const val MAIN_THREAD_SAMPLES = "plasma.mainThreadSamples"

/**
 * Has Plasma write its main thread's stack into the trace every few milliseconds
 * (`MainThreadSamples`), for a run that looks at what the main thread does between frames: the
 * setup before each benchmark ([withBigSource]) turns it off again. Such a run's times are slower
 * than a plain run's.
 */
internal fun withMainThreadSampled() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method mainThread --arg sampled")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("mainThread=sampled" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * The benchmarks' own black screen, [Backdrop], in front, for Plasma Benchmark to start over. Each
 * time a benchmark stops Plasma Benchmark, the backdrop comes back rather than the TV's own Home,
 * which a launcher's Home button override (Plasma's own among them) would open over, and could
 * bring over Plasma Benchmark as it starts.
 */
private fun putBackdropBehind() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.startActivitySync(
        Intent(instrumentation.context, Backdrop::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
}

/**
 * Plasma with brief 6.7's big source, 1,500 channels and 200,000 programs, made up by the app's
 * benchmark build, and set up for the benchmarks (`BenchmarkSetup`, #127). The first time, this
 * takes as long as adding them does.
 */
private fun withBigSource() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method bigSource")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("state=ready" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Turns Settings › Appearance › Lighter Effects on, for a benchmark that measures with it: the
 * setup before each benchmark ([withBigSource]) turns it off again.
 */
internal fun withLighterEffects() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method lighterEffects --arg on")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("lighterEffects=on" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has every surface's focus motion set up as it was before it was set up on demand (#221), for a
 * benchmark that measures a screen both ways in one run: the setup before each benchmark
 * ([withBigSource]) puts it back. Plasma reads the choice as it starts.
 */
internal fun withFocusMotionAsBefore() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method focusMotion --arg asBefore")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("focusMotion=asBefore" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has every column of groups or categories make every one of its rows, as before #227, for a
 * benchmark that measures a screen's opening both ways in one run: the setup before each benchmark
 * ([withBigSource]) puts it back. Plasma reads the choice as it starts.
 */
internal fun withEveryCategoryRowMade() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method categoryRows --arg every")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("categoryRows=every" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has Home made again from nothing each time it comes back from under another screen, as before
 * it was kept made (#234), for a benchmark that measures the way back to Home both ways in one
 * run: the setup before each benchmark ([withBigSource]) puts it back. Plasma reads the choice as
 * it starts.
 */
internal fun withHomeMadeAgain() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method home --arg madeAgain")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("home=madeAgain" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has Home kept made and not placed while another screen is over it, as it was first kept (#234),
 * for a benchmark that measures the way back to Home with it laid out all along and without, in
 * one run: the setup before each benchmark ([withBigSource]) puts it back. Plasma reads the choice
 * as it starts.
 */
internal fun withHomeNotPlacedUnder() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method home --arg notPlaced")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("home=notPlaced" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has Home's drawing left recorded while another screen is over it, behind a layer that draws
 * nothing, so that its kept layers keep their textures and needn't be painted again as it comes
 * back, for a benchmark that measures the way back to Home both ways in one run, and what the
 * textures weigh in graphics memory: the setup before each benchmark ([withBigSource]) puts it
 * back. Plasma reads the choice as it starts.
 */
internal fun withHomesLayersHeld() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method home --arg layersHeld")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("home=layersHeld" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has what the screens read worked out where it's collected, on the main thread, and the parental
 * words' patterns made as the words are, as before both were moved, for a benchmark that measures
 * the way back to Home both ways in one run: the setup before each benchmark ([withBigSource])
 * puts it back. Plasma reads the choice as it starts.
 */
internal fun withReadsAsBefore() = withReads("asBefore")

/**
 * Has what the screens read worked out [way], another way than as built, for a benchmark that
 * measures a start or the way back to Home each way in one run: `asBefore`, `offMainFromTheStart`
 * (moved off the main thread with nothing of Home's held until its world is in, as it was first
 * moved) or `heldUntilTheWorld` (moved, and what Home reads besides its world not read until the
 * world is in). The setup before each benchmark ([withBigSource])
 * puts it back. Plasma reads the choice as it starts.
 */
internal fun withReads(way: String) {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method reads --arg $way")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("reads=$way" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has the world's guide window read beside its first look, as before, where as built it waits
 * for the first look to be out, for a benchmark that measures a start both ways in one run: the
 * setup before each benchmark ([withBigSource]) puts it back. Plasma reads the choice as it starts.
 */
internal fun withTheWholeGuideBesideTheFirstLook() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method wholeGuide --arg besideFirstLook")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("wholeGuide=besideFirstLook" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has the world asked for as Plasma's screen is made, ahead of the screens, where as built nothing
 * asks for it until the first screen's composition does (tried, and not taken), for a benchmark
 * that measures a start both ways in one run: the setup before each benchmark ([withBigSource])
 * puts it back. Plasma reads the choice as it starts.
 */
internal fun withTheWorldAskedForAhead() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method world --arg askedAhead")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("world=askedAhead" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has what's below Home's screen edge (its rows, and each row's tiles) made another way than as
 * built, one piece at a frame: [how] is `atOnce` (all in the frame their contents come, as before
 * they took turns) or `twoAFrame`, for a benchmark that measures a start each way in one run. The
 * setup before each benchmark ([withBigSource]) puts it back. Plasma reads the choice as it starts.
 */
internal fun withWhatsBelowHomeMade(how: String) {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method homeBelow --arg $how")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("homeBelow=$how" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has the four values Plasma's screen waits for as it starts worked out where they're collected,
 * on the main thread, and the app composed whenever the last of them is in, as before; where as
 * built they're worked out off the main thread and the app is composed in the frame after the
 * launch screen's. For a benchmark that measures a start both ways in one run: the setup before
 * each benchmark ([withBigSource]) puts it back. Plasma reads the choice as it starts.
 */
internal fun withTheStartValuesWhereCollected() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method startValues --arg whereCollected")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("startValues=whereCollected" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has the player made another way than as built, which is asked for as the app is first composed
 * and made off the main thread beside it: [whenMade] is `whenComposed` (on the main thread in the
 * app's composition, as it was while the screensaver's watch took it there) or `onceReady` (off
 * the main thread once the first screen has what it shows, as it was first moved there). For a
 * benchmark that measures a start each way in one run: the setup before each benchmark
 * ([withBigSource]) puts it back. Plasma reads the choice as it starts.
 */
internal fun withThePlayerMade(whenMade: String) {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method player --arg $whenMade")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("player=$whenMade" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Gives Home the rows of a TV that's been used a while: favorite channels, so that Favorite
 * Channels is its first row, and movies left part-way, so that Continue Watching has tiles. On a
 * new install, as the setup before each benchmark ([withBigSource]) leaves it again, Home's first
 * row is Continue Watching with nothing in it.
 */
internal fun withALivedInHome() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method homeRows --arg livedIn")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("homeRows=livedIn" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has a guide window read as one query, as before it was read a few guide channels at a time,
 * for a benchmark that measures the Guide's day both ways in one run: the setup before each
 * benchmark ([withBigSource]) puts it back. Plasma reads the choice as it starts.
 */
internal fun withTheGuidesDayReadInOnePiece() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method guideDay --arg inOnePiece")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("guideDay=inOnePiece" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has Live TV make its rows at once as it opens, with nothing kept made, as before #229, for a
 * benchmark that measures its opening both ways in one run: the setup before each benchmark
 * ([withBigSource]) puts it back. Plasma reads the choice as it starts.
 */
internal fun withLiveTvsRowsMadeAtOpening() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method liveTvRows --arg atOpening")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("liveTvRows=atOpening" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Has the Guide as it was at a step before the last, for a benchmark that measures the Guide each
 * way in one run: [cells] is `byPlace` (cells told apart by their places in a row, as on `main`),
 * `placingThemselves` (each keeping its program, #222 alone), `placedByTheRow` (placed by their
 * row, #223, without a cell handed on to the program that comes in) or `handedOn` (#223, with the
 * time header's labels and a cell's words as they were before #225). The setup before each
 * benchmark ([withBigSource]) puts it back. Plasma reads the choice as it starts.
 */
internal fun withGuideCells(cells: String) {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method guideCells --arg $cells")
    Log.i(TAG, "Plasma's benchmark setup: ${answer.trim()}")
    check("guideCells=$cells" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Keys go as they're pressed. UI Automator otherwise waits before each key for the screen to have
 * been still for half a second, which would hold back a run of keys.
 */
private fun keysAtOnce() {
    Configurator.getInstance().waitForIdleTimeout = 0
}

/**
 * Wakes the TV, and ends its screensaver if it has come on: a TV can start it after a few minutes
 * without a key, and UI Automator wakes only a TV whose screen is off. An awake TV ignores it.
 */
internal fun wakeTheTv() {
    device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
}

/** Opens Plasma as Home does: Home Button, which the setup sets to Live TV's channel list. */
internal fun channelList(): Intent = openedAs("Home")

/** Opens Plasma as its icon does, on its own Home. */
internal fun home(): Intent = Intent(Intent.ACTION_MAIN).setClassName(PLASMA, "ca.gureet.plasma.MainActivity")

/** Opens Plasma as the TV turning on does: When the TV Turns On, which the setup sets to the Guide. */
internal fun guide(): Intent = openedAs("PowerOn")

private fun openedAs(reason: String) = Intent(Intent.ACTION_MAIN).apply {
    setClassName(PLASMA, "ca.gureet.plasma.MainActivity")
    putExtra(EXTRA_REASON, reason)
}

/** The device's clock, in seconds, for [waitForFullyDrawn] to read its log from. */
internal fun deviceSeconds(): Long = device.executeShellCommand("date +%s").trim().toLong()

/**
 * Waits for Android to hear that Plasma is ready to use, as the device's log says from [since] on
 * (Plasma reports it as its launch screen starts to go), then for the launch screen to fade and
 * the first screen's rows to come: [thenMs]. A test that presses keys as the first screen shows
 * calls it after its keys, when the log has had the word for a while, for what it reads from it.
 */
internal fun waitForFullyDrawn(since: Long, thenMs: Long = AFTER_FULLY_DRAWN_MS) {
    // UI Automator splits a command at its spaces, without a shell, and logs it: the pattern has
    // no spaces, and doesn't match its own words in that log.
    val said = device.executeShellCommand("timeout $FULLY_DRAWN_S logcat -T $since.000 -m 1 -e Fully\\sdrawn\\s$PLASMA/")
    val line = said.lines().lastOrNull { "Fully drawn" in it }
    FullyDrawnCheckMetric.seenInLog = line != null
    StartupSplitMetric.loggedFullyDrawnMs = line?.let { StartupSplitMetric.millisOf(it.substringAfterLast(": ")) }
    if (line == null) Log.w(TAG, "Plasma wasn't fully drawn in $FULLY_DRAWN_S s") else Log.i(TAG, "Plasma fully drawn in ${line.substringAfterLast(": ")}")
    Thread.sleep(thenMs)
}

/**
 * Waits for Home to have taken its focus, as Plasma's log says from [since] on. Home takes it a
 * frame after it's composed, and a key that comes sooner goes to whatever had focus before (the
 * top bar, where a key that finds nothing focused puts it), before Home takes it to the dock all
 * the same: from here on a key goes where Home means it to.
 */
internal fun waitForHomesFocus(since: Long) {
    // As for the log's word of ready: no spaces in the pattern, and it doesn't match its own words.
    val said = device.executeShellCommand("timeout $FULLY_DRAWN_S logcat -T $since.000 -m 1 -e Home\\stook\\sfocus")
    check(said.lines().any { "Home took focus" in it }) { "Home didn't take its focus in $FULLY_DRAWN_S s" }
}

/**
 * A key pressed and let go without waiting for Plasma to take it, as a remote sends it. UI
 * Automator's own keys return once the app has dealt with them: a key pressed while a long frame
 * holds the main thread would have its letting go wait for the frame, the next key would come
 * that much later, and the hold would show in no key's time. The key's own time is now, so
 * Plasma's measure of its wait ([KEY_WAITED]) counts from here.
 */
internal fun pressWithoutWaiting(keyCode: Int) {
    // The connection UI Automator and the benchmark library use: the same flags give the same one.
    val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(Configurator.getInstance().uiAutomationFlags)
    val now = SystemClock.uptimeMillis()
    for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
        val event = KeyEvent(now, now, action, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)
        if (!automation.injectInputEvent(event, false)) Log.w(TAG, "A key didn't go out: $event")
    }
}

/**
 * Plasma started afresh with [intent], the same way for each iteration: on its first screen once
 * the launch screen has gone, with its channels on screen.
 *
 * A start after which the benchmark library finds Plasma not running is tried once more: an
 * emulator that has just booted has had one such start in a few hundred, and a run shouldn't lose
 * a test to it. The log says so when it happens, and a second failure is the test's.
 */
internal fun MacrobenchmarkScope.startAfresh(intent: Intent, ready: UiDevice.() -> Unit = { waitForChannels() }) {
    wakeTheTv()
    killProcess()
    var since = deviceSeconds()
    try {
        startActivityAndWait(intent)
    } catch (notRunning: IllegalStateException) {
        Log.w(TAG, "Plasma wasn't running after its start: starting it once more", notRunning)
        killProcess()
        Thread.sleep(BEFORE_STARTING_AGAIN_MS)
        since = deviceSeconds()
        startActivityAndWait(intent)
    }
    waitForFullyDrawn(since)
    device.ready()
}

/** Waits for a screen of channels, Live TV's list or the Guide. */
internal fun UiDevice.waitForChannels() {
    check(wait(Until.hasObject(By.desc(CHANNEL_ROW)), SCREEN_MS)) {
        "No channels on screen: ${dumpedScreen()}"
    }
}

/**
 * In the Guide, focus taken to the row of the channel numbered [number], as its number typed on the
 * remote and OK take it there, at the same time of day. Then a moment for the rows around it.
 */
internal fun UiDevice.toGuideChannel(number: Int) {
    for (digit in number.toString()) pressKeyCode(KeyEvent.KEYCODE_0 + (digit - '0'))
    pressDPadCenter()
    check(wait(Until.hasObject(By.desc("$number Channel $number")), SCREEN_MS)) { "Channel $number's row isn't on screen: ${dumpedScreen()}" }
    Thread.sleep(ROWS_MS)
}

/**
 * Waits for the ring in the Guide's picture to stop. The big source's channels play nothing, and
 * while the player still tries the picture's, a ring turns in it: a frame at every refresh, which
 * would be most of the frames measured, and in each key's way. It waits a while for the player's
 * tries, then for as long as the ring is still on screen, and then a moment more.
 */
internal fun UiDevice.letThePictureSettle() {
    Thread.sleep(PICTURE_MS)
    if (!wait(Until.gone(By.desc(RING)), SCREEN_MS)) Log.w(TAG, "The picture's ring is still turning")
    Thread.sleep(PICTURE_REST_MS)
}

/**
 * Waits for Home, with its dock, and gives the rows under it a moment for their posters. Its first
 * row, Continue Watching, has nothing in it on a new install, so New in Movies and New in Series
 * are below the screen's edge until focus goes down to them.
 */
internal fun UiDevice.waitForHome() {
    check(wait(Until.hasObject(By.desc(DOCK_LIVE_TV)), SCREEN_MS)) { "No Home on screen: ${dumpedScreen()}" }
    Thread.sleep(POSTERS_MS)
}

/**
 * From Home, with focus on the dock's first tile (Live TV), the tile [steps] to its right, opened:
 * 2 for Movies and 3 for Series, past the Guide. Then waits for its titles, and their posters.
 */
internal fun UiDevice.openFromDock(steps: Int) {
    toDockTile(steps)
    pressDPadCenter()
    waitForTitles()
}

/** From Home, with focus on the dock's first tile (Live TV), focus moved to the tile [steps] to its right. */
internal fun UiDevice.toDockTile(steps: Int) {
    repeat(steps) {
        pressDPadRight()
        Thread.sleep(DOCK_STEP_MS)
    }
}

/** Waits for a screen of titles, Movies' or Series', and for their posters. */
internal fun UiDevice.waitForTitles() {
    check(wait(Until.hasObject(By.desc(TITLE)), SCREEN_MS)) { "No titles on screen: ${dumpedScreen()}" }
    Thread.sleep(POSTERS_MS)
}

/** The words on screen, as UI Automator sees them (each node's text and description), for a failure's message. */
private fun UiDevice.dumpedScreen(): String {
    val out = java.io.ByteArrayOutputStream()
    dumpWindowHierarchy(out)
    return WORDS.findAll(out.toString()).map { "${it.groupValues[1]}=${it.groupValues[2]}" }.joinToString(" | ").take(DUMP_CHARS)
}

private val WORDS = Regex("(text|content-desc)=\"([^\"]+)\"")

private const val DUMP_CHARS = 8_000
