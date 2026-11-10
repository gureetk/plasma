// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import android.content.Intent
import android.util.Log
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

/** How Home and power-on tell Plasma why it opened (`HomeButton.EXTRA_REASON`). */
private const val EXTRA_REASON = "ca.gureet.plasma.launcher.extra.REASON"

/** A channel's row, as TalkBack reads it: its number, then its name ("1 Channel 1" in the Guide, "1, Channel 1, …" in Live TV). */
private val CHANNEL_ROW: Pattern = Pattern.compile("^\\d+,? Channel \\d+.*")

/** Long enough for a screen to come up on a slow emulator. */
private const val SCREEN_MS = 60_000L

/** Longer than Plasma's launch screen can stay, 20 s, from the start. */
private const val FULLY_DRAWN_S = 30

/** For the launch screen to fade, 200 ms, and the screen under it to settle. */
private const val AFTER_FULLY_DRAWN_MS = 1_000L

private const val TAG = "PlasmaBenchmark"

internal val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

/**
 * Plasma with brief 6.7's big source, 1,500 channels and 200,000 programs, made up by the app's
 * benchmark build, and set up for the benchmarks (`BenchmarkSetup`, #127). The first time, this
 * takes as long as adding them does.
 */
internal fun withBigSource() {
    val answer = device.executeShellCommand("content call --uri content://$PLASMA.setup --method bigSource")
    check("state=ready" in answer) { "Plasma's benchmark setup answered: $answer" }
}

/**
 * Keys go as they're pressed. UI Automator otherwise waits before each key for the screen to have
 * been still for half a second, which would hold back a run of keys.
 */
internal fun keysAtOnce() {
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
 * (Plasma reports it as its launch screen starts to go), then for the launch screen to fade.
 */
internal fun waitForFullyDrawn(since: Long) {
    // UI Automator splits a command at its spaces, without a shell, and logs it: the pattern has
    // no spaces, and doesn't match its own words in that log.
    val said = device.executeShellCommand("timeout $FULLY_DRAWN_S logcat -T $since.000 -m 1 -e Fully\\sdrawn\\s$PLASMA/")
    val line = said.lines().lastOrNull { "Fully drawn" in it }
    if (line == null) Log.w(TAG, "Plasma wasn't fully drawn in $FULLY_DRAWN_S s") else Log.i(TAG, "Plasma fully drawn in ${line.substringAfterLast(": ")}")
    Thread.sleep(AFTER_FULLY_DRAWN_MS)
}

/**
 * Plasma started afresh with [intent], the same way for each iteration: on its first screen once
 * the launch screen has gone, with its channels on screen.
 */
internal fun MacrobenchmarkScope.startAfresh(intent: Intent) {
    wakeTheTv()
    killProcess()
    val since = deviceSeconds()
    startActivityAndWait(intent)
    waitForFullyDrawn(since)
    device.waitForChannels()
}

/** Waits for a screen of channels, Live TV's list or the Guide. */
internal fun UiDevice.waitForChannels() {
    check(wait(Until.hasObject(By.desc(CHANNEL_ROW)), SCREEN_MS)) {
        "No channels on screen: ${dumpedScreen()}"
    }
}

/** What's on screen, as UI Automator sees it, for a failure's message. */
private fun UiDevice.dumpedScreen(): String {
    val out = java.io.ByteArrayOutputStream()
    dumpWindowHierarchy(out)
    return out.toString().take(DUMP_CHARS)
}

private const val DUMP_CHARS = 8_000
