// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The app's benchmark build, which the benchmarks measure. */
internal const val PLASMA = "ca.gureet.plasma.benchmark"

/** The trace section Plasma opens for each key, closed as the next frame is drawn (`KeyTrace`). */
internal const val KEY_TO_DRAW = "Plasma.keyToDraw"

/** How Home and power-on tell Plasma why it opened (`HomeButton.EXTRA_REASON`). */
private const val EXTRA_REASON = "ca.gureet.plasma.launcher.extra.REASON"

/** A channel's row, as TalkBack reads it: its number, then its name. */
private val CHANNEL_ROW: Pattern = Pattern.compile("^\\d+ Channel \\d+.*")

/** Long enough for Plasma's launch screen to have gone once the screen under it is there. */
private const val LAUNCH_SCREEN_MS = 2_500L

/** Long enough for a screen to come up on a slow emulator. */
private const val SCREEN_MS = 60_000L

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

/** Opens Plasma as Home does: Home Button, which the setup sets to Live TV's channel list. */
internal fun channelList(): Intent = openedAs("Home")

/** Opens Plasma as the TV turning on does: When the TV Turns On, which the setup sets to the Guide. */
internal fun guide(): Intent = openedAs("PowerOn")

private fun openedAs(reason: String) = Intent(Intent.ACTION_MAIN).apply {
    setClassName(PLASMA, "ca.gureet.plasma.MainActivity")
    putExtra(EXTRA_REASON, reason)
}

/** Waits for a screen of channels, Live TV's list or the Guide, then for the launch screen to go. */
internal fun UiDevice.waitForChannels() {
    check(wait(Until.hasObject(By.desc(CHANNEL_ROW)), SCREEN_MS)) {
        "No channels on screen: ${dumpedScreen()}"
    }
    Thread.sleep(LAUNCH_SCREEN_MS)
}

/** What's on screen, as UI Automator sees it, for a failure's message. */
private fun UiDevice.dumpedScreen(): String {
    val out = java.io.ByteArrayOutputStream()
    dumpWindowHierarchy(out)
    return out.toString().take(DUMP_CHARS)
}

private const val DUMP_CHARS = 8_000
